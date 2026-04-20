# koog-test

Spring Boot 3.4 (WebFlux) + Kotlin 2.2 + JetBrains Koog 0.8 기반의 스트리밍 채팅 API.
로컬 Ollama 인스턴스와 연동하여 Server-Sent Events(SSE)로 LLM 토큰을 스트리밍합니다.

## 전체 아키텍처

### 기술 스택

| 구분 | 기술 | 버전 |
|------|------|------|
| 언어 | Kotlin (JVM) | 2.2.0 |
| JVM | Foojay 자동 프로비저닝 | 21 |
| 프레임워크 | Spring Boot (WebFlux, Reactor Netty) | 3.4.1 |
| LLM 클라이언트 | JetBrains Koog / LangChain4j (프로파일 전환) | 0.8.0 / 1.13.0 |
| LLM 런타임 | Ollama (로컬) | - |
| 웹 검색 | Tavily Search API | - |
| API 문서 | springdoc-openapi (Swagger UI) | 2.8.6 |
| 빌드 | Gradle (Kotlin DSL) | Wrapper |
| 테스트 | JUnit 5 + kotlin-test + Mockito-Kotlin + reactor-test | - |

### 헥사고날 아키텍처

바깥 레이어에서 안쪽으로만 의존. 도메인은 어떤 프레임워크에도 의존하지 않음.

```
┌──────────────────────────────────────────────────────────────┐
│  adapter/inbound/web                                         │
│  ┌───────────────────┐  ┌──────────────────┐                 │
│  │ ChatSseController │  │ HelloController  │                 │
│  └────────┬──────────┘  └──────────────────┘                 │
│           │ (POST /sse)                                      │
├───────────┼──────────────────────────────────────────────────┤
│  application                                                 │
│           ▼                                                  │
│  ┌─────────────────────┐                                     │
│  │  StreamChatUseCase  │ ← inbound port (interface)          │
│  └────────┬────────────┘                                     │
│           ▼                                                  │
│  ┌─────────────────────┐                                     │
│  │ StreamChatService   │ ← use case 구현 (순수 Kotlin)        │
│  └──┬─────┬────────┬───┘                                     │
│     │     │        │                                         │
│     ▼     ▼        ▼                                         │
│  ┌──────┐┌────────┐┌──────────────┐                          │
│  │Token ││Conver- ││Summarization │ ← outbound ports         │
│  │Stream││sation  ││Port          │                          │
│  │Port  ││Store   ││              │                          │
│  └──────┘└────────┘└──────────────┘                          │
├──────────────────────────────────────────────────────────────┤
│  domain                                                      │
│  ┌────────┐┌──────────┐┌──────────────┐┌─────────────────┐   │
│  │ Prompt ││ChatToken ││ ChatMessage  ││WebSearchResult  │   │
│  └────────┘└──────────┘│ Conversation │└─────────────────┘   │
│                        └──────────────┘                      │
├──────────────────────────────────────────────────────────────┤
│  adapter/outbound                                            │
│  ┌────────────────────┐┌──────────────────┐┌───────────────┐ │
│  │ KoogOllamaToken    ││ KoogOllamaSumma- ││ InMemory      │ │
│  │ StreamAdapter      ││ rizationAdapter  ││ Conversation  │ │
│  │ (profile: koog)    ││ (profile: koog)  ││ Store         │ │
│  ├────────────────────┤├──────────────────┤└───────────────┘ │
│  │ LangChain4jToken   ││ LangChain4j     │                  │
│  │ StreamAdapter      ││ Summarization   │                  │
│  │ (profile:          ││ Adapter         │                  │
│  │  langchain4j)      ││ (profile:       │                  │
│  │                    ││  langchain4j)   │                  │
│  └────────┬───────────┘└────────┬─────────┘                  │
│           │                     │                            │
│           ▼                     ▼                            │
│     ┌──────────┐          ┌──────────┐                       │
│     │  Ollama  │          │  Ollama  │                       │
│     └──────────┘          └──────────┘                       │
│                                                              │
│  ┌────────────────────┐                                      │
│  │ TavilyWebSearch    │ ← WebSearchPort 구현                  │
│  │ Adapter            │                                      │
│  └────────┬───────────┘                                      │
│           ▼                                                  │
│     ┌──────────┐                                             │
│     │  Tavily  │ (api.tavily.com)                            │
│     └──────────┘                                             │
├──────────────────────────────────────────────────────────────┤
│  config                                                      │
│  ┌──────────────┐  ┌──────────────┐                          │
│  │  BeanConfig  │  │ OllamaConfig │                          │
│  └──────────────┘  └──────────────┘                          │
│  순수 Kotlin 서비스를 Spring Bean으로 등록 + CoroutineScope     │
└──────────────────────────────────────────────────────────────┘
```

### 패키지 구조

```
src/main/kotlin/develop/x/
├── App.kt                                  ← Spring Boot 엔트리포인트
├── config/
│   ├── BeanConfig.kt                       ← 유즈케이스 빈 와이어링 + CoroutineScope
│   └── OllamaConfig.kt                    ← OllamaClient, LLModel 빈
├── domain/
│   ├── Prompt.kt                           ← 입력 프롬프트 값 객체 (blank 검증)
│   ├── ChatToken.kt                        ← 스트리밍 토큰 값 객체
│   ├── ChatMessage.kt                      ← role(USER, ASSISTANT, SYSTEM) + content
│   ├── Conversation.kt                    ← 대화 세션 (id + messages + summary)
│   └── WebSearchResult.kt                 ← 웹 검색 결과 값 객체
├── application/
│   ├── port/
│   │   ├── inbound/
│   │   │   └── StreamChatUseCase.kt        ← 채팅 스트리밍 유즈케이스
│   │   └── outbound/
│   │       ├── TokenStreamPort.kt          ← LLM 토큰 스트리밍 포트
│   │       ├── ConversationStore.kt        ← 대화 저장/조회 포트
│   │       ├── SummarizationPort.kt        ← 대화 요약 포트
│   │       └── WebSearchPort.kt            ← 웹 검색 포트
│   └── service/
│       └── StreamChatService.kt            ← 유즈케이스 구현 (멀티턴 + 요약 + 비동기)
└── adapter/
    ├── inbound/web/
    │   ├── ChatSseController.kt            ← POST /sse (SSE 스트리밍)
    │   └── HelloController.kt              ← GET / (인사)
    └── outbound/
        ├── llm/
        │   ├── KoogOllamaTokenStreamAdapter.kt      ← [koog] 하이브리드 tool calling + 스트리밍
        │   ├── KoogOllamaSummarizationAdapter.kt    ← [koog] Ollama로 대화 요약 생성
        │   ├── LangChain4jTokenStreamAdapter.kt     ← [langchain4j] 단일 스트리밍 tool calling
        │   └── LangChain4jSummarizationAdapter.kt   ← [langchain4j] 대화 요약 생성
        ├── persistence/
        │   └── InMemoryConversationStore.kt         ← ConcurrentHashMap 저장소
        └── search/
            └── TavilyWebSearchAdapter.kt            ← Tavily REST API 호출
```

### 의존성 규칙

```
adapter → application → domain
  (바깥)     (가운데)     (안쪽)
```

- **domain**: 순수 Kotlin. 프레임워크 의존성 없음.
- **application**: 코루틴/Flow 허용, Spring/Koog 임포트 금지. `@Bean`으로 외부에서 와이어링.
- **adapter**: 프레임워크 코드 허용 (`@RestController`, `@Component`). 포트 인터페이스를 구현.
- **config**: 순수 Kotlin 서비스를 Spring Bean으로 등록하는 글루 레이어.

### 데이터 흐름

`spring.profiles.active`로 Koog / LangChain4j 전환. 도메인과 서비스는 동일하고 **어댑터만 교체**됨.

#### Koog 프로파일 (`koog`)

하이브리드 방식: `execute()` (비스트리밍)으로 tool call 감지 → `executeStreaming()`으로 최종 응답 스트리밍.
Koog 0.8.0의 `executeStreaming()`이 Ollama에 tools를 전달하지 않는 [버그](docs/koog-streaming-tool-call-analysis.md) 때문.

```
Client       Adapter              Ollama           Tavily
  │            │                    │                │
  │            │ execute()          │                │
  │            │ (with tools,       │                │
  │            │  non-streaming)    │                │
  │            │───────────────────>│                │
  │            │ ToolCall 또는 텍스트│                │
  │            │<───────────────────│                │
  │            │                    │                │
  │            │ [tool call 있으면]  │                │
  │ SSE: [tool: web_search 호출 중...]                │
  │<───────────│ search(query)      │                │
  │            │───────────────────────────────────>│
  │            │ results            │                │
  │            │<───────────────────────────────────│
  │ SSE: [tool: web_search 완료]    │                │
  │<───────────│                    │                │
  │            │ executeStreaming() │                │
  │            │ (tool 결과 포함)    │                │
  │            │───────────────────>│                │
  │ SSE: 토큰   │<── token stream ──│                │
  │<───────────│                    │                │
```

#### LangChain4j 프로파일 (`langchain4j`)

단일 스트리밍: `StreamingChatModel.chat()`으로 **토큰 스트리밍 + tool call 감지를 동시에** 처리.

```
Client       Adapter              Ollama           Tavily
  │            │                    │                │
  │            │ chat(streaming)    │                │
  │            │ (with tools)       │                │
  │            │───────────────────>│                │
  │            │                    │                │
  │            │ onCompleteResponse │                │
  │            │ → tool call 감지   │                │
  │ SSE: [tool: web_search 호출 중...]                │
  │<───────────│ search(query)      │                │
  │            │───────────────────────────────────>│
  │            │ results            │                │
  │            │<───────────────────────────────────│
  │ SSE: [tool: web_search 완료]    │                │
  │<───────────│                    │                │
  │            │ chat(streaming)    │                │
  │            │ (tool 결과 포함)    │                │
  │            │───────────────────>│                │
  │ SSE: 토큰   │<─ onPartialResponse│                │
  │<───────────│   (토큰 단위)       │                │
  │ SSE: 토큰   │                    │                │
  │<───────────│                    │                │
```

#### 프로파일별 비교

| | Koog (`koog`) | LangChain4j (`langchain4j`) |
|---|---|---|
| tool 없는 경우 | `execute()` 1회, 전체 응답 한번에 | **토큰 단위 실시간 스트리밍** |
| tool call 감지 | `execute()` 비스트리밍 | `onCompleteResponse()` 스트리밍 중 감지 |
| 최종 응답 | `executeStreaming()` 토큰 스트리밍 | `onPartialResponse()` 토큰 스트리밍 |
| Ollama 호출 횟수 (tool 없음) | 1회 | 1회 |
| Ollama 호출 횟수 (tool 있음) | 2회 (execute + streaming) | 2회 (streaming × 2) |

## 멀티턴 전략

### 개요

대화 히스토리를 관리하면서 LLM 컨텍스트 윈도우를 효율적으로 사용하기 위한 **슬라이딩 윈도우 + 요약** 전략.

- **최근 N턴**(기본 5턴)은 원문 그대로 LLM에 전달
- **N턴 초과분이 K턴**(기본 3턴) 누적되면 비동기로 요약 트리거
- 요약본은 system 메시지로 최근 메시지 앞에 삽입

### 턴별 동작 예시

| 턴 | LLM에 전달하는 메시지 | 요약 트리거 |
|----|----------------------|------------|
| 1~5 | 원문 메시지 1~5 | X |
| 6 | 요약(없음) + 원문 2~6 | X (초과 1턴) |
| 7 | 요약(없음) + 원문 3~7 | X (초과 2턴) |
| 8 | 요약(없음) + 원문 4~8 | O → 턴 1~3 요약 생성 후 제거 |
| 9 | 요약 + 원문 5~9 | X (초과 1턴) |
| 10 | 요약 + 원문 6~10 | X (초과 2턴) |
| 11 | 요약 + 원문 7~11 | O → 기존 요약 + 턴 4~6 재요약 후 제거 |

### 요약 프로세스

1. 기존 요약본(있으면) + 윈도우 밖 오래된 메시지를 입력으로 LLM 호출
2. 새 요약본 생성 → `Conversation.summary`에 저장
3. 요약된 오래된 메시지를 히스토리에서 제거
4. 전체 과정은 **비동기**로 처리 — 사용자 응답을 블로킹하지 않음

## Tool Calling (웹 검색)

### 개요

LLM이 최신 정보가 필요하다고 판단하면 **Tavily Web Search API**를 자동으로 호출하여 검색 결과를 기반으로 응답합니다.

### 프로파일별 구현

#### Koog (`koog`) — 하이브리드 방식

Koog 0.8.0의 `executeStreaming()`이 Ollama에 tools를 전달하지 않는 [버그](docs/koog-streaming-tool-call-analysis.md)로 인해 하이브리드 접근을 사용합니다.

| Phase | 메서드 | 목적 |
|-------|--------|------|
| Phase 1 | `execute()` (non-streaming) | tool call 감지 + tool 실행 루프 (최대 3회) |
| Phase 2 | `executeStreaming()` (streaming) | tool 결과를 포함하여 최종 응답 토큰 스트리밍 |

- **장점**: tool call을 정확하게 감지하고 실행하며, 최종 응답은 완전한 스트리밍
- **단점**: tool 없는 경우에도 `execute()` 전체 응답 대기 후 한번에 전달 (토큰 단위 스트리밍 아님)

#### LangChain4j (`langchain4j`) — 단일 스트리밍

`StreamingChatModel.chat()`에 tools를 포함하여 호출. Ollama가 스트리밍 응답에서 tool call을 반환하면 `onCompleteResponse()`에서 감지하여 처리.

- **장점**: tool 유무와 관계없이 **처음부터 토큰 단위 실시간 스트리밍**. 단일 API로 tool call + 텍스트 모두 처리
- **단점**: 없음 (LangChain4j 1.13.0 기준)

#### 공통

tool call 발생 시 클라이언트에 **상태 메시지**가 SSE로 전달됩니다:
```
data:[tool: web_search 호출 중...]    ← tool 실행 시작
data:[tool: web_search 완료]          ← tool 실행 완료
data:2025년 한국의 최신 뉴스는...       ← 이후 토큰 단위 스트리밍
```

## 설정

### LLM 클라이언트 전환

`application.yml`의 `spring.profiles.active`로 전환:

```yaml
spring:
  profiles:
    active: koog        # Koog 0.8.0 (하이브리드 방식)
    # active: langchain4j  # LangChain4j 1.13.0 (단일 스트리밍)
```

또는 실행 시 지정:
```bash
./gradlew bootRun --args='--spring.profiles.active=langchain4j'
```

### application.yml

| 항목 | 기본값 | 설명 |
|------|--------|------|
| `spring.profiles.active` | `koog` | LLM 클라이언트 선택 (`koog` / `langchain4j`) |
| `ollama.base-url` | `http://localhost:11434` | Ollama 서버 주소 |
| `ollama.model` | `qwen3:14b` | 사용할 LLM 모델 |
| `chat.max-recent-turns` | `5` | LLM에 원문으로 전달할 최근 턴 수 |
| `chat.summarize-interval` | `3` | 요약을 트리거하는 초과 턴 누적 수 |
| `tavily.api-key` | `${TAVILY_API_KEY:}` | Tavily Search API 키 |

### 시크릿 관리

`.env.properties` 파일에 API 키를 저장하면 Spring Boot가 자동으로 읽습니다 (`.gitignore`에 포함).

```properties
# .env.properties
TAVILY_API_KEY=tvly-xxx
```

또는 환경변수 / VM 옵션으로 주입:
```bash
TAVILY_API_KEY=tvly-xxx ./gradlew bootRun
# 또는
./gradlew bootRun -Dtavily.api-key=tvly-xxx
```

## API

### POST /sse

멀티턴 대화 + 웹 검색 tool calling을 지원하는 SSE 스트리밍 엔드포인트.

```
POST /sse
Content-Type: application/json

{
  "prompt": "안녕하세요",
  "sessionId": null          ← 없으면 새 대화 생성
}

응답 (text/event-stream):
event: session
data: <sessionId>            ← 첫 이벤트로 세션 ID 전달

data: 안
data: 녕
data: 하세요
data: !
```

Tool calling 발생 시 응답 예시:
```
POST /sse
{"prompt": "2025년 한국 최신 뉴스 검색해서 알려줘"}

응답 (text/event-stream):
event: session
data: <sessionId>

data: [tool: web_search 호출 중...]
data: [tool: web_search 완료]
data: 2025
data: 년
data:  한국
data: 의
data:  최신
data:  뉴스는
...                              ← 이후 토큰 단위 스트리밍
```

### GET /

인사 메시지 반환.

### Swagger UI

`http://localhost:8080/swagger-ui.html`

## 빌드 및 실행

```bash
# 사전 조건: ollama serve 실행 + 모델 pull
ollama pull llama3.1:8b

# 서버 실행
./gradlew bootRun

# 테스트
./gradlew test

# 빌드
./gradlew build
```

### Smoke Test

```bash
# 일반 대화
curl -N -X POST http://localhost:8080/sse \
  -H 'Content-Type: application/json' \
  -d '{"prompt":"안녕하세요"}'

# 멀티턴 (sessionId 재사용)
curl -N -X POST http://localhost:8080/sse \
  -H 'Content-Type: application/json' \
  -d '{"prompt":"내 이름 뭐라고 했지?", "sessionId":"<위에서 받은 sessionId>"}'

# 웹 검색 (Tavily API 키 필요)
curl -N -X POST http://localhost:8080/sse \
  -H 'Content-Type: application/json' \
  -d '{"prompt":"2025년 한국 최신 뉴스 검색해서 알려줘"}'
```

## 테스트

총 82개 테스트, JUnit 5 + kotlin-test + Mockito-Kotlin.

| 테스트 클래스 | 테스트 수 | 레이어 |
|-------------|----------|--------|
| `PromptTest` | 5 | domain |
| `ChatTokenTest` | 3 | domain |
| `ChatMessageTest` | 7 | domain |
| `ConversationTest` | 27 | domain |
| `WebSearchResultTest` | 4 | domain |
| `StreamChatServiceTest` | 14 | application/service |
| `ToolCallingIntegrationTest` | 5 | application/service (tool calling) |
| `HelloControllerTest` | 1 | adapter/inbound (WebFlux) |
| `ChatSseControllerTest` | 3 | adapter/inbound (WebFlux + SSE) |
| `KoogOllamaTokenStreamAdapterTest` | 7 | adapter/outbound (Mockito) |
| `InMemoryConversationStoreTest` | 4 | adapter/outbound |

```bash
./gradlew test                                          # 전체 테스트
./gradlew test --tests "develop.x.domain.*"            # 도메인만
./gradlew test --tests "develop.x.FooTest.barMethod"   # 단일 메서드
```

테스트 리포트: `build/reports/tests/test/index.html`

### 동작 검증 기록

- [멀티턴 요약 전략 테스트 결과](docs/multi-turn-test-result.md) — 8턴 대화를 통한 히스토리 유지 및 요약 트리거 검증 (2026-04-17)
- [Tool Calling (Web Search) 테스트 결과](docs/tool-calling-test-result.md) — Tavily 웹 검색 tool calling 하이브리드 방식 검증 (2026-04-17)
- [Koog 스트리밍 Tool Call 분석](docs/koog-streaming-tool-call-analysis.md) — Koog 0.8.0 executeStreaming() tool call 미동작 원인 분석 및 하이브리드 방식 개선 (2026-04-20)
- **LangChain4j 스트리밍 Tool Call 검증** — LangChain4j 1.13.0에서 단일 스트리밍으로 tool call 감지 + 텍스트 스트리밍 정상 동작 확인 (2026-04-20)

## 프레임워크 비교

- [Koog vs LangChain4j 비교](docs/koog-vs-langchain4j.md) — JVM 생태계 LLM 프레임워크 선택 가이드
