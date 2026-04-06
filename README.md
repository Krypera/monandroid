# Monandroid

Monandroid is an Android-first GUI wrapper for XMRig that focuses on simple profile management, benchmarking, diagnostics, and a clean on-device mining dashboard. It is intended for GitHub/F-Droid style distribution, not Google Play.

## What is included

- Kotlin + Jetpack Compose Android app
- Room-backed profile and benchmark storage
- Encrypted secret storage for pool passwords
- Foreground mining service with persistent notification
- Local XMRig HTTP API polling for live stats
- Offline benchmark flow with stored results
- arm64-v8a native XMRig runtime packaged with the app

## Important notes

- Google Play currently disallows apps that mine cryptocurrency on-device. The current app structure is for GitHub Releases/F-Droid style distribution.
- The project includes a configurable support-routing setting from `0%` to `100%`. It is implemented as approximate time-sliced wallet routing, not post-payout splitting.
- Benchmark mode always runs without support routing.
- This repository includes a trimmed upstream XMRig source snapshot under [`third_party/xmrig-upstream`](./third_party/xmrig-upstream) for audit/reference, and ships an Android arm64 runtime under [`native/xmrig/src/main/jniLibs/arm64-v8a`](./native/xmrig/src/main/jniLibs/arm64-v8a).

## Quick start

1. Open `Profiles` and create a profile.
2. Fill in `Profile name`, `Pool endpoint`, `Wallet address`, and pool `Password`.
3. Use `host:port` for plain TCP pools or `stratum+ssl://host:port` for TLS pools.
4. Save the profile and mark it as active.
5. Start mining from `Home`.
6. Use `Benchmark` for offline performance tests that never apply support routing.
7. Use `Profiles > Duplicate` when you want to clone a working pool setup and tweak it without overwriting the original.
8. Use `Home > Recent Logs` to inspect, copy, share, or clear the latest XMRig output without attaching `adb`.
9. Use `Profiles > Export` to save one profile as JSON, or `Profiles > Export all` to create a full app backup that also captures your settings and active-profile selection.
10. Each export lets you choose between a full backup and a share-safe backup that strips saved pool passwords and rig IDs.
11. Use `Profiles > Import file` to restore one or many profiles from a JSON export on another install or device. On a clean install, full app backups also restore saved settings.
12. Use `Benchmark > Export CSV` when you want to compare device runs outside the app.
13. Enable `Advanced Mode` in `Settings` to unlock backup pools, retry tuning, thread hints, keep-alive, and screen-off / charging behavior.
14. Use `Settings > Support and Diagnostics > Export diagnostics` to create a support snapshot with miner state, redacted recent logs and anonymized profile labels.

## Troubleshooting

- `Hashrate`, `Accepted`, and `Rejected` can stay at `0` for the first few summary polls while XMRig warms up.
- `XMRig exited with code -1` usually means the native runtime could not be started, not that the pool rejected your wallet.
- `Profile` validation now rejects whitespace-heavy endpoints, unbracketed IPv6 endpoints, duplicate backup pools, and wallet addresses with embedded spaces or line breaks.
- `Benchmark` results are stored locally until you clear them from the `Saved Results` section.
- `Home > Recent Logs` is meant to be the first debugging stop before reaching for `adb logcat`.
- `Settings > Export diagnostics` creates a shareable JSON snapshot that redacts saved pool passwords, full wallet addresses, anonymizes profile labels, and strips other known sensitive values from recent logs.
- `Profile` export/import uses a human-readable JSON file and now supports both single-profile and full app backups.
- Backup exports can include wallet, rig ID, and pool password data. Use the share-safe export option when you want to send a config without identifying fields.
- `Benchmark > Export CSV` writes your saved result history in spreadsheet-friendly format.

## Build

1. Install Android SDK Platform 35, Build Tools 35+, NDK `26.1.10909125`, and CMake `3.22.1`.
2. Ensure `local.properties` points to your SDK.
3. Optional: set `MONANDROIDO_DEV_WALLET` in `local.properties` or pass it as a Gradle property if you want a non-placeholder developer wallet during local builds.
4. Optional: copy [`keystore.properties.example`](./keystore.properties.example) to a local-only `keystore.properties` file if you want `assembleRelease` to emit a signed APK.
5. Run `./gradlew.bat assembleDebug`.

## Validation

Recommended local validation before publishing or opening a PR:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
```

If you changed Compose UI or navigation, also run:

```powershell
.\gradlew.bat assembleDebugAndroidTest
```

## Community translations

- User-facing text now lives in Android string resources across `app`, `core-data`, `core-miner`, and `native/xmrig`.
- Translation workflow and placeholder rules are documented in [`docs/translations.md`](./docs/translations.md).

## Open-source workflow

- Contributor guide: [`CONTRIBUTING.md`](./CONTRIBUTING.md)
- Security policy: [`SECURITY.md`](./SECURITY.md)
- Code of conduct: [`CODE_OF_CONDUCT.md`](./CODE_OF_CONDUCT.md)
- Release process: [`RELEASING.md`](./RELEASING.md)
- Changelog: [`CHANGELOG.md`](./CHANGELOG.md)
- Third-party notices: [`THIRD_PARTY_NOTICES.md`](./THIRD_PARTY_NOTICES.md)

## Licensing

- This repository is GPL-3.0 because it wraps and redistributes XMRig-related components.
- See [`LICENSE`](./LICENSE).

## Support Routing Behavior

- Default: `10%`
- Range: `0..100`
- Scope: global app setting
- Benchmark: always disabled
- Routing method: weighted round-robin with carry-over debt and a minimum 2-minute slice
