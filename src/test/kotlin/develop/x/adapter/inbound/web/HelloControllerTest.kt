package develop.x.adapter.inbound.web

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.test.web.reactive.server.WebTestClient

@WebFluxTest(HelloController::class)
class HelloControllerTest {

    @Autowired
    private lateinit var client: WebTestClient

    @Test
    fun `GET root returns greeting`() {
        client.get().uri("/")
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java)
            .value { body ->
                assert(body.contains("Hello, Spring WebFlux + Koog/Ollama!"))
            }
    }
}
