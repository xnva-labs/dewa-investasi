package com.xnvalabs.smarteyex.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * SmartEyeX — Color.kt
 * Colour tokens used by the native screens. Tokens that no screen references are removed
 * on purpose, so add a token here only together with the code that uses it.
 */

// ===== GLOBAL / ROOT TOKENS =====
val SmartRed = Color(0xFFFF244F)

// ===== NATIVE LIGHT THEME (Kotlin app only) =====
val LightBgWarm = Color(0xFFFFFBF8)
val LightBgPeach = Color(0xFFFFF1E6)
val LightBgAqua = Color(0xFFEAFBFA)
val LightBgViolet = Color(0xFFF4EFFF)

val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceMuted = Color(0xFFF6F7FA)
val LightBorder = Color(0xFFE7E9EE)

val TextPrimaryLight = Color(0xFF16213D)
val TextMutedLight = Color(0xFF9CA3AF)

val AccentOrange = Color(0xFFFF6A00)
val AccentTurquoise = Color(0xFF0FB8B0)
val AccentViolet = Color(0xFF8B5CF6)
val AccentBlue = Color(0xFF2F6FED)

// ===== ACTIVATION — Eagle Eye (native) =====
val ActivationBgSoft = Color(0xFFF3FDFC)
val ActivationBgTurq = Color(0xFFCFF3EF)
val FractureColor = Color(0xFF0FB8B0)

val EagleIrisOuter = Color(0xFF7A3B12)
val EagleIrisMid = Color(0xFFC96A1E)
val EagleIrisInner = Color(0xFFFFB347)
val EaglePupil = Color(0xFF120A05)
val EagleGlint = Color(0xFFFFFDF6)
val EagleBrow = Color(0xFF3B2A22)

// ===== SYSTEM SCREEN (native) =====
val SystemBgMid = Color(0xFFBFEAE5)
val SystemBgDeep = Color(0xFF3C6B6A)
val SystemTraceIdle = Color(0xFF1F4E4D)
val SystemTraceActive = Color(0xFFFF6A00)
val SystemNodeIdle = Color(0xFFF6FBFA)
val SystemLabelText = Color(0xFFEFFDFB)
val SystemGlassTint = Color(0x33FFFFFF)
val SystemGlassBorder = Color(0x59FFFFFF)

val SystemIrisInner = Color(0xFFFFB000)
val SystemIrisMid = Color(0xFFFF6A00)
val SystemIrisOuter = Color(0xFFFF3D00)
val SystemPupil = Color(0xFF190900)
val SystemGlint = Color(0xFFFFFDF6)
val SystemBrow = Color(0xFF241209)
val SystemRing = Color(0xFF0FB8B0)

// ===== PROFILE SCREEN (native) =====
val ProfileBgTop = Color(0xFFE7E1F0)
val ProfileBgBottom = Color(0xFFD6CCE3)
val ProfileDust = Color(0xFF8B84A0)
val ProfileTitleText = Color(0xFF2A2438)
val ProfileSubText = Color(0xFF6B6480)
val ProfilePanelBg = Color(0x40FFFFFF)
val ProfilePanelBorder = Color(0x59FFFFFF)
val ProfileFocusAccent = Color(0xFF0FB8B0)

// ===== XNAI CORE (native) =====
val XnaiBgBase = Color(0xFF0B0B0D)
val XnaiBgBase2 = Color(0xFF050505)
val XnaiGlowTurquoise = Color(0x2600C8C8)

val XnaiTextPrimary = Color(0xFFF4F4F2)
val XnaiTextMuted = Color(0xFF8A8D91)
val XnaiOnlineGreen = Color(0xFF4ADE80)

val XnaiRelaxA = Color(0xFF22C55E)
val XnaiRelaxB = Color(0xFF86EFAC)
val XnaiHighA = Color(0xFFFF6A00)
val XnaiHighB = Color(0xFFFFB000)
val XnaiSuperA = Color(0xFFFF6A00)
val XnaiSuperB = Color(0xFF22C55E)
val XnaiSuperC = Color(0xFFFFFFFF)

val XnaiUserBubbleStart = Color(0x3DFF6A00)
val XnaiUserBubbleEnd = Color(0x24FF244F)
val XnaiUserBubbleBorder = Color(0x4DFFB100)

val XnaiAiBubbleBg = Color(0x991C1C1F)
val XnaiAiBubbleBorder = Color(0x3867E8F9)

val XnaiAvatarRing = Color(0xFFCFFAFE)
val XnaiAvatarIrisInner = Color(0xFFFFB000)
val XnaiAvatarIrisOuter = Color(0xFFFF3D00)
val XnaiAvatarPupil = Color(0xFF190900)

// ===== NOTIFICATION LISTENER (native) =====
// This screen was ALREADY light in the prototype (white→green gradient,
// no dark mode at all) — no theme-pivot decision needed here, and no
// purple/navy ever existed in its palette to begin with. Ported close to
// 1:1 on color, same as the mist/core/stream mechanics.
val ListenerBgTop = Color(0xFFFFFFFF)
val ListenerBgMid = Color(0xFFF0FDF6)
val ListenerBgLower = Color(0xFFDCFCE7)
val ListenerBgBottom = Color(0xFFBBF7D0)

val ListenerStatusActive = Color(0xFF16A34A)

val ListenerCoreGlowInner = Color(0x5922C55E)
val ListenerCoreGlowOuter = Color(0xA6FFFFFF)
val ListenerCoreBorder = Color(0x6616A34A)
val ListenerIconColor = Color(0xFF15803D)
val ListenerSignalRing = Color(0xFF0D9488)
val ListenerMistColor = Color(0x8C0D9488)

val ListenerTextDark = Color(0xFF0F172A)
val ListenerSectionTitle = Color(0xFF4B7C68)
val ListenerSectionSub = Color(0xFF6B8F80)
val ListenerMsgColor = Color(0xFF5B7A6E)

val ListenerCardBg = Color(0x9EFFFFFF)
val ListenerCardBorder = Color(0x3816A34A)

val ListenerToggleOff = Color(0xFFCBD5C7)
val ListenerToggleOn = Color(0xFF22C55E)

val ListenerModeSelectedBorder = Color(0xFF16A34A)
val ListenerModeSelectedBg = Color(0x2422C55E)

val ListenerEditorOverlayBg = Color(0x590F281E)
