#!/bin/bash
# AI Code Review via local Ollama
# pre-commit hook에서 호출됨

OLLAMA_URL="${OLLAMA_BASE_URL:-http://localhost:11434}"
OLLAMA_MODEL="${OLLAMA_MODEL:-qwen3:14b}"
PROJECT_ROOT="$(git rev-parse --show-toplevel)"
REVIEW_FILE="${PROJECT_ROOT}/.ai-review.md"

# staged diff 가져오기 (커밋 대상만)
DIFF=$(git diff --cached -- '*.kt' '*.kts' '*.yml' '*.yaml')

if [ -z "$DIFF" ]; then
  exit 0
fi

DIFF_SIZE=${#DIFF}
echo "🤖 AI Code Review 시작 (${DIFF_SIZE} bytes, model: ${OLLAMA_MODEL})"

# diff가 너무 크면 truncate
if [ "$DIFF_SIZE" -gt 20000 ]; then
  DIFF=$(echo "$DIFF" | head -c 20000)
  DIFF="${DIFF}\n\n... (truncated)"
  echo "⚠️  diff가 커서 20KB로 잘랐습니다"
fi

# Ollama 서버 확인
if ! curl -s --max-time 3 "${OLLAMA_URL}/api/tags" > /dev/null 2>&1; then
  echo "⚠️  Ollama 서버에 연결할 수 없습니다 (${OLLAMA_URL}). 리뷰를 건너뜁니다."
  exit 0
fi

PROMPT="당신은 Kotlin + Spring Boot 시니어 개발자이며 코드 리뷰어입니다.
아래 프로젝트 규칙을 반드시 숙지하고, diff를 리뷰하세요.

## 프로젝트 아키텍처 규칙 (헥사고날)

패키지 구조: develop.x.{domain, application, adapter, config}

의존성 방향: adapter → application → domain (바깥에서 안쪽으로만)

### 절대 위반 금지 규칙:
1. **domain/** 패키지는 순수 Kotlin만 허용. Spring(@Component, @Service, @Repository 등), Koog, Ktor, Reactor 등 프레임워크 import/annotation 절대 금지.
2. **application/service/** 패키지는 Spring annotation 금지. @Bean으로 config/에서 와이어링.
3. **application/** 패키지에서 adapter/ 패키지를 import 금지 (역방향 의존).
4. domain/ 타입(Prompt, ChatToken, ChatMessage, Conversation)에 프레임워크 어노테이션 금지.
5. ServerSentEvent, Mono, ResponseEntity 등 웹 프레임워크 타입은 adapter/inbound/web/에서만 사용.

### 보안 규칙:
- API 키, 비밀번호 등이 하드코딩되어 있으면 반드시 지적.
- SQL injection, XSS, command injection 가능성 확인.

### 리뷰 형식:
각 이슈를 아래 형식으로 출력하세요:
- [심각도] 파일명:설명

심각도: 🔴 critical, 🟡 warning, 🟢 info

이슈가 없으면 '✅ 문제 없음'만 출력.
한국어로 답변. 최대 15줄.

\`\`\`diff
${DIFF}
\`\`\`"

RESPONSE=$(curl -s --max-time 180 -X POST "${OLLAMA_URL}/api/generate" \
  -H "Content-Type: application/json" \
  -d "$(jq -n --arg model "$OLLAMA_MODEL" --arg prompt "$PROMPT" '{model: $model, prompt: $prompt, stream: false}')" \
  | jq -r '.response // empty')

if [ -z "$RESPONSE" ]; then
  echo "⚠️  리뷰 응답을 받지 못했습니다. 커밋을 계속 진행합니다."
  exit 0
fi

# 터미널 출력
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🤖 AI Review (${OLLAMA_MODEL})"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "$RESPONSE"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# 파일 저장 (.ai-review.md)
TIMESTAMP=$(date '+%Y-%m-%d %H:%M:%S')
STAGED_FILES=$(git diff --cached --name-only -- '*.kt' '*.kts' '*.yml' '*.yaml')

cat > "$REVIEW_FILE" << REVIEW_EOF
# AI Code Review

- **일시**: ${TIMESTAMP}
- **모델**: ${OLLAMA_MODEL}
- **변경 파일**:
$(echo "$STAGED_FILES" | sed 's/^/  - /')

## 리뷰 결과

${RESPONSE}
REVIEW_EOF

echo "📄 리뷰 결과 저장: .ai-review.md"

# IntelliJ에서 자동으로 리뷰 파일 열기 (GUI 커밋 시 결과 확인용)
if command -v idea > /dev/null 2>&1; then
  idea "$REVIEW_FILE" &
elif [ -d "/Applications/IntelliJ IDEA.app" ]; then
  open -a "IntelliJ IDEA" "$REVIEW_FILE" &
fi

# critical 이슈 감지 시 커밋 차단
if echo "$RESPONSE" | grep -q "🔴"; then
  echo "🔴 Critical 이슈가 감지되었습니다. 커밋을 계속하시겠습니까? (y/N)"
  exec < /dev/tty
  read -r answer
  if [ "$answer" != "y" ] && [ "$answer" != "Y" ]; then
    echo "❌ 커밋이 취소되었습니다."
    exit 1
  fi
fi

echo "✅ 커밋을 진행합니다."
exit 0
