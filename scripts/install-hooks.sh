#!/bin/bash
# Git hook 설치 스크립트
# 사용: ./scripts/install-hooks.sh

HOOK_DIR="$(git rev-parse --show-toplevel)/.git/hooks"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

cat > "${HOOK_DIR}/pre-commit" << 'HOOK'
#!/bin/bash
exec "$(git rev-parse --show-toplevel)/scripts/ai-review.sh"
HOOK

chmod +x "${HOOK_DIR}/pre-commit"
echo "✅ pre-commit hook 설치 완료"
echo "   Ollama URL: ${OLLAMA_BASE_URL:-http://localhost:11434}"
echo "   Model: ${OLLAMA_MODEL:-llama3.1:8b}"
echo ""
echo "   설정 변경: export OLLAMA_BASE_URL=http://your-server:11434"
echo "   비활성화: rm ${HOOK_DIR}/pre-commit"
