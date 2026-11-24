package com.example.ui.screens

import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.OfflineBolt
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.util.NotificationHelper
import com.example.domain.model.AppSettings
import com.example.ui.components.SubTranslateTopBar
import com.example.ui.theme.OnPrimaryContainerViolet
import com.example.ui.theme.OnPrimaryFixed
import com.example.ui.theme.OnSurfaceText
import com.example.ui.theme.OnSurfaceVariantText
import com.example.ui.theme.OutlineVariantBorder
import com.example.ui.theme.PrimaryContainerViolet
import com.example.ui.theme.PrimaryFixed
import com.example.ui.theme.PrimaryFixedDim
import com.example.ui.theme.PrimaryViolet
import com.example.ui.theme.SecondaryBlue
import com.example.ui.theme.SecondaryContainerBlue
import com.example.ui.theme.SecondaryFixed
import com.example.ui.theme.SuccessEmerald
import com.example.ui.theme.SurfaceCanvas
import com.example.ui.theme.SurfaceContainer
import com.example.ui.theme.SurfaceContainerHigh
import com.example.ui.theme.SurfaceContainerHighest
import com.example.ui.theme.SurfaceContainerLow
import com.example.ui.theme.WarningAmber
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val AVAILABLE_GEMINI_MODELS = listOf(
    "gemini-2.5-flash" to "2.5 Flash",
    "gemini-2.5-flash-lite" to "2.5 Flash-Lite",
    "gemini-2.5-pro" to "2.5 Pro"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onCoverOverlayChanged: (Boolean) -> Unit,
    onFontSizeChanged: (Int) -> Unit,
    onMaxCharsPerLineChanged: (Int) -> Unit,
    onGlossaryChanged: (String) -> Unit,
    onFilterWatermarksChanged: (Boolean) -> Unit,
    onIgnoredTermsChanged: (String) -> Unit,
    onTranslationEngineChanged: (String) -> Unit,
    onGeminiApiKeyChanged: (String) -> Unit,
    onGeminiModelChanged: (String) -> Unit,
    onBackClick: () -> Unit,
    onAutoFallbackChanged: (Boolean) -> Unit = {},
    onTestGeminiConnection: (suspend (apiKey: String, model: String) -> Result<Long>)? = null
) {
    val context = LocalContext.current
    var glossaryText by remember { mutableStateOf(settings.glossaryRaw) }
    var ignoredTermsText by remember { mutableStateOf(settings.ignoredTermsRaw) }
    var geminiApiKeyText by remember { mutableStateOf(settings.geminiApiKey) }
    var showApiKey by remember { mutableStateOf(false) }
    var isEditingApiKey by remember { mutableStateOf(false) }
    var isTestingConnection by remember { mutableStateOf(false) }
    var connectionTestResult by remember { mutableStateOf<String?>(null) }
    var connectionTestSuccess by remember { mutableStateOf<Boolean?>(null) }
    var fontSize by remember(settings.subtitleFontSizeSp) { mutableIntStateOf(settings.subtitleFontSizeSp) }
    var maxCharsPerLine by remember(settings.maxCharsPerLine) { mutableIntStateOf(settings.maxCharsPerLine.coerceIn(20, 80)) }

    val latestSettings by rememberUpdatedState(settings)
    val latestGlossaryText by rememberUpdatedState(glossaryText)
    val latestIgnoredTermsText by rememberUpdatedState(ignoredTermsText)
    val latestGeminiApiKeyText by rememberUpdatedState(geminiApiKeyText)
    val latestOnGlossaryChanged by rememberUpdatedState(onGlossaryChanged)
    val latestOnIgnoredTermsChanged by rememberUpdatedState(onIgnoredTermsChanged)
    val latestOnGeminiApiKeyChanged by rememberUpdatedState(onGeminiApiKeyChanged)

    val scope = rememberCoroutineScope()

    val isGeminiSelected = settings.translationEngine == "gemini"
    val activeModelTag = if (isGeminiSelected && settings.geminiApiKey.isNotBlank()) {
        settings.geminiModel
    } else {
        "ML Kit Offline"
    }

    val isBatteryUnrestricted = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        } else {
            true
        }
    }
    val hasNotificationsPermission = remember(context) {
        NotificationHelper.hasNotificationPermission(context)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // Sync from DataStore only when local field is empty (initial async load)
    LaunchedEffect(settings.glossaryRaw) {
        if (glossaryText.isEmpty() && settings.glossaryRaw.isNotEmpty()) {
            glossaryText = settings.glossaryRaw
        }
    }
    LaunchedEffect(settings.ignoredTermsRaw) {
        if (ignoredTermsText.isEmpty() && settings.ignoredTermsRaw.isNotEmpty()) {
            ignoredTermsText = settings.ignoredTermsRaw
        }
    }
    LaunchedEffect(settings.geminiApiKey) {
        if (geminiApiKeyText.isEmpty() && settings.geminiApiKey.isNotEmpty()) {
            geminiApiKeyText = settings.geminiApiKey
        }
    }

    // Debounce saving glossary text
    LaunchedEffect(glossaryText) {
        if (glossaryText != latestSettings.glossaryRaw) {
            delay(500)
            latestOnGlossaryChanged(glossaryText)
        }
    }

    // Debounce saving ignored terms
    LaunchedEffect(ignoredTermsText) {
        if (ignoredTermsText != latestSettings.ignoredTermsRaw) {
            delay(500)
            latestOnIgnoredTermsChanged(ignoredTermsText)
        }
    }

    // Debounce saving Gemini API key
    LaunchedEffect(geminiApiKeyText) {
        if (geminiApiKeyText != latestSettings.geminiApiKey) {
            delay(500)
            latestOnGeminiApiKeyChanged(geminiApiKeyText)
        }
    }

    // Flush any pending unsaved text edits immediately when leaving SettingsScreen
    DisposableEffect(Unit) {
        onDispose {
            if (latestGlossaryText != latestSettings.glossaryRaw) {
                latestOnGlossaryChanged(latestGlossaryText)
            }
            if (latestIgnoredTermsText != latestSettings.ignoredTermsRaw) {
                latestOnIgnoredTermsChanged(latestIgnoredTermsText)
            }
            if (latestGeminiApiKeyText != latestSettings.geminiApiKey) {
                latestOnGeminiApiKeyChanged(latestGeminiApiKeyText)
            }
        }
    }

    Scaffold(
        containerColor = SurfaceCanvas,
        topBar = {
            SubTranslateTopBar(
                subtitle = "Motores de IA e Glossário",
                onBackClick = onBackClick,
                modelTag = activeModelTag
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Interactive Feedback Banner (Real connection test result)
            AnimatedVisibility(visible = connectionTestResult != null) {
                val isOk = connectionTestSuccess == true
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isOk) PrimaryContainerViolet else MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isOk) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isOk) SuccessEmerald else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = connectionTestResult ?: "",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isOk) OnPrimaryContainerViolet else MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // ==========================================
            // 0. Engine Selection Card
            // ==========================================
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = SurfaceContainerLow,
                border = androidx.compose.foundation.BorderStroke(1.dp, OutlineVariantBorder.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Motor de Tradução Preferido",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceText
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = isGeminiSelected,
                            onClick = { onTranslationEngineChanged("gemini") },
                            label = { Text("Google Gemini IA") },
                            leadingIcon = {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            shape = CircleShape,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryContainerViolet,
                                selectedLabelColor = OnPrimaryContainerViolet,
                                containerColor = SurfaceContainerHigh,
                                labelColor = OnSurfaceVariantText
                            ),
                            border = null,
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = !isGeminiSelected,
                            onClick = { onTranslationEngineChanged("mlkit") },
                            label = { Text("ML Kit Offline") },
                            leadingIcon = {
                                Icon(Icons.Default.OfflineBolt, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            shape = CircleShape,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryContainerViolet,
                                selectedLabelColor = OnPrimaryContainerViolet,
                                containerColor = SurfaceContainerHigh,
                                labelColor = OnSurfaceVariantText
                            ),
                            border = null,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // ==========================================
            // 1. Primary Engine Card: Google Gemini IA
            // ==========================================
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = SurfaceContainerLow,
                border = androidx.compose.foundation.BorderStroke(1.dp, OutlineVariantBorder.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryContainerViolet.copy(alpha = 0.4f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = PrimaryFixedDim,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Google Gemini IA",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = OnSurfaceText
                                )
                                Text(
                                    text = settings.geminiModel,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = PrimaryFixedDim
                                )
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = if (isGeminiSelected) PrimaryContainerViolet.copy(alpha = 0.25f) else SurfaceContainerHigh
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (isGeminiSelected) PrimaryFixedDim else OnSurfaceVariantText)
                                        .alpha(if (isGeminiSelected) pulseAlpha else 1f)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isGeminiSelected) "Ativo" else "Inativo",
                                    color = if (isGeminiSelected) PrimaryFixedDim else OnSurfaceVariantText,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Model Selector Chips
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Modelo Gemini",
                            style = MaterialTheme.typography.labelLarge,
                            color = OnSurfaceVariantText
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AVAILABLE_GEMINI_MODELS.forEach { (modelId, label) ->
                                FilterChip(
                                    selected = settings.geminiModel == modelId,
                                    onClick = { onGeminiModelChanged(modelId) },
                                    label = { Text(label) },
                                    shape = CircleShape,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = PrimaryContainerViolet,
                                        selectedLabelColor = OnPrimaryContainerViolet,
                                        containerColor = SurfaceContainerHigh,
                                        labelColor = OnSurfaceVariantText
                                    ),
                                    border = null
                                )
                            }
                        }
                    }

                    // API Key Status Row / Card
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = SurfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Key,
                                        contentDescription = null,
                                        tint = SecondaryBlue,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Chave Google AI Studio",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = OnSurfaceVariantText
                                    )
                                }

                                Surface(
                                    shape = CircleShape,
                                    color = SecondaryContainerBlue.copy(alpha = 0.5f)
                                ) {
                                    Text(
                                        text = if (geminiApiKeyText.isNotBlank()) "Configurada" else "Não configurada",
                                        color = SecondaryBlue,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (geminiApiKeyText.isNotBlank()) {
                                        "••••••••••••${geminiApiKeyText.takeLast(4)}"
                                    } else {
                                        "Nenhuma chave informada"
                                    },
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = OnSurfaceText
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    // Real Connection Test Button
                                    Surface(
                                        shape = CircleShape,
                                        color = SurfaceContainerHigh,
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .clickable(enabled = !isTestingConnection) {
                                                scope.launch {
                                                    isTestingConnection = true
                                                    connectionTestResult = null
                                                    if (geminiApiKeyText.trim() != latestSettings.geminiApiKey) {
                                                        latestOnGeminiApiKeyChanged(geminiApiKeyText)
                                                    }
                                                    val testResult = onTestGeminiConnection?.invoke(
                                                        geminiApiKeyText,
                                                        settings.geminiModel
                                                    ) ?: Result.failure(
                                                        IllegalStateException("Teste de conexão indisponível neste contexto.")
                                                    )
                                                    isTestingConnection = false
                                                    testResult.onSuccess { latencyMs ->
                                                        connectionTestSuccess = true
                                                        connectionTestResult = "Conexão confirmada (${settings.geminiModel}): ${latencyMs} ms"
                                                    }.onFailure { err ->
                                                        connectionTestSuccess = false
                                                        connectionTestResult = err.message ?: "Falha ao testar conexão com o Gemini."
                                                    }
                                                    delay(5000)
                                                    connectionTestResult = null
                                                }
                                            }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            if (isTestingConnection) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(14.dp),
                                                    strokeWidth = 2.dp,
                                                    color = SecondaryBlue
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Default.NetworkPing,
                                                    contentDescription = null,
                                                    tint = SecondaryBlue,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Testar",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = SecondaryBlue
                                            )
                                        }
                                    }

                                    // Toggle edit API key
                                    IconButton(
                                        onClick = { isEditingApiKey = !isEditingApiKey },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(SurfaceContainerHigh)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Editar Chave",
                                            tint = OnSurfaceVariantText,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            AnimatedVisibility(visible = isEditingApiKey) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = geminiApiKeyText,
                                        onValueChange = { geminiApiKeyText = it },
                                        placeholder = { Text("Ex: AIzaSy...") },
                                        visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                                        trailingIcon = {
                                            IconButton(onClick = { showApiKey = !showApiKey }) {
                                                Icon(
                                                    imageVector = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                    contentDescription = if (showApiKey) "Ocultar" else "Exibir"
                                                )
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = OnSurfaceText,
                                            unfocusedTextColor = OnSurfaceText,
                                            focusedBorderColor = PrimaryViolet,
                                            unfocusedBorderColor = OutlineVariantBorder
                                        ),
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("gemini_api_key_input")
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ==========================================
            // 2. Fallback Engine Card: Google ML Kit Tradução
            // ==========================================
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = SurfaceContainerLow,
                border = androidx.compose.foundation.BorderStroke(1.dp, OutlineVariantBorder.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(SecondaryContainerBlue.copy(alpha = 0.5f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OfflineBolt,
                                    contentDescription = null,
                                    tint = SecondaryBlue,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Google ML Kit Tradução",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = OnSurfaceText
                                )
                                Text(
                                    text = "Modelos baixados sob demanda por par de idiomas (off-line)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OnSurfaceVariantText
                                )
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = SurfaceContainerHighest
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DownloadDone,
                                    contentDescription = null,
                                    tint = SecondaryBlue,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Offline",
                                    color = SecondaryFixed,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // Auto Fallback Switch (real setting)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Fallback Automático",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = OnSurfaceText
                            )
                            Text(
                                text = "Alterna para o ML Kit local se a internet oscilar ou atingir a cota da API",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceVariantText
                            )
                        }
                        Switch(
                            checked = settings.autoFallbackToMlKit,
                            onCheckedChange = onAutoFallbackChanged,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = OnPrimaryFixed,
                                checkedTrackColor = PrimaryFixedDim,
                                uncheckedThumbColor = OnSurfaceVariantText,
                                uncheckedTrackColor = SurfaceContainerHighest
                            )
                        )
                    }
                }
            }

            // ==========================================
            // 3. Glossary & Watermark Filter Section
            // ==========================================
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = SurfaceContainerLow,
                border = androidx.compose.foundation.BorderStroke(1.dp, OutlineVariantBorder.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF3542C2).copy(alpha = 0.35f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Spellcheck,
                                    contentDescription = null,
                                    tint = Color(0xFFBDC2FF),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Glossário e Termos Protegidos",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = OnSurfaceText
                                )
                                Text(
                                    text = "Defina traduções fixas ou proteja nomes próprios contra tradução",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OnSurfaceVariantText
                                )
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = SurfaceContainerHigh
                        ) {
                            Text(
                                text = "${settings.glossaryTerms.size} regras",
                                color = OnSurfaceVariantText,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    // Glossário personalizado e termos não traduzíveis (termo -> termo)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = null,
                                tint = SecondaryBlue,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Mapeamentos e Termos Não Traduzíveis",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = OnSurfaceText
                            )
                        }
                        Text(
                            text = "Use \"original -> tradução\" (ou \"termo -> termo\" para manter o nome original sem traduzir), um por linha.",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceVariantText
                        )

                        if (settings.glossaryTerms.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                settings.glossaryTerms.entries.take(8).forEach { (src, tgt) ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = SurfaceContainer,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text(
                                                    text = src,
                                                    color = SecondaryFixed,
                                                    fontSize = 12.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Icon(
                                                    imageVector = if (src.equals(tgt, ignoreCase = true)) Icons.Default.Lock else Icons.AutoMirrored.Filled.ArrowForward,
                                                    contentDescription = null,
                                                    tint = OnSurfaceVariantText,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = if (tgt.isBlank() || src.equals(tgt, ignoreCase = true)) "Manter original" else tgt,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = OnSurfaceText,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    val updated = glossaryText.lines()
                                                        .filter { line ->
                                                            val parsed = SettingsRepositoryForUi.extractSourceKey(line)
                                                            !parsed.equals(src, ignoreCase = true)
                                                        }
                                                        .joinToString("\n")
                                                    glossaryText = updated
                                                    onGlossaryChanged(updated)
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Remover regra",
                                                    tint = OnSurfaceVariantText,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = glossaryText,
                            onValueChange = { glossaryText = it },
                            placeholder = { Text("Ex:\nNight City -> Night City\nChoom -> Parceiro") },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = OnSurfaceText,
                                unfocusedTextColor = OnSurfaceText,
                                focusedBorderColor = PrimaryViolet,
                                unfocusedBorderColor = OutlineVariantBorder
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(104.dp)
                                .testTag("glossary_text_field")
                        )
                    }

                    // Filtro de Marcas d'Água / Termos Removidos das Legendas
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FilterAlt,
                                    contentDescription = null,
                                    tint = PrimaryFixedDim,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = "Filtro de Marcas d'Água (Remover)",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = OnSurfaceText
                                    )
                                    Text(
                                        text = "Remove @perfis, URLs e termos abaixo antes de traduzir",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = OnSurfaceVariantText
                                    )
                                }
                            }
                            Switch(
                                checked = settings.filterWatermarks,
                                onCheckedChange = onFilterWatermarksChanged,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = OnPrimaryFixed,
                                    checkedTrackColor = PrimaryFixedDim,
                                    uncheckedThumbColor = OnSurfaceVariantText,
                                    uncheckedTrackColor = SurfaceContainerHighest
                                )
                            )
                        }

                        if (settings.ignoredTerms.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                settings.ignoredTerms.forEach { term ->
                                    Surface(
                                        shape = CircleShape,
                                        color = SurfaceContainer,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, OutlineVariantBorder.copy(alpha = 0.5f)),
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .clickable {
                                                val updated = settings.ignoredTerms
                                                    .filterNot { it.equals(term, ignoreCase = true) }
                                                    .joinToString(", ")
                                                ignoredTermsText = updated
                                                onIgnoredTermsChanged(updated)
                                            }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = term,
                                                color = OnSurfaceText,
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Remover termo ignorado",
                                                tint = OnSurfaceVariantText,
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = ignoredTermsText,
                            onValueChange = { ignoredTermsText = it },
                            placeholder = { Text("Ex: @canal, meucanal, fansub (separados por vírgula)") },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = OnSurfaceText,
                                unfocusedTextColor = OnSurfaceText,
                                focusedBorderColor = PrimaryViolet,
                                unfocusedBorderColor = OutlineVariantBorder
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .testTag("ignored_terms_input")
                        )
                    }
                }
            }

            // ==========================================
            // 4. Background Execution & Permissions Card (Real Device Status)
            // ==========================================
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = SurfaceContainerLow,
                border = androidx.compose.foundation.BorderStroke(1.dp, OutlineVariantBorder.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(SurfaceContainerHighest),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SettingsSuggest,
                                contentDescription = null,
                                tint = OnSurfaceText,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Execução em Segundo Plano",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = OnSurfaceText
                            )
                            Text(
                                text = "Status real das permissões no dispositivo",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceVariantText
                            )
                        }
                    }

                    // Battery status
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BatterySaver,
                                    contentDescription = null,
                                    tint = if (isBatteryUnrestricted) SuccessEmerald else WarningAmber,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Otimização de Bateria",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = OnSurfaceText
                                    )
                                    Text(
                                        text = if (isBatteryUnrestricted) {
                                            "Sem restrições de bateria para o app"
                                        } else {
                                            "Restrição padrão do sistema ativa (pode limitar processos longos)"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = OnSurfaceVariantText
                                    )
                                }
                            }
                            Icon(
                                imageVector = if (isBatteryUnrestricted) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (isBatteryUnrestricted) SuccessEmerald else WarningAmber,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Notification / Foreground status
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = null,
                                    tint = if (hasNotificationsPermission) SecondaryBlue else WarningAmber,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Notificação de Progresso",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = OnSurfaceText
                                    )
                                    Text(
                                        text = if (hasNotificationsPermission) {
                                            "Permissão concedida para o serviço em primeiro plano"
                                        } else {
                                            "Permissão de notificações não concedida"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = OnSurfaceVariantText
                                    )
                                }
                            }
                            Icon(
                                imageVector = if (hasNotificationsPermission) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (hasNotificationsPermission) SecondaryBlue else WarningAmber,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // ==========================================
            // 5. Subtitle Style & Masking Card
            // ==========================================
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = SurfaceContainerLow,
                border = androidx.compose.foundation.BorderStroke(1.dp, OutlineVariantBorder.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FormatSize, contentDescription = null, tint = PrimaryFixedDim)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Estilo e Visualização das Legendas",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = OnSurfaceText
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Cobrir legenda original no player",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = OnSurfaceText
                            )
                            Text(
                                text = "Aplica máscara opaca sobre o texto fixo do vídeo durante a reprodução",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceVariantText
                            )
                        }
                        Switch(
                            checked = settings.coverOriginalOverlay,
                            onCheckedChange = onCoverOverlayChanged,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = OnPrimaryFixed,
                                checkedTrackColor = PrimaryFixedDim,
                                uncheckedThumbColor = OnSurfaceVariantText,
                                uncheckedTrackColor = SurfaceContainerHighest
                            )
                        )
                    }

                    Text(
                        text = "Tamanho da Fonte das Legendas: ${fontSize}sp",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceText
                    )
                    Slider(
                        value = fontSize.toFloat(),
                        onValueChange = {
                            fontSize = it.toInt()
                            onFontSizeChanged(fontSize)
                        },
                        valueRange = 12f..32f,
                        steps = 10,
                        colors = SliderDefaults.colors(
                            thumbColor = PrimaryViolet,
                            activeTrackColor = PrimaryViolet,
                            inactiveTrackColor = SurfaceContainerHighest
                        ),
                        modifier = Modifier.testTag("font_size_slider")
                    )

                    Text(
                        text = "Máximo de caracteres por linha: $maxCharsPerLine",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceText
                    )
                    Slider(
                        value = maxCharsPerLine.toFloat(),
                        onValueChange = {
                            maxCharsPerLine = it.toInt()
                            onMaxCharsPerLineChanged(maxCharsPerLine)
                        },
                        valueRange = 20f..80f,
                        steps = 59,
                        colors = SliderDefaults.colors(
                            thumbColor = PrimaryViolet,
                            activeTrackColor = PrimaryViolet,
                            inactiveTrackColor = SurfaceContainerHighest
                        ),
                        modifier = Modifier.testTag("max_chars_per_line_slider")
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private object SettingsRepositoryForUi {
    fun extractSourceKey(rawLine: String): String {
        val trimmed = rawLine.trim()
        val sepIndex = when {
            trimmed.contains("->") -> trimmed.indexOf("->")
            trimmed.contains("=") -> trimmed.indexOf("=")
            else -> -1
        }
        return if (sepIndex >= 0) trimmed.substring(0, sepIndex).trim() else ""
    }
}
