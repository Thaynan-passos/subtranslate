package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// ==========================================
// SubTranslate M3 Dark Design System Tokens
// ==========================================

// Base Surface Architecture (OLED Ready)
val SurfaceCanvas = Color(0xFF0F141B)
val SurfaceDim = Color(0xFF0F141B)
val SurfaceBright = Color(0xFF343941)
val SurfaceContainerLowest = Color(0xFF090F15)
val SurfaceContainerLow = Color(0xFF171C23)
val SurfaceContainer = Color(0xFF1B2027)
val SurfaceContainerHigh = Color(0xFF252A32)
val SurfaceContainerHighest = Color(0xFF30353D)

// Dynamic Brand & Gemini Accents
val PrimaryViolet = Color(0xFFD0BCFF)
val PrimaryLight = Color(0xFFE9DDFF)
val OnPrimaryViolet = Color(0xFF37265E)
val PrimaryContainerViolet = Color(0xFF4F378B)
val OnPrimaryContainerViolet = Color(0xFFEADDFF)
val PrimaryFixedDim = Color(0xFFD0BCFF)
val PrimaryFixed = Color(0xFFE9DDFF)
val OnPrimaryFixed = Color(0xFF210F48)

// Technical Sky Blue Accents (Playhead, Scrubbing, Timeline)
val SecondaryBlue = Color(0xFF8AB4F8)
val SecondaryBlueLight = Color(0xFFA8C8FF)
val OnSecondaryBlue = Color(0xFF003061)
val SecondaryContainerBlue = Color(0xFF114784)
val OnSecondaryContainerBlue = Color(0xFF8CB6FB)
val SecondaryFixed = Color(0xFFD5E3FF)
val SecondaryFixedDim = Color(0xFFA8C8FF)

// Auxiliary & Indigo Accents
val TertiaryIndigo = Color(0xFF6875F5)
val TertiaryIndigoLight = Color(0xFFDFE0FF)
val OnTertiaryIndigo = Color(0xFF0111A2)
val TertiaryContainerIndigo = Color(0xFF3542C2)
val OnTertiaryContainerIndigo = Color(0xFFBDC2FF)
val TertiaryFixed = Color(0xFFE0E0FF)
val TertiaryFixedDim = Color(0xFFBDC2FF)

// Semantic & Engine Status Accents
val SuccessEmerald = Color(0xFF34D399) // 99%+ OCR confidence & completed
val WarningAmber = Color(0xFFFBBF24) // ML Kit Fallback & warnings
val ErrorCrimson = Color(0xFFF87171) // Dropped frames & errors
val ErrorContainerCrimson = Color(0xFF93000A)
val OnErrorContainerCrimson = Color(0xFFFFDAD6)

// Typography & Content Neutrals
val OnSurfaceText = Color(0xFFF0F3F6)
val OnSurfaceVariantText = Color(0xFF9DA7B3)
val OutlineBorder = Color(0xFF49454F)
val OutlineVariantBorder = Color(0xFF30363D)

// Legacy backward compatibility mappings
val TechBlue = SecondaryBlue
val TechCyan = PrimaryViolet
val TechIndigo = TertiaryIndigo
val TechAmber = WarningAmber
val DarkSurface = SurfaceContainer
val DarkSurfaceVariant = SurfaceContainerHigh
val DarkBackground = SurfaceCanvas
val LightSurface = Color(0xFFF8FAFC)
val LightSurfaceVariant = Color(0xFFE2E8F0)
val LightBackground = Color(0xFFFFFFFF)
