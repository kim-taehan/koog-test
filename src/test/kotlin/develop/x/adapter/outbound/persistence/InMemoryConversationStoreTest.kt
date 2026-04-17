package develop.x.adapter.outbound.persistence

import develop.x.domain.ChatMessage
import develop.x.domain.ChatMessage.Role
import develop.x.domain.Conversation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class InMemoryConversationStoreTest {

    private val store = InMemoryConversationStore()

    @Test
    fun `findById returns null for unknown id`() {
        assertNull(store.findById("nonexistent"))
    }

    @Test
    fun `save and findById round-trip`() {
        val conv = Conversation(id = "abc")
        store.save(conv)
        assertSame(conv, store.findById("abc"))
    }

    @Test
    fun `save overwrites existing conversation`() {
        val first = Conversation(id = "abc")
        val second = Conversation(id = "abc")
        second.addMessage(ChatMessage(Role.USER, "hello"))

        store.save(first)
        store.save(second)

        val found = store.findById("abc")!!
        assertEquals(1, found.messages.size)
    }

    @Test
    fun `different ids are independent`() {
        store.save(Conversation(id = "a"))
        store.save(Conversation(id = "b"))

        assertNull(store.findById("c"))
        assertEquals("a", store.findById("a")!!.id)
        assertEquals("b", store.findById("b")!!.id)
    }
}
