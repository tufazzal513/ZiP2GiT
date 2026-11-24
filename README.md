# Zip2Git

Push a project folder, a ZIP archive, or a set of files straight into a GitHub
repository from an Android phone, using a Personal Access Token.

## What it does

1. Paste a repository URL such as `https://github.com/owner/repo` and a GitHub
   token. The token is verified against `GET /user` before anything else.
2. Pick a source: a `.zip` archive, a whole folder, or individual files.
3. The app compares the selection against the remote branch and shows what would
   be added, modified or deleted.
4. Push. Files are uploaded as Git blobs, assembled into a tree, committed, and
   the branch ref is created or fast-forwarded.

Optionally enable **Mirror Sync** to make the branch an exact copy of the
selection, which means files missing locally are deleted remotely.

## Behaviour worth knowing

- Limits mirror the original app: at most **3000 files** and **120 MB**
  uncompressed. ZIP entries that would escape the target directory
  (`../`, absolute paths) abort the import.
- Dot-files and dot-directories are skipped when a folder is picked, so `.git`
  and `.gradle` are never uploaded.
- When a ZIP has a single common top-level folder, that folder is stripped so
  `myproject-main/src/Main.kt` becomes `src/Main.kt`.
- Pushing under `.github/workflows` needs a token with the `workflow` scope; the
  app reports that explicitly instead of failing silently.
- The token is stored in `EncryptedSharedPreferences` and excluded from cloud
  backup and device transfer.

## Building

The project is a standard Gradle Android app (Kotlin 1.9.22, AGP 8.2.2,
compileSdk 34, minSdk 26, JDK 17).

No `gradle-wrapper.jar` is committed on purpose, so every tracked file stays
text-only. Build with an installed Gradle instead of `./gradlew`:

```sh
gradle :app:assembleDebug
```

`gradle/wrapper/gradle-wrapper.properties` records the intended Gradle version
(8.2). If you prefer the wrapper, run `gradle wrapper` once locally to generate
the jar.

### Continuous integration

`.github/workflows/build.yml` builds the debug APK on every push to `main` and
on demand via `workflow_dispatch`. The APK is published as the **app-debug-apk**
artifact: open the run, then **Artifacts → app-debug-apk**.

The workflow relies on the Android SDK that the `ubuntu-latest` runner image
already provides and invokes plain `gradle`.

## Layout

```
app/src/main/java/com/zip2git/
  MainActivity.kt          activity, SAF pickers, screen dispatch
  Zip2GitApp.kt            application, lazy stores
  data/local/              encrypted token store, theme, history
  data/model/              domain models and GitHub DTOs
  data/remote/             Retrofit API, auth interceptor, repository wrapper
  domain/                  ZIP/SAF sources, path safety, diff engine, sync engine
  ui/                      state, view model, theme, components, screens
```

## Security note

A classic Personal Access Token grants broad access to your account. Use the
narrowest scope that works (`repo`, plus `workflow` when pushing CI files),
store nothing in public places, and revoke the token when you are done with it.
