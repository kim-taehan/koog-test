package develop.x.adapter.inbound.web

import develop.x.application.port.inbound.StreamChatUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/**
 * Inbound adapter — 도메인 토큰을 SSE 프레임으로만 변환. 비즈니스 로직 없음.
 * sessionId 가 없으면 새 대화를 생성하고, 첫 이벤트로 sessionId 를 내려줌.
 */
@RestController
class ChatSseController(
    private val streamChat: StreamChatUseCase,
) {
    data class ChatRequest(
        val prompt: String,
        val sessionId: String? = null,
    )

    @PostMapping("/sse", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun sse(@RequestBody request: ChatRequest): Flow<ServerSentEvent<String>> {
//        val result = streamChat.stream(request.prompt, request.sessionId)
        return flow {
            emit(ServerSentEvent.builder(result.sessionId).event("session").build())
            result.tokens.collect { token ->
                emit(ServerSentEvent.builder(token.value).build())
            }
        }
    }
}
