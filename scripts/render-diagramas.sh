#!/usr/bin/env bash
# Valida e renderiza em SVG todos os diagramas PlantUML de docs/arquitetura.
# Requisitos: Java 17+ e Graphviz (dot). O JAR do PlantUML é baixado na primeira execução.
#
# Uso:
#   ./scripts/render-diagramas.sh           # valida e gera os SVGs ao lado de cada .puml
#   ./scripts/render-diagramas.sh --check   # apenas valida a sintaxe (usado no CI em pull requests)

set -euo pipefail

VERSION="${PLANTUML_VERSION:-1.2024.7}"
TOOLS_DIR=".tools"
JAR="${TOOLS_DIR}/plantuml-${VERSION}.jar"
SRC_DIR="docs/arquitetura"

if [[ ! -f "${JAR}" ]]; then
  mkdir -p "${TOOLS_DIR}"
  echo "Baixando PlantUML ${VERSION}..."
  curl -fsSL -o "${JAR}" \
    "https://github.com/plantuml/plantuml/releases/download/v${VERSION}/plantuml-${VERSION}.jar"
fi

mapfile -t FILES < <(find "${SRC_DIR}" -name '*.puml' | sort)

if [[ ${#FILES[@]} -eq 0 ]]; then
  echo "Nenhum diagrama encontrado em ${SRC_DIR}."
  exit 0
fi

echo "Validando ${#FILES[@]} diagramas..."
java -jar "${JAR}" -charset UTF-8 -checkonly -failfast2 "${FILES[@]}"

if [[ "${1:-}" == "--check" ]]; then
  echo "Sintaxe válida."
  exit 0
fi

echo "Renderizando SVGs..."
java -jar "${JAR}" -charset UTF-8 -tsvg -failfast2 "${FILES[@]}"
echo "Concluído."
