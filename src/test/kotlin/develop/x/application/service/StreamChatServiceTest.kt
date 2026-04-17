package develop.x.application.service

import develop.x.application.port.outbound.ConversationStore
import develop.x.application.port.outbound.SummarizationPort
import develop.x.application.port.outbound.TokenStreamPort
import develop.x.domain.ChatMessage
import develop.x.domain.ChatMessage.Role
import develop.x.domain.ChatToken
import develop.x.domain.Conversation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class StreamChatServiceTest {

    // --- Fake implementations ---

    private class FakeTokenStream : TokenStreamPort {
        var lastMessages: List<ChatMessage>? = null
        var response: List<ChatToken> = listOf(ChatToken("hello"), ChatToken(" world"))

        override fun stream(messages: List<ChatMessage>) =
            flowOf(*response.toTypedArray()).also { lastMessages = messages }
    }

    private class FakeConversationStore : ConversationStore {
        val store = ConcurrentHashMap<String, Conversation>()
        var saveCount = 0

        override fun findById(id: String): Conversation? = store[id]
        override fun save(conversation: Conversation) {
            store[conversation.id] = conversation
            saveCount++
        }
    }

    private class FakeSummarization : SummarizationPort {
        var callCount = 0
        var lastExistingSummary: String? = null
        var lastMessages: List<ChatMessage>? = null

        override suspend fun summarize(existingSummary: String?, messages: List<ChatMessage>): String {
            callCount++
            lastExistingSummary = existingSummary
            lastMessages = messages
            return "summarized: ${messages.size} messages"
        }
    }

    // --- Helper ---

    private fun createService(
        testScope: TestScope,
        tokenStream: FakeTokenStream = FakeTokenStream(),
        store: FakeConversationStore = FakeConversationStore(),
        summarization: FakeSummarization = FakeSummarization(),
        maxRecentTurns: Int = 5,
        summarizeInterval: Int = 3,
    ) = ServiceBundle(
        service = StreamChatService(tokenStream, store, summarization, testScope, maxRecentTurns, summarizeInterval),
        tokenStream = tokenStream,
        store = store,
        summarization = summarization,
    )

    private data class ServiceBundle(
        val service: StreamChatService,
        val tokenStream: FakeTokenStream,
        val store: FakeConversationStore,
        val summarization: FakeSummarization,
    )

    // --- 새 대화 생성 ---

    @Test
    fun `new conversation created when sessionId is null`() = runTest {
        val (service, _, store) = createService(this)
        val result = service.stream("hello", null)
        assertTrue(result.sessionId.isNotBlank())
        assertNotNull(store.findById(result.sessionId))
    }

    @Test
    fun `returns generated sessionId`() = runTest {
        val (service) = createService(this)
        val result = service.stream("hello", null)
        assertTrue(result.sessionId.length > 10) // UUID format
    }

    // --- 기존 대화 재사용 ---

    @Test
    fun `existing conversation reused when sessionId provided`() = runTest {
        val (service, _, store) = createService(this)

        val first = service.stream("first", null)
        first.tokens.toList()
        advanceUntilIdle()

        val second = service.stream("second", first.sessionId)
        second.tokens.toList()
        advanceUntilIdle()

        assertEquals(first.sessionId, second.sessionId)
        val conv = store.findById(first.sessionId)!!
        // 2턴: user+assistant * 2 = 4 messages
        assertEquals(4, conv.messages.size)
    }

    @Test
    fun `unknown sessionId creates new conversation`() = runTest {
        val (service) = createService(this)
        val result = service.stream("hello", "nonexistent-id")
        // sessionId should be a new one, not "nonexistent-id"
        assertTrue(result.sessionId != "nonexistent-id")
    }

    // --- 프롬프트 검증 ---

    @Test
    fun `blank prompt throws exception`() = runTest {
        val (service) = createService(this)
        assertFailsWith<IllegalArgumentException> { service.stream("", null) }
    }

    @Test
    fun `whitespace-only prompt throws exception`() = runTest {
        val (service) = createService(this)
        assertFailsWith<IllegalArgumentException> { service.stream("   ", null) }
    }

    // --- 토큰 스트리밍 ---

    @Test
    fun `tokens are streamed from TokenStreamPort`() = runTest {
        val (service, tokenStream) = createService(this)
        tokenStream.response = listOf(ChatToken("a"), ChatToken("b"), ChatToken("c"))

        val result = service.stream("hello", null)
        val tokens = result.tokens.toList()

        assertEquals(listOf("a", "b", "c"), tokens.map { it.value })
    }

    @Test
    fun `user message is passed to TokenStreamPort`() = runTest {
        val (service, tokenStream) = createService(this)
        val result = service.stream("my question", null)
        result.tokens.toList()

        val sent = tokenStream.lastMessages!!
        assertTrue(sent.any { it.role == Role.USER && it.content == "my question" })
    }

    // --- 비동기 히스토리 저장 ---

    @Test
    fun `assistant response saved after stream completes`() = runTest {
        val (service, _, store) = createService(this)
        val result = service.stream("hello", null)
        result.tokens.toList()
        advanceUntilIdle()

        val conv = store.findById(result.sessionId)!!
        assertEquals(2, conv.messages.size)
        assertEquals(Role.USER, conv.messages[0].role)
        assertEquals(Role.ASSISTANT, conv.messages[1].role)
        assertEquals("hello world", conv.messages[1].content)
    }

    // --- LLM 메시지 구성: 윈도우 ---

    @Test
    fun `within window all messages sent to LLM`() = runTest {
        val (service, tokenStream, store) = createService(this, maxRecentTurns = 5)

        // 3턴 대화
        var sid: String? = null
        repeat(3) {
            val r = service.stream("turn-${it + 1}", sid)
            sid = r.sessionId
            r.tokens.toList()
            advanceUntilIdle()
        }

        // 4번째 호출 시 tokenStream에 전달되는 메시지 확인
        val r = service.stream("turn-4", sid)
        r.tokens.toList()

        val sent = tokenStream.lastMessages!!
        // 3턴 완료 + 4번째 user = 7 메시지 (user*4 + assistant*3), 모두 윈도우 내
        assertEquals(7, sent.size)
        assertNull(sent.find { it.role == Role.SYSTEM }) // 요약 없음
    }

    @Test
    fun `beyond window summary prepended to recent messages`() = runTest {
        val (service, tokenStream, store) = createService(this, maxRecentTurns = 2)

        // 2턴 채움
        var sid: String? = null
        repeat(2) {
            val r = service.stream("turn-${it + 1}", sid)
            sid = r.sessionId
            r.tokens.toList()
            advanceUntilIdle()
        }

        // 수동으로 summary 설정 (요약이 이미 실행된 것처럼)
        val conv = store.findById(sid!!)!!
        conv.summary = "previous context"

        // 3번째 턴
        val r = service.stream("turn-3", sid)
        r.tokens.toList()

        val sent = tokenStream.lastMessages!!
        // 첫 메시지가 SYSTEM (요약)
        assertEquals(Role.SYSTEM, sent[0].role)
        assertTrue(sent[0].content.contains("previous context"))
    }

    // --- 요약 트리거 ---

    @Test
    fun `summarization not triggered within window`() = runTest {
        val (service, _, _, summarization) = createService(this, maxRecentTurns = 5, summarizeInterval = 3)

        var sid: String? = null
        repeat(5) {
            val r = service.stream("turn-${it + 1}", sid)
            sid = r.sessionId
            r.tokens.toList()
            advanceUntilIdle()
        }

        assertEquals(0, summarization.callCount)
    }

    @Test
    fun `summarization triggered when overflow reaches interval`() = runTest {
        val (service, _, store, summarization) = createService(this, maxRecentTurns = 2, summarizeInterval = 2)

        var sid: String? = null
        // 4턴: 2턴 윈도우 + 2턴 초과 → 요약 트리거
        repeat(4) {
            val r = service.stream("turn-${it + 1}", sid)
            sid = r.sessionId
            r.tokens.toList()
            advanceUntilIdle()
        }

        assertEquals(1, summarization.callCount)
        val conv = store.findById(sid!!)!!
        assertNotNull(conv.summary)
    }

    @Test
    fun `summarization trims old messages`() = runTest {
        val (service, _, store, summarization) = createService(this, maxRecentTurns = 2, summarizeInterval = 2)

        var sid: String? = null
        repeat(4) {
            val r = service.stream("turn-${it + 1}", sid)
            sid = r.sessionId
            r.tokens.toList()
            advanceUntilIdle()
        }

        val conv = store.findById(sid!!)!!
        // 요약 후 최근 2턴만 남아야 함
        assertEquals(2, conv.turnCount())
    }

    @Test
    fun `summarization receives existing summary on second trigger`() = runTest {
        val (service, _, store, summarization) = createService(this, maxRecentTurns = 2, summarizeInterval = 2)

        var sid: String? = null
        // 8턴: 요약 2번 트리거 (턴 4에서 1번, 턴 6 또는 8에서 2번)
        repeat(8) {
            val r = service.stream("turn-${it + 1}", sid)
            sid = r.sessionId
            r.tokens.toList()
            advanceUntilIdle()
        }

        assertTrue(summarization.callCount >= 2)
        // 두 번째 요약 시 기존 요약이 전달됨
        assertNotNull(summarization.lastExistingSummary)
    }

    @Test
    fun `summarization not triggered when overflow below interval`() = runTest {
        val (service, _, _, summarization) = createService(this, maxRecentTurns = 3, summarizeInterval = 3)

        var sid: String? = null
        // 5턴: 윈도우 3 + 초과 2 → 아직 interval(3) 미달
        repeat(5) {
            val r = service.stream("turn-${it + 1}", sid)
            sid = r.sessionId
            r.tokens.toList()
            advanceUntilIdle()
        }

        assertEquals(0, summarization.callCount)
    }
}
