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

- **M0 выполнен (25.10.2026)**: Gradle-каркас (Kotlin 2.0.21, AGP 8.7.3, Gradle 8.10.2, Compose BOM 2024.12.01), плагинный SPI с юнит-тестами, файловый менеджер на SAF, «Недавние» на DataStore, CI с гейтом размера.
- **M1 выполнен (25.10.2026)**: Markdown — первый плагин: парсер (commonmark + GFM), собственный Compose-рендер, split-редактор с тулбаром, поиск/оглавление, сохранение через SAF.
- Размеры: debug **9,5 МБ**, release (R8) **1,24 МБ** — бюджет base-APK 25 МБ выдержан с большим запасом.
- Дальше: **M2 — PDF-просмотр** (androidx.pdf + fallback PdfRenderer). Полный план вех — в [архитектуре](docs/architecture.md).

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
