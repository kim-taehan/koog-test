package develop.x.adapter.outbound.llm

import ai.koog.prompt.dsl.prompt as koogPrompt
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.llm.LLModel
import develop.x.application.port.outbound.SummarizationPort
import develop.x.domain.ChatMessage
import kotlin.time.ExperimentalTime
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class KoogOllamaSummarizationAdapter(
    private val client: OllamaClient,
    private val model: LLModel,
) : SummarizationPort {

    private val log = LoggerFactory.getLogger(javaClass)

    @OptIn(ExperimentalTime::class)
    override suspend fun summarize(existingSummary: String?, messages: List<ChatMessage>): String {
        val conversationText = messages.joinToString("\n") { "${it.role}: ${it.content}" }

        val promptText = buildString {
            append("Summarize the following conversation concisely in the same language the users used. ")
            append("Preserve key facts, names, and context that would be needed to continue the conversation.\n\n")
            if (existingSummary != null) {
                append("Previous summary:\n$existingSummary\n\n")
            }
            append("New conversation to incorporate:\n$conversationText")
        }

        val request = koogPrompt("summarize") {
            user(promptText)
        }

        log.info("→ Ollama summarize: model={}, messages={}", model.id, messages.size)
        val responses = client.execute(prompt = request, model = model, tools = emptyList())
        val summary = responses.joinToString("") { it.content }
        log.info("← summary generated: {} chars", summary.length)
        return summary
    }
}
