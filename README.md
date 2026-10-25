# unidocs

Универсальное приложение для Android: **просмотр и редактирование документов** с минимальным установочным размером.

- **Офисные форматы** (docx/xlsx/pptx/odt и legacy) — просмотр, простое редактирование
- **Markdown** — просмотр и полноценное редактирование
- **Электронные книги** (epub, fb2, mobi/azw3, cbz, cbr) — чтение
- **PDF** — просмотр, заполнение форм, простановка факсимиле

Стек: **Kotlin + Jetpack Compose**, гибрид «натив + изолированный WebView» (MIT/Apache JS-движки там, где они выигрывают по весу). Язык, модульная схема и обоснования — в [docs/architecture.md](docs/architecture.md).

## Документы

| Документ | О чём |
|---|---|
| [docs/architecture.md](docs/architecture.md) | Архитектура v0.2: принципы, модули, выбор движков, бюджет размера, дорожная карта |
| [docs/research/apk-leaders.md](docs/research/apk-leaders.md) | Реверс-инжиниринг лидеров (ReadMD, NCloud Office) через REA: что у них внутри и чему учит |
| [docs/research/oss-survey.md](docs/research/oss-survey.md) | Проверенный обзор OSS-библиотек: лицензии, размеры AAR/.so, активность, ловушки |
| [docs/research/oss-apps-office-md.md](docs/research/oss-apps-office-md.md) | Референсы-приложения и встраиваемые компоненты для офиса и Markdown |

## Статус

- **M0 выполнен (25.10.2026)**: Gradle-каркас, плагинный SPI с юнит-тестами, файловый менеджер на SAF, «Недавние» на DataStore, CI.
- **M1 выполнен (25.10.2026)**: Markdown — первый плагин: парсер (commonmark + GFM), собственный Compose-рендер, split-редактор с тулбаром, поиск/оглавление, сохранение через SAF.
- **M2 выполнен (25.10.2026)**: PDF — androidx.pdf 1.0.0-beta01 (sandbox-сервис, PdfView с выделением/ссылками/поиском) + fallback на системном PdfRenderer. Стек обновлён: Gradle 8.14.3, AGP 8.13.0, Kotlin 2.2.20, compileSdk 36 (extension 19), **minSdk 28** (требование pdf-core).
- **M3 выполнен (25.10.2026)**: PDF-редактирование — AcroForm (текст/чекбоксы/радио/выбор) + факсимиле (рисование/сохранённые подписи + позиционирование по предпросмотру страницы) + сохранение через SAF, опциональный флаттер. BouncyCastle исключён.
- Размеры: debug **36,3 МБ**, release (R8) **7,0 МБ**. Бюджеты CI: debug ≤ 40 МБ, release ≤ 8 МБ.
- **M4 выполнен (25.10.2026)**: Книги — foliate-js (MIT) в изолированном WebView (:core:webview): epub/fb2/mobi/azw3/cbz, cbr через junrar→cbz; темы, размер шрифта, оглавление, закладки, позиции чтения.
- Размеры: debug **37,1 МБ**, release (R8) **7,3 МБ**. Бюджеты CI: debug ≤ 40 МБ, release ≤ 8 МБ.
- Дальше: **M5 — Офис-просмотр** (docx-preview/SheetJS/PPTXjs в том же изолированном WebView; сравнение вёрстки с Word).

## Сборка

Нужны JDK 17 и Android SDK (platforms;android-35).

```bash
./gradlew.bat :app:assembleDebug        # Windows
./gradlew :app:assembleDebug            # Linux/macOS
./gradlew.bat :core:model:testDebugUnitTest   # тесты SPI
```

CI (GitHub Actions) собирает debug-APK, гоняет тесты и проверяет бюджет размера на каждый push/PR.

## Репозиторий

- Папка `.ref_apks/` (APK-референсы для реверса) **не фиксируется в git** — см. `.gitignore`.
- `tools/` и `_scratch/` — локальные инструменты анализа и скрипты, тоже вне git.
- Правовая гигиена: файл `THIRD-PARTY-NOTICES.md` будет генерироваться CI из графа зависимостей.
