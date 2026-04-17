package develop.x.application.port.outbound

import develop.x.domain.WebSearchResult

interface WebSearchPort {
    suspend fun search(query: String, maxResults: Int = 5): List<WebSearchResult>
}
