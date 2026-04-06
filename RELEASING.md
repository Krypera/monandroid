# Releasing Monandroid

This document describes the minimum release process for GitHub Releases or F-Droid style distribution.

## Before release

1. Update `versionCode` and `versionName` in [app/build.gradle.kts](./app/build.gradle.kts).
2. Review [CHANGELOG.md](./CHANGELOG.md) and move relevant items out of `Unreleased`.
3. Verify the developer wallet configuration is intentional for the build.
4. Run the standard validation suite:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
```

5. If the UI or navigation changed, also run:

```powershell
.\gradlew.bat assembleDebugAndroidTest
```

## Signing

The repository intentionally does not commit signing keys or keystore configuration.

Recommended local setup:

- copy [`keystore.properties.example`](./keystore.properties.example) to a private `keystore.properties` file and fill in the values
- create a release keystore outside the repository
- provide signing information through local-only Gradle configuration or environment variables
- never commit `keystore.properties`, `*.jks`, `*.keystore`, or passwords

## Release artifacts

Typical outputs:

- unsigned release APK: `app/build/outputs/apk/release/app-release-unsigned.apk`
- R8 mapping: `app/build/outputs/mapping/release/mapping.txt`

Store mapping files for every public release so crash deobfuscation remains possible.

## Release notes

Release notes should include:

- headline user-visible changes
- fixed bugs
- known limitations
- any mining/runtime behavior changes
- any support-routing behavior changes

## Final checklist

- [ ] version bumped
- [ ] changelog updated
- [ ] tests and builds passed
- [ ] signed artifact produced
- [ ] mapping file archived
- [ ] release notes written
- [ ] no local secrets committed
