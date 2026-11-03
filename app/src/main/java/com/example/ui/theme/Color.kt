package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Clean, Modern, Minimal Design Palette (Arc / Apple / Modern IDE inspired)
val CleanBg = Color(0xFF0F172A)           // Smooth deep slate
val CleanSurface = Color(0xFF1E293B)      // Clean card surface
val CleanSurfaceLight = Color(0xFF334155) // Elevated elements
val CleanBorder = Color(0xFF334155).copy(alpha = 0.6f)

// Vibrant Friendly Accent Colors
val CleanBlue = Color(0xFF38BDF8)         // Bright sky blue
val CleanIndigo = Color(0xFF6366F1)       // Vibrant modern indigo
val CleanGreen = Color(0xFF10B981)        // Friendly emerald green (Success/Safe)
val CleanOrange = Color(0xFFF97316)       // Warm energetic orange
val CleanPurple = Color(0xFFA855F7)       // Purple accent
val CleanRed = Color(0xFFF43F5E)          // Soft red

// Typography Colors
val TextMain = Color(0xFFF8FAFC)          // Crisp clean white
val TextMuted = Color(0xFF94A3B8)         // Soft gray
val TextSubtle = Color(0xFF64748B)        // Subtle secondary text

// Backwards-compatible aliases for other files
val ReaBackground = CleanBg
val ReaSurface = CleanSurface
val ReaSurfaceElevated = CleanSurfaceLight
val ReaSurfaceCard = CleanSurface
val ReaSurfaceBorder = CleanBorder

val ReaCyan = CleanBlue
val ReaCyanGlow = CleanBlue
val ReaEmerald = CleanGreen
val ReaAmber = CleanOrange
val ReaPurple = CleanPurple
val ReaRose = CleanRed

val ReaTextPrimary = TextMain
val ReaTextSecondary = TextMuted
val ReaTextMuted = TextSubtle

val ReaCodeBackground = Color(0xFF0B1120)
val ReaCodeKeyword = Color(0xFFF43F5E)
val ReaCodeString = Color(0xFF38BDF8)
val ReaCodeComment = Color(0xFF64748B)
val ReaCodeFunction = Color(0xFFA855F7)
val ReaCodeNumber = Color(0xFFF59E0B)
