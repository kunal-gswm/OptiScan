package com.optiscan.app.ui.theme

import androidx.compose.ui.graphics.Color

// OptiScan Aurora Design System Palette
val BackgroundMidnightNavy = Color(0xFF070B16)  // Base midnight navy
val SurfaceDeepNavy = Color(0xFF0D1527)         // Secondary deep surface
val SurfaceCard = Color(0xFF131D31)             // Primary card surface
val SurfaceCardElevated = Color(0xFF192640)     // Floating or active cards
val BorderSubtle = Color(0xFF1E2D48)            // Restrained card borders
val BorderHighlight = Color(0xFFFFFFFF)         // High-contrast accessibility border
val BorderGlowCyan = Color(0xFF00F2FE)          // Luminous focus indicator

// Luminous Aurora Accents
val AuroraCyan = Color(0xFF00F2FE)              // Electric Cyan (Primary Aurora)
val AuroraBlue = Color(0xFF0A84FF)              // Vivid Blue
val AuroraViolet = Color(0xFF8B5CF6)            // Deep Violet (Secondary Aurora)
val AuroraIndigo = Color(0xFF4F46E5)            // Indigo
val AuroraTeal = Color(0xFF00F5D4)              // Restrained Success Teal
val AuroraAmber = Color(0xFFFFB703)             // Warning Amber
val AuroraError = Color(0xFFFF453A)             // Error Red

// Primary and Secondary Actions
val ActionPrimaryBlue = Color(0xFF0A84FF)
val ActionPrimaryBlueHover = Color(0xFF0066CC)
val ActionSecondaryGreen = Color(0xFF00D2A0)   // Accessible Teal/Green
val ActionSecondaryGreenHover = Color(0xFF00B386)

// Text Colors (High Contrast WCAG AAA compliant)
val TextWhite = Color(0xFFFFFFFF)
val TextCoolWhite = Color(0xFFF8FAFC)
val TextLightGrey = Color(0xFFCBD5E1)
val TextMuted = Color(0xFF94A3B8)

// Backward-compatible Aliases
val BackgroundNearBlack = BackgroundMidnightNavy
val SurfaceDarkCharcoal = SurfaceDeepNavy
val AlertError = AuroraError
val AlertWarning = AuroraAmber
val AlertSuccess = AuroraTeal
val AlertInfo = AuroraCyan
