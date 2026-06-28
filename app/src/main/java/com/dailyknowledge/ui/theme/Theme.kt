package com.dailyknowledge.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ========== 颜色定义 ==========

val Primary = Color(0xFF1976D2)
val PrimaryVariant = Color(0xFF1565C0)
val Secondary = Color(0xFF43A047)
val Background = Color(0xFFFAFAFA)
val Surface = Color(0xFFFFFFFF)
val Error = Color(0xFFE53935)
val OnPrimary = Color(0xFFFFFFFF)
val OnSecondary = Color(0xFFFFFFFF)
val OnBackground = Color(0xFF212121)
val OnSurface = Color(0xFF424242)
val OnError = Color(0xFFFFFFFF)

val FavoriteActive = Color(0xFFFFA726)
val FavoriteInactive = Color(0xFF9E9E9E)

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    secondary = Secondary,
    background = Background,
    surface = Surface,
    error = Error,
    onPrimary = OnPrimary,
    onSecondary = OnSecondary,
    onBackground = OnBackground,
    onSurface = OnSurface,
    onError = OnError
)

/**
 * 每日小知识 Material3 主题
 */
@Composable
fun DailyKnowledgeTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
