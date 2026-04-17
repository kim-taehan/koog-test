# Koog vs LangChain4j 비교

이 프로젝트는 **JetBrains Koog**를 선택했습니다. JVM 생태계의 또 다른 주요 선택지인 **LangChain4j**와의 비교입니다.

## 프레임워크 개요

| 항목 | Koog (JetBrains) | LangChain4j |
|------|-------------------|-------------|
| 언어 | **Kotlin-first** | **Java-first** (Kotlin 호환) |
| 출시 | 2024 (0.x) | 2023 (0.x → 1.x) |
| 설계 철학 | Kotlin DSL 기반 에이전트 프레임워크 | Python LangChain의 Java 포팅, 범용 LLM 통합 |
| 코루틴 | `suspend` / `Flow` 네이티브 | `CompletableFuture` 기반 (Reactor 지원은 별도) |
| 라이선스 | Apache 2.0 | Apache 2.0 |

## LLM 프로바이더 지원

| 프로바이더 | Koog | LangChain4j |
|-----------|------|-------------|
| OpenAI / Azure OpenAI | O | O |
| Anthropic | O | O |
| Ollama (로컬) | O (`prompt-executor-ollama-client`) | O (`langchain4j-ollama`) |
| Google Vertex AI | O | O |
| AWS Bedrock | O | O |
| HuggingFace | O | O |
| Mistral / Groq / etc. | 일부 | O (광범위) |

LangChain4j가 프로바이더 수에서 압도적. Koog는 주요 프로바이더에 집중.

## 스트리밍

| 항목 | Koog | LangChain4j |
|------|------|-------------|
| 스트리밍 API | `Flow<StreamFrame>` | `StreamingChatLanguageModel` + `TokenStream` |
| 프레임 타입 | `TextDelta`, `ReasoningDelta`, `ToolCallDelta`, `ToolCallComplete` 등 구분 | `onNext(String)` 콜백 기반 |
| WebFlux 통합 | `Flow` → SSE 자연스러움 | Reactor 변환 필요 (`Flux.create`) |
| Kotlin 친화성 | **네이티브** (suspend, Flow) | Java 콜백 → coroutine 브릿지 필요 |

## Tool Calling

| 항목 | Koog | LangChain4j |
|------|------|-------------|
| Tool 정의 | `ToolDescriptor` 수동 구성 | `@Tool` 어노테이션 또는 `ToolSpecification` |
| Tool 실행 루프 | **수동 구현** (execute → tool call 감지 → 결과 전달 → 재호출) | **자동** (`AiServices`가 tool 실행 루프 내장) |
| 스트리밍 + Tool | 하이브리드 필요 (이 프로젝트의 Phase 1/2 방식) | 자동 처리 (모델에 따라 제한) |
| 유연성 | 높음 (직접 제어) | 중간 (프레임워크 컨벤션 따름) |

**핵심 차이**: LangChain4j의 `AiServices`는 tool calling 루프를 자동으로 처리합니다. Koog는 tool call 감지 → 실행 → 재호출을 개발자가 직접 구현해야 합니다. 이 프로젝트에서 하이브리드 방식을 채택한 이유이기도 합니다.

## 메모리 / 멀티턴

| 항목 | Koog | LangChain4j |
|------|------|-------------|
| 대화 메모리 | **없음** (직접 구현) | `ChatMemory` 내장 (`MessageWindowChatMemory`, `TokenWindowChatMemory`) |
| 요약 메모리 | **없음** (직접 구현) | `ChatMemoryProvider` + 커스텀 가능 |
| 메시지 관리 | Prompt DSL로 수동 구성 | `ChatMemory`가 자동 관리 |

이 프로젝트에서 `Conversation` + 슬라이딩 윈도우 + 비동기 요약을 직접 구현한 이유입니다. LangChain4j는 이를 내장으로 제공합니다.

## Spring Boot 통합

| 항목 | Koog | LangChain4j |
|------|------|-------------|
| Spring Boot Starter | **없음** (수동 빈 등록) | `langchain4j-spring-boot-starter` 공식 제공 |
| Auto-configuration | X | O (properties로 모델 설정) |
| 빈 와이어링 | `@Configuration`에서 직접 | `@AiService` 어노테이션으로 자동 |

## 에이전트 프레임워크

| 항목 | Koog | LangChain4j |
|------|------|-------------|
| 에이전트 런타임 | `agents-core` (그래프 기반 워크플로우) | `AiServices` (선형 체인) |
| 워크플로우 정의 | Kotlin DSL 그래프 | 체인 빌더 또는 직접 구현 |
| RAG | `rag-base` 모듈 제공 | `EasyRAG`, `ContentRetriever` 등 풍부 |
| 임베딩/벡터 DB | 기본 지원 | Pinecone, Milvus, Weaviate, Chroma 등 광범위 |

## 이 프로젝트에서 Koog를 선택한 이유

1. **Kotlin-native**: `suspend` / `Flow`가 Spring WebFlux + Reactor Netty와 자연스럽게 매핑. 콜백 브릿지 불필요.
2. **경량**: 필요한 모듈만 선택적 의존 (`prompt-model`, `prompt-executor-ollama-client`). LangChain4j는 전이 의존성이 무거울 수 있음.
3. **JetBrains 생태계**: IntelliJ 플러그인, Kotlin 컴파일러와의 호환성 보장. Kotlin 2.2 같은 최신 버전 즉시 지원.
4. **학습 목적**: 프레임워크가 자동화하는 부분(메모리, tool calling 루프)을 직접 구현하면서 LLM 통합의 내부 동작을 이해.

## Koog가 유리한 경우

- Kotlin 프로젝트에서 **코루틴/Flow 기반 스트리밍**이 핵심인 경우
- 에이전트 워크플로우를 **그래프 DSL**로 정의하고 싶은 경우
- JetBrains IDE 통합이 중요한 경우
- 프레임워크 의존을 최소화하고 **직접 제어**를 원하는 경우

## LangChain4j가 유리한 경우

- **Java 팀**이거나 Kotlin 경험이 적은 경우
- Tool calling, 메모리, RAG 등을 **빠르게 프로토타이핑**하고 싶은 경우
- 다양한 **벡터 DB / 프로바이더 통합**이 필요한 경우
- Spring Boot **auto-configuration**으로 설정을 최소화하고 싶은 경우
- **커뮤니티 크기**와 레퍼런스가 중요한 경우 (LangChain4j가 훨씬 큼)
