package com.zip2git.data.remote

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds the headers every GitHub REST call needs:
 *  - `Authorization: Bearer <pat>` when a token is present
 *  - `Accept: application/vnd.github+json`
 *  - `X-GitHub-Api-Version: 2022-11-28`
 *  - a stable `User-Agent` (GitHub rejects requests without one)
 *
 * A 401/403 body is read once here so the caller can show the API's own message;
 * the body is then rebuilt for the normal converter.
 */
class AuthInterceptor(private val tokenProvider: () -> String?) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val builder = original.newBuilder()
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", NetworkModule.API_VERSION)
            .header("User-Agent", NetworkModule.USER_AGENT)

        tokenProvider()?.takeIf { it.isNotBlank() }?.let {
            builder.header("Authorization", "Bearer $it")
        }

        return chain.proceed(builder.build())
    }
}
