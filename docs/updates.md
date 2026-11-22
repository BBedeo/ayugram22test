# Обновления ExFullGram

Как устроены обновления, что реально работает на телефоне, и что нужно, чтобы выложить
новую сборку.

## Что происходит на телефоне

1. При каждом `onResume` `LaunchActivity.checkAppUpdate(false, null)` проверяет
   `BaseRemoteHelper.hasMetadataChannel()`. В форке метаданных канала нет, поэтому
   управление всегда уходит в `app.exteraless.updater.GitHubUpdater`.
2. `GitHubUpdater.check(false)` уважает канал из настроек (`NaConfig.autoUpdateChannel`:
   `Off` / `Releases` / `Beta`), троттлинг 6 часов и «Пропустить версию» (`skipped_tag`).
3. Стабильный канал спрашивает `GET /repos/BBedeo/ayugram22test/releases/latest`, бета —
   список релизов. В обоих случаях берутся **только теги вида `vX.Y.Z`**: CI публикует по
   пре-релизу `ci-<sha>` на каждый коммит, а его APK собран на раннере и подписан чужим
   ключом — ставить его поверх локальной сборки нельзя.
4. Если релиз новее (`BuildVars.BUILD_VERSION_STRING`, дальше — `/compare`, затем
   `published_at`), снизу выезжает `UpdateSheet`: имя приложения и версия собираются
   локально, текст релиза чистится от имени апстрима.
5. «Обновить» качает APK в `cacheDir/updates`, затем отдаёт его системному установщику
   (`ACTION_VIEW` + `FileProvider ${applicationId}.provider`). Перед этим проверяется
   `canRequestPackageInstalls()`, иначе Android молча отказывает.
6. После замены пакета приходит `ACTION_MY_PACKAGE_REPLACED`, `AppUpdateReceiver`
   перезапускает приложение — пользователь возвращается уже в новую версию. Начиная с
   Android 10 система может заблокировать запуск активности из фона
   (`Background activity launch blocked`), тогда остаётся кнопка «Открыть» в системном
   установщике: исключения не будет, след останется только в logcat. Перезапуск
   выполняется лишь для установки, начатой из приложения: `GitHubUpdater.install()`
   ставит флаг `relaunch_after_update`, а receiver без него молчит — при ручной установке
   APK из файлового менеджера пользователь занят другим делом.

## «Применить на лету» невозможно — почему

Android не переопределяет загруженные классы: ART держит `base.apk` в памяти процесса,
новый код вступает в силу только после переустановки пакета и старта процесса заново.
Плюс подпись пакета проверяется установщиком, а `resources.arsc`/нативные библиотеки
не подменяются на ходу. Поэтому единственный путь — системный установщик; всё, что можно
сделать «на лету», это перезапустить приложение сразу после установки (и это сделано).
Живая перезагрузка **настроек** (без нового кода) по-прежнему работает через `NaConfig`,
`AppearancePresets.applyPreset()` и `PluginsController.restart()`.

## Оформление под пресет

`UpdateSheet` наследует фон от `BottomSheet`, который выбирает один из трёх режимов
(`LiquidGlassTuning.isActive()` → стекло, `SheetSurfaceMode.isM3()` → Material 3,
иначе классика). Контент листа берёт те же режимы через
`app.exteraless.appearance.SheetSurfaceMode`: акцент (M3 — синий токен Material),
радиус карточки (M3 — `AppearanceConfig.sectionRadius()`), обводку
(`outlineVariant` в M3, блик 0.6dp в стекле), радиусы кнопок 20/22/10dp и дорожку
прогресса. Пресет Material 3 раньше вообще не включал M3-хром, потому что
`BottomSheet` требовал `GlassOutlineStyle.SOLID`, а пресет ставит `HIDDEN`.

## Как выложить релиз

1. Собрать APK: `./gradlew :TMessagesProj:assembleRelease -x lint`
   (`TMessagesProj/build/outputs/apk/release/`).
2. Тег обязан содержать `X.Y.Z` — из него считается версия релиза. Версия в APK —
   `1.<commits/100>.<commits%100>` из `git rev-list --count 6ba4739f1^..HEAD`, и при
   сборке с `COMMIT_ID` к ней добавляется суффикс (`1.3.70-3f99855`). Сравниваются только
   первые три числа, поэтому тег должен совпадать с этим `X.Y.Z`: иначе приложение будет
   вечно предлагать «обновление» само себе либо не предложит его вовсе.
3. Создать релиз **не** `prerelease` и **не** `draft`, приложив ассет с `.apk` в имени:

   ```bash
   curl -sS -X POST -H "Authorization: token $GITHUB_TOKEN" \
        -H "Accept: application/vnd.github+json" \
        https://api.github.com/repos/BBedeo/ayugram22test/releases \
        -d '{"tag_name":"v1.3.70","name":"ExFullGram 1.3.70","prerelease":false,"draft":true}'
   curl -sS -X POST -H "Authorization: token $GITHUB_TOKEN" \
        -H "Content-Type: application/vnd.android.package-archive" \
        "https://uploads.github.com/repos/BBedeo/ayugram22test/releases/<id>/assets?name=ExFullGram-v1.3.70(1631).apk" \
        --data-binary @'ExFullGram-v1.3.70(1631).apk'
   ```

   Имя ассета повторяет вывод Gradle (`ExFullGram-v<version>(<code>).apk`, ABI в имени нет,
   поэтому `pickAsset()` идёт по ветке «любой `.apk`»). Затем `PATCH /releases/<id>` с
   `{"draft":false}` — до этого шага релиз не виден приложению (`draft` отфильтрован).
4. Проверить: `GET /repos/BBedeo/ayugram22test/releases/latest` отдаёт нужный тег и ассет
   `.apk`. На телефоне: «Проверить обновления» в настройках (или пункт в профиле) →
   лист обновления → «Обновить» → установщик → приложение возвращается в себя
   (если система не заблокировала фоновый запуск — тогда кнопка «Открыть» в установщике).

CI (`.github/workflows/release.yml`) собирает APK на каждый пуш и публикует пре-релиз
`ci-<sha>`. Приложению он не предлагается никогда: это сборочный артефакт, а не релиз.
`fetch-depth: 0` в workflow обязателен — без полной истории `git rev-list` падает и APK
уезжает с базовой версией Telegram (13.2.1/1261), которая выглядит новее форка.
