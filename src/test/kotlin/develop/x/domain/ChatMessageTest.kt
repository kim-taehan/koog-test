package develop.x.domain

import develop.x.domain.ChatMessage.Role
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ChatMessageTest {

    @Test
    fun `creates user message`() {
        val msg = ChatMessage(Role.USER, "hi")
        assertEquals(Role.USER, msg.role)
        assertEquals("hi", msg.content)
    }

    @Test
    fun `creates assistant message`() {
        val msg = ChatMessage(Role.ASSISTANT, "hello")
        assertEquals(Role.ASSISTANT, msg.role)
    }

    @Test
    fun `creates system message`() {
        val msg = ChatMessage(Role.SYSTEM, "summary")
        assertEquals(Role.SYSTEM, msg.role)
    }

    @Test
    fun `data class equality works`() {
        val a = ChatMessage(Role.USER, "hi")
        val b = ChatMessage(Role.USER, "hi")
        assertEquals(a, b)
    }

    @Test
    fun `different role means not equal`() {
        val a = ChatMessage(Role.USER, "hi")
        val b = ChatMessage(Role.ASSISTANT, "hi")
        assertNotEquals(a, b)
    }

    @Test
    fun `different content means not equal`() {
        val a = ChatMessage(Role.USER, "hi")
        val b = ChatMessage(Role.USER, "bye")
        assertNotEquals(a, b)
    }

    @Test
    fun `all three roles are distinct`() {
        val roles = Role.entries
        assertEquals(3, roles.size)
        assertEquals(setOf(Role.USER, Role.ASSISTANT, Role.SYSTEM), roles.toSet())
    }
}
