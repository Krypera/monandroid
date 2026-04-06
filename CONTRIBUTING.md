# Contributing to Monandroid

Thanks for considering a contribution.

Monandroid is an Android-first open-source wrapper around XMRig. The project values small, reviewable changes, clear reasoning, and respectful collaboration.

## Before you start

- Read the [README](./README.md) for project scope and build requirements.
- Read the translation guide in [docs/translations.md](./docs/translations.md) before changing user-facing copy.
- Open an issue first for large changes, architectural work, or behavior changes that may affect mining defaults, security, or support-routing behavior.

## Local setup

Install the following first:

- Android SDK Platform 35
- Android Build Tools 35+
- NDK `26.1.10909125`
- CMake `3.22.1`
- JDK 17

The project reads `sdk.dir` from `local.properties` in the usual Android way.

Optional developer-wallet override methods:

- `local.properties`: `MONANDROIDO_DEV_WALLET=...`
- Gradle property: `-PMONANDROIDO_DEV_WALLET=...`

If no wallet override is provided, the project uses a placeholder build value. That is acceptable for local development and CI.

Optional release-signing methods:

- copy [`keystore.properties.example`](./keystore.properties.example) to `keystore.properties`
- or provide the same keys as Gradle properties / environment variables:
  - `MONANDROID_RELEASE_STORE_FILE`
  - `MONANDROID_RELEASE_STORE_PASSWORD`
  - `MONANDROID_RELEASE_KEY_ALIAS`
  - `MONANDROID_RELEASE_KEY_PASSWORD`

## Build and verification

Run the standard validation set before opening a PR:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
```

If you changed Compose UI, navigation, or Android resources, also run:

```powershell
.\gradlew.bat assembleDebugAndroidTest
```

## What we look for in PRs

- Clear, focused scope
- No unrelated formatting churn
- No accidental secret leakage
- Updated tests when behavior changes
- Updated docs when contributor or user workflows change
- Kept placeholders and format strings intact in resource files

## Coding guidelines

- Prefer existing project patterns over introducing new abstractions.
- Keep user-facing text in Android string resources, not embedded in Kotlin.
- Preserve placeholders exactly in translated strings: `%1$s`, `%1$d`, `%1$.1f`, `%%`
- Do not commit `local.properties`, keystores, signed APKs, or wallet secrets.
- Be careful with mining defaults. Changes that affect wallet routing, support-routing behavior, runtime constraints, or benchmark semantics should be called out explicitly in the PR description.

## Pull request checklist

- [ ] The change is scoped and explained clearly
- [ ] `testDebugUnitTest` passes
- [ ] `lintDebug` passes
- [ ] `assembleDebug` passes
- [ ] `assembleRelease` passes
- [ ] Docs were updated if needed
- [ ] No secrets or local signing material were added

## Translations

Translation contributions are welcome.

- Add locale-specific resource files under `values-xx`
- Keep string names unchanged
- Translate only values, not resource IDs
- Preserve technical tokens such as `rx/0`, `rx/wow`, `host:port`, and `stratum+ssl://`

See [docs/translations.md](./docs/translations.md) for the full guide.
