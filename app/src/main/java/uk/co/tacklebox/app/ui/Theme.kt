package uk.co.tacklebox.app.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Background=Color(0xFF0E1A1E); val Surface=Color(0xFF15272D); val Inset=Color(0xFF1C343B); val Ink=Color(0xFFF3EEE4); val Muted=Color(0xFF9BB0B3); val Brass=Color(0xFFC9A24B); val BrassSoft=Color(0xFFE2C890); val Teal=Color(0xFF57B3A6)
private val scheme=darkColorScheme(primary=Brass,onPrimary=Background,secondary=Teal,background=Background,onBackground=Ink,surface=Surface,onSurface=Ink,surfaceVariant=Inset,onSurfaceVariant=Muted,outline=Color(0xFF38515A),error=Color(0xFFFFB4AB))
private val spectral=FontFamily.Serif; private val figtree=FontFamily.SansSerif
private val type=Typography(displaySmall=TextStyle(fontFamily=spectral,fontSize=38.sp,lineHeight=42.sp,fontWeight=FontWeight.SemiBold),headlineLarge=TextStyle(fontFamily=spectral,fontSize=32.sp,fontWeight=FontWeight.SemiBold),headlineMedium=TextStyle(fontFamily=spectral,fontSize=26.sp,fontWeight=FontWeight.Medium),titleLarge=TextStyle(fontFamily=spectral,fontSize=22.sp,fontWeight=FontWeight.Medium),bodyLarge=TextStyle(fontFamily=figtree,fontSize=16.sp,lineHeight=24.sp),bodyMedium=TextStyle(fontFamily=figtree,fontSize=14.sp,lineHeight=20.sp),labelLarge=TextStyle(fontFamily=figtree,fontSize=14.sp,fontWeight=FontWeight.SemiBold,letterSpacing=.3.sp))
@Composable fun TackleboxTheme(content:@Composable ()->Unit)=MaterialTheme(colorScheme=scheme,typography=type,content=content)
