package develop.x.config

import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import kotlin.time.ExperimentalTime
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Ollama 인프라 와이어링. base-url / model 은 application.yml 또는 ENV 로 오버라이드.
 * destroyMethod="close" 로 OllamaClient 의 Ktor HttpClient lifecycle 관리.
 */
@Configuration
class OllamaConfig {

    @Bean(destroyMethod = "close")
    @OptIn(ExperimentalTime::class)
    fun ollamaClient(
        @Value("\${ollama.base-url:http://localhost:11434}") baseUrl: String,
    ): OllamaClient = OllamaClient(baseUrl = baseUrl)

    @Bean
    fun ollamaModel(
        @Value("\${ollama.model:llama3.2}") modelId: String,
    ): LLModel = LLModel(
        provider = LLMProvider.Ollama,
        id = modelId,
        capabilities = listOf(LLMCapability.Completion),
    )
}
