# Реверс-инжиниринг лидеров: что внутри приложений, которыми вы пользуетесь

Дата: 25.10.2026. Инструменты: REA 6.7.0 (CLI, провайдер JADX headless 0.7.1), `aapt` из Android SDK 35.0.1, Python zipfile-инвентаризация.

Источники — два APK из `.ref_apks/` (в git не попадают):

| Файл | Размер на диске | Пакет | Версия |
|---|---|---|---|
| `app.readmd.readmd.apk` | 4,6 МБ | `app.readmd.readmd` | 1.3.0 (27) |
| `com.ncloudtech.cloudoffice.apk` | 273 МБ | `com.ncloudtech.cloudoffice` | 26.3.0 (2964415) |

---

## 1. ReadMD — «маленький» MD-редактор на Flutter

### Что в APK

- Один `classes.dex` (2,65 МБ), 2751 класс, **нет каталога `lib/`**.
- В `assets/flutter_assets/` лежат: `MaterialIcons-Regular.otf` (1,57 МБ), `JetBrainsMono-Regular.ttf` (0,27 МБ), `CupertinoIcons.ttf` (0,25 МБ) и — главное — **веб-стек для рендера Markdown**:

| Ассет | Размер | Назначение |
|---|---|---|
| `assets/js/markdown-it.min.js` | 103 КБ | парсер/рендерер Markdown |
| `assets/js/highlight.min.js` | 122 КБ | подсветка кода |
| `assets/js/mermaid.min.js` | 2,96 МБ | диаграммы Mermaid |
| `assets/template.html` | 44 КБ | HTML-шаблон предпросмотра |
| `assets/css/highlight-github.css` | 3 КБ | тема подсветки |

- Плагины (по строкам в dex): `dev.flutter.pigeon` (генерация каналов), `flutter_secure_storage`, `in_app_review`, `androidx.datastore`, Custom Tabs, Play Billing, Firebase/AdMob.
- `minSdk 24`, `targetSdk 36`, разрешения: сеть, биллинг, рекламный ID, чтение внешнего хранилища (до API 32).

### Ключевой вывод по методике

REA `identify-flutter-build` дал `flutter_detected: false` и пояснение: *«No lib/*/libapp.so was found; the target does not carry a Flutter AOT payload»*. Это **split-APK**: сам движок (`libflutter.so`) и код приложения (`libapp.so`) приходят отдельными конфигурационными APK при установке из Play. То есть 4,6 МБ — это base-модуль, а не полный установочный размер: реальный вес на устройстве включает нативные библиотеки Flutter для ABI (обычно +8–12 МБ). Вывод для нас: **сравнивать «размер APK из магазина» напрямую нельзя, надо сравнивать установочный размер** (base + splits + .so).

### Чему учит

1. Markdown-рендер через WebView + markdown-it — компактный и «бесплатно» получает GFM, подсветку и Mermaid. Цена — вес WebView-сессии, отсутствие нативной пагинации, дрожание скролла на больших документах.
2. Нативная альтернатива (Markwon/commonmark + Compose) даёт лучший UX и меньше памяти, но требует дописать Mermaid (или отказаться) и подсветку кода.
3. Для минимального размера Flutter-путь выглядит соблазнительно (один движок на всё), но именно UI-стек Flutter тянет +10 МБ нативных библиотек на ABI, а редактор текста/таблиц на Flutter всё равно потребует нативных вставок для серьёзных форматов.

---

## 2. NCloud Office (NAVER Cloud) — «всё включено» за 273 МБ

### Анатомия APK

- 10 842 записи, 574 МБ в распакованном виде, 7 dex-файлов (~70 МБ кода), только `arm64-v8a`.
- Нативные библиотеки (сжатые, в APK):

| Библиотека | Размер | Что это |
|---|---|---|
| `libCloudOfficeJNIcore42.so` | 138 МБ | офисное ядро, версия 4.2 |
| `libCloudOfficeJNIcore41.so` | 136 МБ | офисное ядро, версия 4.1 (две версии в одном APK — миграция) |
| `libcoksn.so` | 32 МБ | корейская раскладка/ввод? (cok = Chosung?) |
| `libcompdf.so` | 16 МБ | PDF-движок ComPDFKit |
| `libsbb.so` | 12 МБ | вспомогательный (smart book/bookmarks?) |
| `libnct-grammar.so` | 4,4 МБ | грамматика/орфография |
| `libhunspell-bindings-lib.so` | 0,6 МБ | Hunspell (проверка орфографии) |
| `libavcodec/avformat/avutil-amplituda` + `libmp3lame`, `libmpg123` | ~4 МБ | аудио (запись голосовых заметок) |

- `assets/www.zip` — 38,8 МБ веб-бандла (гибрид: часть UI/справки/редактора на HTML/JS).
- `assets/core41|core42/icu-resources/**` — данные ICU (коллации/локали), `assets/en_US.dic`, `assets/syntax.dic` — словари.
- Шрифты пачкой: Noto Sans/Mono, EB Garamond, Cormorant, Tinos, PT Sans/Serif, DroidSansFallback — десятки мегабайт «на всякий случай».
- UI: **Jetpack Compose** (Material3, 21 810 маркеров `compose` в dex), Ktor (в т.ч. `io.ktor.server` — локальный сервер), Kodein DI, Coil/Glide, Room, Firebase, Crashlytics, ucrop, Kaspersky (тестовая инфраструктура).
- Точка входа: `com.ncloudtech.cloudoffice.android.myfm.launch.LaunchActivity` («myfm» = my file manager). Локали: es, fr, ru (+ базовые).

### Движки

**Офисное ядро — собственное, не LibreOffice.** Классы `com.ncloudtech.cloudoffice.ndk.core41.*` (и `core42`): `EditorCoreFactory`, `TextEditorCore`, `WorksheetEditorCore`, `PresentationEditorCore`, `CellEditorCore`, `Tile`, `LayoutScannerHandler`, `Session`, десятки handler'ов (charts, notes, review, TOC, pivot tables, comments, undo/redo, clipboard MIME, collaboration). Это полноценная объектная модель документа с тайловым рендерингом и протоколом совместного редактирования (`ndk.core41.collaboration.*`: `NetworkDocumentConnectionFactory`, `FullConnectionToServer`, события вставки/удаления листов, изменения стилей). Следы «libreoffice» в именах классов отсутствуют — движок свой (или глубоко переработанный), в `assets` — ICU и Hunspell как типичные зависимости офисного C++.

**PDF — коммерческий SDK ComPDFKit** (`com.compdfkit.core.*` + `com.compdfkit.tools.*` + `com.compdfkit.ui.*`): документ, страница, текст-страница, поиск, аннотации 20+ типов (ink, stamp, freetext, redact, sound, movie), **AcroForm-виджеты** (text/checkbox/radio/combo/list/pushbutton/signature), цифровые подписи (X509, сертификаты, verify), редактирование контента, водяные знаки, шифрование, BOTA-режим, монетизация/лицензирование внутри SDK. Это закрытый платный SDK — в open-source проекте недоступен.

**Архитектурный паттерн интеграции**: Kotlin-фасад `EditorCoreFactory.create(name)` → нативное ядро через JNI (`CoreSettings` + `MediaStorage` на входе), рендер тайлами, UI отдельно (Compose), документ отдельно (ядро не знает про Android View).

### Цена такого подхода

- 273 МБ установочного размера, из них ~270 МБ — нативные ядра и ассеты; на слабых устройствах — долгий холодный старт.
- Две версии ядра одновременно (core41+core42) — наследие миграции, удвоение веса.
- Закрытые SDK (ComPDFKit) — лицензионные платежи и невозможность форка.
- Всё это ради «полного офиса» — Word/Excel/PowerPoint редактирование, совместная работа, конвертация.

---

## 3. Что из этого следует для unidocs

1. **Ни один лидер не является «лёгким»**. ReadMD лёгок только как base-split; NCloud — противоположный полюс. Значит, рыночная ниша «маленькое, но всеядное» реально свободна — но только если отказаться от полного редактирования офисных форматов «как в Office».
2. **Просмотр ≠ редактирование по стоимости.** Рендер PDF/MD/книг — это единицы-десятки МБ. Полноценное офисное редактирование — это 130+ МБ нативного ядра (NCloud) или 200+ МБ LibreOfficeKit. Разумная стратегия: *просмотр всего* + *простое редактирование там, где оно дёшево* (MD, PDF-формы/факсимиле, правка ячеек/текста в OOXML на уровне рана).
3. **Разделение «ядро ↔ UI»** — правильный паттерн: движки не должны зависеть от Android View, тогда их можно грузить динамическими модулями (Play Feature Delivery) и держать базовый APK маленьким.
4. **ComPDFKit-функциональность (формы, подписи, штампы) в open-source собирается из двух частей**: рендер pdfium (BSD) + работа с AcroForm/картинками через PDFBox-Android (Apache-2.0). Это на порядок меньше 16 МБ `libcompdf.so` и легально.
5. **Веб-стек для MD — рабочая, но не оптимальная тактика**: markdown-it+mermaid = 3,2 МБ ассетов и WebView; нативный Markwon даёт тот же GFM за ~1 МБ dex и лучший UX. Mermaid — опциональный динамический модуль (или замена на локальный рендер диаграмм).
6. **Шрифты — скрытый вес.** NCloud везёт десятки гарнитур. Нам хватит 2–3 (UI + моноширинный + опционально serif для книг), всё остальное — системные шрифты Android.
7. **ICU/локали** для корректной сортировки и чисел нужны только офисному движку; для просмотра PDF/книг не нужны вовсе.
