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
- **Обновление 0.7.1 (27.10.2026)**: Mermaid-диаграммы в MD (изолированный WebView, mermaid 9.4.3), аннотации в книгах (выделение → подсветка, список/удаление, восстановление), en-локализация UI всех фич.
- Размеры: debug **38,1 МБ**, **release (подписанный) 7,94 МБ** — гейты CI: debug ≤ 40 МБ, release ≤ 8 МБ. Тесты: 42, зелёные.
- **v1.0 (27.10.2026)**: именование APK `unidoc-v1.0-release.apk`; ИСПРАВЛЕНО: клик по «Недавним» не открывал файл (строка не была кликабельной); добавлен Robolectric-набор: клик-тест недавних, роутинг всех типов в плагины, открытие md/epub/fb2/mobi/cbz по пайплайну, AcroForm round-trip (включены, были @Ignore), ODT с картинками/colspan — всего 63 теста.

- **v1.1 (21.11.2026)**: по отчётам с устройства — ИСПРАВЛЕНО: (1) клик по «Недавним», (2) книги не открывались (view.open() не возвращает книгу — брать view.book), (3) docx падал (конфликт JSZip2/JSZip3 — единый JSZip3); ДОБАВЛЕНО: txt/log/csv (TextPlugin), rtf (свой конвертер: cp1251/unicode/b/i/ul/fs, \*-группы), xls (SheetJS читает BIFF), odt — автоматические стили (жирный/курсив/подчёркивание/цвет/размер/выравнивание с наследованием). Тесты 63→77.

- Размеры: debug **37,6 МБ**, **release (подписанный) 7,96 МБ** — гейты CI: debug ≤ 40 МБ, release ≤ 8 МБ. Тесты: 77, зелёные.

## Известные ограничения

- legacy .doc/.xls не поддерживаются (решение в §8 архитектуры), Mermaid-диаграммы работают офлайн (9.4.3 вендорен), en-локализация сообщений движков — backlog.

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
