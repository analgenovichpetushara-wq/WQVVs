<#
.SYNOPSIS
    P3WIN — проверка окружения сборки на Windows 11.

.DESCRIPTION
    Проверяет JDK, Android SDK, NDK, CMake, Build-Tools, platform android-35,
    local.properties, длину пути и наличие вендорных артефактов ядра.
    Ничего не устанавливает и не меняет — только диагностирует.

.PARAMETER SdkPath
    Путь к Android SDK. По умолчанию %LOCALAPPDATA%\Android\Sdk.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\scripts\check_env.ps1
    powershell -ExecutionPolicy Bypass -File .\scripts\check_env.ps1 -SdkPath "D:\Android\Sdk"

.NOTES
    Требуемые версии заданы проектом (см. docs/00_PHASE0_BASELINE.md §3):
      JDK 17 · AGP 8.4.2 · Gradle 8.14.5 · compileSdk 35 · Build-Tools 34.0.0
      NDK 24.0.8215888 · CMake 3.22.1 · ABI arm64-v8a
#>

[CmdletBinding()]
param(
    [string]$SdkPath = (Join-Path $env:LOCALAPPDATA 'Android\Sdk'),

    # Версии, зафиксированные проектом. Менять только вместе с app/build.gradle.
    [string]$RequiredNdk      = '24.0.8215888',
    [string]$RequiredCmake    = '3.22.1',
    [string]$RequiredBuildTls = '34.0.0',
    [int]   $RequiredPlatform = 35,
    [int]   $RequiredJdk      = 17,
    [int]   $MaxSafePathLength = 100
)

$ErrorActionPreference = 'Stop'
$script:results = @()

function Add-Result {
    param([string]$Check, [string]$Status, [string]$Detail)
    $script:results += [PSCustomObject]@{ Check = $Check; Status = $Status; Detail = $Detail }
}

function Test-CommandExists { param([string]$Name) return [bool](Get-Command $Name -ErrorAction SilentlyContinue) }

Write-Host ''
Write-Host '============================================================' -ForegroundColor Cyan
Write-Host ' P3WIN - environment check (Phase 0)' -ForegroundColor Cyan
Write-Host '============================================================' -ForegroundColor Cyan

# ---------------------------------------------------------------------------
# 1. Расположение проекта
# ---------------------------------------------------------------------------
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Add-Result 'Project root' 'INFO' $projectRoot

if ($projectRoot -match 'OneDrive') {
    Add-Result 'Path sanitisation' 'FAIL' 'Проект лежит в OneDrive - перенесите в C:\p3\WQVVs'
} elseif ($projectRoot -match '\s') {
    Add-Result 'Path sanitisation' 'WARN' 'В пути есть пробелы - CMake/ProcessBuilder могут ломаться'
} else {
    Add-Result 'Path sanitisation' 'OK' 'нет пробелов и OneDrive'
}

if ($projectRoot.Length -gt $MaxSafePathLength) {
    Add-Result 'Path length' 'WARN' "длина $($projectRoot.Length) > $MaxSafePathLength - включите LongPathsEnabled"
} else {
    Add-Result 'Path length' 'OK' "$($projectRoot.Length) символов"
}

# ---------------------------------------------------------------------------
# 2. JDK 17
# ---------------------------------------------------------------------------
$jdkOk = $false
if (Test-CommandExists 'java') {
    $javaLine = (& java -version 2>&1 | Select-Object -First 1) -as [string]
    if ($javaLine -match '"?(?<ver>\d+)(\.|")') {
        $major = [int]$Matches['ver']
        if ($major -eq $RequiredJdk) {
            $jdkOk = $true
            Add-Result "JDK" 'OK' $javaLine.Trim()
        } else {
            Add-Result "JDK" 'FAIL' "$($javaLine.Trim()) - нужен JDK $RequiredJdk (AGP 8.4.2)"
        }
    }
} else {
    Add-Result 'JDK' 'FAIL' 'java не найден в PATH - установите Temurin 17 и откройте новый терминал'
}

if ($env:JAVA_HOME) {
    if ($env:JAVA_HOME -match 'Android\\Android Studio|jbr') {
        Add-Result 'JAVA_HOME' 'WARN' "указывает на JBR Android Studio ($env:JAVA_HOME) - для терминала лучше Temurin 17"
    } else {
        Add-Result 'JAVA_HOME' 'OK' $env:JAVA_HOME
    }
} else {
    Add-Result 'JAVA_HOME' 'WARN' 'не задан (Gradle возьмёт JDK из PATH; в Studio задайте Gradle JDK = 17)'
}

# ---------------------------------------------------------------------------
# 3. Android SDK
# ---------------------------------------------------------------------------
if (-not (Test-Path $SdkPath)) {
    Add-Result 'Android SDK' 'FAIL' "нет каталога $SdkPath - SDK Manager: SDK Location"
} else {
    Add-Result 'Android SDK' 'OK' $SdkPath

    # local.properties
    $localProps = Join-Path $projectRoot 'local.properties'
    if (Test-Path $localProps) {
        $declared = (Get-Content $localProps | Where-Object { $_ -match '^sdk\.dir=' }) -replace '^sdk\.dir=','' -replace '\\\\','\'
        if ($declared -and ((Resolve-Path $declared -ErrorAction SilentlyContinue).Path -ne (Resolve-Path $SdkPath).Path)) {
            Add-Result 'local.properties' 'WARN' "sdk.dir=$declared не совпадает с проверяемым SDK"
        } else {
            Add-Result 'local.properties' 'OK' 'sdk.dir корректен'
        }
    } else {
        Add-Result 'local.properties' 'FAIL' 'файл отсутствует - Studio и терминал будут использовать разные SDK'
    }

    # platform android-35
    if (Test-Path (Join-Path $SdkPath "platforms\android-$RequiredPlatform")) {
        Add-Result 'SDK Platform' 'OK' "android-$RequiredPlatform"
    } else {
        Add-Result 'SDK Platform' 'FAIL' "нет platforms\android-$RequiredPlatform (compileSdk 35)"
    }

    # build-tools
    $bt = Join-Path $SdkPath "build-tools\$RequiredBuildTls"
    if (Test-Path $bt) { Add-Result 'Build-Tools' 'OK' $RequiredBuildTls }
    else {
        $present = (Get-ChildItem (Join-Path $SdkPath 'build-tools') -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Name) -join ', '
        Add-Result 'Build-Tools' 'FAIL' "нужен $RequiredBuildTls; установлено: $present"
    }

    # NDK
    $ndk = Join-Path $SdkPath "ndk\$RequiredNdk"
    if (Test-Path (Join-Path $ndk 'toolchains')) {
        Add-Result 'NDK' 'OK' $RequiredNdk
    } else {
        $present = (Get-ChildItem (Join-Path $SdkPath 'ndk') -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Name) -join ', '
        Add-Result 'NDK' 'FAIL' "нужен $RequiredNdk (app/build.gradle: ndkVersion); установлено: $present"
    }

    # CMake
    $cmake = Join-Path $SdkPath "cmake\$RequiredCmake\bin\cmake.exe"
    if (Test-Path $cmake) {
        Add-Result 'CMake' 'OK' "$RequiredCmake (SDK-managed)"
    } elseif (Test-CommandExists 'cmake') {
        $v = (& cmake --version | Select-Object -First 1) -as [string]
        Add-Result 'CMake' 'WARN' "$v в PATH, но AGP ищет cmake\$RequiredCmake в SDK"
    } else {
        Add-Result 'CMake' 'FAIL' "нужен CMake $RequiredCmake из SDK Manager"
    }

    # platform-tools
    if (Test-Path (Join-Path $SdkPath 'platform-tools\adb.exe')) { Add-Result 'Platform-Tools' 'OK' 'adb найден' }
    else { Add-Result 'Platform-Tools' 'FAIL' 'нет platform-tools\adb.exe' }

    # подключённое устройство (не критично)
    if (Test-Path (Join-Path $SdkPath 'platform-tools\adb.exe')) {
        $devices = & (Join-Path $SdkPath 'platform-tools\adb.exe') devices 2>&1 | Select-Object -Skip 1 | Where-Object { $_ -match '\tdevice$' }
        if ($devices) { Add-Result 'Device' 'OK' (($devices | ForEach-Object { ($_ -split "`t")[0] }) -join ', ') }
        else { Add-Result 'Device' 'INFO' 'устройство не подключено (adb devices пуст)' }
    }
}

# ---------------------------------------------------------------------------
# 4. Gradle wrapper
# ---------------------------------------------------------------------------
$wrapperProps = Join-Path $projectRoot 'gradle\wrapper\gradle-wrapper.properties'
if (Test-Path $wrapperProps) {
    $url = (Get-Content $wrapperProps | Where-Object { $_ -match 'distributionUrl' })
    if ($url -match 'gradle-(?<v>[\d.]+)-bin\.zip') {
        Add-Result 'Gradle wrapper' 'OK' $Matches['v']
    } else {
        Add-Result 'Gradle wrapper' 'WARN' $url
    }
} else {
    Add-Result 'Gradle wrapper' 'FAIL' 'нет gradle\wrapper\gradle-wrapper.properties'
}

if (Test-Path (Join-Path $projectRoot 'gradle\wrapper\gradle-wrapper.jar')) {
    Add-Result 'Wrapper JAR' 'OK' 'gradle-wrapper.jar присутствует'
} else {
    Add-Result 'Wrapper JAR' 'FAIL' 'нет gradle-wrapper.jar - запустите: gradle wrapper --gradle-version 8.14.5'
}

# ---------------------------------------------------------------------------
# 5. Вендорные артефакты ядра (25 x .tzst, ~141 MB)
# ---------------------------------------------------------------------------
$assetsDir = Join-Path $projectRoot 'app\src\main\assets'
if (Test-Path $assetsDir) {
    $tzst = Get-ChildItem $assetsDir -Recurse -Filter '*.tzst' -ErrorAction SilentlyContinue
    $sumsFile = Join-Path $projectRoot 'core\assets.sha256'
    $expected = 0
    if (Test-Path $sumsFile) { $expected = (Get-Content $sumsFile | Where-Object { $_.Trim() }).Count }

    if ($expected -gt 0 -and $tzst.Count -ge $expected) {
        Add-Result 'Core assets' 'OK' "$($tzst.Count) .tzst на месте"
    } elseif ($tzst.Count -eq 0) {
        Add-Result 'Core assets' 'FAIL' 'assets пуст - выполните: bash scripts/bootstrap_assets.sh'
    } else {
        Add-Result 'Core assets' 'WARN' "$($tzst.Count) .tzst, ожидалось $expected - запустите verify_assets.sh"
    }
    $mb = [math]::Round((($tzst | Measure-Object Length -Sum).Sum / 1MB), 1)
    Add-Result 'Assets size' 'INFO' "$mb MB"
} else {
    Add-Result 'Core assets' 'FAIL' 'нет app\src\main\assets'
}

# ---------------------------------------------------------------------------
# 6. Итог
# ---------------------------------------------------------------------------
Write-Host ''
$fmt = '{0,-18} {1,-6} {2}'
Write-Host ($fmt -f 'CHECK','STATUS','DETAIL') -ForegroundColor DarkGray
Write-Host ('-' * 100) -ForegroundColor DarkGray
foreach ($r in $script:results) {
    $color = switch ($r.Status) {
        'OK'   { 'Green' }
        'WARN' { 'Yellow' }
        'FAIL' { 'Red' }
        default { 'Gray' }
    }
    # INFO-строки в DETAIL длинные; выравниваем статус отдельно
    Write-Host ($fmt -f $r.Check, $r.Status, $r.Detail) -ForegroundColor $color
}
Write-Host ('-' * 100) -ForegroundColor DarkGray

$fails = @($script:results | Where-Object Status -eq 'FAIL').Count
$warns = @($script:results | Where-Object Status -eq 'WARN').Count

if ($fails -gt 0) {
    Write-Host "РЕЗУЛЬТАТ: $fails FAIL, $warns WARN - сборка не начнётся, устраните FAIL." -ForegroundColor Red
    exit 1
} elseif ($warns -gt 0) {
    Write-Host "РЕЗУЛЬТАТ: окружение годно, $warns предупреждени(й)." -ForegroundColor Yellow
    Write-Host 'Дальше: .\gradlew.bat --no-daemon clean :app:assembleDebug --stacktrace' -ForegroundColor Cyan
    exit 0
} else {
    Write-Host 'РЕЗУЛЬТАТ: окружение полностью соответствует пинам P3WIN.' -ForegroundColor Green
    Write-Host 'Дальше: .\gradlew.bat --no-daemon clean :app:assembleDebug --stacktrace' -ForegroundColor Cyan
    exit 0
}
