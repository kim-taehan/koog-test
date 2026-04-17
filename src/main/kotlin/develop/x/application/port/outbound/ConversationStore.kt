package develop.x.application.port.outbound

import develop.x.domain.Conversation

interface ConversationStore {
    fun findById(id: String): Conversation?
    fun save(conversation: Conversation)
}
