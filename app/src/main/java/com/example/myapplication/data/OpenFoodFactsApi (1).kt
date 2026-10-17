package com.example.myapplication.data

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query

interface OpenFoodFactsApi {

    @Headers("User-Agent: KuehlschrankProfi - Android - Version 1.1")
    @GET("api/v2/product/{barcode}.json")
    suspend fun getProduktByBarcode(
        @Path("barcode") barcode: String
    ): ProductResponse

    @Headers("User-Agent: KuehlschrankProfi - Android - Version 1.1")
    @GET("cgi/search.pl?search_simple=1&action=process&json=1&page_size=5")
    suspend fun searchProduktByName(
        @Query("search_terms") name: String
    ): SearchResponse

    companion object {
        private const val BASE_URL = "https://world.openfoodfacts.org/"

        fun create(): OpenFoodFactsApi {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(OpenFoodFactsApi::class.java)
        }
    }
}
