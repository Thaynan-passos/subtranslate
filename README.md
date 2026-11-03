<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# SubTranslate

Aplicativo Android para extrair legendas de vídeos com OCR e traduzi-las com alta precisão usando o **Google Gemini IA** (sem limitações de gírias e contexto) ou modelos locais offline do **ML Kit**.

## Motores de Tradução Híbridos

- **Google Gemini IA (Recomendado):** Traduz qualquer diálogo em inglês sem limitações, adaptando gírias, expressões idiomáticas (*phrasal verbs*) e linguagem coloquial com naturalidade em português brasileiro. Basta informar sua chave gratuita do Google AI Studio nas Configurações.
- **Fallback Automático & Modo Offline:** Se a internet oscilar ou nenhuma chave for informada, o app faz fallback transparente e instantâneo para os modelos locais do **Google ML Kit Translate**.
- **Regras de Vocabulário & Glossário:** Suporta dicionário de termos personalizados e termos que não devem ser traduzidos, respeitados tanto pela IA quanto pelo motor offline.

## Run Locally

**Prerequisites:** [Android Studio](https://developer.android.com/studio), JDK 17 or newer, and the Android SDK required by `compileSdk` in `app/build.gradle.kts`.

1. Clone the repository and open its root directory in Android Studio.
2. Allow Gradle to sync and install any requested Android SDK components.
3. Run the `app` configuration on an emulator or physical device.

For videos with small or visually varied captions, the extractor can retry short or empty OCR results with contrast-enhanced and black-and-white versions of the frame. If the selected subtitle region contains no text, it also checks the full frame. Automatic region detection samples the video at multiple points and includes text found at different horizontal and vertical positions; increase the sampling FPS to better catch brief captions, at the cost of processing time.

The OCR output is text and timing only: it does not identify the original font or reliably preserve the text's color and styling. SRT/VTT exports therefore do not reproduce the video's original typography or placement.

Extraction runs in an Android foreground service with an ongoing notification, so you can leave the app or turn off the screen while it processes. A partial wake lock keeps the CPU available during extraction and is released while processing is paused; Android may still stop long-running services or stop them when the user force-stops the app. Allow notifications to see progress and use the notification's Cancel action.

Debug builds use Android's standard debug signing configuration. Release builds require `KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD` environment variables and a keystore whose alias is `upload`.

Run unit tests from the repository root with `gradlew.bat :app:testDebugUnitTest` on Windows or `./gradlew :app:testDebugUnitTest` on macOS/Linux.
