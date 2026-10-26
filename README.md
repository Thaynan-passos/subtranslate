<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# SubTranslate

Aplicativo Android para extrair legendas de vídeos com OCR e traduzi-las usando os modelos locais do ML Kit.

## Run Locally

**Prerequisites:** [Android Studio](https://developer.android.com/studio), JDK 17 or newer, and the Android SDK required by `compileSdk` in `app/build.gradle.kts`.

1. Clone the repository and open its root directory in Android Studio.
2. Allow Gradle to sync and install any requested Android SDK components.
3. Run the `app` configuration on an emulator or physical device.

OCR and translation run on-device with ML Kit; no Gemini API key or `.env` file is required. The initial translation may require an internet connection to download language models.

Debug builds use Android's standard debug signing configuration. Release builds require `KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD` environment variables and a keystore whose alias is `upload`.

Run unit tests from the repository root with `gradlew.bat :app:testDebugUnitTest` on Windows or `./gradlew :app:testDebugUnitTest` on macOS/Linux.
