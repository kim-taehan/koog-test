package develop.x.adapter.inbound.web

import develop.x.application.port.inbound.StreamChatUseCase
import develop.x.domain.ChatToken
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.test.StepVerifier

@WebFluxTest(ChatSseController::class)
class ChatSseControllerTest {

    @Autowired
    private lateinit var client: WebTestClient

    @MockitoBean
    private lateinit var streamChat: StreamChatUseCase

    @Test
    fun `POST sse returns event stream with session and tokens`() {
        val tokens = flowOf(ChatToken("hello"), ChatToken("world"))
        whenever(streamChat.stream(eq("hi"), isNull()))
            .thenReturn(StreamChatUseCase.Result("test-session-id", tokens))

        val result = client.post().uri("/sse")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"prompt":"hi"}""")
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
            .returnResult(String::class.java)

        StepVerifier.create(result.responseBody)
            .expectNext("test-session-id")
            .expectNext("hello")
            .expectNext("world")
            .verifyComplete()
    }

    @Test
    fun `POST sse with sessionId passes it to use case`() {
        val tokens = flowOf(ChatToken("ok"))
        whenever(streamChat.stream(eq("question"), eq("existing-sid")))
            .thenReturn(StreamChatUseCase.Result("existing-sid", tokens))

        val result = client.post().uri("/sse")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"prompt":"question","sessionId":"existing-sid"}""")
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk
            .returnResult(String::class.java)

        StepVerifier.create(result.responseBody)
            .expectNext("existing-sid")
            .expectNext("ok")
            .verifyComplete()
    }

    @Test
    fun `POST sse with missing prompt returns 400`() {
        client.post().uri("/sse")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{}""")
            .exchange()
            .expectStatus().isBadRequest
    }
}
