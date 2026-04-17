package develop.x.adapter.inbound.web

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HelloController {
    @GetMapping("/")
    fun hello(): String = """
        Hello, Spring WebFlux + Koog/Ollama!
        Try: curl -N 'http://localhost:8080/sse?prompt=Explain%20Kotlin%20coroutines%20briefly'
    """.trimIndent()
}
