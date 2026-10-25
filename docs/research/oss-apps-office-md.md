# OSS-проекты-референсы: офисные документы и Markdown

**Дата проверки: 25.10.2026.** Второй срез после `docs/research/oss-survey.md`: не библиотеки, а **целые приложения и встраиваемые компоненты**, которые умеют просматривать и/или редактировать документы. Цель — найти архитектурные уроки и лицензионно чистые кандидаты для unidocs.

Метод: GitHub REST API (`license`, `pushed_at`, `archived`, звёзды), сырые `LICENSE`-файлы, npm registry, размеры файлов через jsDelivr, F-Droid API + HEAD APK, разбор APK через `ZipFile` (OpenDocument Reader, Markor). Всё, что помечено **факт**, проверено 25.10.2026; **оценка** — вывод, который требует отдельной проверки.

Первый обзор лежит в `docs/research/oss-survey.md` (при подготовке репозитория файл был перемещён из `research/` в `docs/research/`). Ниже — только то, чего в нём нет: приложения, редактирование, web-компоненты.

**Короткий ответ.** Просмотр офиса остаётся JS-путём (`docx-preview` 74 KiB + SheetJS 861 KiB) — это подтверждено. Полноценное редактирование офиса стоит **242,6 MiB на ABI** (Collabora), но появился средний путь — `OpenDocument.core` (MPL-2.0, 8,56 MiB/ABI, HTML, edit+save OOXML/ODF). Markdown остаётся нативным (Markor 11,4 MiB, Apache-2.0); web-WYSIWYG для v1 избыточен.

Общая уверенность: **высокая** по лицензиям и размерам (живые страницы и APK), **средняя** по качеству редактирования ODR — его я не собирал и не тестировал.

## 0. Что меняет картину после первого обзора

1. **`docx-preview` — Apache-2.0**, версия 0.4.1, LICENSE в репозитории `VolodymyrBaydalka/docxjs`; min-сборка — **74 KiB** (факт, jsDelivr). Открытый вопрос №4 в `architecture.md` закрыт.
2. **Collabora Office для Android — open source** (MPL-2.0), а не «закрытая упаковка»: исходники в Gerrit-монорепо `online`, движок — `engine/` (бывший Collabora Office core). Цена — **242,6 MiB** на arm64.
3. **LibreOffice Viewer — 83,6 MiB** в актуальной версии 26.2.6.3 (vc=131), а не 74,0 MiB: 74,0 было у vc=118. Редактирование есть, но F-Droid прямо пишет: «experimental … not yet suited to production use».
4. **Появился средний путь для офиса**: `OpenDocument.core` — C++-библиотека (MPL-2.0), которая рендерит документы в HTML и **умеет edit+save для odt/ods/odp/docx/pptx/xlsx**. Это не «POI без вёрстки» и не «LibreOffice на 240 МБ».
5. **Web-экосистема поредела**: Toast UI Editor и Luckysheet архивированы; ProseMirror ушёл с GitHub на Forgejo (репозитории архивированы 01.04.2026, проект жив).

## (а) Сводная таблица

### а1. Офисные приложения и десктопные референсы

Лицензии, версии и звёзды в таблицах — **факт** по GitHub API, npm и F-Droid на 25.10.2026, если рядом не стоит «оценка». Размеры APK — факт по HEAD/разбору, но universal-APK и arm64-only APK несравнимы напрямую (см. оговорку по ODR).

| Проект | Платформа | Лицензия | Движок | Просмотр / редактирование | Что берём (вердикт) |
|---|---|---|---|---|---|
| LibreOffice Viewer | Android | MPL-2.0 на F-Droid; в репо `COPYING` — GPL-3.0 | LibreOfficeKit, `liblo-native-code.so` | просмотр; edit экспериментальный | урок «84 МБ = view-first»; main: нет, dyn: нет |
| Collabora Office | Android | MPL-2.0 (монорепо `online`) | LOK, `engine/` | полное редактирование | эталон full-edit UX; 242,6 МБ — не для minimal; main: нет |
| OpenDocument Reader | Android | MPL-2.0 | `OpenDocument.core` C++→HTML + WebView; `libodr_jni.so` 8,56 MiB | просмотр всех; edit в Pro; ядро умеет save OOXML/ODF | C++→HTML, 9 МБ/ABI; main: нет (MPL), dyn: **кандидат** |
| ONLYOFFICE Documents | Android | закрытая | собственный | редактирование | открытого клиента нет; main: нет |
| AndrOpen Office | Android | GPL/LGPL/MPL (часть кода) | OpenOffice port | редактирование | исходники неполные, org 404; main: нет |
| gander | Android | MIT | WebView + docx-preview/SheetJS/PPTXjs | просмотр | главный референс base-офиса; main: да |
| Docxion | Android | Apache-2.0 | то же, Compose-обёртка | просмотр | молодой, не эталон; наблюдать |
| LibreOffice | Desktop | MPL-2.0 файлы / GPL-3.0 `COPYING` | LOK | редактирование | урок: одно ядро + тайловый GL |
| Calligra | Desktop | GPL-2.0 | собственный | редактирование | урок: фильтры форматов отдельно от UI |
| AbiWord | Desktop | GPL-2.0 | собственный | редактирование | урок: лёгкий word-процессор возможен, но проект мёртв |
| Gnumeric | Desktop | GPL (GitHub: NOASSERTION) | собственный | редактирование | урок: движок таблиц отдельно от UI |

### а2. Markdown-приложения

| Проект | Платформа | Лицензия | Движок | Просмотр / редактирование | Что берём (вердикт) |
|---|---|---|---|---|---|
| Markor | Android | Apache-2.0 | Android Views + flexmark 0.42.14 | source + preview | 11,4 МБ; нативный путь; main: да (подход) |
| Joplin | Android | AGPL-3.0 | RN + CodeMirror 6 в WebView | edit + preview | 144,4 МБ — антипаттерн по весу; main: нет |
| Nextcloud Notes | Android | GPL-3.0 | нативный Android + nextcloud-commons markdown | edit + preview | простой EditText+preview достаточен; main: нет (GPL) |
| OpenNote-Compose | Android | GPL-3.0 | Compose | edit | MD UX на Compose реален; main: нет (GPL) |
| Zettel Notes | Android | закрытая (публичного репо нет) | н/д | edit | не референс; main: нет |
| MarkText | Desktop | MIT | Electron | edit + preview | v0.23.0 (21.10.2026) ожил; урок split/focus |
| Zettlr | Desktop | GPL-3.0 | Electron | edit + preview | урок: экспорт/pandoc — не наша зона |
| ghostwriter | Desktop | GPL-3.0 | Qt/KDE | edit | урок: distraction-free |
| Apostrophe | Desktop | GPL-3.0 | GTK | edit | мёртв с 2022; main: нет |

## (б) Разбор кандидатов

### б1. Офисные приложения

**LibreOffice Viewer (Android).**
- **Факт:** F-Droid `org.documentfoundation.libreoffice`, vc=131, 26.2.6.3, APK **87 682 841 B = 83,6 MiB** (HEAD, 25.10.2026). Версия выросла с 74,0 MiB (vc=118) на ~13% — цифра из первого обзора устарела.
- **Факт:** страница F-Droid: «experimental editing features, which are not yet suited to production use»; лицензия на странице — MPL-2.0. При этом в корне `LibreOffice/core` `COPYING` — **GPL-3.0** (raw, 25.10.2026), и GitHub определяет репозиторий как GPL-3.0. Конфликт не разрешён; для закрытой сборки это блокер до аудита.
- **Что берём:** масштаб «view-first = 84 МБ» и архитектуру LOK (одно ядро, тайловый рендер, отдельный JNI-поток) как эталон, но не код.
- **Вердикт:** main — нет; dynamic — нет. 84 МБ за просмотр и альфа-редактирование несовместимы с целью «минимальный размер».

**Collabora Office (Android).**
- **Факт:** исходники открыты, лицензия MPL-2.0; собирается из Gerrit-монорепо `https://gerrit.collaboraoffice.com/online`, движок — `engine/` (бывший Collabora Office core); ставится через сторонний F-Droid-репозиторий `https://www.collaboraoffice.com/downloads/fdroid/repo/`.
- **Факт:** APK 26.04.4.2 (05.10.2026): arm64 **254 391 190 B = 242,6 MiB**, universal **826 940 159 B = 788,6 MiB**; minSdk 26, targetSdk 37; в манифесте `MANAGE_EXTERNAL_STORAGE`, `READ/WRITE_EXTERNAL_STORAGE`, `INTERNET`.
- **Что берём:** доказательство, что полноценное редактирование на Android стоит ~243 МБ на ABI; UX-паттерны панелей/выделения; антипаттерн — `MANAGE_EXTERNAL_STORAGE` вместо SAF.
- **Вердикт:** main — нет; dynamic — нет (размер). Только эталон качества и источник идей.

**OpenDocument Reader (Android) — главная находка.**
- **Факт:** `opendocument-app/OpenDocument.droid`, MPL-2.0, 481★, push 24.10.2026; APK 4.22.1 — **40 109 298 B = 38,3 MiB**.
- **Факт (разбор APK):** `lib/arm64-v8a/libodr_jni.so` — **8 977 568 B (8,56 MiB)**, `libc++_shared.so` — 1,25 МБ, `classes.dex` — 1,19 МБ; внутри 4 ABI. 38 МБ — это в основном один C++-движок, а не Java/UI.
- **Оговорка:** APK 38,3 MiB — универсальный, с 4 ABI. В arm64-only сборке нативные библиотеки ~8,6 MiB, то есть ориентир APK ~15–20 MiB (оценка), а не 38. С 242,6 MiB Collabora (arm64-only) надо сравнивать именно этот ориентир.
- **Факт:** движок — `OpenDocument.core` («C++ library that reads documents and renders them to HTML», MPL-2.0, 25.10.2026). README: **edit+save — да** для odt/odp/ods/odg и flat-XML, docx/pptx/xlsx, txt, csv; только чтение — doc/ppt/xls, pdf (свой парсер), pages/key/numbers, rtf, md, изображения, шрифты, медиа.
- **Факт:** в приложении редактирование ограничено Pro-изданием: «new and joined paragraphs, formatting, PDF marks»; бесплатное — просмотр. То есть движок умеет больше, чем продукт показывает.
- **Что берём:** архитектуру «C++-конвертер → HTML → WebView»; список форматов; понимание, что 8,6 МБ на ABI дают офисный рендер без 240 МБ.
- **Вердикт:** main — нет (MPL-2.0 вне текущего allowlist); dynamic — **кандидат** после юридического решения по MPL и проверки, что GPL-компоненты из той же org (`pdf2htmlEX-Android` GPL-3.0, `wvWare-Android` GPL-2.0) не линкуются в `libodr_jni.so`. Это риск-пункт, не факт.

**ONLYOFFICE Documents (Android).**
- **Факт:** `ONLYOFFICE/documents-app-android` — 404; в org `ONLYOFFICE` нет Android-репозитория: есть `desktop-sdk`, `desktop-apps`, `DesktopEditors`, `mobile-projects` (последний push 2017), `documents-ios` (2022). Обсуждения (Reddit, PrivacyGuides) сообщают, что мобильные репозитории закрыты.
- **Факт:** `ONLYOFFICE/DocumentServer` — AGPL-3.0, 7 051★, push 22.10.2026; `ONLYOFFICE/core` — AGPL-3.0 (там же x2t).
- **Что берём:** ничего для Android. DS/x2t — только серверный/AGPL-путь, не для APK.
- **Вердикт:** main — нет; dynamic — нет.

**AndrOpen Office.**
- **Факт:** сайт `andropenoffice.com/source-code`: «You can find a part of source code … under GPL or LGPL or MPL», ссылка на `github.com/androidports`; org и репозиторий отдают 404 (25.10.2026).
- **Что берём:** ничего; исходники неполные, проект не сопровождается.
- **Вердикт:** main — нет; dynamic — нет.

**gander / Docxion.** Уже разобраны в первом обзоре; здесь важно: gander (MIT) — эталон «офис в ~5 МБ» на WebView (заявлено в README, не замерял), Docxion (Apache-2.0) — молодая Compose-обёртка, не эталон.

**Десктопные уроки (коротко).**
- **LibreOffice:** одно ядро `liblo-native-code.so`, тайловый рендер GL, LOK-поток; UI тонкий. Урок: не тащить рендер в Kotlin.
- **Collabora:** тот же движок в разных оболочках (Android/iOS/Online) — ядро переиспользуемо, оболочка дешевле.
- **Calligra:** GPL-2.0, 210★, push 23.10.2026; фильтры форматов отделены от приложений — урок для `:feature:*`.
- **AbiWord:** GPL-2.0, 179★, последний push 08.02.2024; лёгкий word-процессор возможен, но проект остановлен.
- **Gnumeric:** GPL (GitHub NOASSERTION), 104★, push 25.10.2026; движок таблиц/формул отделён от UI — урок, если делать свои формулы.

### б2. Markdown-приложения

**Markor.**
- **Факт:** Apache-2.0 (README: «The code of the app is licensed Apache 2.0»), 6 326★, push 23.10.2026; APK 2.15.2 — **11 929 636 B = 11,4 MiB**.
- **Факт (разбор APK):** `classes.dex` — 8,97 MiB; `assets/mermaid/mermaid.min.js` — **2,54 MiB**; `assets/asciidoc/asciidoctor.min.js` — 0,73 MiB; шрифты Liberation — ~1,1 MiB.
- **Факт:** Markdown-движок — flexmark 0.42.14, не commonmark; редактор нативный, source + preview.
- **Что берём:** модель «нативный редактор + preview»; отказ от mermaid в базе (экономия 2,5 МБ); flexmark как альтернативу commonmark.
- **Вердикт:** main — да (подход, не код); Apache-2.0.

**Joplin (Android).**
- **Факт:** AGPL-3.0 (LICENSE: «All code … AGPL-3.0-or-later»), 56 863★, push 25.10.2026; APK 3.6.22 — **151 436 585 B = 144,4 MiB**.
- **Факт:** редактор — `@joplin/editor` с CodeMirror 6 (`@codemirror/*` в `packages/editor/package.json`).
- **Что берём:** антипаттерн по весу; подтверждение, что CodeMirror 6 — рабочий source-редактор в WebView.
- **Вердикт:** main — нет (AGPL); dynamic — нет.

**Nextcloud Notes.**
- **Факт:** GPL-3.0, 1 111★, push 25.10.2026; нативный Android; markdown через `com.github.stefan-niedermann.nextcloud-commons:markdown`.
- **Что берём:** простой нативный edit + preview, без WYSIWYG.
- **Вердикт:** main — нет (GPL), но урок применим.

**OpenNote-Compose.**
- **Факт:** GPL-3.0, 775★, push 17.09.2025; Compose.
- **Что берём:** MD-редактор на Compose реален; код не берём из-за GPL.

**Zettel Notes.**
- **Факт:** на сайте `zettelnotes.com` нет ссылки на репозиторий и нет лицензии; публичного репо не найдено. Признаков open source нет.
- **Вердикт:** main — нет.

**Кладбище:** `mua` (MIT, 2020), `MarkdownEditors` (Apache-2.0, 2016), `MarkdownX` (без лицензии, 2015), `writeily-pro` (MIT, 2017) — не поддерживаются; брать только идеи.

**Десктоп (коротко).**
- **MarkText** — MIT, 63 072★, релиз **v0.23.0 от 21.10.2026** (проект ожил); Electron; урок: split-view, focus mode, экспорт.
- **Zettlr** — GPL-3.0, 13 818★, active; урок: академические ссылки/pandoc — не наша зона.
- **ghostwriter** — GPL-3.0 (KDE), 5 022★, active; урок: distraction-free.
- **Apostrophe** — GPL-3.0, 520★, последний push 26.10.2022 — мёртв; не брать.

## (в) Встраиваемые web-компоненты

| Компонент | Лицензия | Версия | Вес (факт) | Режим | Вердикт |
|---|---|---|---|---|---|
| Univer | Apache-2.0 | 1.2.0 | `engine-render` 27,2 MiB unpacked; `sheets-ui` 8,5; `core` 4,2 | edit xlsx/docs | dyn: кандидат, оценка 5–10 МБ min |
| Luckysheet | MIT | 2.1.13 | 2,9 MiB UMD | edit | push 2025-08, архивирован — нет |
| x-spreadsheet | MIT | 1.1.9 | 197 KiB | edit simple | main: опционально |
| Handsontable | проприетарная | 18.1.2 | 1,8 MiB min | edit | нет (только non-commercial) |
| ONLYOFFICE DocumentServer | AGPL-3.0 | — | сервер | edit | нет |
| x2t (`ONLYOFFICE/core`) | AGPL-3.0 | — | конвертер | convert | нет |
| Collabora Online | MPL-2.0 | — | LibreOffice WASM/сервер | edit | нет (не для WebView) |
| SuperDoc | AGPL-3.0 | 2.26.0 | 1,34 MiB min + pdf worker 1,58 | docx edit | нет |
| docx-preview | Apache-2.0 | 0.4.1 | **74 KiB** min | docx view | main: да |
| mammoth | BSD-2 | 1.13.0 | 1,6 MiB unpacked | docx→HTML | main: опционально |
| CKEditor 5 | GPL-2.0+/commercial | — | — | rich text | нет |
| TinyMCE | GPL-2.0+ | — | — | rich text | нет |
| Toast UI Editor | MIT | 3.2.2 | 342 KiB min | WYSIWYG/split | push 2024-08, архивирован — нет |
| Vditor | MIT | 4.0.1 | 290 KiB + Lute 3,6 MiB + mermaid 3,5 | WYSIWYG/IR/split | нет (тяжёлый) |
| Milkdown | MIT | 7.22.3 | Crepe 3,3 MiB unpacked | WYSIWYG | dyn: может быть |
| bytemd | MIT | 1.22.0 | 603 KiB UMD | split | устарел (2025-02) — нет |
| TipTap | MIT | 3.32.0 | core 3,15 MiB unpacked; starter-kit 54 KiB | WYSIWYG | dyn: может быть |
| ProseMirror | MIT | 1.25.12 | model 530 KiB unpacked | toolkit | dyn: может быть |
| Lexical | MIT | 0.52.0 | 3,08 MiB unpacked; markdown 220 KiB | WYSIWYG | dyn: может быть |
| CodeMirror 6 | MIT | 6.x | модульный, ~1–1,5 МБ (оценка) | source | dyn: может быть |
| marked | MIT | 18.1.1 | 47 KiB | render | main: да |
| markdown-it | MIT | 15.0.3 | 98 KiB min | render | main: да |

**Таблицы.** Univer — самый заметный активный Apache-2.0-редактор таблиц (22 710★, push 25.10.2026), но его `engine-render` — 27,2 MiB распакованного JS; после сборки это ориентировочно 5–10 МБ (оценка, готовой UMD-сборки нет). Luckysheet (MIT) — последний push 19.08.2025, репозиторий архивирован: новые ошибки никто не исправит, 2,9 MiB UMD. x-spreadsheet — 197 KiB, MIT, последний push 07.08.2024: годится для «правки нескольких ячеек», но не для сложных формул/форматов/вёрстки. Handsontable — ловушка: бесплатно только personal/evaluation, коммерческое использование — платная лицензия, плюс запрет делать конкурирующий продукт; для unidocs не подходит.

**DOCX и текст.** SuperDoc (AGPL-3.0, 1,34 MiB min + 1,58 MiB pdf-worker) — самый зрелый из найденных OSS-редактор docx в браузере, но AGPL: либо открывать всё приложение, либо коммерческая лицензия. `docx-preview` — только просмотр, зато 74 KiB и Apache-2.0: идеальная база. `mammoth` (BSD-2) даёт семантический HTML, а не вёрстку — запасной путь для «кривых» docx. ONLYOFFICE DS/x2t — AGPL и серверная архитектура; Collabora Online — MPL, но это полноценный LibreOffice в браузере, не компонент для Android WebView. CKEditor 5 (GPL-2.0+/commercial) и TinyMCE (GPL-2.0+) для закрытой сборки не годятся.

**Markdown.** Toast UI Editor (MIT, 342 KiB) — последний push 01.08.2024, репозиторий архивирован: новый код на нём строить нельзя. Vditor (MIT) даёт WYSIWYG/IR/split, но тянет Lute 3,6 MiB и mermaid 3,5 MiB; для «просто MD» это избыточно. Milkdown (MIT, Crepe 3,3 MiB unpacked) и TipTap (MIT, core 3,15 MiB unpacked, starter-kit 54 KiB) — WYSIWYG на ProseMirror; TipTap 3.32 всё ещё зависит от `prosemirror-model ^1.25.12`. ProseMirror (MIT) — toolkit без UI; GitHub-репозитории архивированы 01.04.2026, разработка переехала на `code.haverbeke.berlin`, npm-пакеты живут. Lexical (MIT, 3,08 MiB unpacked, `@lexical/markdown` 220 KiB) — альтернатива без ProseMirror. bytemd (MIT, 603 KiB) — split-редактор, но последний push 12.02.2025. CodeMirror 6 (MIT) — лучший source-редактор (его использует Joplin), но в WebView он дороже нативного `BasicTextField`. marked (47 KiB) и markdown-it (98 KiB) — только рендер.

**Оговорка о весах.** `unpackedSize` npm — это не размер APK: туда входят типы, исходники, несколько форматов. Цифры jsDelivr — реальные min-файлы; для Univer/Milkdown/TipTap готовой min-сборки нет, поэтому их вес — оценка до Vite-сборки.

## (г) Выводы для unidocs

### 1. Что подтверждается

- **База офис-просмотра дешевле, чем казалось:** `docx-preview` — 74 KiB (Apache-2.0), SheetJS — 861 KiB min, PPTXjs — MIT (по первому обзору). Весь JS-набор офис-просмотра ~1–2 МБ, а не 5. Это подтверждает §8 `architecture.md`.
- **Markdown нативно дешевле:** Markor — 11,4 МБ (Apache-2.0) с mermaid и asciidoc; без них — ~8 МБ (оценка). Joplin — 144,4 МБ (AGPL, RN + WebView). Выбор `commonmark + BasicTextField + split` в §6 архитектуры верен.
- **WebView-путь для книг/офиса** подтверждён: и gander, и ODR используют HTML-рендер; разница — где парсить (JS vs C++).

### 2. Что меняется в `architecture.md`

1. **§8, фраза «лёгкого нативного OSS-рендерера OOXML не существует — между POI и LibreOffice пусто» — устарела.** Есть `OpenDocument.core` (MPL-2.0): C++→HTML, 8,56 MiB на arm64, edit+save для OOXML/ODF. Это третий путь, который нужно добавить в сравнение.
2. **§8/§10, «LibreOffice Viewer = 74 МБ, редактирование поддержано» — уточнить:** актуальный Viewer — 83,6 МБ, edit экспериментальный; полноценный редактор — Collabora Office, **242,6 МБ** на arm64. Бюджет «всё включено ~40 МБ» верен только без полноценного офис-редактирования.
3. **§3, карта модулей:** добавить кандидата `:lib:office-native` (ODR core, MPL-2.0, ~9 МБ/ABI) как динамический модуль для edit+save OOXML/ODF. Это не замена `:feature:office` (просмотр), а отдельный режим.
4. **§11, лицензионная матрица:** добавить MPL-2.0 (ODR core, Collabora Online) как «юридически совместимо с закрытым приложением при публикации изменённых MPL-файлов, но вне текущего allowlist»; `docx-preview` — Apache-2.0 (подтверждено); SuperDoc/OnlyOffice — AGPL; Handsontable — проприетарная; CKEditor/TinyMCE — GPL.
5. **§13, открытые вопросы:** №4 (лицензия docx-preview) закрыт; добавить №7 «MPL-политика» и №8 «ODR core: сборка, fidelity, GPL-линковка».
6. **Путь к первому обзору:** в шапке `architecture.md` указан `docs/research/oss-survey.md`, фактически файл — `research/oss-survey.md`.

### 3. Лицензионные вердикты

| Компонент | Лицензия | main (закрытая чистая сборка) | dynamic-модуль |
|---|---|---|---|
| docx-preview, mammoth, SheetJS, x-spreadsheet, Univer, marked, markdown-it | Apache-2.0 / BSD-2 / MIT | да (кроме Univer — по весу) | да |
| Markor (подход), MarkText (уроки) | Apache-2.0 / MIT | да | да |
| OpenDocument.core (ODR) | MPL-2.0 | нет по текущему allowlist | **кандидат** после юридического решения |
| Collabora Online / Collabora Office | MPL-2.0 | нет (размер) | нет |
| SuperDoc, OnlyOffice DS, x2t, MuPDF | AGPL-3.0 | нет | нет (только коммерческая лицензия или открытие всего) |
| CKEditor 5, TinyMCE, Nextcloud Notes, Zettlr, ghostwriter, Apostrophe, Calligra, AbiWord, Gnumeric | GPL-2.0+ | нет | нет |
| Handsontable | проприетарная | нет | нет |
| Luckysheet, Toast UI | MIT, но архивированы | нет (нет поддержки) | нет |

### 4. Антипаттерны

- **Не копировать `MANAGE_EXTERNAL_STORAGE`** из Collabora: unidocs живёт на SAF (§9 архитектуры).
- **Не бандлить mermaid по умолчанию:** у Markor это 2,54 МБ из 11,4 МБ. Mermaid — отдельный опциональный модуль (как и записано в §6).
- **Не строить MD на React Native/WebView:** Joplin — 144,4 МБ; нативный Markor — 11,4 МБ.
- **Не считать GitHub-архив смертью проекта:** ProseMirror архивирован, но жив на Forgejo; проверять npm-версии и changelog.
- **Не путать «open source OnlyOffice mobile» с реальностью:** Android-репозитория нет, клиент закрыт.
- **Не использовать `unpackedSize` как вес APK:** для Univer/TipTap/Milkdown нужна сборка и замер.

### 5. Что проверить дальше (spikes)

1. **ODR core:** собрать arm64 (Conan + NDK), замерить stripped `.so`, прогнать edit+save на 10–15 реальных docx/xlsx/pptx; отдельно проверить, не линкуются ли GPL `pdf2htmlEX`/`wvWare`.
2. **Univer:** собрать Vite-бандл `@univerjs/presets` + `preset-sheets-core`, замерить min+gzip; сравнить с x-spreadsheet на правке 1000 строк.
3. **docx-preview:** сравнить вёрстку с Word на тех же 10–15 файлах (это уже вопрос №2 архитектуры).
4. **MPL-политика:** решить, допустим ли MPL-2.0 в dynamic-модуле; если да — ODR core становится главным кандидатом на «офисное редактирование без 240 МБ».
5. **MD-редактор:** измерить `BasicTextField` vs CodeMirror 6 в WebView на 1 МБ текста (память, ввод, Undo); если нативный проходит — не тащить WebView.

## Приложение. Источники и что не проверено

**Ключевые источники (все открывались 25.10.2026):**
- F-Droid API + HEAD: `org.documentfoundation.libreoffice` (vc=131, 87 682 841 B), `at.tomtasche.reader` (vc=42201, 40 109 298 B), `net.gsantner.markor` (vc=161, 11 929 636 B), `net.cozic.joplin` (vc=2097810, 151 436 585 B).
- F-Droid страница LibreOffice Viewer: `https://f-droid.org/en/packages/org.documentfoundation.libreoffice/` (цитата про experimental editing, MPL-2.0).
- Collabora: `https://www.collaboraonline.com/collabora-office-android-ios`, `https://collaboraonline.github.io/post/build-code-android`, F-Droid index `https://www.collaboraoffice.com/downloads/fdroid/repo/index-v2.json` (arm64 254 391 190 B).
- ODR: `https://github.com/opendocument-app/OpenDocument.droid`, `https://github.com/opendocument-app/OpenDocument.core` (README: форматы, edit+save; LICENSE: MPL-2.0).
- GitHub API: `ONLYOFFICE/*`, `dream-num/univer`, `mengshukeji/Luckysheet`, `myliang/x-spreadsheet`, `handsontable/handsontable`, `superdoc/docx-editor`, `gsantner/markor`, `laurent22/joplin`, `nextcloud/notes-android`, `nhn/tui.editor`, `Milkdown/milkdown`, `Vanessa219/vditor`, `pd4d10/bytemd`, `ueberdosis/tiptap`, `facebook/lexical`, `marktext/marktext`, `Zettlr/Zettlr`, `KDE/ghostwriter`, `ApostropheEditor/Apostrophe`, `KDE/calligra`, `AbiWord/abiword`, `GNOME/gnumeric`.
- npm registry + jsDelivr: версии, лицензии, `unpackedSize`, min-файлы (docx-preview 74 KiB, xlsx.full.min 861 KiB, Luckysheet 2 979 KiB, Handsontable 1 840 KiB, SuperDoc 1 368 KiB, Toast UI 349 899 B, Vditor 290 KiB + Lute 3 656 KiB, bytemd 603 KiB, marked 47 KiB, markdown-it 98 KiB).
- ProseMirror: `https://discuss.prosemirror.net/t/prosemirrors-migration-to-forgejo/8974` (миграция, 01.04.2026).
- OnlyOffice mobile: `https://discuss.privacyguides.net/t/onlyoffice-github-404/38117`, `https://www.reddit.com/r/OnlyOffice/comments/1tihucg/android_app_repo_removed` (репозитории закрыты; 404 проверен).

**Не проверено:**
- Точная лицензия кода самого приложения Collabora Office по файлам (MPL-2.0 у движка; приложение может иметь свои исключения).
- Реальный min-бандл Univer, TipTap, Milkdown, Lexical — без сборки; цифры `unpackedSize` завышены.
- Сборка ODR core под Android своими руками, stripped-размер `.so`, качество edit+save на реальных файлах.
- Линковка GPL `pdf2htmlEX`/`wvWare` в текущем `libodr_jni.so` — не проверялась (в org есть GPL-3.0/GPL-2.0 репозитории).
- Закрытость Zettel Notes — доказано отсутствием публичного репо/лицензии, а не заявлением автора.
- Размеры APK OnlyOffice/AndrOpen — не проверялись (у OnlyOffice нет открытого APK, у AndrOpen источник неполный).
- ABI-состав APK LibreOffice Viewer 131 — не проверял; сравнение 83,6 MiB с arm64-APK Collabora может быть неоднородным.
