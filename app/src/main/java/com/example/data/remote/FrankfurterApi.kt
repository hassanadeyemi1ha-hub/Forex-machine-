package com.example.data.remote

import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class FrankfurterLatestResponse(
    val amount: Double = 1.0,
    val base: String = "USD",
    val date: String = "",
    val rates: Map<String, Double> = emptyMap()
)

interface FrankfurterService {
    @GET("latest")
    suspend fun getLatestRates(
        @Query("from") base: String = "USD",
        @Query("to") symbols: String = "EUR,GBP,JPY,CHF,AUD,CAD"
    ): FrankfurterLatestResponse

    companion object {
        private const val BASE_URL = "https://api.frankfurter.app/"

        fun create(): FrankfurterService {
            val client = OkHttpClient.Builder()
                .connectTimeout(4, TimeUnit.SECONDS)
                .readTimeout(4, TimeUnit.SECONDS)
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create())
                .build()
                .create(FrankfurterService::class.java)
        }
    }
}

interface CryptoSpotService {
    @GET("simple/price")
    suspend fun getSpotPrices(
        @Query("ids") ids: String = "bitcoin,ethereum,solana,ripple",
        @Query("vs_currencies") vsCurrencies: String = "usd"
    ): Map<String, Map<String, Double>>

    companion object {
        private const val BASE_URL = "https://api.coingecko.com/api/v3/"

        fun create(): CryptoSpotService {
            val client = OkHttpClient.Builder()
                .connectTimeout(4, TimeUnit.SECONDS)
                .readTimeout(4, TimeUnit.SECONDS)
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create())
                .build()
                .create(CryptoSpotService::class.java)
        }
    }
}
