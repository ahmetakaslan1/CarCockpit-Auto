package com.asn.rifterlog.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.asn.rifterlog.data.Settings

// Peugeot Rifter i-Cockpit Gece (Karanlık) Renk Paleti - Sürüşte göz almaz
private val RifterDarkColorScheme = darkColorScheme(
    primary = Color(0xFF38BDF8),            // Kokpit Buz Mavisi
    onPrimary = Color(0xFF062032),
    primaryContainer = Color(0xFF0F2942),   // Koyu Kokpit Laciverti
    onPrimaryContainer = Color(0xFFD0EFFF),
    secondary = Color(0xFFF59E0B),          // Kehribar / Turuncu vurgu
    onSecondary = Color(0xFF2A1800),
    secondaryContainer = Color(0xFF1E293B), // Çalar / Kart yüzeyi
    onSecondaryContainer = Color(0xFFE2E8F0),
    tertiary = Color(0xFF34D399),           // GPS / Park Yeşili
    onTertiary = Color(0xFF052E1C),
    tertiaryContainer = Color(0xFF132A3A),  // Park kartı koyu yüzey
    onTertiaryContainer = Color(0xFFE0F2FE),
    background = Color(0xFF0B0F17),         // Derin Karbon Siyahı
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF151C28),            // Kart Yüzeyi
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFF94A3B8),
    error = Color(0xFFF87171)
)

// Peugeot Rifter Gündüz (Açık) Renk Paleti - Güneş altında yüksek kontrast
private val RifterLightColorScheme = lightColorScheme(
    primary = Color(0xFF0284C7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF072F49),
    secondary = Color(0xFFD97706),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF1F5F9),
    onSecondaryContainer = Color(0xFF0F172A),
    tertiary = Color(0xFF059669),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE0F2FE),
    onTertiaryContainer = Color(0xFF0C4A6E),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF475569),
    error = Color(0xFFDC2626)
)

@Composable
fun RifterLogTheme(
    themeMode: Int = Settings.THEME_DARK,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        Settings.THEME_DARK -> true
        Settings.THEME_LIGHT -> false
        else -> systemDark
    }

    val colorScheme = if (useDark) RifterDarkColorScheme else RifterLightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.primaryContainer.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !useDark
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
