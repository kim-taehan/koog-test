package develop.x.domain

data class ChatMessage(
    val role: Role,
    val content: String,
) {
    enum class Role { USER, ASSISTANT, SYSTEM }
}
