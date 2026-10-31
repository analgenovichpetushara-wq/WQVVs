#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# P3WIN — проверка целостности вендорных артефактов ядра.
#
#   bash scripts/verify_assets.sh            # только проверка
#   bash scripts/verify_assets.sh --strict   # + сверка количества файлов
#
# Сверяет все .tzst из app/src/main/assets с core/assets.sha256.
# Запускать из Git Bash / WSL из корня репозитория.
# ---------------------------------------------------------------------------
set -u

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SUMS="$ROOT/core/assets.sha256"
ASSETS="$ROOT/app/src/main/assets"
STRICT=0
[ "${1:-}" = "--strict" ] && STRICT=1

if [ ! -f "$SUMS" ]; then
  echo "FAIL: не найден $SUMS" >&2
  exit 2
fi

cd "$ROOT" || exit 2

EXPECTED=$(grep -c . "$SUMS")
PRESENT=$(find "$ASSETS" -name '*.tzst' -type f | wc -l | tr -d ' ')

echo "============================================================"
echo " P3WIN — контроль вендорных артефактов"
echo "============================================================"
echo " записей в эталоне : $EXPECTED"
echo " файлов на диске   : $PRESENT"
echo "------------------------------------------------------------"

if command -v sha256sum >/dev/null 2>&1; then
  HASHER="sha256sum"
elif command -v shasum >/dev/null 2>&1; then
  HASHER="shasum -a 256"
else
  echo "FAIL: нет sha256sum/shasum" >&2
  exit 2
fi

FAILED=0
MISSING=0
while IFS= read -r line; do
  [ -z "$line" ] && continue
  want="${line%%  *}"
  file="${line#*  }"
  if [ ! -f "$file" ]; then
    echo "MISSING  $file"
    MISSING=$((MISSING + 1))
    continue
  fi
  got=$($HASHER "$file" | awk '{print $1}')
  if [ "$got" = "$want" ]; then
    echo "OK       $file"
  else
    echo "CORRUPT  $file"
    echo "         expected $want"
    echo "         actual   $got"
    FAILED=$((FAILED + 1))
  fi
done < "$SUMS"

echo "------------------------------------------------------------"
echo " повреждено: $FAILED   отсутствует: $MISSING"

STATUS=0
if [ "$FAILED" -ne 0 ] || [ "$MISSING" -ne 0 ]; then
  STATUS=1
fi

if [ "$STRICT" -eq 1 ] && [ "$PRESENT" -ne "$EXPECTED" ]; then
  echo "STRICT: на диске $PRESENT .tzst, ожидалось $EXPECTED"
  STATUS=1
fi

if [ "$STATUS" -eq 0 ]; then
  echo "РЕЗУЛЬТАТ: артефакты соответствуют baseline (a030f552 / winlator v11.2.0)"
else
  echo "РЕЗУЛЬТАТ: артефакты повреждены/неполны — выполните: bash scripts/bootstrap_assets.sh"
fi
exit "$STATUS"
