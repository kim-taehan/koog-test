package develop.x.config

import develop.x.application.port.inbound.StreamChatUseCase
import develop.x.application.port.outbound.ConversationStore
import develop.x.application.port.outbound.SummarizationPort
import develop.x.application.port.outbound.TokenStreamPort
import develop.x.application.service.StreamChatService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * application/ 레이어를 프레임워크 어노테이션 없이 유지하기 위한 와이어링 지점.
 * 새 use case 가 추가되면 여기에서 @Bean 으로 등록.
 */
@Configuration
class BeanConfig {

    @Bean
    fun asyncScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Bean
    fun streamChatUseCase(
        tokenStream: TokenStreamPort,
        conversationStore: ConversationStore,
        summarization: SummarizationPort,
        asyncScope: CoroutineScope,
        @Value("\${chat.max-recent-turns:5}") maxRecentTurns: Int,
        @Value("\${chat.summarize-interval:3}") summarizeInterval: Int,
    ): StreamChatUseCase = StreamChatService(
        tokenStream = tokenStream,
        conversationStore = conversationStore,
        summarization = summarization,
        asyncScope = asyncScope,
        maxRecentTurns = maxRecentTurns,
        summarizeInterval = summarizeInterval,
    )
}
