# Архитектура unidocs — универсальный просмотрщик/редактор документов для Android

Версия: 0.3 (проектная, после двух обзоров OSS). Дата: 25.10.2026.
Цель: **минимальное по размеру, но максимально функциональное приложение**: офисные документы (просмотр; простое редактирование), Markdown (просмотр + редактирование), электронные книги (чтение), PDF (просмотр + заполнение форм, факсимиле).

Сопутствующие документы: `docs/research/apk-leaders.md` (реверс двух лидеров), `docs/research/oss-survey.md` (проверенный обзор библиотек: версии, лицензии, замеры), `docs/research/oss-apps-office-md.md` (референсы-приложения и web-компоненты: офис и Markdown).

---

## 1. Принципы

1. **Просмотр — дёшево, редактирование — дорого.** Рендер всех форматов должен уложиться в десятки МБ; полноценное офисное редактирование (как у NCloud Office: 273 МБ APK, два нативных ядра по 137 МБ) — осознанно за рамками.
2. **Модули = фичи.** Каждый формат — отдельный Gradle-модуль (и, где оправдано, динамический фича-модуль Play Feature Delivery): базовый APK маленький, тяжёлые движки ставятся по требованию.
3. **Ядро не знает про UI.** Движки возвращают нормализованную модель/поверхность рендера; Compose-слой только рисует и собирает события. Это позволяет тестировать движки на JVM и заменять реализации.
4. **Гибрид «натив + WebView» по задачам, а не по догме.** Где есть зрелый MIT/Apache JS-движок (книги, офисный просмотр) — используем его в изолированном WebView с запретом сети; где нужен UX и запись (MD-редактор, PDF-формы) — нативный код.
5. **Оффлайн-первичность и приватность**: никакой обязательной телеметрии, всё работает локально.
6. **Лицензионная гигиена**: в основной сборке только Apache-2.0/MIT/BSD/OFL; GPL/AGPL/LGPL-компоненты не попадают в v1 вовсе (см. §11).
7. **Бюджет размера фиксируется в CI**: сборка считает размер base + каждого модуля; превышение бюджета ломает пайплайн.

## 2. Язык и стек — почему Kotlin

**Решение: Kotlin + Jetpack Compose (Material 3), single-activity; неймспейс и applicationId — `tech.stpnk.unidocs` (конвенция Сергея: все его проекты — `tech.stpnk.*`).** Аргументы:

- Вся нужная нативная экосистема — JVM/Android-first: `androidx.pdf`, pdfium-биндинги, PDFBox-Android (формы), commonmark, epub4j.
- Compose даёт наименьший объём UI-кода и удобный хостинг «чужих» поверхностей (AndroidView/WebView) для движков, которые рендерят HTML.
- Корпоративный опыт лидеров подтверждает паттерн: даже NAVER Cloud строит UI на Compose, оставляя движки в C++ за JNI. Мы копируем схему «тонкий Kotlin-фасад → движок», но движки берём готовые open-source.
- Flutter-путь (как у ReadMD) даёт один UI-стек, но: +8–12 МБ движка на ABI; MD всё равно рендерится через WebView (markdown-it+mermaid — см. разбор ReadMD); работа с AcroForm/офисными JS-движками через платформенные каналы многословнее. React Native — тяжёлый мост без выигрыша в размере. Rust-ядро — не сейчас: выигрыш только в парсинге, рендер PDF всё равно pdfium (C).

## 3. Карта модулей

```
:app                      — шелл: файловый менеджер, «Недавние», роутинг, настройки
:core:model               — DocumentInfo, плагинный SPI, типы
:core:storage             — SAF-обёртки, кэш превью, DataStore-настройки
:core:ui                  — общие Compose-компоненты (панели, тулбары, диалоги)
:core:webview             — изолированный WebView-хост: запрет сети/файлов, инъекция локальных assets,
                            лицензии JS-движков в assets/licenses/

:feature:markdown  → commonmark-java + собственный рендер (Compose spans)
:feature:pdf       → androidx.pdf:pdf-viewer (основа), PdfRenderer (fallback), [динамич.] pdfium (io.legere),
                     [динамич.] :lib:pdf-forms (PDFBox-Android)
:feature:books     → :core:webview + foliate-js (MIT): epub/mobi/azw3/fb2/cbz
                     [нативный fallback] epub4j для парсинга без WebView
:feature:office    → :core:webview + docx-preview/SheetJS/PPTXjs (просмотр; JS ~1–2 МБ),
                     [динамич.] :lib:office-edit (POI: правка данных xlsx/docx),
                     [динамич., кандидат] :lib:office-native (OpenDocument.core, MPL-2.0:
                     C++→HTML, ~9 МБ/ABI, edit+save OOXML/ODF)
```

Динамическая доставка: `:lib:pdf-forms` и `:lib:office-edit` — on-demand (+12 и +7 МБ соответственно); всё остальное — в базовом APK.

## 4. Плагинный SPI документов

```kotlin
interface DocumentPlugin {
    val id: String                                   // "pdf", "markdown", "epub", "docx"...
    fun supports(mime: String?, name: String, sniff: ByteSequence): Boolean
    suspend fun open(ctx: OpenContext): DocumentSession
}

interface DocumentSession {
    val info: DocumentInfo                           // заголовок, страницы/главы, метаданные
    val surface: DocumentSurface                     // Compose-рендер + жесты
    val outline: Flow<List<OutlineNode>>
    suspend fun search(query: String): Flow<SearchHit>
    suspend fun save(target: SaveTarget)             // для редактируемых форматов
    fun close()
}
```

- Поверхности: PDF — `androidx.pdf`/`PdfRenderer`/pdfium; книги и офис-просмотр — изолированный WebView; MD — Compose-композиция; таблицы-правки — `LazyGrid`.
- Регистрация плагинов — ручной список в `:core:model`; новый формат = новый модуль + 2 файла.

## 5. Домен: PDF (просмотр + формы + факсимиле)

- **Просмотр (основной)**: `androidx.pdf:pdf-viewer` 1.0.0-beta01 (Apache-2.0, AAR 1,23 МБ, без `.so` — работает поверх системного `PdfRenderer`): готовый экран с поиском, выделением, ссылками. Fallback — собственный экран на `PdfRenderer` (API 21+).
- **Расширенный режим (динамический модуль)**: pdfium через живой биндинг `io.legere:pdfiumandroid` (Apache-2.0; `libpdfium.so` arm64 4,55 МБ) — собственный тайловый рендер и текстовый слой, если beta-версия androidx.pdf не устроит качеством/скоростью.
- **Формы (AcroForm) и факсимиле** — динамический модуль на `com.tom_roush:pdfbox-android` (Apache-2.0; AAR 3,10 МБ + BouncyCastle ~8,9 МБ): чтение/заполнение полей, штамп-изображение подписи, сохранение (опционально флаттенинг). Это единственный Apache-2.0-путь «писать в PDF».
- Подписи-картинки хранятся локально (DataStore/EncryptedFile), повторное использование одним тапом.
- **Отвергнуто**: MuPDF (AGPL — либо открытие всего приложения, либо коммерческая лицензия Artifex), pdf.js как основной просмотрщик (только опция точной вёрстки), коммерческие SDK (ComPDFKit — он и стоит в NCloud Office).

## 6. Домен: Markdown (просмотр + редактирование)

- Парсер: `commonmark-java` (BSD-2, 0,20 МБ) + расширения (таблицы, зачёркивание, списки задач).
- Рендер: **собственный** — Compose `AnnotatedString`/TextView spans (подсветка кода, изображения). Причина: Markwon удобен, но без релизов с 2021 (заморожен) — брать можно, строить на нём ядро нельзя.
- Редактор: `BasicTextField` + тулбар (ж/к/заголовки/списки/ссылка), split-режим «текст | превью», моноширинный системный шрифт (опц. JetBrains Mono, 0,3 МБ). **sora-editor отвергнут — LGPL-2.1** (см. §11). Референсы: Markor (Apache-2.0) — 11,4 МБ APK целиком на нативном подходе; Joplin (144 МБ, AGPL, RN+WebView) — антипаттерн по весу.
- Mermaid — v2, отдельный опциональный WebView-модуль (у ReadMD — 3 МБ, у Markor — 2,54 МБ из 11,4 МБ APK: подтверждение, что в базу его бандлить нельзя).

## 7. Домен: книги (epub/fb2/mobi/azw3/cbz/cbr)

- **Основной движок: foliate-js (MIT, чистый JS)** в изолированном WebView через `:core:webview` — один движок закрывает EPUB, MOBI, KF8/AZW3, FB2, CBZ сразу, без нативного кода и без GPL. Проверено практикой: Anx Reader (MIT, Flutter) построен на foliate-js; Readest — тоже web-стек.
- **Нативный fallback**: `epub4j` (Apache-2.0, 0,27 МБ — форк epublib, у которого в репо нет LICENSE — оригинал не брать); fb2 — собственный `XmlPullParser`-парсер (сотни строк); cbz — `java.util.zip`.
- **CBR**: `junrar` (0,15 МБ) — лицензия UnRAR freeware (не OSI): только распаковка, отдельный NOTICE, не смешивать с GPL-кодом.
- Пагинация/темы/закладки — поверх foliate-js API; нативный paginator — только если WebView-вариант провалит бенчмарки (см. §13).
- **DjVu исключён из v1**: DjVuLibre — GPL-2.0, без Maven-биндинга, потребовал бы NDK-сборки и GPL-очистки всего продукта (Orion Viewer живёт именно так). Вердикт обзора: это единственный формат, ломающий лицензионную чистоту — решение «GPL-издание или не поддерживать» принимается отдельно.

## 8. Домен: офисные форматы (view-first)

Три реальных пути (проверены двумя обзорами): JS-конвертеры в WebView, LibreOfficeKit (~84–243 МБ) и посередине — `OpenDocument.core` (MPL-2.0, C++→HTML, 8,56 МБ на arm64, edit+save для OOXML/ODF):

- **Просмотр (база)**: WebView + JS-конвертеры, подход доказан приложением **gander** (MIT, ~5 МБ, без натива): `docx-preview` 0.4.1 — **Apache-2.0, 74 КиБ** (лицензия подтверждена — открытый вопрос закрыт), **SheetJS** (XLSX, Apache-2.0, 861 КиБ min), `PPTXjs` (MIT), ODT — свой XML→HTML. Весь JS-набор офис-просмотра ~1–2 МБ, вёрстка приблизительная.
- **Правка данных (динамический модуль)**: Apache POI (`poi-ooxml` + `xmlbeans`, 6,9 МБ jars, на Android через `poi-on-android`) — значения ячеек xlsx, текст docx на уровне ранов. Обещать «как в Word» нельзя и не обещаем.
- **Настоящее офисное редактирование — LibreOfficeKit** (`liblo-native-code.so`, тайловый рендер GL): актуальный LibreOffice Viewer 26.2.6.3 — **83,6 МБ**, edit объявлен F-Droid экспериментальным; полноценный редактор — Collabora Office (исходники открыты, MPL-2.0, монорепо `online`) — **242,6 МБ на arm64**. Нюансы: NDK-сборка на часы и лицензионный конфликт LibreOffice (файлы MPL-2.0, корневой `COPYING` — GPL-3.0) — блокер до аудита. Решение отложено до явного запроса продукта.
- **Средний путь — `OpenDocument.core`** (MPL-2.0, движок OpenDocument Reader): C++-конвертер → HTML → WebView, **8,56 МБ `.so` на arm64**, заявлены edit+save для odt/ods/odp/docx/pptx/xlsx. Кандидат на динамический модуль `:lib:office-native` после: (а) MPL-решения, (б) спайка по качеству edit+save на реальных файлах, (в) проверки, что GPL-компоненты той же организации (`pdf2htmlEX-Android`, `wvWare-Android`) не линкуются в ядро.
- **Legacy .doc/.xls**: POI HWPF/HSSF — текст/таблицы без вёрстки; лёгкого рендерера не существует (честно фиксируем деградацию).

## 9. Файлы и хранилище

- SAF как единственный источник файлов: без `MANAGE_EXTERNAL_STORAGE` и `READ_EXTERNAL_STORAGE`.
- «Недавние»/закладки/позиции чтения — DataStore/Room; кэш превью — `cacheDir` с LRU.
- Запись: только по явному «Сохранить» через SAF; автосохранение правок — в черновики приложения.

## 10. Бюджет размера (цель, сверено с замерами обзора)

| Часть | Оценка | Комментарий |
|---|---|---|
| base (факт M2): шелл + MD + PDF (androidx.pdf) | **4,7 МБ release** (debug 32,6 МБ) | R8 вычистил material/sandbox; книги/офис добавят ~2–5 МБ JS-assets |
| [динамич.] `:lib:pdf-forms` (PDFBox + BouncyCastle) | +12 МБ | формы + факсимиле + запись PDF |
| [динамич.] `:lib:office-edit` (POI) | +7 МБ | правка данных xlsx/docx |
| [динамич., кандидат] `:lib:office-native` (OpenDocument.core) | +9 МБ/ABI | edit+save OOXML/ODF; требует MPL-решения и спайка |
| **Итого (факт M2): release ~5 МБ; с книгами/офисом прогноз ~8–12 МБ** | | против 273 МБ у NCloud Office и 83,6 МБ у LibreOffice Viewer |

Механика сдерживания: R8 full + resource shrinking, App Bundle с ABI-сплитами, динамические модули, никакого Firebase/аналитики, baseline profiles.

## 11. Лицензионная матрица (v1 — только чистый набор)

| Компонент | Лицензия | Решение |
|---|---|---|
| androidx.pdf, PDFBox-Android, POI, SheetJS, epub4j | Apache-2.0 | да |
| commonmark-java, flexmark | BSD-2 | да |
| docx-preview (Apache-2.0 — подтверждено), PPTXjs, foliate-js, mammoth, marked, markdown-it, gander (референс) | MIT / Apache-2.0 / BSD-2 | да |
| pdf.js | Apache-2.0 | опция точной вёрстки |
| io.legere:pdfiumandroid | Apache-2.0 (POM; в репо NOASSERTION — зафиксировать в NOTICE) | да |
| Noto Sans | OFL-1.1 | да, в THIRD-PARTY |
| junrar | UnRAR freeware (не OSI) | да, отдельный NOTICE, только распаковка |
| Markwon | Apache-2.0 | опционально (заморожен) |
| OpenDocument.core (ODR) | MPL-2.0 | вне allowlist v1; кандидат в динамический модуль после MPL-решения |
| SuperDoc, OnlyOffice DocumentServer/x2t | AGPL-3.0 | нет |
| Handsontable | проприетарная (free — только non-commercial) | нет |
| Toast UI Editor, Luckysheet | MIT, но архивированы | нет (нет поддержки) |
| sora-editor | **LGPL-2.1** | **нет** в v1 |
| MuPDF | **AGPL-3.0** | **нет** (коммерческая лицензия или открытие всего продукта) |
| DjVuLibre, Orion Viewer, CoolReader | **GPL-2.0/3.0** | **нет** в v1 |
| LibreOfficeKit | MPL-2.0 файлы / **GPL-3.0 COPYING** | **нет** до аудита; кандидат на «настоящее редактирование» |

Обязательно: `THIRD-PARTY-NOTICES.md` из графа зависимостей в CI; лицензии JS-движков — в `assets/licenses/` (код поставляется пользователю).

## 12. Дорожная карта

- **M0. Каркас** — ✅ выполнен 25.10.2026: Gradle-скелет (модули `:app`, `:core:model`, `:core:storage`, `:core:ui`), плагинный SPI с юнит-тестами, SAF-менеджер + «Недавние» (DataStore), CI (сборка + тесты + гейты размера).
- **M1. Markdown** — ✅ выполнен 25.10.2026: модуль `:feature:markdown` — плагин SPI, парсер commonmark 0.24.0 (+GFM-таблицы/зачёркивание/списки задач) → собственная модель MdBlock/MdSpan (12 юнит-тестов), Compose-рендер без WebView, сессия с единым источником текста, поиск, оглавление, сохранение через SAF; экран «Текст / Сплит / Превью» с тулбаром разметки и защитой несохранённых правок. Размеры: debug 9,5 МБ, release (R8) 1,24 МБ.
- **M2. PDF-просмотр** — ✅ выполнен 25.10.2026: модуль `:feature:pdf` — androidx.pdf 1.0.0-beta01 (PdfView: выделение/ссылки/поиск UI; sandbox-сервис; session-поиск через searchDocument) + fallback-движок на системном PdfRenderer (переключатель в шапке). Потребовал обновления стека: Gradle 8.14.3, AGP 8.13.0, Kotlin 2.2.20, compileSdk 36 (+extension 19), minSdk 28 (требование pdf-core). Размеры: debug 32,6 МБ / release (R8) **4,7 МБ**. Качество рендера — бенчмарк на устройстве (§13).
- **M3. PDF-редактирование** — ✅ выполнен 25.10.2026: модуль `:feature:pdf-edit` — PDFBox-Android 2.0.27.0 (BouncyCastle исключён — криптоподписи не входят): заполнение AcroForm (текст/чекбоксы/радио/выбор), факсимиле — рисование пальцем или сохранённые подписи (DataStore, до 5), позиционирование тапом по предпросмотру страницы, сохранение через SAF + опциональный флаттер. Вход — «✎ Редактировать» из PDF-экрана. Размеры: debug 36,3 МБ / release **7,0 МБ**. JVM-тесты AcroForm помечены @Ignore: AFM-метрики порт читает только из Android-assets; логика форм проверяется на устройстве.
- **M4. Книги**: foliate-js (epub/fb2/mobi/azw3/cbz), темы, закладки, позиции; cbr через junrar.
- **M5. Офис-просмотр**: docx-preview/SheetJS/PPTXjs в WebView; сравнение вёрстки с Word на 10–15 реальных документах.
- **M6. Правка данных**: динамический модуль POI (ячейки xlsx, раны docx); pdfium-режим при необходимости.
- **M7. Полировка**: локализация (ru/en), size-проход, публикация (F-Droid/Play).

Метрика каждой вехи: «размер ≤ бюджета §10» + прохождение smoke-тестов форматов.

## 13. Открытые вопросы

1. `androidx.pdf` beta на реальных PDF (сканы, CJK, формы) на API 26/31/35 — качество системного рендера и наличие выделения текста на старых прошивках. Решает судьбу pdfium-модуля.
2. Вёрстка docx-preview/SheetJS против Word на реальных документах — главный риск WebView-подхода к офису (эталон для сравнения — сборка gander).
3. foliate-js на больших EPUB (100+ МБ) и MOBI без DRM — память и скорость WebView на устройстве с 2 ГБ RAM.
4. ~~docx-preview LICENSE~~ — закрыто: Apache-2.0, 0.4.1, min-сборка 74 КиБ.
5. POI на Android (`poi-on-android`) — вес dex после R8 и время старта; если плохо — правка данных только для xlsx.
6. DjVu — отдельное продуктовое решение (GPL-издание vs отсутствие формата), не техническое.
7. MPL-политика: допустим ли MPL-2.0 в динамическом модуле; если да — `OpenDocument.core` становится главным кандидатом на «офисное редактирование без 243 МБ».
8. Спайк ODR core: сборка arm64, замер stripped `.so`, качество edit+save на 10–15 реальных docx/xlsx/pptx, проверка линковки GPL-компонент (`pdf2htmlEX`, `wvWare`).
9. Univer (Apache-2.0, активный) как редактор таблиц в динамическом модуле: собрать Vite-бандл и замерить min+gzip (оценка 5–10 МБ).
10. MD-редактор: `BasicTextField` vs CodeMirror 6 в WebView на 1 МБ текста (память/ввод/undo) — если нативный проходит, WebView не тащим.
