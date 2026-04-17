#!/bin/bash
# AI Code Review via local Ollama
# pre-commit hook에서 호출됨

OLLAMA_URL="${OLLAMA_BASE_URL:-http://localhost:11434}"
OLLAMA_MODEL="${OLLAMA_MODEL:-llama3.1:8b}"

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

PROMPT="You are a senior Kotlin developer. Review this commit diff.
Focus only on:
1. Bugs or logic errors
2. Security issues
3. Architecture violations (hexagonal: domain must not depend on frameworks)

If no issues found, say '문제 없음'.
Be very concise (max 10 lines). Use Korean.

\`\`\`diff
${DIFF}
\`\`\`"

RESPONSE=$(curl -s --max-time 120 -X POST "${OLLAMA_URL}/api/generate" \
  -H "Content-Type: application/json" \
  -d "$(jq -n --arg model "$OLLAMA_MODEL" --arg prompt "$PROMPT" '{model: $model, prompt: $prompt, stream: false}')" \
  | jq -r '.response // empty')

if [ -z "$RESPONSE" ]; then
  echo "⚠️  리뷰 응답을 받지 못했습니다. 커밋을 계속 진행합니다."
  exit 0
fi

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🤖 AI Review (${OLLAMA_MODEL})"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "$RESPONSE"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# 심각한 이슈 키워드 감지
if echo "$RESPONSE" | grep -qi "보안\|취약\|security\|injection\|버그\|bug\|critical"; then
  echo "⚠️  잠재적 이슈가 감지되었습니다. 커밋을 계속하시겠습니까? (y/N)"
  exec < /dev/tty
  read -r answer
  if [ "$answer" != "y" ] && [ "$answer" != "Y" ]; then
    echo "❌ 커밋이 취소되었습니다."
    exit 1
  fi
fi

echo "✅ 커밋을 진행합니다."
exit 0
