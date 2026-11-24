package com.zip2git.data.remote

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Builds the single OkHttp/Retrofit stack used by the app. The token is not baked
 * in here; it is supplied per-request by [GitHubRepository] through a holder so a
 * token change takes effect immediately without rebuilding the client.
 */
object NetworkModule {

    const val API_BASE_URL = "https://api.github.com/"
    const val API_VERSION = "2022-11-28"
    const val USER_AGENT = "Zip2Git-Android"

    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
        isLenient = true
    }

    /** Mutable token holder read by [AuthInterceptor] on every request. */
    @Volatile
    var token: String? = null

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor { token })
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    val gitHubApi: GitHubApi by lazy { retrofit.create(GitHubApi::class.java) }
}
