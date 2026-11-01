# ---------------------------------------------------------------------------
# P3WIN — сборка релиза с обязательными проверками.
#
#   powershell -ExecutionPolicy Bypass -File .\scripts\release.ps1
#   powershell -ExecutionPolicy Bypass -File .\scripts\release.ps1 -SkipBuild
#
# Порядок намеренно жёсткий: сначала бесплатные проверки (статика, контрольные
# суммы, чистота ядра), потом дорогая сборка. Нет смысла ждать десять минут
# Gradle, чтобы узнать, что в ресурсах опечатка.
# ---------------------------------------------------------------------------
param(
    [switch]$SkipBuild,
    [switch]$SkipAssets,
    [string]$OutputDir = "dist"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $root

$failed = @()
function Step($title) {
    Write-Host ""
    Write-Host "============================================================" -ForegroundColor Cyan
    Write-Host " $title" -ForegroundColor Cyan
    Write-Host "============================================================" -ForegroundColor Cyan
}

function Note($text) { Write-Host "  $text" }
function Warn($text) { Write-Host "  $text" -ForegroundColor Yellow }

# --- 0. Окружение ----------------------------------------------------------
Step "0/5 Окружение"
$java = (Get-Command java -ErrorAction SilentlyContinue)
if (-not $java) {
    Write-Host "FAIL: java не найдена в PATH. Нужен JDK 17." -ForegroundColor Red
    exit 2
}
Note "java: $((java -version 2>&1 | Select-Object -First 1))"
if (-not (Test-Path "local.properties")) {
    Warn "local.properties отсутствует — Gradle не найдёт SDK. Сначала scripts/check_env.ps1."
    $failed += "local.properties"
}

# --- 1. Ядро не изменено ---------------------------------------------------
Step "1/5 Ядро com.winlator не изменено"
# Вся работа ведётся в com/p3win. Если ядро правилось, обновление Winlator
# превратится в ручной merge — это нарушение архитектурного правила проекта.
if (Get-Command git -ErrorAction SilentlyContinue) {
    $changed = git status --porcelain -- app/src/main/java/com/winlator 2>$null
    if ($changed) {
        Write-Host "  Изменения в ядре:" -ForegroundColor Red
        $changed | ForEach-Object { Write-Host "    $_" -ForegroundColor Red }
        $failed += "ядро изменено"
    } else {
        Note "изменений нет"
    }
} else {
    Warn "git недоступен — проверка пропущена"
}

# --- 2. Статические проверки ----------------------------------------------
Step "2/5 Статические проверки P3"
$python = Get-Command python -ErrorAction SilentlyContinue
if (-not $python) { $python = Get-Command python3 -ErrorAction SilentlyContinue }
if (-not $python) {
    Write-Host "FAIL: python не найден — scripts/p3_static_check.py не запустить." -ForegroundColor Red
    $failed += "python"
} else {
    & $python.Source "scripts/p3_static_check.py"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "FAIL: статические проверки не пройдены." -ForegroundColor Red
        $failed += "p3_static_check"
    }
}

# --- 3. Целостность артефактов --------------------------------------------
Step "3/5 Вендорные артефакты"
if ($SkipAssets) {
    Warn "пропущено по -SkipAssets"
} elseif (Get-Command bash -ErrorAction SilentlyContinue) {
    bash "scripts/verify_assets.sh" --strict
    if ($LASTEXITCODE -ne 0) {
        Write-Host "FAIL: контрольные суммы .tzst не совпадают." -ForegroundColor Red
        $failed += "verify_assets"
    }
} else {
    Warn "bash не найден (Git Bash/WSL) — проверка сумм пропущена"
}

if ($failed.Count -gt 0) {
    Write-Host ""
    Write-Host "РЕЛИЗ ОСТАНОВЛЕН. Проблемы: $($failed -join ', ')" -ForegroundColor Red
    exit 1
}

# --- 4. Сборка -------------------------------------------------------------
Step "4/5 Сборка"
if ($SkipBuild) {
    Warn "пропущено по -SkipBuild"
} else {
    & ".\gradlew.bat" --no-daemon clean ":app:assembleRelease" --stacktrace
    if ($LASTEXITCODE -ne 0) {
        Write-Host "FAIL: сборка не удалась." -ForegroundColor Red
        exit 1
    }
}

# --- 5. Артефакты ----------------------------------------------------------
Step "5/5 Результат"
$apkDir = "app/build/outputs/apk"
$apks = Get-ChildItem -Path $apkDir -Filter "*.apk" -Recurse -ErrorAction SilentlyContinue
if (-not $apks) {
    Warn "APK не найден в $apkDir"
    exit 0
}

if (-not (Test-Path $OutputDir)) { New-Item -ItemType Directory -Path $OutputDir | Out-Null }

$version = (Select-String -Path "app/build.gradle" -Pattern "versionName\s+'([^']+)'" |
    Select-Object -First 1).Matches.Groups[1].Value
if (-not $version) { $version = "unknown" }

foreach ($apk in $apks) {
    $variant = $apk.Directory.Name
    $target = Join-Path $OutputDir ("P3WIN-{0}-{1}.apk" -f $version, $variant)
    Copy-Item $apk.FullName $target -Force
    $sizeMb = [math]::Round($apk.Length / 1MB, 1)
    Note "$variant : $sizeMb MB -> $target"

    # Подпись. Gradle не подписывает release без ключа в репозитории (и не
    # должен): ключ живёт вне гита, apksigner берёт его из переменных окружения.
    if ($variant -eq "release") {
        $store = $env:P3_RELEASE_STORE_FILE
        if (-not $store) {
            Warn "P3_RELEASE_STORE_FILE не задан — APK останется неподписанным (годен только для теста)."
        } else {
            $apksigner = $null
            if ($env:ANDROID_HOME) {
                $candidate = Get-ChildItem -Path (Join-Path $env:ANDROID_HOME "build-tools") `
                    -Filter "apksigner.bat" -Recurse -ErrorAction SilentlyContinue |
                    Sort-Object FullName -Descending | Select-Object -First 1
                if ($candidate) { $apksigner = $candidate.FullName }
            }
            if (-not $apksigner) {
                Warn "apksigner не найден (ANDROID_HOME/build-tools) — подпишите APK вручную."
            } else {
                & $apksigner sign `
                    "--ks" $store `
                    "--ks-pass" ("pass:" + $env:P3_RELEASE_STORE_PASSWORD) `
                    "--ks-key-alias" $env:P3_RELEASE_KEY_ALIAS `
                    "--key-pass" ("pass:" + $env:P3_RELEASE_KEY_PASSWORD) `
                    $target
                if ($LASTEXITCODE -ne 0) {
                    Write-Host "FAIL: подпись не удалась." -ForegroundColor Red
                    exit 1
                }
                & $apksigner verify --print-certs $target | Select-Object -First 3 | ForEach-Object { Note $_ }
                Note "подписан"
            }
        }
    }

    # Отпечаток SHA-256: без него невозможно проверить, что у пользователя
    # именно тот файл, который собрали здесь.
    $hash = (Get-FileHash $target -Algorithm SHA256).Hash.ToLower()
    "$hash  $([System.IO.Path]::GetFileName($target))" |
        Out-File -FilePath (Join-Path $OutputDir "SHA256SUMS.txt") -Append -Encoding ascii
    Note "sha256: $hash"
}

Write-Host ""
Write-Host "Готово. Файлы: $OutputDir" -ForegroundColor Green
Write-Host "Перед публикацией: подпишите APK и проверьте установку на устройстве." -ForegroundColor Yellow
