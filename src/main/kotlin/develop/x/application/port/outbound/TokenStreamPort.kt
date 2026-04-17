package develop.x.application.port.outbound

import develop.x.domain.ChatMessage
import develop.x.domain.ChatToken
import kotlinx.coroutines.flow.Flow

fun interface TokenStreamPort {
    fun stream(messages: List<ChatMessage>): Flow<ChatToken>
}
