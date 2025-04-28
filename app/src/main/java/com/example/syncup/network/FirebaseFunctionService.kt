package com.example.syncup.network

import com.example.syncup.model.MessageRequest
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface FirebaseFunctionService {
    @POST("sendMessage") // Endpoint sesuai nama Firebase Function kamu
    suspend fun sendMessage(@Body messageRequest: MessageRequest): Response<ResponseBody>
}
