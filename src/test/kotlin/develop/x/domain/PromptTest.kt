package develop.x.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PromptTest {

    @Test
    fun `valid prompt preserves value`() {
        val prompt = Prompt("hello")
        assertEquals("hello", prompt.value)
    }

    @Test
    fun `blank prompt throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> { Prompt("") }
    }

    @Test
    fun `whitespace-only prompt throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> { Prompt("   ") }
    }

    @Test
    fun `prompt with leading and trailing spaces preserves value as-is`() {
        val prompt = Prompt("  hello  ")
        assertEquals("  hello  ", prompt.value)
    }

    @Test
    fun `equality is based on value`() {
        assertEquals(Prompt("abc"), Prompt("abc"))
    }
}
