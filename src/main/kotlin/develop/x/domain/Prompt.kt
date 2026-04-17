package develop.x.domain

/**
 * 사용자 입력 프롬프트. blank 는 도메인 invariant 위반.
 */
@JvmInline
value class Prompt(val value: String) {
    init {
        require(value.isNotBlank()) { "prompt must not be blank" }
    }
}
