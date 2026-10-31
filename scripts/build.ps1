# ---------------------------------------------------------------------------
# P3WIN — Gradle-хелперы для Windows 11 (PowerShell 5.1+ / 7+)
#
#   . .\scripts\build.ps1          # импорт функций в текущую сессию
#   Build-Debug                    # чистая сборка debug APK
#   Build-Native                   # только CMake-слой (диагностика NDK)
#   Install-Debug                  # сборка + установка на realme P3
#   Show-LogCrash                  # logcat с фильтром падений
#
# Скрипт НИЧЕГО не устанавливает: только вызывает gradle wrapper.
# ---------------------------------------------------------------------------

$ErrorActionPreference = 'Stop'

$script:P3Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$script:Gradle = Join-Path $script:P3Root 'gradlew.bat'
$script:Apk    = Join-Path $script:P3Root 'app\build\outputs\apk\debug\app-debug.apk'

function Assert-GradleWrapper {
    if (-not (Test-Path $script:Gradle)) {
        throw "Не найден gradlew.bat в $script:P3Root"
    }
    $props = Join-Path $script:P3Root 'gradle\wrapper\gradle-wrapper.properties'
    if (-not (Test-Path $props)) {
        throw 'Не найден gradle\wrapper\gradle-wrapper.properties'
    }
}

function Build-Debug {
    <#
      .SYNOPSIS Чистая сборка debug-варианта (Java + 6 native-модулей).
      .PARAMETER NoDaemon  Не поднимать Gradle daemon (полный лог, дольше).
    #>
    param([switch]$NoDaemon = $true)

    Assert-GradleWrapper
    Push-Location $script:P3Root
    try {
        # NB: не используем $args — это автоматическая переменная PowerShell.
        $gradleArgs = @('clean', ':app:assembleDebug', '--stacktrace')
        if ($NoDaemon) { $gradleArgs += '--no-daemon' }

        Write-Host "> .\gradlew.bat $($gradleArgs -join ' ')" -ForegroundColor Cyan
        $sw = [System.Diagnostics.Stopwatch]::StartNew()
        & $script:Gradle @gradleArgs
        $code = $LASTEXITCODE
        $sw.Stop()

        if ($code -ne 0) {
            Write-Host "СБОРКА ПРОВАЛЕНА (exit $code) за $([math]::Round($sw.Elapsed.TotalMinutes,1)) мин." -ForegroundColor Red
            Write-Host 'Смотрите docs/00_PHASE0_BASELINE.md §4.3 (длина пути, OneDrive, Defender).' -ForegroundColor Yellow
            return $false
        }

        $sizeMb = [math]::Round((Get-Item $script:Apk).Length / 1MB, 1)
        Write-Host "OK: $script:Apk ($sizeMb MB) за $([math]::Round($sw.Elapsed.TotalMinutes,1)) мин." -ForegroundColor Green
        return $true
    }
    finally { Pop-Location }
}

function Build-Native {
    <#
      .SYNOPSIS Только CMake/NDK-слой — быстрая проверка нативного кода.
    #>
    Assert-GradleWrapper
    Push-Location $script:P3Root
    try {
        Write-Host '> externalNativeBuildDebug' -ForegroundColor Cyan
        & $script:Gradle --no-daemon :app:externalNativeBuildDebug --stacktrace
        return ($LASTEXITCODE -eq 0)
    }
    finally { Pop-Location }
}

function Build-Release {
    <#
      .SYNOPSIS Release-вариант (R8 + proguard). На Фазе 0 не подписан.
    #>
    Assert-GradleWrapper
    Push-Location $script:P3Root
    try {
        & $script:Gradle --no-daemon :app:assembleRelease --stacktrace
        return ($LASTEXITCODE -eq 0)
    }
    finally { Pop-Location }
}

function Get-Adb {
    $sdk = $null
    $localProps = Join-Path $script:P3Root 'local.properties'
    if (Test-Path $localProps) {
        $line = Get-Content $localProps | Where-Object { $_ -match '^sdk\.dir=' }
        if ($line) { $sdk = ($line -replace '^sdk\.dir=','') -replace '\\\\','\' }
    }
    if (-not $sdk) { $sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }

    $adb = Join-Path $sdk 'platform-tools\adb.exe'
    if (-not (Test-Path $adb)) { throw "adb не найден: $adb" }
    return $adb
}

function Install-Debug {
    <#
      .SYNOPSIS Сборка (если APK нет) + установка на подключённое устройство.
    #>
    param([switch]$Rebuild)

    if ($Rebuild -or -not (Test-Path $script:Apk)) {
        if (-not (Build-Debug)) { return $false }
    }
    $adb = Get-Adb

    & $adb devices
    Write-Host "> adb install -r $script:Apk" -ForegroundColor Cyan
    & $adb install -r $script:Apk
    return ($LASTEXITCODE -eq 0)
}

function Show-LogCrash {
    <#
      .SYNOPSIS logcat с фильтром падений и нативного слоя.
      .PARAMETER Clear Сначала очистить буфер.
    #>
    param([switch]$Clear)

    $adb = Get-Adb
    if ($Clear) { & $adb logcat -c }

    & $adb logcat -v time |
        Select-String -Pattern 'winlator|P3WIN|FATAL EXCEPTION|dlopen|SIGILL|SIGSEGV|AndroidRuntime|DEBUG'
}

function Start-FirstRun {
    <#
      .SYNOPSIS Полный цикл Фазы 0: сборка -> установка -> чистый лог первого запуска.
    #>
    if (-not (Install-Debug)) { return }
    $adb = Get-Adb
    & $adb shell am force-stop com.winlator
    Write-Host '> Запуск приложения (распаковка rootfs 1-3 мин)...' -ForegroundColor Cyan
    & $adb shell monkey -p com.winlator -c android.intent.category.LAUNCHER 1 | Out-Null
    Show-LogCrash -Clear
}

Write-Host 'P3WIN helpers загружены:' -ForegroundColor Green
Write-Host '  Build-Debug [-NoDaemon:$true] | Build-Native | Build-Release' -ForegroundColor Gray
Write-Host '  Install-Debug [-Rebuild] | Show-LogCrash [-Clear] | Start-FirstRun' -ForegroundColor Gray
