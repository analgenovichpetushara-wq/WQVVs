#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# P3WIN — восстановление вендорных артефактов ядра из запиненного снапшота.
#
#   bash scripts/bootstrap_assets.sh            # скачать/восстановить отсутствующие
#   bash scripts/bootstrap_assets.sh --force    # перезаписать всё
#
# Источник: brunodev85/winlator-app @ a030f552 (тот коммит, из которого собран
# релизный APK Winlator 11.2). Внутри архива лежат все assets, в т.ч. 141 MB .tzst.
#
# Требования: curl, tar, sha256sum. Windows: Git Bash или WSL2.
#   ВНИМАНИЕ: скрипт НЕ запускается из PowerShell/CMD — только bash.
# ---------------------------------------------------------------------------
set -u

PIN="a030f552f452158a2db64fdb32b490fa19c0b48d"
URL="https://codeload.github.com/brunodev85/winlator-app/tar.gz/${PIN}"
SUMS_NAME="assets.sha256"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SUMS="$ROOT/core/$SUMS_NAME"
ASSETS="$ROOT/app/src/main/assets"
FORCE=0
[ "${1:-}" = "--force" ] && FORCE=1

for bin in curl tar sha256sum; do
  command -v "$bin" >/dev/null 2>&1 || { echo "FAIL: нужен '$bin'" >&2; exit 2; }
done
[ -f "$SUMS" ] || { echo "FAIL: нет $SUMS" >&2; exit 2; }

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
echo "tmp: $TMP"

need_download=0
if [ "$FORCE" -eq 1 ]; then
  need_download=1
else
  while IFS= read -r line; do
    [ -z "$line" ] && continue
    file="${line#*  }"
    [ -f "$ROOT/$file" ] || { need_download=1; break; }
  done < "$SUMS"
fi

if [ "$need_download" -eq 0 ]; then
  echo "Все артефакты на месте — сверка (verify_assets.sh)."
  exec bash "$ROOT/scripts/verify_assets.sh"
fi

echo "Скачивание снапшота app@${PIN:0:8} (~148 MB)..."
curl -fL --retry 3 --progress-bar -o "$TMP/app.tar.gz" "$URL" || {
  echo "FAIL: не удалось скачать $URL" >&2
  echo "Проверьте сеть/прокси. Альтернатива: скачать архив вручную и распаковать assets/ в $ASSETS" >&2
  exit 3
}

echo "Распаковка assets/ ..."
mkdir -p "$TMP/src"
tar -xzf "$TMP/app.tar.gz" -C "$TMP/src" --strip-components=1 \
    "winlator-app-${PIN}/app/src/main/assets" || {
  echo "FAIL: архив не содержит app/src/main/assets" >&2
  exit 4
}

if [ ! -d "$TMP/src/app/src/main/assets" ]; then
  echo "FAIL: неожиданная структура архива" >&2
  exit 4
fi

mkdir -p "$ASSETS"
if [ "$FORCE" -eq 1 ]; then
  echo "Полная перезапись assets (--force)..."
  cp -a "$TMP/src/app/src/main/assets/." "$ASSETS/"
else
  echo "Докладываю только отсутствующие/повреждённые файлы..."
  while IFS= read -r line; do
    [ -z "$line" ] && continue
    want="${line%%  *}"
    file="${line#*  }"
    rel="${file#"${file%%app/src/main/assets/*}"}"
    dest="$ROOT/$file"
    ok=0
    if [ -f "$dest" ]; then
      got=$(sha256sum "$dest" | awk '{print $1}')
      [ "$got" = "$want" ] && ok=1
    fi
    if [ "$ok" -eq 0 ]; then
      mkdir -p "$(dirname "$dest")"
      cp -a "$TMP/src/app/src/main/assets/${rel#app/src/main/assets/}" "$dest" 2>/dev/null \
        || cp -a "$TMP/src/$file" "$dest"
      echo "restored $file"
    fi
  done < "$SUMS"

  # прочие assets (json, .icp, wallpapers, soundfont) — только если отсутствуют
  ( cd "$TMP/src/app/src/main/assets" && find . -type f ) | while IFS= read -r rel; do
    dest="$ASSETS/${rel#./}"
    [ -f "$dest" ] || { mkdir -p "$(dirname "$dest")"; cp -a "$TMP/src/app/src/main/assets/${rel#./}" "$dest"; echo "restored ${rel#./}"; }
  done
fi

echo "------------------------------------------------------------"
bash "$ROOT/scripts/verify_assets.sh" --strict
