# Tool Calling (Web Search) 테스트 결과

- **테스트 일시**: 2026-04-17
- **모델**: llama3.1:8b (Ollama 로컬)
- **검색 API**: Tavily Search API

## 동작 흐름

```
사용자: "2025년 한국 최신 뉴스 검색해서 알려줘"
        │
        ▼
Phase 1: execute (tool check) — non-streaming
        │
        ▼
LLM 판단: web_search tool call 필요
        │  args: {"query":"2025년 한국 최신 뉴스"}
        │
SSE → 클라이언트: [tool: web_search 호출 중...]
        │
        ▼
Tavily API 호출 → 5개 검색 결과 반환
        │
SSE → 클라이언트: [tool: web_search 완료]
        │
        ▼
Phase 2: executeStreaming (final)
— toolCall/toolResult DSL로 검색 결과 포함
— 토큰 단위 스트리밍 응답
```

## 서버 로그

```
→ Ollama execute (tool check): model=llama3.1:8b, messages=1, toolResults=0
← tool call: id=ollama_tool_call_3311411995, name=web_search, args={"query":"2025년 한국 최신 뉴스"}
→ Tavily search: query=2025년 한국 최신 뉴스, maxResults=5
← Tavily returned 5 results
→ tool result: 4371 chars
→ Ollama executeStreaming (final): model=llama3.1:8b, messages=1, toolResults=1
stream completed (cause=none)
```

## SSE 응답

```
event:session
data:3b2910db-990d-4830-b3c3-f299c8d24347

data:[tool: web_search 호출 중...]        ← tool 상태 메시지

data:[tool: web_search 완료]              ← tool 완료 메시지

data:202                                  ← 이후 토큰 단위 스트리밍
data:5
data:년
data: 한국
data:의
data: 최신
data: 뉴
data:스는
data: 다음과
data: 같습니다
...
```

## LLM 응답 요약

Tavily 검색 결과를 기반으로 2025년 한국 주요 뉴스 10건 정리:

1. 조기 대선으로 이재명 정부 출범
2. 윤석열 전 대통령 탄핵
3. 코스피 사상 최초 4000선 돌파
4. SKT, KT, 쿠팡 등 대규모 개인정보 유출
5. 국정자원 화재
6. 캄보디아 한국인 납치 살해 사건
7. 검찰청 폐지
8. 전국 대형 산불
9. 대규모 개인정보 유출
10. 한미 관세 협상 타결

## 모델 비교

| 항목 | qwen3:14b | llama3.1:8b |
|------|-----------|-------------|
| tool call 감지 | O (Phase 1) | O (Phase 1) |
| tool result 처리 | 메시지 형식(SYSTEM)으로 전달 → 빈 응답 | Koog DSL(toolCall/toolResult)로 전달 → 정상 응답 |
| 스트리밍 tool call | X (ToolCallComplete 프레임 미발생) | X (동일) |
| 응답 품질 | 자체 지식으로 답변 시도 | 검색 결과 기반 정확한 답변 |
| 속도 | ~40초 | ~15초 |

### 핵심 발견

1. **Koog 0.8.0 스트리밍에서 ToolCallComplete 미발생**: 바이트코드에 파싱 코드가 있지만 실제 동작하지 않음 → 하이브리드 방식 채택
2. **tool result 전달 형식이 중요**: 단순 SYSTEM 메시지가 아닌 Koog의 `toolCall().toolResult()` DSL을 사용해야 Ollama가 인식
3. **llama3.1:8b가 tool calling에 더 안정적**: 빠르고 tool result를 잘 활용함

## 구현 방식

```
Phase 1: execute() — non-streaming
  ├── tool call 감지
  ├── SSE: [tool: xxx 호출 중...]
  ├── tool 실행 (Tavily API)
  ├── SSE: [tool: xxx 완료]
  └── 반복 (최대 3회)

Phase 2: executeStreaming() — streaming
  ├── toolCall/toolResult DSL로 prompt 구성
  └── 토큰 단위 스트리밍 → SSE
```
