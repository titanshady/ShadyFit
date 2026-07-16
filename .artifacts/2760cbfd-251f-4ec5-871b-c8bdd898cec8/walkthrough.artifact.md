# Walkthrough - Fix Build and Dependency Issues

This task addressed dependency resolution errors and build failures related to SDK version requirements.

## Changes Made

### Dependency Management
Updated [libs.versions.toml](file:///D:/Documents/GitHub/ShadyFit/gradle/libs.versions.toml) to use Vico version `2.5.2`. This resolved a "Failed to resolve" error for `com.patrykandpatrick.vico:core:3.2.3`, as that specific core artifact was not found in the configured repositories.

### SDK Configuration
Updated [build.gradle.kts](file:///D:/Documents/GitHub/ShadyFit/app/build.gradle.kts) to use `compileSdk = 37` and `targetSdk = 37`.
- This was necessary because several dependencies (including Hilt 1.4.0, Vico 2.5.2, and recent AndroidX libraries) require compiling against Android 15 (API 37) or higher.

## Verification Results

### Automated Tests
- **Gradle Sync**: Successful. All dependencies are resolved.
- **Project Build**: Running `:app:assembleDebug` finished successfully. AAR metadata checks now pass with the updated `compileSdk`.

> [!IMPORTANT]
> Since the `targetSdk` was updated to 37, you should eventually review the [Android 15 behavior changes](https://developer.android.com/about/versions/15/behavior-changes-all) to ensure your app remains fully compatible.
