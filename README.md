# BlownChart

A simple, fast Android home screen launcher with categorized folders in the
app drawer and a real screen-lock mode — a fork of
[Lawnchair](https://github.com/LawnchairLauncher/lawnchair).

**This is a fork of Lawnchair. Original project:
https://github.com/LawnchairLauncher/lawnchair. Licensed under the GNU
General Public License v3.0 (GPLv3), same as the original.** See
[`LICENSE`](LICENSE), [`LICENSE.txt`](LICENSE.txt) and [`NOTICE`](NOTICE)
for the full license text and the exact license boundaries between the
AOSP-derived code and the GPLv3 Lawnchair/BlownChart code.

[![License: GPLv3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Build debug APK](https://github.com/wolk-tambowskij/blownchart/actions/workflows/ci.yml/badge.svg?branch=15-dev)](https://github.com/wolk-tambowskij/blownchart/actions/workflows/ci.yml)
[![Latest release](https://img.shields.io/github/v/release/wolk-tambowskij/blownchart?include_prereleases&label=version)](https://github.com/wolk-tambowskij/blownchart/releases)

---

## English

### Why this fork exists

BlownChart started because a simple wish had no ready-made answer: a
launcher that categorizes apps into folders right in the app drawer, stays
fast even with a large number of apps and categories, and can fully lock
its settings and layout down (important once the drawer has grown large
and you don't want to reorganize it by accident). Nothing off-the-shelf
covered all three, so this fork builds on Lawnchair's solid Launcher3
foundation to add them.

### Differences from the original

App drawer folders:

- One level of folder-in-folder nesting, with a small badge marking a
  folder that contains another; nested folders are always sorted before
  apps.
- Optional manual drag-and-drop ordering of folders and their contents
  (alphabetical stays the default).
- Search bar when picking apps for a folder.
- Export and import your whole folder layout as a JSON file.
- A thin outline on folder previews, independent of theme/opacity.
- Folder previews render using the exact configured icon shape (instead
  of being approximated to a handful of hardcoded shapes) and no longer
  overflow their bounds for unusual app-count/shape combinations.
- The "App drawer folders" screen and folder editing stay fast even with
  a large number of installed apps and folders (tested with 40 folders /
  1766 apps).

Privacy and locking:

- "Lock app drawer" and "Lock home screen" are separate settings: the
  drawer lock blocks renaming/hiding/changing the icon/uninstalling apps
  from the drawer, while the home screen lock separately blocks moving
  and resizing widgets too — closing a gap where either lock could
  previously be bypassed via the Uninstall shortcut or widget
  placement/resize.
- A PIN/fingerprint lock gates the launcher's own settings and any exit
  into system Settings (it does not apply to shortcuts or widgets that
  belong to other apps).

Other:

- App drawer search results show which folder an app is in, including
  the full path for an app inside a nested subfolder.
- The launcher's own app-drawer entry is hidden by default.
- A persistent prompt to exempt the launcher from battery optimization,
  since it's easy to dismiss once and forget.
- Rebranded identity (name, icon, `applicationId`) so it can be installed
  side by side with Lawnchair itself; hand-maintained Russian
  translations instead of upstream's Crowdin-managed ones; signed
  CI/release workflows and periodic upstream-sync tracking (see
  [`.github/workflows/`](.github/workflows/)).

### Download

Not yet available — no signed release has been published yet. This
section will be filled in once the first signed GitHub Release goes out
(see [`docs/UPSTREAM_SYNC.md`](docs/UPSTREAM_SYNC.md) for how upstream
changes are tracked, and the CI/release workflows under
[`.github/workflows/`](.github/workflows/)).

### Build

Requirements: JDK 17, Android SDK, and the NDK/CMake versions pinned in
`build.gradle`.

```sh
git clone --recurse-submodules https://github.com/wolk-tambowskij/blownchart.git
cd blownchart
git checkout 15-dev

# List all available build variants
./gradlew tasks --group=build

# Debug build (unsigned), GitHub/FOSS channel
./gradlew assembleLawnWithQuickstepGithubDebug
```

You can also open the project directly in Android Studio and build/run any
variant from the Build Variants panel.

A signed **release** build additionally needs signing credentials. Create a
`keystore.properties` file at the repository root (already covered by
`.gitignore`, never commit it):

```properties
storeFile=/absolute/path/to/your.keystore
storePassword=...
keyAlias=...
keyPassword=...
```

then run e.g. `./gradlew assembleLawnWithQuickstepGithubRelease`. In CI,
the same signing config is fed from GitHub Secrets instead of this file.

### Support the project

BlownChart is free and always will be — donating never unlocks a feature,
that would contradict both the spirit and the letter of the GPLv3. If
you'd still like to chip in, it's in-app under
`Settings → About → Support development`, or directly:

- **PayPal:** https://www.paypal.com/donate/?business=wolk.tambowskij%40gmail.com&currency_code=USD
- **YooMoney (ЮMoney):** https://yoomoney.ru/to/4100119588109985

See [`docs/DONATIONS.md`](docs/DONATIONS.md) for how the config works and
why the Google Play build hides direct payment links.

### License and attribution

- Full license texts: [`LICENSE`](LICENSE) (GPLv3), [`LICENSE.txt`](LICENSE.txt) (Apache-2.0).
- License boundaries and copyright: [`NOTICE`](NOTICE).
- Third-party code carried over from other projects: [`THIRD_PARTY.md`](THIRD_PARTY.md).
- Contributing to Lawnchair upstream: [`CONTRIBUTING.md`](CONTRIBUTING.md).

---

## Русский

### Зачем этот форк

BlownChart появился из-за того, что на простое желание не нашлось готового
ответа: лончер, который раскладывает приложения по категориям прямо в меню
приложений (app drawer), остаётся быстрым даже при большом количестве
приложений и категорий, и умеет полностью заблокировать свои настройки и
раскладку (это важно, когда меню приложений уже разрослось и не хочется
случайно всё переорганизовать). Готового решения, закрывающего все три
пункта разом, в открытом доступе найти не удалось — поэтому форк строится
поверх прочной базы Lawnchair/Launcher3.

### Отличия от оригинала

Папки в меню приложений:

- Один уровень вложенности папок друг в друга; папка, содержащая другую
  папку, помечается небольшим значком, вложенные папки всегда идут перед
  приложениями.
- Опциональная ручная сортировка папок и их содержимого перетаскиванием
  (по умолчанию — алфавитная).
- Строка поиска при выборе приложений для папки.
- Экспорт и импорт всей раскладки папок в JSON-файл.
- Тонкая обводка у превью папок, не зависящая от темы/прозрачности.
- Превью папок рисуются по точной настроенной форме иконок (а не по
  нескольким жёстко закодированным приближениям) и больше не выходят за
  границы при необычных сочетаниях формы и количества приложений.
- Экран «Папки в app drawer» и редактирование папок остаются быстрыми
  даже при большом количестве установленных приложений и папок
  (проверено на 40 папках и 1766 приложениях).

Приватность и блокировка:

- «Заблокировать app drawer» и «Заблокировать главный экран» — теперь
  раздельные настройки: блокировка app drawer запрещает переименование,
  скрытие, смену иконки и удаление приложений из меню приложений, а
  блокировка главного экрана отдельно запрещает ещё и перемещение с
  изменением размера виджетов — закрыт обход, при котором любую из
  блокировок раньше можно было обойти через пункт «Удалить» или
  размещение/изменение размера виджета.
- PIN-код/отпечаток блокирует настройки самого лончера и любой выход в
  системные настройки (не распространяется на ярлыки и виджеты сторонних
  приложений).

Прочее:

- Результаты поиска в меню приложений показывают, в какой папке лежит
  приложение, включая полный путь для приложения во вложенной подпапке.
- Собственная запись лончера в меню приложений скрыта по умолчанию.
- Постоянное (а не одноразовое) напоминание исключить лончер из
  оптимизации батареи — одноразовое слишком легко закрыть и забыть.
- Собственный брендинг (название, иконка, `applicationId`), чтобы можно
  было ставить рядом с оригинальным Lawnchair; переведено на русский
  вручную, без CrowdIn, которым пользуется апстрим; подписанные
  CI/release workflow'ы и периодическое отслеживание апстрима (см.
  [`.github/workflows/`](.github/workflows/)).

### Скачать

Пока недоступно — ещё не вышло ни одного подписанного релиза. Раздел
заполнится после первого GitHub Release (о том, как отслеживаются
изменения апстрима, см. `docs/UPSTREAM_SYNC.md`, и workflow'ы в
[`.github/workflows/`](.github/workflows/)).

### Сборка

Нужны: JDK 17, Android SDK, а также версии NDK/CMake, закреплённые в
`build.gradle`.

```sh
git clone --recurse-submodules https://github.com/wolk-tambowskij/blownchart.git
cd blownchart
git checkout 15-dev

# Список всех доступных вариантов сборки
./gradlew tasks --group=build

# Debug-сборка (неподписанная), канал GitHub/FOSS
./gradlew assembleLawnWithQuickstepGithubDebug
```

Проект также можно открыть прямо в Android Studio и собрать/запустить
любой вариант через панель Build Variants.

Для подписанной **release**-сборки дополнительно нужны реквизиты подписи.
Создайте файл `keystore.properties` в корне репозитория (уже покрыт
`.gitignore`, никогда не коммитьте его):

```properties
storeFile=/absolute/path/to/your.keystore
storePassword=...
keyAlias=...
keyPassword=...
```

затем выполните, например, `./gradlew assembleLawnWithQuickstepGithubRelease`.
В CI та же конфигурация подписи берётся из GitHub Secrets вместо этого файла.

### Поддержать проект

BlownChart бесплатен и останется таким — донат никогда не открывает
какую-либо функцию, это противоречило бы и духу, и букве GPLv3. Если всё
же хотите поддержать — это есть прямо в приложении, `Настройки → О
приложении → Поддержать разработку`, либо напрямую:

- **PayPal:** https://www.paypal.com/donate/?business=wolk.tambowskij%40gmail.com&currency_code=USD
- **ЮMoney:** https://yoomoney.ru/to/4100119588109985

Как устроен конфиг и почему в Play-сборке скрыты прямые платёжные ссылки
— в [`docs/DONATIONS.md`](docs/DONATIONS.md).

### Лицензия и авторство

- Полные тексты лицензий: [`LICENSE`](LICENSE) (GPLv3), [`LICENSE.txt`](LICENSE.txt) (Apache-2.0).
- Границы лицензий и копирайты: [`NOTICE`](NOTICE).
- Сторонний код из других проектов: [`THIRD_PARTY.md`](THIRD_PARTY.md).
- Контрибуция в апстрим Lawnchair: [`CONTRIBUTING.md`](CONTRIBUTING.md).
