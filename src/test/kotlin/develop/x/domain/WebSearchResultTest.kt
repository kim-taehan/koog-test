package develop.x.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class WebSearchResultTest {

    @Test
    fun `creates result with all fields`() {
        val result = WebSearchResult(
            title = "Test Title",
            url = "https://example.com",
            snippet = "Some snippet",
        )
        assertEquals("Test Title", result.title)
        assertEquals("https://example.com", result.url)
        assertEquals("Some snippet", result.snippet)
    }

    @Test
    fun `data class equality works`() {
        val a = WebSearchResult("t", "u", "s")
        val b = WebSearchResult("t", "u", "s")
        assertEquals(a, b)
    }

    @Test
    fun `different fields means not equal`() {
        val a = WebSearchResult("t1", "u", "s")
        val b = WebSearchResult("t2", "u", "s")
        assertNotEquals(a, b)
    }

    @Test
    fun `empty fields are allowed`() {
        val result = WebSearchResult("", "", "")
        assertEquals("", result.title)
    }
}
