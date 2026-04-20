package develop.x.adapter.outbound.llm

import dev.langchain4j.agent.tool.ToolSpecification
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessage as LC4jMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.ToolExecutionResultMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.StreamingChatModel
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.request.json.JsonObjectSchema
import dev.langchain4j.model.chat.request.json.JsonStringSchema
import dev.langchain4j.model.chat.response.ChatResponse
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler
import develop.x.application.port.outbound.TokenStreamPort
import develop.x.application.port.outbound.WebSearchPort
import develop.x.domain.ChatMessage
import develop.x.domain.ChatMessage.Role
import develop.x.domain.ChatToken
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("langchain4j")
class LangChain4jTokenStreamAdapter(
    private val streamingChatModel: StreamingChatModel,
    private val webSearch: WebSearchPort,
) : TokenStreamPort {

    private val log = LoggerFactory.getLogger(javaClass)

    private val webSearchTool = ToolSpecification.builder()
        .name("web_search")
        .description("Search the web for current information. Use this when the user asks about recent events, news, or anything that requires up-to-date information.")
        .parameters(
            JsonObjectSchema.builder()
                .addStringProperty("query", "The search query string")
                .required("query")
                .build()
        )
        .build()

    private val tools = listOf(webSearchTool)

    override fun stream(messages: List<ChatMessage>): Flow<ChatToken> = callbackFlow<ChatToken> {
        val lc4jMessages = messages.map { it.toLc4j() }.toMutableList()

        fun doStream(remainingIterations: Int) {
            log.info("→ LangChain4j streaming: messages={}, remainingIterations={}", lc4jMessages.size, remainingIterations)

            val request = ChatRequest.builder()
                .messages(lc4jMessages)
                .toolSpecifications(tools)
                .build()

            streamingChatModel.chat(request, object : StreamingChatResponseHandler {
                override fun onPartialResponse(partialResponse: String) {
                    trySend(ChatToken(partialResponse))
                }

                override fun onCompleteResponse(completeResponse: ChatResponse) {
                    val aiMessage = completeResponse.aiMessage()
                    val toolCalls = aiMessage.toolExecutionRequests() ?: emptyList()

                    if (toolCalls.isEmpty()) {
                        log.info("← no tool calls, streaming complete")
                        close()
                        return
                    }

                    if (remainingIterations <= 0) {
                        log.info("← max iterations exhausted, closing")
                        close()
                        return
                    }

                    // tool call 처리
                    lc4jMessages.add(aiMessage)
                    for (toolCall in toolCalls) {
                        log.info("← tool call: name={}, args={}", toolCall.name(), toolCall.arguments())
                        trySend(ChatToken("[tool: ${toolCall.name()} 호출 중...]\n"))
                        val result = runBlocking { executeToolCall(toolCall.name(), toolCall.arguments()) }
                        log.info("→ tool result: {} chars", result.length)
                        trySend(ChatToken("[tool: ${toolCall.name()} 완료]\n"))
                        lc4jMessages.add(ToolExecutionResultMessage.from(toolCall, result))
                    }

                    // tool 결과를 포함하여 다시 스트리밍
                    doStream(remainingIterations - 1)
                }

                override fun onError(error: Throwable) {
                    log.error("stream error", error)
                    close(error)
                }
            })
        }

        doStream(remainingIterations = 3)
        awaitClose()
    }
        .onStart { log.info("stream subscribed") }
        .catch { e -> log.error("stream error", e); throw e }
        .onCompletion { cause -> log.info("stream completed (cause={})", cause?.message ?: "none") }

    private fun ChatMessage.toLc4j(): LC4jMessage = when (role) {
        Role.USER -> UserMessage.from(content)
        Role.ASSISTANT -> AiMessage.from(content)
        Role.SYSTEM -> SystemMessage.from(content)
    }

    private suspend fun executeToolCall(toolName: String, arguments: String): String {
        return when (toolName) {
            "web_search" -> {
                val query = try {
                    val json = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
                        .readTree(arguments)
                    json.get("query")?.asText() ?: arguments
                } catch (_: Exception) {
                    arguments
                }
                val results = webSearch.search(query)
                results.joinToString("\n\n") { r ->
                    "**${r.title}**\n${r.url}\n${r.snippet}"
                }.ifEmpty { "No results found for: $query" }
            }
            else -> "Unknown tool: $toolName"
        }
    }
}
