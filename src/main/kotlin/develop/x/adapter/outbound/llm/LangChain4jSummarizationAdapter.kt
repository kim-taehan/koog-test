package develop.x.adapter.outbound.llm

import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.request.ChatRequest
import develop.x.application.port.outbound.SummarizationPort
import develop.x.domain.ChatMessage
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("langchain4j")
class LangChain4jSummarizationAdapter(
    private val chatModel: ChatModel,
) : SummarizationPort {

    private val log = LoggerFactory.getLogger(javaClass)

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

        log.info("→ LangChain4j summarize: messages={}", messages.size)
        val request = ChatRequest.builder()
            .messages(listOf(UserMessage.from(promptText)))
            .build()
        val response = chatModel.chat(request)
        val summary = response.aiMessage().text()
        log.info("← summary generated: {} chars", summary.length)
        return summary
    }
}
