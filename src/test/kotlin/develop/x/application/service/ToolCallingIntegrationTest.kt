package develop.x.application.service

import develop.x.application.port.outbound.ConversationStore
import develop.x.application.port.outbound.SummarizationPort
import develop.x.application.port.outbound.TokenStreamPort
import develop.x.application.port.outbound.WebSearchPort
import develop.x.domain.ChatMessage
import develop.x.domain.ChatMessage.Role
import develop.x.domain.ChatToken
import develop.x.domain.Conversation
import develop.x.domain.WebSearchResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tool calling 경로를 서비스 레벨에서 검증.
 * OllamaClient가 final suspend이라 mock이 어려우므로,
 * TokenStreamPort를 fake로 구현하여 tool call 결과가 포함된 응답을 시뮬레이션.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ToolCallingIntegrationTest {

    // --- Fakes ---

    /** 전달받은 메시지 중 SYSTEM(tool 결과)이 있으면 그걸 기반으로 응답하는 fake */
    private class FakeToolAwareTokenStream : TokenStreamPort {
        var callCount = 0
        var lastMessages: List<ChatMessage> = emptyList()

        override fun stream(messages: List<ChatMessage>): Flow<ChatToken> {
            callCount++
            lastMessages = messages
            val hasToolResult = messages.any { it.role == Role.SYSTEM && it.content.contains("Tool ") }
            return if (hasToolResult) {
                // tool 결과가 있으면 그걸 기반으로 응답
                flowOf(ChatToken("Based on search: "), ChatToken("Korea news 2025"))
            } else {
                // 일반 응답
                flowOf(ChatToken("hello"), ChatToken(" world"))
            }
        }
    }

    private class FakeConversationStore : ConversationStore {
        val store = ConcurrentHashMap<String, Conversation>()
        override fun findById(id: String): Conversation? = store[id]
        override fun save(conversation: Conversation) { store[conversation.id] = conversation }
    }

    private class FakeSummarization : SummarizationPort {
        override suspend fun summarize(existingSummary: String?, messages: List<ChatMessage>): String =
            "summary"
    }

    private class FakeWebSearch : WebSearchPort {
        var searchCount = 0
        var lastQuery: String? = null

        override suspend fun search(query: String, maxResults: Int): List<WebSearchResult> {
            searchCount++
            lastQuery = query
            return listOf(
                WebSearchResult("Korea News", "https://example.com/1", "Latest Korea news 2025"),
                WebSearchResult("Economy Update", "https://example.com/2", "KOSPI hits 4000"),
            )
        }
    }

    private fun createService(
        testScope: TestScope,
        tokenStream: TokenStreamPort = FakeToolAwareTokenStream(),
        webSearch: FakeWebSearch = FakeWebSearch(),
    ): ServiceBundle {
        val store = FakeConversationStore()
        val service = StreamChatService(
            tokenStream = tokenStream,
            conversationStore = store,
            summarization = FakeSummarization(),
            asyncScope = testScope,
            maxRecentTurns = 5,
            summarizeInterval = 3,
        )
        return ServiceBundle(service, store, webSearch, tokenStream as? FakeToolAwareTokenStream)
    }

    private data class ServiceBundle(
        val service: StreamChatService,
        val store: FakeConversationStore,
        val webSearch: FakeWebSearch,
        val tokenStream: FakeToolAwareTokenStream?,
    )

    // --- Tests ---

    @Test
    fun `normal prompt without tool call returns regular response`() = runTest {
        val (service) = createService(this)
        val result = service.stream("안녕하세요", null)
        val tokens = result.tokens.toList()

        assertEquals(listOf("hello", " world"), tokens.map { it.value })
    }

    @Test
    fun `tool result in messages produces search-based response`() = runTest {
        val (service, store) = createService(this)

        // 첫 턴: 일반 대화
        val r1 = service.stream("안녕", null)
        r1.tokens.toList()
        advanceUntilIdle()

        // 수동으로 tool 결과를 대화에 삽입 (어댑터가 하는 역할 시뮬레이션)
        val conv = store.findById(r1.sessionId)!!
        conv.addMessage(ChatMessage(Role.ASSISTANT, "[tool_call: web_search({\"query\":\"korea news\"})]"))
        conv.addMessage(ChatMessage(Role.SYSTEM, "Tool 'web_search' returned:\nKorea news results"))
        store.save(conv)

        // 다음 턴: tool 결과가 메시지에 있으므로 다른 응답
        val r2 = service.stream("검색 결과 알려줘", r1.sessionId)
        val tokens = r2.tokens.toList()

        // FakeToolAwareTokenStream이 SYSTEM 메시지 감지하고 검색 기반 응답
        assertEquals("Based on search: Korea news 2025", tokens.joinToString("") { it.value })
    }

    @Test
    fun `conversation preserves tool call history in messages`() = runTest {
        val (service, store) = createService(this)

        val r1 = service.stream("hello", null)
        r1.tokens.toList()
        advanceUntilIdle()

        // tool 결과 삽입
        val conv = store.findById(r1.sessionId)!!
        conv.addMessage(ChatMessage(Role.ASSISTANT, "[tool_call: web_search]"))
        conv.addMessage(ChatMessage(Role.SYSTEM, "Tool 'web_search' returned:\nresults"))
        store.save(conv)

        val r2 = service.stream("정리해줘", r1.sessionId)
        r2.tokens.toList()
        advanceUntilIdle()

        // 대화 히스토리에 tool 관련 메시지가 보존됨
        val allMessages = store.findById(r1.sessionId)!!.messages
        assertTrue(allMessages.any { it.role == Role.SYSTEM && it.content.contains("Tool ") })
    }

    @Test
    fun `multi-turn with tool results maintains session continuity`() = runTest {
        val tokenStream = FakeToolAwareTokenStream()
        val (service, store) = createService(this, tokenStream = tokenStream)

        // 3턴 대화
        var sid: String? = null
        repeat(3) {
            val r = service.stream("turn-${it + 1}", sid)
            sid = r.sessionId
            r.tokens.toList()
            advanceUntilIdle()
        }

        // 모든 턴이 같은 세션
        val conv = store.findById(sid!!)!!
        assertEquals(3, conv.turnCount())
        assertEquals(3, tokenStream.callCount)
    }

    @Test
    fun `messages sent to token stream include system messages with tool results`() = runTest {
        val tokenStream = FakeToolAwareTokenStream()
        val (service, store) = createService(this, tokenStream = tokenStream)

        val r1 = service.stream("hello", null)
        r1.tokens.toList()
        advanceUntilIdle()

        // tool 결과를 대화에 삽입
        val conv = store.findById(r1.sessionId)!!
        conv.addMessage(ChatMessage(Role.ASSISTANT, "[tool_call: web_search]"))
        conv.addMessage(ChatMessage(Role.SYSTEM, "Tool 'web_search' returned:\nsearch results here"))
        store.save(conv)

        // 다음 턴
        val r2 = service.stream("정리해줘", r1.sessionId)
        r2.tokens.toList()

        // TokenStreamPort에 전달된 메시지에 SYSTEM(tool 결과)이 포함됨
        val systemMessages = tokenStream.lastMessages.filter { it.role == Role.SYSTEM }
        assertTrue(systemMessages.any { it.content.contains("search results here") })
    }
}
