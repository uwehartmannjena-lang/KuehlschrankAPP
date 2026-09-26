package com.example.myapplication.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class BillingManager(private val context: Context, private val coroutineScope: CoroutineScope) : PurchasesUpdatedListener {

    private val _isSubscribed = MutableStateFlow(false)
    val isSubscribed: StateFlow<Boolean> = _isSubscribed

    private val _subscriptionDetails = MutableStateFlow<ProductDetails?>(null)
    val subscriptionDetails: StateFlow<ProductDetails?> = _subscriptionDetails

    private lateinit var billingClient: BillingClient
    private val subProductId = "pro_subscription_monthly" // Platzhalter für Play Store ID

    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 3

    fun startConnection() {
        billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases()
            .build()

        connectToPlayBilling()
    }

    private fun connectToPlayBilling() {
        if (!::billingClient.isInitialized) return

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    reconnectAttempts = 0
                    Log.d("BillingManager", "Billing setup finished successfully")
                    queryPurchases()
                    queryProductDetails()
                } else {
                    Log.w(
                        "BillingManager",
                        "Billing setup failed with code ${billingResult.responseCode}: ${billingResult.debugMessage}"
                    )
                }
            }

            override fun onBillingServiceDisconnected() {
                if (reconnectAttempts < maxReconnectAttempts) {
                    reconnectAttempts++
                    Log.w("BillingManager", "Billing service disconnected. Retry attempt $reconnectAttempts/$maxReconnectAttempts")
                    connectToPlayBilling()
                } else {
                    Log.e("BillingManager", "Billing service disconnected. Max retry attempts reached.")
                }
            }
        })
    }

    fun queryPurchases() {
        if (!::billingClient.isInitialized || !billingClient.isReady) {
            Log.w("BillingManager", "BillingClient is not ready for queryPurchases")
            return
        }

        coroutineScope.launch {
            try {
                val params = QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()

                val purchasesResult = billingClient.queryPurchasesAsync(params)
                if (purchasesResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    processPurchases(purchasesResult.purchasesList)
                } else {
                    Log.w(
                        "BillingManager",
                        "Query purchases failed with code ${purchasesResult.billingResult.responseCode}: ${purchasesResult.billingResult.debugMessage}"
                    )
                }
            } catch (e: Exception) {
                Log.e("BillingManager", "Error querying purchases", e)
            }
        }
    }

    private fun queryProductDetails() {
        if (!::billingClient.isInitialized || !billingClient.isReady) {
            Log.w("BillingManager", "BillingClient is not ready for queryProductDetails")
            return
        }

        val queryProductDetailsParams = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(subProductId)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            )
            .build()

        billingClient.queryProductDetailsAsync(queryProductDetailsParams) { billingResult, productDetailsList ->
            when (billingResult.responseCode) {
                BillingClient.BillingResponseCode.OK -> {
                    if (productDetailsList.isNotEmpty()) {
                        _subscriptionDetails.value = productDetailsList.first()
                        Log.d("BillingManager", "Fetched product details successfully: ${productDetailsList.first()}")
                    } else {
                        Log.w("BillingManager", "Product details list is empty for SKU: $subProductId")
                    }
                }
                BillingClient.BillingResponseCode.ITEM_UNAVAILABLE,
                BillingClient.BillingResponseCode.DEVELOPER_ERROR -> {
                    Log.w(
                        "BillingManager",
                        "Product details unavailable (code ${billingResult.responseCode}: ${billingResult.debugMessage}). " +
                        "This occurs if package is not published or item '$subProductId' is not configured in Google Play Console."
                    )
                }
                else -> {
                    Log.e(
                        "BillingManager",
                        "Failed to query product details (code ${billingResult.responseCode}: ${billingResult.debugMessage})"
                    )
                }
            }
        }
    }

    fun launchBillingFlow(activity: Activity) {
        val productDetails = _subscriptionDetails.value ?: return
        val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: return

        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .setOfferToken(offerToken)
                .build()
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        billingClient.launchBillingFlow(activity, billingFlowParams)
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            processPurchases(purchases)
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.i("BillingManager", "User canceled purchase")
        } else {
            Log.e("BillingManager", "Purchase failed: ${billingResult.responseCode}")
        }
    }

    private fun processPurchases(purchases: List<Purchase>) {
        var hasActiveSub = false
        for (purchase in purchases) {
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                if (purchase.products.contains(subProductId)) {
                    hasActiveSub = true
                    if (!purchase.isAcknowledged) {
                        acknowledgePurchase(purchase.purchaseToken)
                    }
                }
            }
        }
        _isSubscribed.value = hasActiveSub
    }

    private fun acknowledgePurchase(purchaseToken: String) {
        val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchaseToken)
            .build()
        
        billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                _isSubscribed.value = true
            }
        }
    }
}
