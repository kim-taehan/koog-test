package develop.x.domain

import develop.x.domain.ChatMessage.Role
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConversationTest {

    private fun user(content: String) = ChatMessage(Role.USER, content)
    private fun assistant(content: String) = ChatMessage(Role.ASSISTANT, content)

    private fun conversationWithTurns(n: Int): Conversation {
        val conv = Conversation()
        repeat(n) { i ->
            conv.addMessage(user("user-${i + 1}"))
            conv.addMessage(assistant("assistant-${i + 1}"))
        }
        return conv
    }

    // --- 기본 생성 ---

    @Test
    fun `new conversation has UUID id`() {
        val conv = Conversation()
        assertTrue(conv.id.isNotBlank())
    }

    @Test
    fun `new conversation has empty messages`() {
        val conv = Conversation()
        assertTrue(conv.messages.isEmpty())
    }

    @Test
    fun `new conversation has null summary`() {
        val conv = Conversation()
        assertNull(conv.summary)
    }

    @Test
    fun `custom id is preserved`() {
        val conv = Conversation(id = "custom-id")
        assertEquals("custom-id", conv.id)
    }

    @Test
    fun `two conversations have different ids`() {
        assertNotEquals(Conversation().id, Conversation().id)
    }

    // --- addMessage ---

    @Test
    fun `addMessage appends to messages`() {
        val conv = Conversation()
        conv.addMessage(user("hello"))
        assertEquals(1, conv.messages.size)
        assertEquals("hello", conv.messages[0].content)
    }

    @Test
    fun `messages maintain insertion order`() {
        val conv = Conversation()
        conv.addMessage(user("first"))
        conv.addMessage(assistant("second"))
        conv.addMessage(user("third"))
        assertEquals(listOf("first", "second", "third"), conv.messages.map { it.content })
    }

    // --- turnCount ---

    @Test
    fun `turnCount is zero for empty conversation`() {
        assertEquals(0, Conversation().turnCount())
    }

    @Test
    fun `turnCount counts user messages only`() {
        val conv = conversationWithTurns(3)
        assertEquals(3, conv.turnCount())
    }

    @Test
    fun `turnCount with user message without assistant response`() {
        val conv = conversationWithTurns(2)
        conv.addMessage(user("pending"))
        assertEquals(3, conv.turnCount())
    }

    // --- recentMessages ---

    @Test
    fun `recentMessages returns all when n exceeds total turns`() {
        val conv = conversationWithTurns(3)
        val recent = conv.recentMessages(10)
        assertEquals(6, recent.size) // 3턴 * 2 메시지
    }

    @Test
    fun `recentMessages returns last n turns`() {
        val conv = conversationWithTurns(5)
        val recent = conv.recentMessages(2)
        // 마지막 2턴: user-4, assistant-4, user-5, assistant-5
        assertEquals(4, recent.size)
        assertEquals("user-4", recent[0].content)
        assertEquals("assistant-5", recent[3].content)
    }

    @Test
    fun `recentMessages with n=0 returns empty`() {
        val conv = conversationWithTurns(3)
        assertTrue(conv.recentMessages(0).isEmpty())
    }

    @Test
    fun `recentMessages with negative n returns empty`() {
        val conv = conversationWithTurns(3)
        assertTrue(conv.recentMessages(-1).isEmpty())
    }

    @Test
    fun `recentMessages includes trailing user without assistant`() {
        val conv = conversationWithTurns(2)
        conv.addMessage(user("pending"))
        // n=1 → 마지막 1턴의 user부터: "pending" (assistant 아직 없음)
        val recent = conv.recentMessages(1)
        assertEquals(1, recent.size)
        assertEquals("pending", recent[0].content)
    }

    @Test
    fun `recentMessages with n=1 on full turns`() {
        val conv = conversationWithTurns(5)
        val recent = conv.recentMessages(1)
        assertEquals(2, recent.size)
        assertEquals("user-5", recent[0].content)
        assertEquals("assistant-5", recent[1].content)
    }

    // --- oldMessages ---

    @Test
    fun `oldMessages returns empty when all within window`() {
        val conv = conversationWithTurns(3)
        assertTrue(conv.oldMessages(5).isEmpty())
    }

    @Test
    fun `oldMessages returns messages outside window`() {
        val conv = conversationWithTurns(5)
        val old = conv.oldMessages(2)
        // 턴 1~3 = 6 메시지
        assertEquals(6, old.size)
        assertEquals("user-1", old[0].content)
        assertEquals("assistant-3", old[5].content)
    }

    @Test
    fun `oldMessages returns empty for empty conversation`() {
        assertTrue(Conversation().oldMessages(5).isEmpty())
    }

    @Test
    fun `oldMessages with n=0 returns all messages`() {
        val conv = conversationWithTurns(3)
        val old = conv.oldMessages(0)
        assertEquals(6, old.size)
    }

    // --- trimToRecent ---

    @Test
    fun `trimToRecent keeps only last n turns`() {
        val conv = conversationWithTurns(5)
        conv.trimToRecent(2)
        assertEquals(4, conv.messages.size)
        assertEquals("user-4", conv.messages[0].content)
        assertEquals("assistant-5", conv.messages[3].content)
    }

    @Test
    fun `trimToRecent with n exceeding total keeps all`() {
        val conv = conversationWithTurns(3)
        conv.trimToRecent(10)
        assertEquals(6, conv.messages.size)
    }

    @Test
    fun `trimToRecent with n=0 clears all messages`() {
        val conv = conversationWithTurns(3)
        conv.trimToRecent(0)
        assertTrue(conv.messages.isEmpty())
    }

    @Test
    fun `trimToRecent does not affect summary`() {
        val conv = conversationWithTurns(5)
        conv.summary = "existing summary"
        conv.trimToRecent(2)
        assertEquals("existing summary", conv.summary)
    }

    // --- summary ---

    @Test
    fun `summary can be set and read`() {
        val conv = Conversation()
        conv.summary = "test summary"
        assertEquals("test summary", conv.summary)
    }

    @Test
    fun `summary can be overwritten`() {
        val conv = Conversation()
        conv.summary = "first"
        conv.summary = "second"
        assertEquals("second", conv.summary)
    }

    // --- oldMessages + trimToRecent 연계 ---

    @Test
    fun `trim after checking old messages leaves correct state`() {
        val conv = conversationWithTurns(8)
        val old = conv.oldMessages(5)
        assertEquals(6, old.size) // 턴 1~3

        conv.trimToRecent(5)
        assertEquals(10, conv.messages.size) // 턴 4~8
        assertEquals("user-4", conv.messages[0].content)
        assertTrue(conv.oldMessages(5).isEmpty())
    }
}
