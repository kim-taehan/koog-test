package develop.x.adapter.outbound.search

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import develop.x.application.port.outbound.WebSearchPort
import develop.x.domain.WebSearchResult
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody

@Component
class TavilyWebSearchAdapter(
    @Value("\${tavily.api-key:}") private val apiKey: String,
) : WebSearchPort {

    private val log = LoggerFactory.getLogger(javaClass)
    private val webClient = WebClient.builder()
        .baseUrl("https://api.tavily.com")
        .build()

    override suspend fun search(query: String, maxResults: Int): List<WebSearchResult> {
        log.info("→ Tavily search: query={}, maxResults={}", query, maxResults)

        val response = webClient.post()
            .uri("/search")
            .bodyValue(TavilyRequest(apiKey = apiKey, query = query, maxResults = maxResults))
            .retrieve()
            .awaitBody<TavilyResponse>()

        log.info("← Tavily returned {} results", response.results.size)
        return response.results.map { r ->
            WebSearchResult(title = r.title, url = r.url, snippet = r.content)
        }
    }

    private data class TavilyRequest(
        @JsonProperty("api_key") val apiKey: String,
        val query: String,
        @JsonProperty("max_results") val maxResults: Int,
        @JsonProperty("search_depth") val searchDepth: String = "basic",
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class TavilyResponse(
        val results: List<TavilyResult> = emptyList(),
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class TavilyResult(
        val title: String = "",
        val url: String = "",
        val content: String = "",
    )
}
