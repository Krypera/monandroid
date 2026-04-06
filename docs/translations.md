# Translations

Monandroid keeps user-facing copy in Android resource files so community translations can be added without touching Kotlin source.

## Where strings live

- App UI: [`app/src/main/res/values/strings.xml`](../app/src/main/res/values/strings.xml)
- Shared profile/data validation: [`core-data/src/main/res/values/strings.xml`](../core-data/src/main/res/values/strings.xml)
- Miner notification and runtime state copy: [`core-miner/src/main/res/values/strings.xml`](../core-miner/src/main/res/values/strings.xml)
- Native XMRig startup/runtime errors: [`native/xmrig/src/main/res/values/strings.xml`](../native/xmrig/src/main/res/values/strings.xml)

## Adding a language

1. Create the matching `values-xx` folder in the module you want to translate.
2. Copy the base `strings.xml` from that module into the new folder.
3. Translate only the text values.
4. Leave string names unchanged.
5. Keep placeholders such as `%1$s`, `%2$d`, and `%1$.1f` intact.
6. Do not translate entries marked with `translatable="false"`.

## Translation rules

- Prefer natural phrasing over literal word-by-word translation.
- Keep technical tokens like `rx/0`, `rx/wow`, `host:port`, and `stratum+ssl://` unchanged.
- Do not change JSON keys, route names, or internal file names in code.
- Preserve punctuation that is part of validation or error guidance.
- If a string includes a quoted dynamic value such as `'%1$s'`, keep the quotes unless your locale strongly requires a different style.

## Placeholder guide

- `%1$s`: text such as a profile name or endpoint
- `%1$d`: integer number
- `%1$.1f`: decimal number with one fractional digit
- `%%`: literal percent sign

## Before opening a PR

Run:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
```

If you changed Android UI copy, it is also helpful to run:

```powershell
.\gradlew.bat assembleDebugAndroidTest
```
