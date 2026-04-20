# Koog 0.8.0 스트리밍 Tool Call 분석

> 2026-04-20 작성. Koog 0.8.0 + Ollama (qwen3:14b) 환경에서 스트리밍 tool call 동작을 분석한 기록.

## 배경

LLM 스트리밍 응답에서 tool call을 감지하면 `execute()` (비스트리밍) + `executeStreaming()` 이중 호출 없이 **단일 스트리밍**으로 tool call과 텍스트 응답을 모두 처리할 수 있다. 이 가능성을 검증했다.

## Ollama의 스트리밍 tool call 지원

Ollama 자체는 스트리밍 응답에서 `tool_calls`를 별도 필드로 구분하여 반환한다.

### 텍스트 응답 (tool 없음)

```json
{"message": {"content": "안"}, "done": false}
{"message": {"content": "녕"}, "done": false}
{"message": {"content": "!"}, "done": true}
```

### tool call 응답

```json
{"message": {"role": "assistant", "content": "", "thinking": "...(생략)...",
  "tool_calls": [{
    "id": "call_ir71xg6a",
    "function": {"name": "web_search", "arguments": {"query": "latest news from Korea today"}}
  }]}, "done": false}
{"message": {"role": "assistant", "content": ""}, "done": true}
```

`message.tool_calls` 유무로 텍스트와 tool call을 구분할 수 있다. OpenAI와 달리 tool call을 토큰 단위로 쪼개지 않고 **한 청크에 완전한 tool call**을 보낸다.

### 비교: OpenAI vs Ollama

| | OpenAI | Ollama |
|---|---|---|
| tool call 전달 | `delta.tool_calls`로 토큰 단위 분할 | `message.tool_calls`로 한번에 전달 |
| 스트리밍 중 구분 | `delta.content` vs `delta.tool_calls` | `message.content` vs `message.tool_calls` |
| 파싱 복잡도 | 높음 (인자를 조각별로 조립) | 낮음 (완성된 JSON) |

## Koog의 StreamFrame 타입 체계

Koog의 `executeStreaming()`은 `Flow<StreamFrame>`을 반환하며, 다음 프레임 타입을 제공한다.

```
StreamFrame (sealed interface)
├─ DeltaFrame
│   ├─ TextDelta(text, index)
│   ├─ ReasoningDelta(text, index)
│   └─ ToolCallDelta(id, name, content, index)
├─ CompleteFrame
│   ├─ TextComplete(index)
│   ├─ ReasoningComplete(index)
│   └─ ToolCallComplete(id, name, content, index)
└─ End()
```

`filterTextOnly()`는 `TextDelta`만 필터링하는 확장 함수다.

## Koog 바이트코드 분석 결과

`prompt-executor-ollama-client-jvm-0.8.0.jar`의 `OllamaClient$executeStreaming` 클래스를 디컴파일한 결과:

1. **`OllamaChatMessageDTO`에 `toolCalls: List<OllamaToolCallDTO>` 필드 존재** — Ollama 응답의 tool_calls를 파싱할 구조가 있음
2. **스트리밍 루프에서 3가지 타입 처리 코드 존재**:
   - `getContent()` → `emitTextDelta()`
   - `getThinking()` → `emitReasoningDelta()`
   - `getToolCalls()` → `emitToolCallDelta()` ← **코드 존재**
3. tool call ID 생성: `generateToolCallId(toolName, arguments, index)` 함수 사용

**결론: Koog 코드에는 스트리밍 tool call 파싱 로직이 구현되어 있다.**

## 실제 테스트 결과

### 테스트 환경

- Koog 0.8.0
- Ollama qwen3:14b (thinking 모드 활성)
- `executeStreaming(prompt, model, tools)` — tools 포함하여 호출

### 수신된 프레임 로그

```
← frame: ReasoningDelta(text=Okay, ...)
← frame: ReasoningDelta(text= the, ...)
← frame: ReasoningDelta(text= user, ...)
... (thinking 토큰 약 100개)
← frame: ReasoningComplete(text=[...전체 thinking...])
← frame: TextDelta(text=I, ...)
← frame: TextDelta(text= currently, ...)
← frame: TextDelta(text= cannot, ...)
← frame: TextDelta(text= perform, ...)
← frame: TextDelta(text= real, ...)
← frame: TextDelta(text=-time, ...)
← frame: TextDelta(text= web, ...)
← frame: TextDelta(text= searches, ...)
...
```

**`ToolCallDelta`나 `ToolCallComplete` 프레임이 단 한 건도 수신되지 않았다.**

모델이 "I currently cannot perform real-time web searches"라고 텍스트로 응답 — tool이 제공되었음을 인식하지 못했다.

### 대조: 같은 질문을 `execute()` (비스트리밍)로 호출

```
← tool call: id=ollama_tool_call_539490340, name=web_search, args={"query":"latest Korea news today"}
```

**비스트리밍에서는 tool call이 정상 반환된다.**

### 대조: Ollama 직접 호출 (`curl`, `stream: true`)

```json
{"message": {"tool_calls": [{"id": "call_ir71xg6a", "function": {"name": "web_search", "arguments": {"query": "latest news from Korea today"}}}]}, "done": false}
```

**Ollama 자체는 스트리밍에서도 tool call을 반환한다.**

## 근본 원인: Koog 0.8.0 OllamaClient 버그

| 호출 방식 | tools 전달 | tool call 반환 |
|---|---|---|
| Ollama 직접 (`curl`, `stream: true`) | ✅ | ✅ |
| Koog `execute()` (비스트리밍) | ✅ | ✅ |
| Koog `executeStreaming()` | ❌ **null 전달 (버그)** | ❌ |

### 바이트코드 분석 결과

`OllamaClient.executeStreaming(Prompt, LLModel, List<ToolDescriptor>)` 메서드를 디컴파일하면:

```
// executeStreaming() 바이트코드 (offset 25-29)
25: aload_2          // model (param 2) ✓
26: aload_0          // this ✓
27: aload_1          // prompt (param 1) ✓
28: aconst_null      // null ← tools (param 3) 을 사용하지 않고 null 전달! ❌
29: invokespecial    // OllamaClient$executeStreaming$1.<init>()
```

inner class의 constructor signature: `(LLModel, OllamaClient, Prompt, Continuation)` — **tools 파라미터가 아예 없다.**

inner class에서 OllamaChatRequestDTO 생성 시:
```
// OllamaClient$executeStreaming$1.invokeSuspend() (offset 115-116)
new OllamaChatRequestDTO(
  model,       // ✓
  messages,    // ✓
  null,        // tools ← 항상 null ❌
  null,        // format
  options,
  true,        // stream = true
  ...
)
```

**메서드 시그니처는 `List<ToolDescriptor>`를 받지만, 구현체에서 해당 파라미터를 한 번도 참조하지 않는다.**

### 관련 GitHub 이슈

이 문제는 이미 보고되어 있다:

- [Issue #624](https://github.com/JetBrains/koog/issues/624): `Missing tools argument in executeStreaming() of LLMClient and PromptExecutor`
- [Issue #513](https://github.com/JetBrains/koog/issues/513): `Any plans to support the stream tool call functionality?`
- [PR #747](https://github.com/JetBrains/koog/pull/747): `Refactor streaming api to support tool calls` (2025-09-18 머지)

PR #747은 streaming API를 `Flow<String>` → `Flow<StreamFrame>`으로 변경하고 응답 파싱 로직(`emitToolCallDelta`)은 추가했지만, **OllamaClient의 요청 빌드에서 tools를 DTO에 전달하는 부분이 누락된 채 0.8.0에 릴리스**되었다. 파싱은 준비되어 있으나 요청에 tools가 빠지므로 Ollama가 tool call을 생성하지 않는다.

## LangChain4j와의 비교

LangChain4j도 Ollama 스트리밍 tool call 지원에 유사한 이슈가 있었다.

- [PR #2210](https://github.com/langchain4j/langchain4j/pull/2210): Ollama 스트리밍 모드 tool 지원 추가
- [Issue #2698](https://github.com/langchain4j/langchain4j/issues/2698): 스트리밍 + tool + Ollama 조합 문제 보고

LangChain4j에서 밝혀진 Ollama의 동작:
> "Ollama always returns all tool calls in one response, unlike other implementations that return them in deltas"

## 현재 채택한 하이브리드 방식

```
Phase 1: execute() — 비스트리밍으로 tool call 감지
  ├─ tool call 없음 → execute() 응답 텍스트를 직접 사용 (이중 호출 방지)
  └─ tool call 있음 → tool 실행 → toolResults에 추가 → 루프 (최대 3회)

Phase 2: executeStreaming() — tool 결과 포함하여 최종 응답 스트리밍 (tool call이 있었을 때만)
```

### 이전 방식과의 차이

| | 이전 (v1) | 현재 (v2) |
|---|---|---|
| tool 없는 경우 | `execute()` + `executeStreaming()` = 2회 호출, 이중 생성 | `execute()` 1회, 응답 직접 사용 |
| tool 있는 경우 | 동일 | 동일 |
| 스트리밍 UX (tool 없음) | 토큰 단위 스트리밍 (2회차) | 전체 응답 한번에 전달 |

### 트레이드오프

- **장점**: tool 없는 경우 이중 호출 제거로 응답 속도 향상
- **단점**: tool 없는 경우 토큰 단위 스트리밍 UX 없음 (전체 응답 한번에 전달)
- **향후**: Koog의 `OllamaClient.executeStreaming()`에서 tools를 DTO에 전달하도록 수정되면 단일 스트리밍으로 전환 가능. 응답 파싱 로직(`emitToolCallDelta`)은 이미 구현되어 있으므로 요청 측만 수정하면 된다.

## 참고 자료

- [Ollama Blog: Streaming responses with tool calling](https://ollama.com/blog/streaming-tool)
- [LangChain4j PR #2210: Ollama streaming tool support](https://github.com/langchain4j/langchain4j/pull/2210)
- [LangChain4j Issue #2698: Streaming with tools](https://github.com/langchain4j/langchain4j/issues/2698)
- [OpenAI: Streaming API responses](https://developers.openai.com/api/docs/guides/streaming-responses)
- [Koog: Predefined agent strategies](https://docs.koog.ai/predefined-agent-strategies/)
- [Koog Issue #624: Missing tools in executeStreaming()](https://github.com/JetBrains/koog/issues/624)
- [Koog Issue #513: Stream tool call support](https://github.com/JetBrains/koog/issues/513)
- [Koog PR #747: Refactor streaming api to support tool calls](https://github.com/JetBrains/koog/pull/747)
