package develop.x.application.port.outbound

import develop.x.domain.ChatMessage

interface SummarizationPort {
    suspend fun summarize(existingSummary: String?, messages: List<ChatMessage>): String
}
