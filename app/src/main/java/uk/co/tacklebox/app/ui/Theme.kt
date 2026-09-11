/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import uk.co.tacklebox.app.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Background=Color(0xFF0E1A1E); val Surface=Color(0xFF15272D); val Inset=Color(0xFF1C343B); val Ink=Color(0xFFF3EEE4); val Muted=Color(0xFF9BB0B3); val Dim=Color(0xFF6F8589); val Brass=Color(0xFFC9A24B); val BrassSoft=Color(0xFFE2C890); val Teal=Color(0xFF57B3A6)
private val scheme=darkColorScheme(primary=Brass,onPrimary=Background,secondary=Teal,background=Background,onBackground=Ink,surface=Surface,onSurface=Ink,surfaceVariant=Inset,onSurfaceVariant=Muted,outline=Color(0xFF38515A),error=Color(0xFFFFB4AB))
private val spectral=FontFamily(Font(R.font.spectral_regular, FontWeight.Normal),Font(R.font.spectral_medium, FontWeight.Medium),Font(R.font.spectral_semibold, FontWeight.SemiBold),Font(R.font.spectral_bold, FontWeight.Bold))
private val figtree=FontFamily(Font(R.font.figtree_regular, FontWeight.Normal),Font(R.font.figtree_medium, FontWeight.Medium),Font(R.font.figtree_semibold, FontWeight.SemiBold),Font(R.font.figtree_bold, FontWeight.Bold))
private val type=Typography(displaySmall=TextStyle(fontFamily=spectral,fontSize=38.sp,lineHeight=42.sp,fontWeight=FontWeight.SemiBold),headlineLarge=TextStyle(fontFamily=spectral,fontSize=32.sp,fontWeight=FontWeight.SemiBold),headlineMedium=TextStyle(fontFamily=spectral,fontSize=26.sp,fontWeight=FontWeight.Medium),titleLarge=TextStyle(fontFamily=spectral,fontSize=22.sp,fontWeight=FontWeight.Medium),bodyLarge=TextStyle(fontFamily=figtree,fontSize=16.sp,lineHeight=24.sp),bodyMedium=TextStyle(fontFamily=figtree,fontSize=14.sp,lineHeight=20.sp),bodySmall=TextStyle(fontFamily=figtree,fontSize=12.sp,lineHeight=16.sp),labelSmall=TextStyle(fontFamily=figtree,fontSize=10.sp),titleMedium=TextStyle(fontFamily=figtree,fontSize=16.sp,fontWeight=FontWeight.SemiBold),labelMedium=TextStyle(fontFamily=figtree,fontSize=12.sp,fontWeight=FontWeight.SemiBold),labelLarge=TextStyle(fontFamily=figtree,fontSize=14.sp,fontWeight=FontWeight.SemiBold,letterSpacing=.3.sp))
// Every screen must sit on a Surface. Without one, content drawn outside a Scaffold — onboarding — falls back to the
// platform window background and to black content colour (TB-A-01).
@Composable fun TackleboxTheme(content:@Composable ()->Unit)=MaterialTheme(colorScheme=scheme,typography=type){Surface(color=scheme.background,contentColor=scheme.onBackground,content=content)}