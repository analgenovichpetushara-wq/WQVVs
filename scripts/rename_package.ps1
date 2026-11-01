# ---------------------------------------------------------------------------
# P3WIN — переименование пакета приложения (Phase 6+, ОПЦИОНАЛЬНО).
#
#   powershell -ExecutionPolicy Bypass -File .\scripts\rename_package.ps1 `
#       -NewPackage "com.p3win.app" -DryRun
#   powershell -ExecutionPolicy Bypass -File .\scripts\rename_package.ps1 `
#       -NewPackage "com.p3win.app"
#
# ВНИМАНИЕ: скрипт намеренно НЕ делает массовый replace по всему дереву.
# Переименование applicationId ломает четыре независимых места, и если
# поправить только манифест, приложение соберётся и даже запустится, но
# контейнеры перестанут стартовать. Скрипт проходит их по очереди и
# останавливается, если чего-то не нашёл — молчаливая частичная замена хуже
# отказа.
#
#   1. app/build.gradle        — applicationId (namespace НЕ трогаем: на него
#                                завязан R-класс и все импорты com.winlator.R)
#   2. AndroidManifest.xml     — android:name активностей остаются com.winlator.*,
#                                меняется только applicationId/пакет приложения
#   3. rootfs                  — пути /data/data/com.winlator внутри rootfs.tzst
#                                и wineprefix-шаблона
#   4. Turnip ICD JSON         — "library_path": "/data/data/com.winlator/..."
#   5. JNI                      — макросы вида Java_com_winlator_... в *.cpp
#
# Пункты 3–5 требуют распаковки бинарных артефактов и пересборки нативных
# библиотек. Скрипт проверяет их наличие и печатает точный список того, что
# нужно пересобрать; за вас он этого не делает.
# ---------------------------------------------------------------------------
param(
    [Parameter(Mandatory = $true)][string]$NewPackage,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $root

function Fail($message) {
    Write-Host "FAIL: $message" -ForegroundColor Red
    exit 1
}

function Step($message) {
    Write-Host ""
    Write-Host "== $message" -ForegroundColor Cyan
}

if ($NewPackage -notmatch '^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$') {
    Fail "Некорректный package id: '$NewPackage'. Ожидается вид com.p3win.app (только нижний регистр, точки, подчёркивания)."
}

$gradle = "app/build.gradle"
$manifest = "app/src/main/AndroidManifest.xml"
if (-not (Test-Path $gradle)) { Fail "не найден $gradle" }
if (-not (Test-Path $manifest)) { Fail "не найден $manifest" }

$current = (Select-String -Path $gradle -Pattern "applicationId\s+'([^']+)'" |
    Select-Object -First 1).Matches.Groups[1].Value
if (-not $current) { Fail "не удалось прочитать applicationId из $gradle" }

Write-Host "Текущий applicationId : $current"
Write-Host "Новый applicationId   : $NewPackage"
Write-Host "Режим                 : $(if ($DryRun) { 'DRY RUN (ничего не меняется)' } else { 'ЗАПИСЬ' })"

if ($current -eq $NewPackage) {
    Write-Host "Пакет уже переименован — делать нечего." -ForegroundColor Green
    exit 0
}

# --- 1. applicationId ------------------------------------------------------
Step "1/5 applicationId в $gradle"
$content = Get-Content $gradle -Raw
$updated = $content -replace "(applicationId\s+')$([regex]::Escape($current))(')", "`${1}$NewPackage`${2}"
if ($updated -eq $content) { Fail "строка applicationId не найдена — проверьте формат файла вручную" }
if (-not $DryRun) { Set-Content -Path $gradle -Value $updated -NoNewline }
Write-Host "  applicationId -> $NewPackage" -ForegroundColor Green

# namespace остаётся прежним: R-класс и все импорты com.winlator.R к нему привязаны.
if ($updated -match "namespace\s+'(com\.winlator)'") {
    Write-Host "  namespace оставлен com.winlator (R-класс, импорты P3) — это ожидаемо."
}
elseif ($updated -match "namespace\s+'([^']+)'") {
    Write-Host "  ВНИМАНИЕ: namespace = $($Matches[1]). P3-файлы импортируют com.winlator.R;" -ForegroundColor Yellow
    Write-Host "  при смене namespace нужно обновить 25+ импортов. Скрипт этого не делает." -ForegroundColor Yellow
}

# --- 2. Манифест -----------------------------------------------------------
Step "2/5 AndroidManifest.xml"
$manifestText = Get-Content $manifest -Raw
$authorities = [regex]::Matches($manifestText, 'android:authorities="([^"]+)"')
if ($authorities.Count -gt 0) {
    foreach ($a in $authorities) {
        $value = $a.Groups[1].Value
        if ($value.StartsWith($current)) {
            Write-Host "  FileProvider authority: $value" -ForegroundColor Yellow
            Write-Host "  -> его нужно заменить на $NewPackage" -NoNewline
            Write-Host (".$($value.Substring($current.Length).TrimStart('.'))")
            if (-not $DryRun) {
                $manifestText = $manifestText.Replace($value,
                    $NewPackage + $value.Substring($current.Length))
            }
        }
    }
}
if (-not $DryRun -and $manifestText -ne (Get-Content $manifest -Raw)) {
    Set-Content -Path $manifest -Value $manifestText -NoNewline
    Write-Host "  authorities обновлены" -ForegroundColor Green
}

# --- 3. rootfs -------------------------------------------------------------
Step "3/5 rootfs (пути /data/data/$current)"
$rootfs = "app/src/main/assets/rootfs.tzst"
if (Test-Path $rootfs) {
    Write-Host "  $rootfs присутствует." -ForegroundColor Yellow
    Write-Host "  Артефакт содержит абсолютные пути /data/data/$current." -ForegroundColor Yellow
    Write-Host "  Требуется пересборка (см. docs/02_PACKAGE_RENAME.md). Скрипт НЕ трогает бинарь." -ForegroundColor Yellow
} else {
    Write-Host "  rootfs.tzst не найден — пропуск (артефакт качается отдельно)."
}

# --- 4. Turnip ICD ---------------------------------------------------------
Step "4/5 Turnip ICD JSON"
$icd = Get-ChildItem -Path "app/src/main/assets/graphics_driver" -Filter "*.json" -Recurse -ErrorAction SilentlyContinue
$icdHits = 0
foreach ($file in $icd) {
    $text = Get-Content $file.FullName -Raw
    if ($text -match [regex]::Escape("/data/data/$current")) {
        $icdHits++
        Write-Host "  $($file.Name): содержит /data/data/$current" -ForegroundColor Yellow
    }
}
if ($icdHits -eq 0) { Write-Host "  прямых ссылок на data-dir в ICD не найдено." }

# --- 5. JNI ----------------------------------------------------------------
Step "5/5 JNI-макросы"
$jniPattern = "Java_" + ($current -replace '\.', '_')
$cppHits = Select-String -Path "app/src/main/cpp/**/*.cpp","app/src/main/cpp/**/*.c" `
    -Pattern $jniPattern -SimpleMatch -ErrorAction SilentlyContinue
if ($cppHits) {
    Write-Host "  Найдено $($cppHits.Count) совпадений '$jniPattern*' в нативном коде." -ForegroundColor Yellow
    Write-Host "  Нативные библиотеки нужно пересобрать после замены макросов." -ForegroundColor Yellow
} else {
    Write-Host "  JNI-макросов с именем пакета не найдено (используются RegisterNatives) — правка не нужна." -ForegroundColor Green
}

Write-Host ""
if ($DryRun) {
    Write-Host "DRY RUN завершён. Запустите без -DryRun, чтобы применить шаги 1–2." -ForegroundColor Cyan
} else {
    Write-Host "Шаги 1–2 применены." -ForegroundColor Green
    Write-Host "Перед сборкой прочитайте docs/02_PACKAGE_RENAME.md: rootfs и драйверы" -ForegroundColor Yellow
    Write-Host "содержат абсолютные пути и требуют пересборки артефактов." -ForegroundColor Yellow
}
