# Regras ProGuard / R8 para SubTranslate

# Manter numeração de linha para stacktraces legíveis
-keepattributes SourceFile,LineNumberTable

# Room Database: entidades, DAOs e banco
-keep class com.example.data.local.entity.** { *; }
-keep class com.example.data.local.dao.** { *; }
-keep class com.example.data.local.SubTranslateDatabase { *; }

# Modelos de domínio e serialização
-keep class com.example.domain.model.** { *; }
-keep class com.example.domain.engine.** { *; }

# Serialização JSON (org.json / Gemini API)
-keepclassmembers class * {
    @org.json.* <fields>;
}

# Suporte ao Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
