package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.AppSettings
import kotlinx.coroutines.delay

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
    var fontSize by remember(settings.subtitleFontSizeSp) { mutableIntStateOf(settings.subtitleFontSizeSp) }
    var maxCharsPerLine by remember(settings.maxCharsPerLine) { mutableIntStateOf(settings.maxCharsPerLine.coerceIn(20, 80)) }

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
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_settings), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Motor de Tradução e IA Card (Destaque Principal)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Motor de Tradução (Sem Limitações)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Escolha o motor de tradução. O Gemini IA traduz qualquer gíria, frase coloquial e diálogo em inglês com máxima naturalidade.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Seletor: Gemini vs ML Kit
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onTranslationEngineChanged("gemini") },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = settings.translationEngine == "gemini",
                                onClick = { onTranslationEngineChanged("gemini") }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = "Google Gemini IA (Alta Precisão)",
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Tradução sem limites de gírias e contexto. Requer internet e API Key gratuita.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onTranslationEngineChanged("mlkit") },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = settings.translationEngine == "mlkit",
                                onClick = { onTranslationEngineChanged("mlkit") }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = "Google ML Kit (Offline / Básico)",
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "100% offline no celular, porém com vocabulário mais literal e limitado.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Campo da Chave de API do Gemini
                    AnimatedVisibility(visible = settings.translationEngine == "gemini") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Chave da API do Google Gemini (Google AI Studio):",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )

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
                                leadingIcon = {
                                    Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("gemini_api_key_input")
                            )

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "• Gratuita: Crie sua chave no Google AI Studio (aistudio.google.com)\n• Protegida: O app salva sua chave somente localmente no aparelho.\n• Fallback Seguro: Se a internet oscilar ou a chave falhar, o app usa o ML Kit offline automaticamente.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Watermark & Noise Filter Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Block, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Filtro de Marcas d'Água e Ruídos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Filtrar marcas d'água e URLs automaticamente", fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "Ignora @handles de redes sociais, links (Telegram, sites), textos estáticos e tags de qualidade (1080p, fansub)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.filterWatermarks,
                            onCheckedChange = onFilterWatermarksChanged
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Termos ou canais adicionais a ignorar:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Insira nomes de canais, sites ou palavras fixas a ignorar (separados por vírgula ou por linha):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = ignoredTermsText,
                        onValueChange = { ignoredTermsText = it },
                        placeholder = { Text("Ex: meucanal, dorama_br, t.me/canal") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(90.dp)
                            .testTag("ignored_terms_input")
                    )
                }
            }

            // Subtitle Style & Masking
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FormatSize, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Estilo e Visualização das Legendas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Cobrir legenda original no player", fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "Aplica máscara opaca sobre o texto fixo do vídeo durante a reprodução",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.coverOriginalOverlay,
                            onCheckedChange = onCoverOverlayChanged
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Tamanho da Fonte das Legendas: ${fontSize}sp", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = fontSize.toFloat(),
                        onValueChange = {
                            fontSize = it.toInt()
                            onFontSizeChanged(fontSize)
                        },
                        valueRange = 12f..32f,
                        steps = 10,
                        modifier = Modifier.testTag("font_size_slider")
                    )

                    Text(
                        text = "Máximo de caracteres por linha na exportação: $maxCharsPerLine",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = maxCharsPerLine.toFloat(),
                        onValueChange = {
                            maxCharsPerLine = it.toInt()
                            onMaxCharsPerLineChanged(maxCharsPerLine)
                        },
                        valueRange = 20f..80f,
                        steps = 59,
                        modifier = Modifier.testTag("max_chars_per_line_slider")
                    )
                }
            }

            // Glossary and "Do not translate" list
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Glossário & Regras de Vocabulário", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = "Insira termos um por linha no formato: termoOriginal -> termoTraduzido. Deixe o destino vazio para manter o termo original. Funciona tanto com Gemini quanto com ML Kit.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = glossaryText,
                        onValueChange = {
                            glossaryText = it
                        },
                        placeholder = { Text("Ex: Fireball -> Bola de Fogo\nHP -> HP") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .testTag("glossary_input")
                    )
                }
            }

            // Legal & Copyright Notice
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Privacidade e Direitos Autorais", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.label_privacy_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.label_copyright_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
