# Fix Build Errors: Update Compile SDK

The build is failing because several dependencies (including Hilt, Vico, and AndroidX libraries) require a `compileSdk` of at least 37. The project is currently targeting and compiling against SDK 35.

## Proposed Changes

### [Component Name]

#### [MODIFY] [build.gradle.kts](file:///D:/Documents/GitHub/ShadyFit/app/build.gradle.kts)
*   Update `compileSdk` from `35` to `37`.
*   Update `targetSdk` from `35` to `37` to maintain consistency with the latest Android APIs.

## Verification Plan

### Automated Tests
- Run Gradle sync.
- Execute `:app:assembleDebug` to verify that AAR metadata checks pass and the build succeeds.
