package develop.x.adapter.outbound.llm

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import develop.x.application.port.outbound.WebSearchPort
import develop.x.domain.ChatMessage
import develop.x.domain.ChatMessage.Role
import develop.x.domain.WebSearchResult
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertTrue

class KoogOllamaTokenStreamAdapterTest {

    private val model = LLModel(
        provider = LLMProvider.Ollama,
        id = "test-model",
        capabilities = listOf(LLMCapability.Completion),
    )

    private val webSearch = mock<WebSearchPort>()

    private fun mockClient(): OllamaClient = runBlocking {
        val client = mock<OllamaClient>()
        whenever(client.execute(any<Prompt>(), eq(model), any())).thenReturn(emptyList())
        client
    }

    // --- Phase 2: 스트리밍 (tool call 없는 경우) ---

    @Test
    fun `no tool call - streams text tokens from Phase 2`() = runTest {
        val client = mockClient()
        whenever(client.executeStreaming(any<Prompt>(), eq(model), any()))
            .thenReturn(flowOf(StreamFrame.TextDelta("hello"), StreamFrame.TextDelta(" world")))

        val adapter = KoogOllamaTokenStreamAdapter(client, model, webSearch)
        val tokens = adapter.stream(listOf(ChatMessage(Role.USER, "test"))).toList()

        assertEquals(listOf("hello", " world"), tokens.map { it.value })
    }

    @Test
    fun `no tool call - filters out reasoning frames in Phase 2`() = runTest {
        val client = mockClient()
        whenever(client.executeStreaming(any<Prompt>(), eq(model), any()))
            .thenReturn(flowOf(StreamFrame.ReasoningDelta(text = "thinking"), StreamFrame.TextDelta("answer")))

        val adapter = KoogOllamaTokenStreamAdapter(client, model, webSearch)
        val tokens = adapter.stream(listOf(ChatMessage(Role.USER, "q"))).toList()

        assertEquals(listOf("answer"), tokens.map { it.value })
    }

    @Test
    fun `no tool call - empty streaming response produces no tokens`() = runTest {
        val client = mockClient()
        whenever(client.executeStreaming(any<Prompt>(), eq(model), any()))
            .thenReturn(flowOf())

        val adapter = KoogOllamaTokenStreamAdapter(client, model, webSearch)
        val tokens = adapter.stream(listOf(ChatMessage(Role.USER, "hello"))).toList()

        assertTrue(tokens.isEmpty())
    }

    @Test
    fun `no tool call - web search port is not invoked`() = runTest {
        val client = mockClient()
        whenever(client.executeStreaming(any<Prompt>(), eq(model), any()))
            .thenReturn(flowOf(StreamFrame.TextDelta("ok")))

        val adapter = KoogOllamaTokenStreamAdapter(client, model, webSearch)
        adapter.stream(listOf(ChatMessage(Role.USER, "hello"))).toList()

        verify(webSearch, never()).search(any(), any())
    }

    // --- Phase 1: tool call 감지 + 실행 ---

    @Test
    fun `tool call detected - web search is invoked and result streamed`() = runTest {
        val client = mock<OllamaClient>()
        val toolCall = mock<Message.Tool.Call>()
        whenever(toolCall.tool).thenReturn("web_search")
        whenever(toolCall.content).thenReturn("""{"query":"korea news"}""")

        // suspend function stubbing in coroutine context
        whenever(client.execute(any<Prompt>(), eq(model), any()))
            .thenReturn(listOf(toolCall))
            .thenReturn(emptyList())

        whenever(client.executeStreaming(any<Prompt>(), eq(model), any()))
            .thenReturn(flowOf(StreamFrame.TextDelta("search result: "), StreamFrame.TextDelta("done")))

        val localWebSearch = mock<WebSearchPort>()
        whenever(localWebSearch.search(eq("korea news"), any()))
            .thenReturn(listOf(WebSearchResult("Title", "https://example.com", "Snippet")))

        val adapter = KoogOllamaTokenStreamAdapter(client, model, localWebSearch)
        val tokens = adapter.stream(listOf(ChatMessage(Role.USER, "search korea news"))).toList()

        verify(localWebSearch).search(eq("korea news"), any())
        val text = tokens.joinToString("") { it.value }
        assertTrue(text.contains("[tool: web_search 호출 중...]"))
        assertTrue(text.contains("[tool: web_search 완료]"))
        assertTrue(text.contains("search result: done"))
    }

    @Test
    fun `tool call with plain text content falls back gracefully`() = runTest {
        val client = mock<OllamaClient>()
        val toolCall = mock<Message.Tool.Call>()
        whenever(toolCall.tool).thenReturn("web_search")
        whenever(toolCall.content).thenReturn("not json")

        whenever(client.execute(any<Prompt>(), eq(model), any()))
            .thenReturn(listOf(toolCall))
            .thenReturn(emptyList())

        whenever(client.executeStreaming(any<Prompt>(), eq(model), any()))
            .thenReturn(flowOf(StreamFrame.TextDelta("ok")))

        val localWebSearch = mock<WebSearchPort>()
        whenever(localWebSearch.search(eq("not json"), any()))
            .thenReturn(emptyList())

        val adapter = KoogOllamaTokenStreamAdapter(client, model, localWebSearch)
        adapter.stream(listOf(ChatMessage(Role.USER, "search"))).toList()

        verify(localWebSearch).search(eq("not json"), any())
    }

    @Test
    fun `unknown tool name returns error message without calling web search`() = runTest {
        val client = mock<OllamaClient>()
        val toolCall = mock<Message.Tool.Call>()
        whenever(toolCall.tool).thenReturn("unknown_tool")
        whenever(toolCall.content).thenReturn("{}")

        whenever(client.execute(any<Prompt>(), eq(model), any()))
            .thenReturn(listOf(toolCall))
            .thenReturn(emptyList())

        whenever(client.executeStreaming(any<Prompt>(), eq(model), any()))
            .thenReturn(flowOf(StreamFrame.TextDelta("ok")))

        val localWebSearch = mock<WebSearchPort>()
        val adapter = KoogOllamaTokenStreamAdapter(client, model, localWebSearch)
        adapter.stream(listOf(ChatMessage(Role.USER, "do something"))).toList()

        verify(localWebSearch, never()).search(any(), any())
    }
}
