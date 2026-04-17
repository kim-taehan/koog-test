package develop.x.application.service

import develop.x.application.port.inbound.StreamChatUseCase
import develop.x.application.port.outbound.ConversationStore
import develop.x.application.port.outbound.SummarizationPort
import develop.x.application.port.outbound.TokenStreamPort
import develop.x.domain.ChatMessage
import develop.x.domain.ChatMessage.Role
import develop.x.domain.ChatToken
import develop.x.domain.Conversation
import develop.x.domain.Prompt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class StreamChatService(
    private val tokenStream: TokenStreamPort,
    private val conversationStore: ConversationStore,
    private val summarization: SummarizationPort,
    private val asyncScope: CoroutineScope,
    private val maxRecentTurns: Int,
    private val summarizeInterval: Int,
) : StreamChatUseCase {

    override fun stream(prompt: String, sessionId: String?): StreamChatUseCase.Result {
        val validated = Prompt(prompt)
        val conversation = sessionId?.let { conversationStore.findById(it) }
            ?: Conversation()

        conversation.addMessage(ChatMessage(Role.USER, validated.value))
        conversationStore.save(conversation)

        val llmMessages = buildLlmMessages(conversation)

        val buffer = StringBuilder()
        val tokens: Flow<ChatToken> = tokenStream.stream(llmMessages)
            .onEach { token -> buffer.append(token.value) }
            .onCompletion { cause ->
                if (cause == null) {
                    asyncScope.launch {
                        conversation.addMessage(ChatMessage(Role.ASSISTANT, buffer.toString()))
                        conversationStore.save(conversation)
                        trySummarize(conversation)
                    }
                }
            }

        return StreamChatUseCase.Result(sessionId = conversation.id, tokens = tokens)
    }

    private fun buildLlmMessages(conversation: Conversation): List<ChatMessage> {
        val recent = conversation.recentMessages(maxRecentTurns)
        val summary = conversation.summary ?: return recent
        return listOf(ChatMessage(Role.SYSTEM, "Previous conversation summary:\n$summary")) + recent
    }

    private suspend fun trySummarize(conversation: Conversation) {
        val oldMessages = conversation.oldMessages(maxRecentTurns)
        if (oldMessages.isEmpty()) return

        val oldTurns = oldMessages.count { it.role == Role.USER }
        if (oldTurns < summarizeInterval) return

        val newSummary = summarization.summarize(conversation.summary, oldMessages)
        conversation.summary = newSummary
        conversation.trimToRecent(maxRecentTurns)
        conversationStore.save(conversation)
    }
}
