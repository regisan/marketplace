#!/usr/bin/env bash
# FF-07: verifica estrutura, numeração e indexação dos ADRs.
# Uso: ./scripts/verificar-adrs.sh   (executar a partir da raiz do repositório)
set -euo pipefail

ADR_DIR="docs/adr"
INDEX="${ADR_DIR}/README.md"
SECOES=("## Contexto" "## Alternativas consideradas" "## Decisão" "## Consequências" "## Validação")
STATUS_VALIDOS='^- \*\*Status:\*\* (Proposto|Aceito|Substituído por ADR-[0-9]{3}|Descontinuado)'
erros=0

falha() { echo "ERRO: $*"; erros=$((erros + 1)); }

shopt -s nullglob
arquivos=("${ADR_DIR}"/ADR-*.md)
[[ ${#arquivos[@]} -gt 0 ]] || { echo "Nenhum ADR encontrado em ${ADR_DIR}."; exit 1; }

declare -A numeros=()
for f in "${arquivos[@]}"; do
  nome=$(basename "$f")
  if [[ ! "$nome" =~ ^ADR-([0-9]{3})-[a-z0-9-]+\.md$ ]]; then
    falha "${nome}: nome fora do padrão ADR-nnn-titulo-em-kebab-case.md"
    continue
  fi
  num="${BASH_REMATCH[1]}"

  [[ -z "${numeros[$num]:-}" ]] || falha "${nome}: número ${num} repetido em ${numeros[$num]}"
  numeros[$num]="$nome"

  head -n 1 "$f" | grep -Eq "^# ADR-${num}: .+" || falha "${nome}: título deve começar com '# ADR-${num}: '"
  grep -Eq "$STATUS_VALIDOS" "$f" || falha "${nome}: status ausente ou inválido"
  for s in "${SECOES[@]}"; do
    grep -Fxq "$s" "$f" || falha "${nome}: seção obrigatória ausente: '${s}'"
  done
  grep -Fq "(${nome})" "$INDEX" || falha "${nome}: não está referenciado no índice ${INDEX}"
done

if [[ $erros -gt 0 ]]; then
  echo "${erros} problema(s) encontrado(s) nos ADRs."
  exit 1
fi
echo "ADRs válidos: ${#arquivos[@]} verificados."
