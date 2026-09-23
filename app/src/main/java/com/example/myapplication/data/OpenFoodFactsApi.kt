package com.example.myapplication.data

import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface OpenFoodFactsApi {

    @GET("api/v2/product/{barcode}.json")
    suspend fun getProduktByBarcode(
        @Path("barcode") barcode: String
    ): ProductResponse

    @GET("cgi/search.pl?search_simple=1&action=process&json=1&page_size=10&lc=de")
    suspend fun searchProduktByName(
        @Query("search_terms") name: String
    ): SearchResponse

    companion object {
        private const val BASE_URL = "https://de.openfoodfacts.org/"
        private const val FALLBACK_HOST = "world.openfoodfacts.org"
        private const val USER_AGENT = "KuehlschrankProfi - Android - Version 1.1 - https://github.com/KuehlschrankProfi"

        fun create(): OpenFoodFactsApi {
            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val originalRequest = chain.request()
                    val requestWithHeaders = originalRequest.newBuilder()
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "application/json")
                        .build()

                    var response = chain.proceed(requestWithHeaders)

                    // Transient error handling & retry with fallback host on HTTP 503, 502, 504, 429
                    if (!response.isSuccessful && response.code in listOf(503, 502, 504, 429)) {
                        response.close()

                        val fallbackUrl = requestWithHeaders.url.newBuilder()
                            .host(FALLBACK_HOST)
                            .build()
                        val fallbackRequest = requestWithHeaders.newBuilder()
                            .url(fallbackUrl)
                            .build()

                        try {
                            Thread.sleep(500)
                        } catch (_: InterruptedException) {
                            Thread.currentThread().interrupt()
                        }

                        response = chain.proceed(fallbackRequest)

                        if (!response.isSuccessful && response.code in listOf(503, 502, 504, 429)) {
                            response.close()
                            try {
                                Thread.sleep(1000)
                            } catch (_: InterruptedException) {
                                Thread.currentThread().interrupt()
                            }
                            response = chain.proceed(fallbackRequest)
                        }
                    }
                    response
                }
                .build()

            val lenientGson = GsonBuilder()
                .registerTypeAdapter(Double::class.javaObjectType,
                    JsonDeserializer<Double?> { json, _, _ ->
                        try {
                            if (json == null || json.isJsonNull) null
                            else if (json.isJsonPrimitive) {
                                val prim = json.asJsonPrimitive
                                if (prim.isNumber) prim.asDouble
                                else if (prim.isString) prim.asString.toDoubleOrNull()
                                else null
                            } else null
                        } catch (_: Exception) { null }
                    })
                .registerTypeAdapter(Double::class.java, JsonDeserializer<Double> { json, _, _ ->
                    try {
                        if (json != null && json.isJsonPrimitive) {
                            val prim = json.asJsonPrimitive
                            if (prim.isNumber) prim.asDouble
                            else prim.asString.toDoubleOrNull() ?: 0.0
                        } else 0.0
                    } catch (_: Exception) { 0.0 }
                })
                .registerTypeAdapter(String::class.java, JsonDeserializer<String?> { json, _, _ ->
                    try {
                        if (json == null || json.isJsonNull) null
                        else if (json.isJsonPrimitive) json.asString
                        else null
                    } catch (_: Exception) { null }
                })
                .create()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create(lenientGson))
                .build()
                .create(OpenFoodFactsApi::class.java)
        }
    }
}
