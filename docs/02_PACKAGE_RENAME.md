# P3WIN — переименование пакета (опционально)

> Статус: **не выполнено намеренно.** `applicationId` остаётся `com.winlator`,
> пока не пересобраны rootfs, драйверы и нативные библиотеки.
> Автоматизация: `scripts/rename_package.ps1` (шаги 1–2), остальное — вручную.

---

## 1. Почему это не «найти и заменить»

Переименование Android-пакета выглядит как одна строка в `build.gradle`, но
здесь приложение — тонкая прослойка над **Linux-окружением**, которое о
новом имени ничего не знает. Абсолютные пути к data-dir зашиты в четырёх
независимых слоях. Если поправить только `applicationId`:

* сборка пройдёт;
* приложение запустится;
* **контейнеры перестанут стартовать** — wineprefix ищет `/data/data/com.winlator/...`,
  а его там больше нет. Android создаст новый пустой каталог, и пользователь
  получит либо «чёрный экран», либо бесконечную повторную распаковку rootfs.

Именно поэтому в фазе 0 было принято решение оставить имя пакета, а не
сделать «красиво» и сломать запуск.

## 2. Пять мест, которые должны меняться вместе

| # | Слой | Что именно | Автоматизация |
|---|---|---|---|
| 1 | `app/build.gradle` | `applicationId` ( **не** `namespace` ) | `rename_package.ps1` |
| 2 | `AndroidManifest.xml` | `android:authorities` у FileProvider | `rename_package.ps1` |
| 3 | `assets/rootfs.tzst` | пути `/data/data/com.winlator` внутри rootfs | вручную, пересборка |
| 4 | `assets/graphics_driver/*.json` | `library_path` в Turnip ICD | вручную |
| 5 | `app/src/main/cpp/**` | макросы `Java_com_winlator_*` | пересборка NDK |

### 2.1. namespace ≠ applicationId

`namespace` определяет **Java-пакет, в котором генерируется `R`**. На него
завязаны все импорты в P3-слое:

```java
import com.winlator.R;   // ← 25 файлов P3, так и должно остаться
```

Если поменять `namespace`, придётся править каждый `import com.winlator.R`
и все ссылки `com.winlator.R.*`. Именно поэтому `rename_package.ps1`
предупреждает, но не трогает `namespace`: смена `applicationId` решает
задачу «отдельный пакет в системе» и не требует переписывать код.

## 3. Порядок действий

```powershell
# 1. Посмотреть, что изменится
powershell -ExecutionPolicy Bypass -File .\scripts\rename_package.ps1 `
    -NewPackage "com.p3win.app" -DryRun

# 2. Применить шаги 1–2 (gradle + manifest)
powershell -ExecutionPolicy Bypass -File .\scripts\rename_package.ps1 `
    -NewPackage "com.p3win.app"

# 3. Пересобрать артефакты (без этого контейнеры НЕ запустятся)
#    — rootfs: заменить префикс пути в распакованном образе
#    — Turnip ICD: library_path -> /data/data/com.p3win.app/files/...
#    — cpp: заменить Java_com_winlator_* на Java_com_p3win_app_*
.\gradlew.bat --no-daemon clean :app:assembleDebug
```

### 3.1. Что проверить после

1. `bash scripts/verify_assets.sh --strict` — контрольные суммы не изменились.
2. `python3 scripts/p3_static_check.py` — статические проверки.
3. На устройстве: **Диагностика** в Центре управления. Строка «Сверка путей»
   должна показывать `OK`; если там текст про рассинхронизацию — шаг 3 не
   доделан, и запускать игры бессмысленно.
4. Создать контейнер и запустить. Первый запуск распаковывает rootfs —
   это долго, это нормально.

## 4. Обратный переход

Скрипт идемпотентен: запуск с `-NewPackage "com.winlator"` вернёт
`applicationId` обратно. Данные старого пакета Android не удаляет, но и не
переносит — при смене имени приложение выглядит как новое.

## 5. Почему скрипт останавливается, а не «доделывает»

Частичная замена опаснее отказа. Если `scripts/rename_package.ps1` не нашёл
строку `applicationId`, он завершается с `FAIL`, а не пытается угадать формат.
Так же и здесь: непереименованный пакет — это неудобство, а наполовину
переименованный — неработающие игры у пользователя.
