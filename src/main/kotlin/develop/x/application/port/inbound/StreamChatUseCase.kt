package develop.x.application.port.inbound

import develop.x.domain.ChatToken
import kotlinx.coroutines.flow.Flow

interface StreamChatUseCase {
    data class Result(val sessionId: String, val tokens: Flow<ChatToken>)

    fun stream(prompt: String, sessionId: String?): Result
}
