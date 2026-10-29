package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.data.model.MayaPersona

data class PersonaAccent(
    val primary: Color,
    val secondary: Color,
    val container: Color,
    val onContainer: Color
)

val LocalPersonaAccent = staticCompositionLocalOf {
    PersonaAccent(
        primary = MayaCoral,
        secondary = MayaAmber,
        container = MayaContainer,
        onContainer = OnMayaContainer
    )
}

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

fun accentForPersona(persona: MayaPersona): PersonaAccent = when (persona) {
    MayaPersona.MAYA -> PersonaAccent(MayaCoral, MayaAmber, MayaContainer, OnMayaContainer)
    MayaPersona.FRIDAY -> PersonaAccent(FridayCyan, FridaySapphire, FridayContainer, OnFridayContainer)
    MayaPersona.VENOM -> PersonaAccent(VenomCrimson, VenomViolet, VenomContainer, OnVenomContainer)
}

@Composable
fun MayaTheme(
    persona: MayaPersona = MayaPersona.MAYA,
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val accent = accentForPersona(persona)

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = accent.primary,
            onPrimary = Color(0xFF090B12),
            primaryContainer = accent.container,
            onPrimaryContainer = accent.onContainer,
            secondary = accent.secondary,
            onSecondary = Color(0xFF090B12),
            secondaryContainer = ObsidianSurfaceBright,
            onSecondaryContainer = IvoryText,
            tertiary = EmeraldSuccess,
            background = ObsidianBg,
            onBackground = IvoryText,
            surface = ObsidianSurface,
            onSurface = IvoryText,
            surfaceVariant = ObsidianSurfaceHigh,
            onSurfaceVariant = MutedSlateText,
            outline = ObsidianOutline,
            error = RubyDanger,
            onError = Color.White
        )
    } else {
        lightColorScheme(
            primary = accent.primary,
            onPrimary = Color.White,
            primaryContainer = accent.onContainer,
            onPrimaryContainer = accent.container,
            secondary = accent.secondary,
            background = AlabasterBg,
            onBackground = InkText,
            surface = AlabasterSurface,
            onSurface = InkText,
            surfaceVariant = AlabasterSurfaceHigh,
            onSurfaceVariant = InkMuted,
            error = RubyDanger
        )
    }

    CompositionLocalProvider(LocalPersonaAccent provides accent) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = AppShapes,
            content = content
        )
    }
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MayaTheme(persona = MayaPersona.MAYA, darkTheme = darkTheme, content = content)
}
