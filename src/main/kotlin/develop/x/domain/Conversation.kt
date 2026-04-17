package develop.x.domain

import java.util.UUID

class Conversation(
    val id: String = UUID.randomUUID().toString(),
    private val _messages: MutableList<ChatMessage> = mutableListOf(),
    var summary: String? = null,
) {
    val messages: List<ChatMessage> get() = _messages

    fun addMessage(message: ChatMessage) {
        _messages.add(message)
    }

    /** 1턴 = user + assistant 한 쌍. 전체 턴 수 반환. */
    fun turnCount(): Int = _messages.count { it.role == ChatMessage.Role.USER }

    /** 최근 [n]턴의 메시지만 반환 (user+assistant 쌍 기준). */
    fun recentMessages(n: Int): List<ChatMessage> {
        if (n <= 0) return emptyList()
        var userCount = 0
        for (i in _messages.indices.reversed()) {
            if (_messages[i].role == ChatMessage.Role.USER) {
                userCount++
                if (userCount == n) {
                    return _messages.subList(i, _messages.size)
                }
            }
        }
        // n이 전체 턴수 이상이면 모든 메시지 반환
        return _messages.toList()
    }

    /** 최근 [n]턴을 제외한 오래된 메시지 반환. */
    fun oldMessages(n: Int): List<ChatMessage> {
        val recent = recentMessages(n)
        val recentStart = _messages.size - recent.size
        return if (recentStart > 0) _messages.subList(0, recentStart).toList() else emptyList()
    }

    /** 오래된 메시지를 제거하고 최근 [n]턴만 남김. */
    fun trimToRecent(n: Int) {
        val recent = recentMessages(n).toList()
        _messages.clear()
        _messages.addAll(recent)
    }
}
