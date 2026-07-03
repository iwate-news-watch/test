package com.example.taskmanager.data.remote

import android.content.Context
import com.example.taskmanager.auth.ServerConfig
import com.example.taskmanager.auth.TokenStore
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {
    @Volatile
    private var cachedBaseUrl: String? = null

    @Volatile
    private var cachedRetrofit: Retrofit? = null

    private fun retrofit(context: Context): Retrofit {
        val baseUrl = ServerConfig.getBaseUrl(context)
        cachedRetrofit?.let { if (cachedBaseUrl == baseUrl) return it }

        val appContext = context.applicationContext
        val gson = GsonBuilder().serializeNulls().create()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val token = TokenStore.getToken(appContext)
                val request = if (token != null) {
                    chain.request().newBuilder().addHeader("Authorization", "Bearer $token").build()
                } else {
                    chain.request()
                }
                chain.proceed(request)
            }
            .build()

        val built = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

        cachedBaseUrl = baseUrl
        cachedRetrofit = built
        return built
    }

    fun authApi(context: Context): AuthApi = retrofit(context).create(AuthApi::class.java)

    fun taskApi(context: Context): TaskApi = retrofit(context).create(TaskApi::class.java)
}
