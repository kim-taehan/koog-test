package develop.x.adapter.outbound.persistence

import develop.x.application.port.outbound.ConversationStore
import develop.x.domain.Conversation
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class InMemoryConversationStore : ConversationStore {

    private val store = ConcurrentHashMap<String, Conversation>()

    override fun findById(id: String): Conversation? = store[id]

    override fun save(conversation: Conversation) {
        store[conversation.id] = conversation
    }
}
