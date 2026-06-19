package com.pinakes.app.data.repository

import com.pinakes.app.data.model.MessageRequest
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.network.NetworkModule
import com.pinakes.app.data.network.apiCall

/** "Message the library" contact form (`POST /messages`). */
class MessagesRepository(private val network: NetworkModule) {

    suspend fun send(subject: String, body: String): ApiResult<Unit> {
        val api = network.api()
        return apiCall {
            api.sendMessage(
                MessageRequest(
                    subject = subject.trim().take(255),
                    body = body.trim().take(5000),
                )
            )
        }
    }
}
