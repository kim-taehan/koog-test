package develop.x.adapter.outbound.llm

import ai.koog.prompt.dsl.prompt as koogPrompt
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.filterTextOnly
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import develop.x.application.port.outbound.TokenStreamPort
import develop.x.application.port.outbound.WebSearchPort
import develop.x.domain.ChatMessage
import develop.x.domain.ChatMessage.Role
import develop.x.domain.ChatToken
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("koog")
class KoogOllamaTokenStreamAdapter(
    private val client: OllamaClient,
    private val model: LLModel,
    private val webSearch: WebSearchPort,
) : TokenStreamPort {

    private val log = LoggerFactory.getLogger(javaClass)

    private val webSearchTool = ToolDescriptor(
        name = "web_search",
        description = "Search the web for current information. Use this when the user asks about recent events, news, or anything that requires up-to-date information.",
        requiredParameters = listOf(
            ToolParameterDescriptor("query", "The search query string", ToolParameterType.String),
        ),
    )

    private val tools = listOf(webSearchTool)

    /** tool call 결과를 Koog의 toolCall/toolResult DSL로 올바르게 전달하기 위한 내부 모델 */
    private data class ToolCallResult(
        val id: String,
        val name: String,
        val args: String,
        val result: String,
    )

    @OptIn(ExperimentalTime::class)
    override fun stream(messages: List<ChatMessage>): Flow<ChatToken> = flow {
        val toolResults = mutableListOf<ToolCallResult>()
        var maxIterations = 3

        // Phase 1: non-streaming으로 tool call 루프 처리
        while (maxIterations-- > 0) {
            val request = buildPrompt(messages, toolResults)
            log.info("→ Ollama execute (tool check): model={}, messages={}, toolResults={}", model.id, messages.size, toolResults.size)

            val responses = client.execute(prompt = request, model = model, tools = tools)
            val toolCalls = responses.filterIsInstance<Message.Tool.Call>()

            if (toolCalls.isEmpty()) {
                log.info("← no tool calls")
                break
            }

            for (toolCall in toolCalls) {
                log.info("← tool call: id={}, name={}, args={}", toolCall.id, toolCall.tool, toolCall.content)
                emit(ChatToken("[tool: ${toolCall.tool} 호출 중...]\n"))
                val result = executeToolCall(toolCall.tool, toolCall.content)
                log.info("→ tool result: {} chars", result.length)
                emit(ChatToken("[tool: ${toolCall.tool} 완료]\n"))
                toolResults.add(ToolCallResult(toolCall.id ?: "call_${toolResults.size}", toolCall.tool, toolCall.content, result))
            }
        }

        // Phase 2: tool 결과를 포함하여 최종 응답을 스트리밍
        val finalRequest = buildPrompt(messages, toolResults)
        log.info("→ Ollama executeStreaming (final): model={}, messages={}, toolResults={}", model.id, messages.size, toolResults.size)

        client.executeStreaming(prompt = finalRequest, model = model, tools = emptyList())
            .filterTextOnly()
            .collect { text -> emit(ChatToken(text)) }
    }
        .onStart { log.info("stream subscribed") }
        .catch { e -> log.error("stream error", e); throw e }
        .onCompletion { cause -> log.info("stream completed (cause={})", cause?.message ?: "none") }

    @OptIn(ExperimentalTime::class)
    private fun buildPrompt(messages: List<ChatMessage>, toolResults: List<ToolCallResult>) = koogPrompt("chat") {
        for (msg in messages) {
            when (msg.role) {
                Role.USER -> user(msg.content)
                Role.ASSISTANT -> assistant(msg.content)
                Role.SYSTEM -> system(msg.content)
            }
        }
        // tool call → tool result 쌍을 Koog DSL로 추가
        for (tr in toolResults) {
            toolCall(tr.id, tr.name, tr.args)
                .toolResult(tr.id, tr.name, tr.result)
        }
    }

    private suspend fun executeToolCall(toolName: String, content: String): String {
        return when (toolName) {
            "web_search" -> {
                val query = try {
                    val json = Json.parseToJsonElement(content).jsonObject
                    json["query"]?.jsonPrimitive?.content ?: content
                } catch (_: Exception) {
                    content
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
