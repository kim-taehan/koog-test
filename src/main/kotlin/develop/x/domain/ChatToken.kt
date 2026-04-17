package develop.x.domain

/**
 * 스트리밍 응답의 단일 토큰. 프레임워크와 무관한 순수 도메인 값.
 */
@JvmInline
value class ChatToken(val value: String)
