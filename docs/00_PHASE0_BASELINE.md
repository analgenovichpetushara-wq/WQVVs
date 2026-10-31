# P3WIN — Фаза 0: базовый слой (Baseline)

> Статус: **исходники зафиксированы, конфигурация сборки приведена к AGP 8.x, артефакты подготовлены.**
> Проверка компиляции выполняется на машине разработчика (Windows 11 + Android Studio) — см. §5.

---

## 1. Какой именно исходник Winlator 11.2 нам нужен

Главная ловушка проекта: **репозиторий Winlator — это не приложение, а «мета-репозиторий» на git-submodule**.
Приложение лежит в отдельном репозитории, движки — в двух других.

```
brunodev85/winlator        (мета: submodules + rootfs-артефакты + вендорные компоненты)
├── app     → brunodev85/winlator-app     ← ВЕСЬ Android-проект (Java + JNI + assets)
├── vortek  → brunodev85/vortek           ← Vulkan-транслятор (генерирует assets/graphics_driver/vortek-*.tzst)
└── gladio  → brunodev85/gladio           ← OpenGL через GLES (assets/graphics_driver/gladio-*.tzst)
```

### 1.1. Расхождение тега и релиза (важно!)

| Что | Коммит `app` | compileSdk | versionCode | Gradle wrapper | `namespace` |
|---|---|---|---|---|---|
| тег `v11.2.0` (`fb66541`) | `c2f4ad4` | 34 | 28 (`11.1`) | 7.3.3 | **нет** (package в манифесте) |
| опубликованный APK `Winlator_11.2.apk` (загружен 2026-09-14) | **`a030f552`** | 35 | 33 (`11.2`) | 8.14.5 | **да** |

Тег `v11.2.0` был поставлен 2026-06-12 на `app@c2f4ad4` (это фактически ещё 11.1),
а бинарник 11.2 в релизе пересобран позже из `app@a030f552`.
**Базой P3WIN берём `a030f552` — тот коммит, из которого реально собран APK 11.2.**

### 1.2. Точные пины (baseline P3WIN)

| Компонент | Репозиторий | Пин |
|---|---|---|
| Мета-ядро | `github.com/brunodev85/winlator` | тег `v11.2.0` → `fb66541b93a4eb3ee585a433b4c7b20544d58e40` |
| **Android-приложение (база)** | `github.com/brunodev85/winlator-app` | **`a030f552f452158a2db64fdb32b490fa19c0b48d`** |
| Vulkan-транслятор | `github.com/brunodev85/vortek` | `ab7329c4b445a4abd9b9af91b8148e1ca41464fa` |
| OpenGL-транслятор | `github.com/brunodev85/gladio` | `eaa2a8d6eda3a1a6af755370ea9fac6cf7792ac3` |
| Корневая ФС Wine | `assets/rootfs.tzst` (в `app`) | `aeea2938cc63b0708cb2da999b5a0e3915229b6ab4eac13de061c87f34ffc1dc` |
| Все 25 `.tzst` компонентов | `core/assets.sha256` | см. файл |

Проверка целостности архива мета-репозитория, из которого собран `core/`:

```
sha256( winlator-11.2.0.tar.gz ) = d4d6d0ab0757d63ff5a7b2d090da047c1566fe87454048afbe559aa59507e9ae
```

### 1.3. Что лежит в этом репозитории (P3WIN)

```
WQVVs/                              ← корень = приложение (бывший winlator-app)
├── app/                            ← Android-модуль (Java 266 файлов, C/C++ 295 файлов, assets 143 MB)
├── core/                           ← «ядро» Winlator, вынесенное из мета-репозитория (слой CORE)
│   ├── android_alsa/               ← ALSA-плагин aserver (сборка отдельно, в APK не входит)
│   ├── glibc_patches/              ← патчи glibc под bionic
│   ├── input_controls/             ← 100+ .icp профилей управления (библиотека P3 Input Manager)
│   ├── installable_components/     ← индексы версий box64/dxvk/turnip/vkd3d/wined3d
│   └── assets.sha256               ← контрольные суммы всех вендорных .tzst
├── docs/                           ← инженерная документация
├── scripts/                        ← bootstrap / verify / prereq-check
├── gradlew, gradlew.bat, gradle/   ← Gradle wrapper 8.14.5
└── build.gradle, settings.gradle, gradle.properties
```

> **Сознательное решение:** `core/` — это *не* второй Gradle-модуль. Это склад ядра и справочник по версиям.
> UI-слой P3WIN будет подключаться в Фазе 3 как отдельный модуль `:p3shell`, а `app/` останется
> «голым» рантаймом Wine. Так выполняется постулат о разделении слоёв.

---

## 2. Почему `applicationId` НЕ менялся на `com.p3win` (и когда поменяем)

Это не забывчивость, а измеренный факт. Абсолютный путь `/data/data/com.winlator/` зашит **внутри бинарных артефактов**:

| Артефакт | Вхождений `/data/data/com.winlator` |
|---|---|
| `assets/rootfs.tzst` (`etc/ld.so.cache`, `libc.so.6`, `wineserver`, `fonts.conf`, `alsa.conf`, `localedef`, …) | **163 файла** |
| `assets/graphics_driver/turnip-26.2.0.tzst` → `freedreno_icd.aarch64.json` (`library_path`) | 1 |
| `cpp/*/include/*.h` (`APP_CACHE_DIR`, `VORTEK_SERVER_PATH`, `X11_SERVER_PATH`) | 3 макроса |
| `java/…/AppUtils.java` → `INTERNAL_STORAGE` | 1 |

Смена `applicationId` «в лоб» даёт APK, который устанавливается, но **контейнер не стартует**
(ld.so не находит библиотеки, pulseaudio не находит `daemon-binary`, shm/сокеты уходят в несуществующий каталог).

Поэтому в Фазе 0 `namespace`/`applicationId` = `com.winlator`, а корректный ренейм вынесен в Фазу 1
как отдельная скриптованная миграция (см. §6, задача M-1).

---

## 3. Матрица версий инструментов (проверить/докачать)

Значения взяты **из самого проекта**, не «по памяти»:

| Компонент | Требуемая версия | Откуда взято |
|---|---|---|
| JDK (Gradle JVM) | **17** | AGP 8.4.2 требует JDK 17 |
| Android Gradle Plugin | **8.4.2** | `build.gradle` (корень) |
| Gradle | **8.14.5** (wrapper уже в репо) | `gradle/wrapper/gradle-wrapper.properties` |
| compileSdk | **35** | `app/build.gradle` |
| Build-Tools | **34.0.0** (дефолт AGP 8.4.x) | AGP 8.4 release notes |
| **NDK** | **24.0.8215888** | `app/build.gradle` → `ndkVersion` |
| **CMake** | **3.22.1** | `app/build.gradle` → `externalNativeBuild.cmake.version` |
| ABI | **только `arm64-v8a`** | CMake-модули падают на других ABI (`libadrenotools` явно `FATAL_ERROR`) |
| minSdk / targetSdk | **26 / 28** | `app/build.gradle` |
| Platform-Tools | свежие | `adb` для realme P3 |

**WSL2 на Фазе 0 не нужен.** Весь проект собирается штатным NDK на Windows.
WSL2 понадобится позже и только для двух задач: (а) пересборка `core/android_alsa` под Android,
(б) кросс-сборка Turnip/Mesa под Adreno 810.

---

## 4. Окружение Windows 11: обязательные проверки до первого билда

### 4.1. Что установить через SDK Manager (Android Studio → Settings → Languages & Frameworks → Android SDK)

Вкладка **SDK Platforms**:
- ☑ Android 15.0 (**API 35**) — обязателен для `compileSdk 35`;
- ☑ Android 8.0 (**API 26**) — не обязателен, но полезен для проверки minSdk.

Вкладка **SDK Tools** (включить «Show Package Details»):

| Пакет | Версия |
|---|---|
| Android SDK Build-Tools | **34.0.0** |
| NDK (Side by side) | **24.0.8215888** |
| CMake | **3.22.1** |
| Android SDK Platform-Tools | последняя |
| Android SDK Command-line Tools | последняя |

> `24.0.8215888` и `3.22.1` — **не «latest»**. Если поставить NDK r27/r28 или CMake 3.31,
> проект может собрать Java-часть, но упадёт на нативном слое. Версии жёстко прописаны в `app/build.gradle`.

PowerShell-проверка после установки:

```powershell
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
Get-ChildItem "$sdk\ndk"            | Select-Object Name     # ожидаем 24.0.8215888
Get-ChildItem "$sdk\cmake"          | Select-Object Name     # ожидаем 3.22.1
Get-ChildItem "$sdk\build-tools"    | Select-Object Name     # ожидаем 34.0.0
Get-ChildItem "$sdk\platforms"      | Select-Object Name     # ожидаем android-35
```

### 4.2. JDK 17

AGP 8.4.2 **не запустится на JDK 21+ без последствий** для toolchain-настроек проекта.

- Android Studio 2025.3+ несёт JBR 21 → для проекта выставляем Gradle JDK **явно**:
  `Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK → 17`
  (или `jbr-17`, если он ещё в списке).
- Для терминала:

```powershell
winget install --id EclipseAdoptium.Temurin.17.JDK -e
# после установки — новая сессия PowerShell:
java -version    # ожидаем "17.x"
```

`JAVA_HOME` **не** должен указывать на JBR из Android Studio — только на Temurin 17.

### 4.3. Три «тихих убийцы» сборки на Windows

1. **Длина пути.** Самый длинный путь в проекте: 89+ символов внутри `app/src/main/cpp/virglrenderer/...`,
   к которому добавляется `C:\Users\<Имя>\…\WQVVs\`. При длинном имени пользователя CMake падает
   с `No such file or directory` на ровном месте. Решение:

   ```powershell
   # 1) включить поддержку длинных путей (нужен перезапуск / может требовать admin)
   New-ItemProperty -Path "HKLM:\SYSTEM\CurrentControlSet\Control\FileSystem" `
       -Name "LongPathsEnabled" -Value 1 -PropertyType DWORD -Force

   # 2) git не должен ломать регистр/пути
   git config --global core.longpaths true

   # 3) клонировать в короткий путь
   git clone https://github.com/<owner>/WQVVs.git C:\p3\WQVVs
   ```
   **Рекомендуется `C:\p3\WQVVs`** — именно этот путь используется во всех примерах ниже.

2. **Пробелы и OneDrive.** Путь `C:\Users\Иван\OneDrive\Документы\…` ломает и CMake, и `ProcessBuilder`.
   Проект живёт **вне** OneDrive/Desktop/Documents.

3. **Windows Defender.** 140+ MB assets и 6 нативных модулей = тысячи операций I/O.
   Добавить в исключения: `C:\p3\WQVVs`, `%USERPROFILE%\.gradle`, `%LOCALAPPDATA%\Android\Sdk`.

### 4.4. PowerShell-скрипт проверки окружения

```powershell
cd C:\p3\WQVVs
powershell -ExecutionPolicy Bypass -File .\scripts\check_env.ps1
```

Скрипт проверяет JDK, SDK, NDK, CMake, Build-Tools, наличие `local.properties`, длину пути
и печатает итоговую таблицу **OK/FAIL** — запускать перед каждым «чистым» билдом.

---

## 5. Открытие проекта в Android Studio и первый APK

Порядок важен: **`local.properties` создаётся до первого Sync**, иначе Studio подставит путь к SDK
из своей памяти, а терминал — нет (и билды начнут расходиться).

```powershell
# 0) клонирование
git clone https://github.com/<owner>/WQVVs.git C:\p3\WQVVs
cd C:\p3\WQVVs

# 1) путь к SDK (Studio тоже читает этот файл)
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
"sdk.dir=$($sdk -replace '\\','\\')" | Out-File -Encoding ascii local.properties
Get-Content local.properties

# 2) проверка окружения
powershell -ExecutionPolicy Bypass -File .\scripts\check_env.ps1

# 3) подготовка артефактов ядра (см. §5.1)
bash .\scripts\verify_assets.sh          # WSL/Git Bash: SHA-256 всех 25 .tzst
```

### 5.1. Про большие файлы в Git

25 файлов `.tzst` (141 MB) — **вендорные бинарники**, они уже в репозитории и не должны пересобираться.
Если репозиторий клонируется частично/через архив, восстановление:

```bash
# Git Bash / WSL
bash scripts/bootstrap_assets.sh          # скачает запиненный снапшот app@a030f552 и разложит assets
bash scripts/verify_assets.sh             # сверка с core/assets.sha256
```

### 5.2. Первый Sync и сборка

**GUI:**
1. `File → Open…` → `C:\p3\WQVVs` (именно корень, не `app/`).
2. Дождаться `Gradle Sync`. Первый Sync качает Gradle 8.14.5 + AGP 8.4.2 (~5–10 мин).
3. `Build → Make Project` (`Ctrl+F9`) — компилируются Java + 6 CMake-модулей.
4. `Build → Build Bundle(s)/APK(s) → Build APK(s)`.

**Терминал (эталонная команда, её и считаем «зелёным билдом»):**

```powershell
cd C:\p3\WQVVs
.\gradlew.bat --no-daemon clean :app:assembleDebug --stacktrace
```

Ожидаемый результат:

```
BUILD SUCCESSFUL
app\build\outputs\apk\debug\app-debug.apk        (~150–160 MB)
```

> `--no-daemon` — намеренно: при первом прогоне он даёт полный, читаемый лог без «залипшего» демона.
> После первого успеха можно убрать.

### 5.3. Установка на realme P3 5G

```powershell
adb devices                       # устройство должно быть "device", не "unauthorized"
adb install -r .\app\build\outputs\apk\debug\app-debug.apk

# лог первого запуска (распаковка rootfs ~1–3 мин)
adb logcat -c
adb logcat | Select-String -Pattern "winlator|P3WIN|DEBUG|FATAL|dlopen"
```

На устройстве (Realme UI душит фоновые процессы):
`Настройки → Приложения → Winlator → Батарея → Без ограничений`, плюс `Автозапуск → Разрешить`,
плюс `Недавние → Зафиксировать (замок)`.

**Критерий Фазы 0 (Definition of Done):**
- [ ] `assembleDebug` → `BUILD SUCCESSFUL` без ошибок линковки;
- [ ] APK устанавливается на realme P3 5G;
- [ ] приложение распаковывает rootfs и открывает экран контейнеров;
- [ ] создаётся контейнер, запускается **рабочий стол Wine** (пустой `wine explorer`);
- [ ] в `adb logcat` нет `FATAL EXCEPTION` и нет `dlopen failed`.

---

## 6. Что уже сделано в коде (Фаза 0, этот коммит)

| # | Файл | Изменение | Причина |
|---|---|---|---|
| 1 | `app/src/main/AndroidManifest.xml` | убран `package="com.winlator"` | AGP 8.x: namespace только из `build.gradle`, иначе ошибка `Incorrect package=…` |
| 2 | `app/build.gradle` | `lintOptions` → `lint { … }` | legacy DSL, AGP 8 |
| 3 | `app/build.gradle` | `debug { minifyEnabled false }`, отдельный `release { minifyEnabled true }` | на Фазе 0 нужен читаемый лог, а не R8-манглинг |
| 4 | `app/build.gradle` | `packaging { jniLibs { useLegacyPackaging = true } }` | манифест требует `extractNativeLibs="true"`; в AGP 8 это задаётся здесь |
| 5 | `app/build.gradle` | комментарии-пины AGP/NDK/CMake | версии не должны «уплыть» |
| 6 | `gradle.properties` | `android.suppressUnsupportedCompileSdk=35`, JVM args, `parallel`, `caching` | убрать шум warning'ов; уложиться в 16 GB RAM |
| 7 | `core/` | вынесены ALSA, glibc-патчи, 100+ `.icp`, индексы компонентов, `assets.sha256` | слой CORE отделён от UI; библиотека профилей ввода для Фазы 5 |
| 8 | `docs/`, `scripts/` | документация + bootstrap/verify/check_env | воспроизводимость сборки |

**Не делалось намеренно:** ренейм пакета, смена `targetSdk`, миграция `INTERNAL_STORAGE` — это Фаза 1 (M-1…M-3).

---

## 7. Технические риски, зафиксированные на Фазе 0

### R-1. Отсутствие aarch32 на Snapdragon 6 Gen 4 (критично)

Ядро Cortex-A720/A520 **не имеет AArch32 EL0**. Проверка артефактов:

| Факт | Значение |
|---|---|
| В `rootfs.tzst` директория `i386-unix/` | **отсутствует** |
| 32-битные PE-библиотеки `i386-windows/` | **738 файлов** (нужны для 32-битных игр) |
| `wow64.dll`, `wow64win.dll`, `wow64cpu.dll` в `x86_64-windows/` | **есть** |
| Бинарник Box64 0.4.4 | ELF x86-64 → AArch64 JIT, символы `box32_dynarec_mmap`, `box64_is32bits` присутствуют |

Вывод: 32-битные Windows-приложения идут по пути
**Wine WoW64 (32-bit PE → 64-bit host) → Box64**, без единого AArch32-вызова.
Задача Фазы 1 — **зафиксировать это тестом**: запуск 32-битного `.exe` и проверка, что
`box64` не пытается исполнять ARM32-код (в `logcat` не должно быть `SIGILL`).

### R-2. Adreno 810 и Turnip

`assets/graphics_driver/turnip-26.2.0.tzst` содержит строки `Adreno (TM) 810`, `Mesa 26`, `26.2.0` —
**ревизия Gen 8 поддерживается**. Версия по умолчанию задаётся в `DefaultVersion.TURNIP = "26.2.0"`.
P3 GPU Manager (Фаза 5) обязан проверять версию **до** подмены `.so`: `libvulkan_freedreno.so`
старше 25.x на Adreno 810 даёт чёрный экран/краш.

### R-3. `vortek`/`gladio` — исходники или бинарники?

В `app/src/main/cpp/` лежат **готовые исходники** `vortekrenderer` и `gladiorenderer`
(они собираются CMake'ом как `libvortekrenderer.so` / `libgladiorenderer.so`).
Отдельные репозитории `vortek`/`gladio` нужны только для генерации `.tzst`-пакетов драйверов
(эти пакеты уже в `assets/`). Для Фазы 0 ничего дополнительно клонировать не надо.

### R-4. Мусор в нативном дереве

В `cpp/virglrenderer/src/gallium/auxiliary/util/` лежат `__pycache__/*.pyc` (cpython 3.6/3.10).
На сборку не влияют, но удалены из рабочей копии P3WIN.

---

## 8. Дорожная карта (актуализирована под измеренные факты)

| Фаза | Содержание | Ключевые задачи |
|---|---|---|
| **0** ✅ | База: пин исходников, AGP 8.x-конфиг, зелёный APK, рабочий стол Wine на realme P3 | этот документ |
| **1** | Инициализация P3WIN: пакет, пути, каркас | **M-1** скриптованный ренейм `com.winlator` → `com.p3win` (манифест+JNI+rootfs+`ld.so.cache`+ICD-json) · **M-2** `AppPaths` вместо `INTERNAL_STORAGE`/`APP_CACHE_DIR`/`*_SERVER_PATH` · **M-3** пересборка `rootfs.tzst` c новым префиксом · **M-4** `targetSdk` 28→35 + storage strategy |
| 2 | Данные | Room: `Container`, `Game`, `GameProfile`, `GraphicsDriver`, `InputProfile` + миграции |
| 3 | P3WIN Shell | навигация, темы P3, библиотека, карточки |
| 4 | UI ↔ CORE | запуск по профилю, «Fail-Safe» перехват `stderr`/SIGSEGV в внутренний лог |
| 5 | Менеджеры | GPU Manager (+ верификация Adreno 810), Input Manager (`.icp` из `core/input_controls`), P3 Engine (overlay/FPS) |
| 6 | Полировка | профили под SD 6 Gen 4 / Adreno 810, релиз |

---

## Приложение A. Быстрые команды (шпаргалка)

```powershell
# окружение
powershell -ExecutionPolicy Bypass -File .\scripts\check_env.ps1

# чистая сборка
.\gradlew.bat --no-daemon clean :app:assembleDebug --stacktrace

# только нативная часть (диагностика CMake/NDK)
.\gradlew.bat --no-daemon :app:externalNativeBuildDebug

# установка и лог
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
adb logcat -c; adb logcat | Select-String "winlator|FATAL|dlopen|SIGILL"
```

```bash
# Git Bash / WSL
bash scripts/verify_assets.sh     # SHA-256 вендорных .tzst
bash scripts/bootstrap_assets.sh  # восстановить assets из запиненного снапшота
```
