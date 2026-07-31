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

<!-- TODO: describe recursive/nested folders in the app drawer -->
<!-- TODO: describe the settings lock / screen-lock feature -->
<!-- TODO: describe folder backup/restore -->
<!-- TODO: describe icon shape changes -->
<!-- TODO: describe branding/applicationId changes -->
<!-- TODO: describe any other fork-specific fixes and features -->

### Download

Not yet available — release packaging is being set up. This section will
be filled in once signed GitHub Releases are live (see
[`docs/UPSTREAM_SYNC.md`](docs/UPSTREAM_SYNC.md) once published, and the
CI/release workflows under [`.github/workflows/`](.github/workflows/)).

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

Donations are voluntary — see [`docs/DONATIONS.md`](docs/DONATIONS.md)
*(coming soon)* for supported channels. No feature of BlownChart is ever
gated behind a payment; that would contradict both the spirit and the
letter of the GPLv3.

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

<!-- TODO: описать рекурсивные/вложенные папки в app drawer -->
<!-- TODO: описать блокировку настроек / экрана блокировки -->
<!-- TODO: описать резервное копирование/восстановление папок -->
<!-- TODO: описать изменения формы иконок -->
<!-- TODO: описать смену брендинга/applicationId -->
<!-- TODO: описать прочие форк-специфичные фиксы и фичи -->

### Скачать

Пока недоступно — настраивается сборка релизов. Раздел заполнится после
запуска подписанных GitHub Releases (см. `docs/UPSTREAM_SYNC.md`, когда
появится, и workflow'ы в [`.github/workflows/`](.github/workflows/)).

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

Донаты — исключительно добровольные, см. [`docs/DONATIONS.md`](docs/DONATIONS.md)
*(скоро)* за списком способов. Ни одна функция BlownChart никогда не
скрывается за оплатой — это противоречило бы и духу, и букве GPLv3.

### Лицензия и авторство

- Полные тексты лицензий: [`LICENSE`](LICENSE) (GPLv3), [`LICENSE.txt`](LICENSE.txt) (Apache-2.0).
- Границы лицензий и копирайты: [`NOTICE`](NOTICE).
- Сторонний код из других проектов: [`THIRD_PARTY.md`](THIRD_PARTY.md).
- Контрибуция в апстрим Lawnchair: [`CONTRIBUTING.md`](CONTRIBUTING.md).
