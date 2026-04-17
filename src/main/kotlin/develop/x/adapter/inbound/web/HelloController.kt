package develop.x.adapter.inbound.web

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HelloController {
    @GetMapping("/")
    fun hello(): String = """
        Hello, Spring WebFlux + Koog/Ollama!
        Try: curl -N -X POST http://localhost:8080/sse -H 'Content-Type: application/json' -d '{"prompt":"안녕하세요"}'
    """.trimIndent()

    @GetMapping("/health")
    fun health(): Map<String, Any> = mapOf(
        "status" to "UP",
        "service" to "koog-test",
        "version" to "1.0",
    )
}
