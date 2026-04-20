package develop.x.config

import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.StreamingChatModel
import dev.langchain4j.model.ollama.OllamaChatModel
import dev.langchain4j.model.ollama.OllamaStreamingChatModel
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import java.time.Duration

@Configuration
@Profile("langchain4j")
class LangChain4jConfig {

    @Bean
    fun streamingChatModel(
        @Value("\${ollama.base-url:http://localhost:11434}") baseUrl: String,
        @Value("\${ollama.model:qwen3:14b}") modelName: String,
    ): StreamingChatModel = OllamaStreamingChatModel.builder()
        .baseUrl(baseUrl)
        .modelName(modelName)
        .timeout(Duration.ofSeconds(120))
        .build()

    @Bean
    fun chatModel(
        @Value("\${ollama.base-url:http://localhost:11434}") baseUrl: String,
        @Value("\${ollama.model:qwen3:14b}") modelName: String,
    ): ChatModel = OllamaChatModel.builder()
        .baseUrl(baseUrl)
        .modelName(modelName)
        .timeout(Duration.ofSeconds(120))
        .build()
}
