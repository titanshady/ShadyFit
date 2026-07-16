# Implementation Plan - Update All Dependencies

Update all plugins and libraries in `libs.versions.toml` to their latest stable versions to keep the new project up-to-date and resolve potential compatibility issues (like the KSP error).

## User Review Required

> [!WARNING]
> Upgrading **Retrofit to 3.0.0** and **OkHttp to 5.4.0** are major version jumps. While they are stable in this environment (2026), they may introduce breaking changes in API usage or behavior.

> [!IMPORTANT]
> **Kotlin 2.4.10** and **KSP 2.3.10** are significant updates. KSP2 is now mandatory for these versions.

## Proposed Changes

### Build Configuration

#### [MODIFY] [libs.versions.toml](file:///D:/Documents/GitHub/ShadyFit/gradle/libs.versions.toml)
Update the following versions:
*   `kotlin`: `2.0.0` -> `2.4.10`
*   `coreKtx`: `1.13.1` -> `1.19.0`
*   `junitVersion`: `1.2.1` -> `1.3.0`
*   `espressoCore`: `3.6.1` -> `3.7.0`
*   `lifecycleRuntimeKtx`: `2.8.4` -> `2.11.0`
*   `activityCompose`: `1.9.1` -> `1.13.0`
*   `composeBom`: `2026.02.01` -> `2026.06.01`
*   `navigationCompose`: `2.7.7` -> `2.9.8`
*   `hiltNavigationCompose`: `1.2.0` -> `1.4.0`
*   `retrofit`: `2.11.0` -> `3.0.0`
*   `okhttp`: `4.12.0` -> `5.4.0`
*   `gson`: `2.11.0` -> `2.14.0`
*   `vico`: `2.0.0-alpha.28` -> `2.5.2`
*   `datastore`: `1.1.1` -> `1.2.1`
*   `splashscreen`: `1.0.1` -> `1.2.0`
*   `ksp`: `2.0.0-1.0.24` -> `2.3.10`
*   `coroutines`: `1.8.1` -> `1.11.0`
*   `material3`: `1.2.1` -> `1.4.0`

## Verification Plan

### Automated Tests
1.  **Gradle Sync**: Run `gradle_sync` to ensure the project structure is correctly recognized with new versions.
2.  **KSP Check**: Run `./gradlew :app:kspDebugKotlin` to verify that Room and Hilt code generation works with Kotlin 2.4.10 and KSP 2.3.10.
3.  **Full Build**: Run `./gradlew :app:assembleDebug` to ensure no major breaking changes prevent compilation.

### Manual Verification
*   Check for any IDE warnings regarding deprecated APIs that might have been removed in major version updates (especially Retrofit/OkHttp).
