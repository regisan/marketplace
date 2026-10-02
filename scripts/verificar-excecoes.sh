#!/usr/bin/env bash
# FF-10: impede exceções técnicas vencidas ou referências a exceções inexistentes.
# Uso: ./scripts/verificar-excecoes.sh   (executar a partir da raiz do repositório)
set -euo pipefail

EXC_DIR="docs/governanca/excecoes"
HOJE="${HOJE:-$(date -u +%F)}"
erros=0
falha() { echo "ERRO: $*"; erros=$((erros + 1)); }

declare -A ativas=()
shopt -s nullglob
for f in "${EXC_DIR}"/EXC-[0-9][0-9][0-9]-*.md; do
  id=$(basename "$f" | grep -Eo '^EXC-[0-9]{3}')
  status=$(grep -Eo '^- \*\*Status:\*\* .+' "$f" | sed 's/^- \*\*Status:\*\* //' || true)
  expira=$(grep -Eo '^- \*\*Expira em:\*\* [0-9]{4}-[0-9]{2}-[0-9]{2}' "$f" | grep -Eo '[0-9]{4}-[0-9]{2}-[0-9]{2}' || true)

  [[ -n "$status" ]] || { falha "${id}: status ausente"; continue; }
  if [[ "$status" == "Ativa" ]]; then
    [[ -n "$expira" ]] || { falha "${id}: exceção ativa sem data de expiração"; continue; }
    if [[ "$expira" < "$HOJE" ]]; then
      falha "${id}: expirou em ${expira}. Cumpra a regra, renove com aprovação ou encerre a exceção."
    else
      ativas[$id]=1
    fi
  fi
done

# Toda referência a EXC-nnn fora da pasta de exceções precisa apontar para uma exceção ativa.
while IFS=: read -r arquivo ref; do
  [[ -n "${ativas[$ref]:-}" ]] || falha "${arquivo} referencia ${ref}, que não existe ou não está ativa"
done < <(grep -rEo 'EXC-[0-9]{3}' --exclude-dir=.git --exclude-dir="$(basename "$EXC_DIR")" \
           --exclude-dir=node_modules --exclude-dir=target --exclude-dir=.tools . 2>/dev/null | sort -u || true)

if [[ $erros -gt 0 ]]; then
  echo "${erros} problema(s) com exceções técnicas."
  exit 1
fi
echo "Exceções técnicas válidas: ${#ativas[@]} ativa(s)."
