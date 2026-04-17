package develop.x.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class ChatTokenTest {

    @Test
    fun `token preserves value`() {
        val token = ChatToken("hello")
        assertEquals("hello", token.value)
    }

    @Test
    fun `empty token is allowed`() {
        val token = ChatToken("")
        assertEquals("", token.value)
    }

    @Test
    fun `equality is based on value`() {
        assertEquals(ChatToken("abc"), ChatToken("abc"))
    }
}
