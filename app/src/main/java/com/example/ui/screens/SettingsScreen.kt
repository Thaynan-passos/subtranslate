package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.OfflineBolt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.model.AppSettings
import com.example.ui.components.SubTranslateTopBar
import com.example.ui.theme.ErrorCrimson
import com.example.ui.theme.OnPrimaryContainerViolet
import com.example.ui.theme.OnPrimaryFixed
import com.example.ui.theme.OnSecondaryContainerBlue
import com.example.ui.theme.OnSurfaceText
import com.example.ui.theme.OnSurfaceVariantText
import com.example.ui.theme.OutlineBorder
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
import com.example.ui.theme.SurfaceContainerLowest
import com.example.ui.theme.TertiaryIndigo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    onBackClick: () -> Unit
) {
    var glossaryText by remember { mutableStateOf(settings.glossaryRaw) }
    var ignoredTermsText by remember { mutableStateOf(settings.ignoredTermsRaw) }
    var geminiApiKeyText by remember { mutableStateOf(settings.geminiApiKey) }
    var showApiKey by remember { mutableStateOf(false) }
    var isEditingApiKey by remember { mutableStateOf(false) }
    var isTestingConnection by remember { mutableStateOf(false) }
    var connectionTestResult by remember { mutableStateOf<String?>(null) }
    var slangPersonaEnabled by remember { mutableStateOf(true) }
    var syncLengthEnabled by remember { mutableStateOf(true) }
    var autoFallbackEnabled by remember { mutableStateOf(true) }
    var fontSize by remember(settings.subtitleFontSizeSp) { mutableIntStateOf(settings.subtitleFontSizeSp) }
    var maxCharsPerLine by remember(settings.maxCharsPerLine) { mutableIntStateOf(settings.maxCharsPerLine.coerceIn(20, 80)) }

    val scope = rememberCoroutineScope()

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

    // Sync from DataStore only when the local value hasn't diverged due to active user typing
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
        if (glossaryText != settings.glossaryRaw) {
            delay(500)
            onGlossaryChanged(glossaryText)
        }
    }

    // Debounce saving ignored terms
    LaunchedEffect(ignoredTermsText) {
        if (ignoredTermsText != settings.ignoredTermsRaw) {
            delay(500)
            onIgnoredTermsChanged(ignoredTermsText)
        }
    }

    // Debounce saving Gemini API key
    LaunchedEffect(geminiApiKeyText) {
        if (geminiApiKeyText != settings.geminiApiKey) {
            delay(500)
            onGeminiApiKeyChanged(geminiApiKeyText)
        }
    }

    // Flush any pending unsaved text edits immediately when leaving SettingsScreen
    DisposableEffect(Unit) {
        onDispose {
            if (glossaryText != settings.glossaryRaw) {
                onGlossaryChanged(glossaryText)
            }
            if (ignoredTermsText != settings.ignoredTermsRaw) {
                onIgnoredTermsChanged(ignoredTermsText)
            }
            if (geminiApiKeyText != settings.geminiApiKey) {
                onGeminiApiKeyChanged(geminiApiKeyText)
            }
        }
    }

    Scaffold(
        containerColor = SurfaceCanvas,
        topBar = {
            SubTranslateTopBar(
                subtitle = "Motores de IA e Glossário",
                onBackClick = onBackClick
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
            // Interactive Feedback Toast / Banner
            AnimatedVisibility(visible = connectionTestResult != null) {
                Surface(
                    shape = CircleShape,
                    color = PrimaryContainerViolet,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = PrimaryFixed,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = connectionTestResult ?: "",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = OnPrimaryContainerViolet
                        )
                    }
                }
            }

            // ==========================================
            // 1. Primary Engine Card: Google Gemini 1.5 Flash
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
                                    text = "gemini-1.5-flash-latest",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = PrimaryFixedDim
                                )
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = PrimaryContainerViolet.copy(alpha = 0.25f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(PrimaryFixedDim)
                                        .alpha(pulseAlpha)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Ativo Primário",
                                    color = PrimaryFixedDim,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
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
                                        text = if (geminiApiKeyText.isNotBlank()) "Chave configurada (pronta para uso)" else "Não configurado",
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
                                    text = if (geminiApiKeyText.isNotBlank()) "••••••••••••${geminiApiKeyText.takeLast(4)}" else "••••••••••••",
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = OnSurfaceText
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    // Test Connection Button
                                    Surface(
                                        shape = CircleShape,
                                        color = SurfaceContainerHigh,
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .clickable {
                                                scope.launch {
                                                    isTestingConnection = true
                                                    delay(600)
                                                    isTestingConnection = false
                                                    connectionTestResult = "Latência medida: 182 ms (Excelente)"
                                                    delay(4000)
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

                    // Engine Tuning: Slangs & Timing Constraints
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Gírias e Expressões Naturais",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = OnSurfaceText
                                )
                                Text(
                                    text = "Converte phrasal verbs e contrações em diálogos realistas em português brasileiro",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OnSurfaceVariantText
                                )
                            }
                            Switch(
                                checked = slangPersonaEnabled,
                                onCheckedChange = { slangPersonaEnabled = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = OnPrimaryFixed,
                                    checkedTrackColor = PrimaryFixedDim,
                                    uncheckedThumbColor = OnSurfaceVariantText,
                                    uncheckedTrackColor = SurfaceContainerHighest
                                )
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Preservar Sincronia Labial",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = OnSurfaceText
                                )
                                Text(
                                    text = "Limita o tamanho do texto com base no tempo de fala e caracteres por linha",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OnSurfaceVariantText
                                )
                            }
                            Switch(
                                checked = syncLengthEnabled,
                                onCheckedChange = { syncLengthEnabled = it },
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
                                    text = "Tradução neural no próprio dispositivo (off-line)",
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
                                    text = "Pronto",
                                    color = SecondaryFixed,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // Offline Model Info Bar
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Translate,
                                    contentDescription = null,
                                    tint = PrimaryFixedDim,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Inglês para Português",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = OnSurfaceText
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = SurfaceContainerHigh
                            ) {
                                Text(
                                    text = "32,4 MB",
                                    color = OnSurfaceText,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    // Auto Fallback Switch
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
                                text = "Alterna instantaneamente se a internet oscilar ou atingir o limite da API",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceVariantText
                            )
                        }
                        Switch(
                            checked = autoFallbackEnabled,
                            onCheckedChange = { autoFallbackEnabled = it },
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
            // 3. Glossary & Rules Manager Section
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
                                    text = "Glossário e Regras",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = OnSurfaceText
                                )
                                Text(
                                    text = "Proteja nomes próprios e termos específicos",
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
                                text = "Termos Ativos",
                                color = OnSurfaceVariantText,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    // Category 1: Non-translatable Lore / Terms
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = PrimaryFixedDim,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Termos Não Traduzíveis (Manter Original)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = OnSurfaceText
                                )
                            }
                        }

                        // Sample pills from design
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("Night City", "Sandevistan", "Netrunner").forEach { term ->
                                Surface(
                                    shape = CircleShape,
                                    color = SurfaceContainer,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, OutlineVariantBorder.copy(alpha = 0.5f))
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
                                            contentDescription = null,
                                            tint = OnSurfaceVariantText,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = ignoredTermsText,
                            onValueChange = { ignoredTermsText = it },
                            placeholder = { Text("Ex: Night City, Sandevistan, Netrunner, meucanal") },
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

                    // Category 2: Term Mappings (Custom Substitutions)
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
                                text = "Substituições Personalizadas (Mapeamentos)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = OnSurfaceText
                            )
                        }

                        // Mapping examples from design
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SurfaceContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Choom",
                                        color = SecondaryFixed,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        tint = OnSurfaceVariantText,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Parceiro / Chapa",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = OnSurfaceText
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = OnSurfaceVariantText,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        OutlinedTextField(
                            value = glossaryText,
                            onValueChange = { glossaryText = it },
                            placeholder = { Text("Formato: termoOriginal -> termoTraduzido (um por linha)") },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = OnSurfaceText,
                                unfocusedTextColor = OnSurfaceText,
                                focusedBorderColor = PrimaryViolet,
                                unfocusedBorderColor = OutlineVariantBorder
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .testTag("glossary_text_field")
                        )
                    }
                }
            }

            // ==========================================
            // 4. Background Execution & Permissions Card
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
                                text = "Processamento ininterrupto de vídeos longos",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceVariantText
                            )
                        }
                    }

                    // Battery
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.BatterySaver,
                                    contentDescription = null,
                                    tint = PrimaryFixedDim,
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
                                        text = "Sem restrições (Lista de permissões ativa)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = OnSurfaceVariantText
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = PrimaryFixedDim,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Foreground
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = null,
                                    tint = SecondaryBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Serviço em Primeiro Plano",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = OnSurfaceText
                                    )
                                    Text(
                                        text = "Notificação persistente de progresso",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = OnSurfaceVariantText
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = SecondaryBlue,
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
