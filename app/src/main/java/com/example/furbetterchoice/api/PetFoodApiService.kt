package com.example.furbetterchoice.api

import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface PetFoodApiService {

    @GET("api/v2/product/{barcode}.json")
    suspend fun getProduct(@Path("barcode") barcode: String): Response<ProductResponse>

    @GET("api/v2/search")
    suspend fun searchProducts(
        @Query("categories_tags_en") category: String,
        @Query("sort_by") sortBy: String = "nutriscore_score",
        @Query("fields") fields: String = "code,product_name,brands,image_url,nutriscore_grade,ecoscore_grade,categories_tags,nutriments,packaging_text,packaging_tags,labels_tags,ingredients_text",
        @Query("page_size") pageSize: Int = 10
    ): Response<SearchResponse>

    companion object {
        private const val PET_BASE_URL = "https://world.openpetfoodfacts.org/"
        private const val FOOD_BASE_URL = "https://world.openfoodfacts.org/"

        fun create(useGeneralFoodDb: Boolean = false): PetFoodApiService {
            val url = if (useGeneralFoodDb) FOOD_BASE_URL else PET_BASE_URL
            return Retrofit.Builder()
                .baseUrl(url)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(PetFoodApiService::class.java)
        }
    }
}
