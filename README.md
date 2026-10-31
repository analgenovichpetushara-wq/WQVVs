# P3WIN

**Премиальная автономная игровая экосистема для Android на базе Winlator 11.2.**

P3WIN — это не форк «на скорость», а переработка архитектуры: слой эмуляции (Wine, Box64, XServer,
Proot, графические трансляторы) отделён от слоя представления (консольный Shell, библиотека игр,
профилировщик, оверлеи). Разработка идёт микро-шагами с обязательной верификацией сборки.

| | |
|---|---|
| **Эталонное устройство** | realme P3 5G — Snapdragon 6 Gen 4 (ARMv9, **без AArch32**), Adreno 810, 12/256 GB, Android 15 |
| **База** | Winlator 11.2 — `brunodev85/winlator-app` @ `a030f552` (см. `docs/00_PHASE0_BASELINE.md`) |
| **Текущая фаза** | **0 — Baseline**: пин исходников, конфигурация сборки, верификация APK |
| **Сборка** | AGP 8.4.2 · Gradle 8.14.5 · JDK 17 · compileSdk 35 · NDK 24.0.8215888 · CMake 3.22.1 · `arm64-v8a` |

## Структура репозитория

```
app/        Android-модуль: Java-слой, JNI/C++ (6 нативных модулей), ассеты (Wine rootfs, драйверы)
core/       Слой CORE Winlator, вынесенный из мета-репозитория: ALSA, glibc-патчи,
            профили управления (.icp), индексы компонентов, SHA-256 вендорных .tzst
docs/       Инженерная документация по фазам
scripts/    bootstrap_assets.sh · verify_assets.sh · check_env.ps1 · build.ps1
```

## Быстрый старт (Windows 11)

```powershell
git clone https://github.com/<owner>/WQVVs.git C:\p3\WQVVs
cd C:\p3\WQVVs

# 1. указать SDK до первого Sync
"sdk.dir=$($env:LOCALAPPDATA -replace '\\','\\')\\Android\\Sdk" | Out-File -Encoding ascii local.properties

# 2. проверить окружение (JDK 17, NDK 24.0.8215888, CMake 3.22.1, Build-Tools 34.0.0)
powershell -ExecutionPolicy Bypass -File .\scripts\check_env.ps1

# 3. собрать
.\gradlew.bat --no-daemon clean :app:assembleDebug --stacktrace
```

Полная инструкция, включая настройку SDK Manager, JDK и «тихих убийц» сборки на Windows —
в **[docs/00_PHASE0_BASELINE.md](docs/00_PHASE0_BASELINE.md)**.

## Целостность вендорных артефактов

25 файлов `.tzst` (~141 MB) — сторонние бинарники (Wine rootfs, DXVK/VKD3D, Turnip, VirGL, Box64).
Они запинены по SHA-256 в `core/assets.sha256`:

```bash
bash scripts/verify_assets.sh          # проверка
bash scripts/bootstrap_assets.sh       # восстановление из снапшота app@a030f552
```

## Лицензия и происхождение

Проект является производной работой от Winlator (© brunodev85), распространяемой под
**GNU LGPL 2.1** — см. `LICENSE`. Все сторонние компоненты сохраняют собственные лицензии:

Wine · Box64 (ptitSeb) · Mesa / Turnip / Zink / VirGL · DXVK (doitsujin) · VKD3D (winehq) ·
cnc-ddraw (FunkyFr3sh) · adrenotools · glibc-патчи termux-pacman.

Исходники производной работы распространяются на тех же условиях (LGPL 2.1).
