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

- **M0–M6 выполнены (25.10.2026)**: каркас+SPI, Markdown-редактор (commonmark+GFM, split), PDF-просмотр (androidx.pdf + fallback PdfRenderer) и редактирование (AcroForm + факсимиле, PDFBox), книги (foliate-js: epub/fb2/mobi/azw3/cbz/cbr — темы/закладки/позиции/поиск), офис-просмотр (docx/xlsx/pptx/odt/ods) — всё в изолированных WebView + SAF-first.
- **M7 выполнен (25.10.2026)**: подписанный релиз (keystore вне git), locale-фильтр ru/en, en-строки шелла, версионирование 0.7.0 (7).
- Размеры: debug **36,5 МБ**, **release (подписанный) 7,12 МБ** — гейты CI: debug ≤ 40 МБ, release ≤ 8 МБ. Тесты: 42, зелёные.
- Известные ограничения: legacy .doc/.xls не поддерживаются (§8), Mermaid — v2 (ломает бюджет 8 МБ), en-локализация фич — backlog.

## Сборка релиза

Ключ подписи: `signing/unidocs-release.jks` (вне git), параметры — в `local.properties` (`signing.*`). Потеря ключа = невозможность обновлять приложение в Play; сохраните копию.

```bash
./gradlew.bat :app:assembleRelease   # → app/build/outputs/apk/release/app-release.apk (подписанный)
```

На CI ключа нет — собирается unsigned (гейт размера работает по нему).

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
