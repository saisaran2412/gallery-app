package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

fun colorSchemeForTheme(mode: ThemeMode): ColorScheme = when (mode) {
    ThemeMode.LIQUID_GLASS -> lightColorScheme(
        primary = Color(0xFF0A84FF),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFDCEEFF),
        onPrimaryContainer = Color(0xFF07315C),
        secondary = Color(0xFF56667D),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE4ECF6),
        onSecondaryContainer = Color(0xFF1D2838),
        tertiary = Color(0xFFD97724),
        background = Color(0xFFEEF3FA),
        onBackground = Color(0xFF101620),
        surface = Color(0xFFFDFEFF),
        onSurface = Color(0xFF101620),
        surfaceVariant = Color(0xFFE3EBF6),
        onSurfaceVariant = Color(0xFF516075),
        outline = Color(0xFFCFDCEB)
    )

    ThemeMode.LIGHT -> lightColorScheme(
        primary = Color(0xFFD97724),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFBE9D7),
        onPrimaryContainer = Color(0xFF5C2B04),
        secondary = Color(0xFF6B5E52),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFF1EEE8),
        onSecondaryContainer = Color(0xFF2B241D),
        tertiary = Color(0xFFB45309),
        background = Color(0xFFFAF8F5),
        onBackground = Color(0xFF1A1816),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF1A1816),
        surfaceVariant = Color(0xFFF1EEE8),
        onSurfaceVariant = Color(0xFF6A635B),
        outline = Color(0xFFE4DFD7)
    )

    ThemeMode.DARK -> darkColorScheme(
        primary = Color(0xFFEA9445),
        onPrimary = Color(0xFF281302),
        primaryContainer = Color(0xFF332010),
        onPrimaryContainer = Color(0xFFFDE2C6),
        secondary = Color(0xFFA8A39D),
        onSecondary = Color(0xFF1C1A17),
        secondaryContainer = Color(0xFF1F1F24),
        onSecondaryContainer = Color(0xFFE8E6E3),
        tertiary = Color(0xFFF4B16B),
        background = Color(0xFF0A0A0C),
        onBackground = Color(0xFFF5F4F2),
        surface = Color(0xFF141417),
        onSurface = Color(0xFFF5F4F2),
        surfaceVariant = Color(0xFF1E1E23),
        onSurfaceVariant = Color(0xFF9F9FA8),
        outline = Color(0xFF2C2C33)
    )

    ThemeMode.AMOLED_BLACK -> darkColorScheme(
        primary = Color(0xFFF09D4F),
        onPrimary = Color(0xFF261100),
        primaryContainer = Color(0xFF2E1C0D),
        onPrimaryContainer = Color(0xFFFFE5CC),
        secondary = Color(0xFFA1A1AA),
        onSecondary = Color(0xFF18181B),
        secondaryContainer = Color(0xFF19191D),
        onSecondaryContainer = Color(0xFFE4E4E7),
        tertiary = Color(0xFFEAB308),
        background = Color(0xFF000000),
        onBackground = Color(0xFFF8F8FA),
        surface = Color(0xFF111114),
        onSurface = Color(0xFFF8F8FA),
        surfaceVariant = Color(0xFF1A1A1F),
        onSurfaceVariant = Color(0xFF9C9CA6),
        outline = Color(0xFF27272E)
    )

    ThemeMode.WARM -> lightColorScheme(
        primary = Color(0xFFB85430),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFF8DFD4),
        onPrimaryContainer = Color(0xFF47180B),
        secondary = Color(0xFF7D5D43),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFF1E7DC),
        onSecondaryContainer = Color(0xFF3A2718),
        tertiary = Color(0xFFB7791F),
        background = Color(0xFFFAF6F0),
        onBackground = Color(0xFF241C16),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF241C16),
        surfaceVariant = Color(0xFFF0E9DF),
        onSurfaceVariant = Color(0xFF69584C),
        outline = Color(0xFFDCD2C6)
    )

    ThemeMode.COOL -> lightColorScheme(
        primary = Color(0xFF2B6CB0),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFDBE8F7),
        onPrimaryContainer = Color(0xFF0E294A),
        secondary = Color(0xFF3D6B73),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFDDEAEB),
        onSecondaryContainer = Color(0xFF133338),
        tertiary = Color(0xFF4C51BF),
        background = Color(0xFFF3F7FB),
        onBackground = Color(0xFF131D29),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF131D29),
        surfaceVariant = Color(0xFFE6EDF5),
        onSurfaceVariant = Color(0xFF4C5F73),
        outline = Color(0xFFC8D5E3)
    )

    ThemeMode.BLUE -> darkColorScheme(
        primary = Color(0xFF7CA7F8),
        onPrimary = Color(0xFF0A1D42),
        primaryContainer = Color(0xFF1E3254),
        onPrimaryContainer = Color(0xFFDCE6FF),
        secondary = Color(0xFF9AB1CE),
        onSecondary = Color(0xFF132236),
        secondaryContainer = Color(0xFF1D2B40),
        onSecondaryContainer = Color(0xFFDCE6F4),
        tertiary = Color(0xFFE0AF68),
        background = Color(0xFF0F1724),
        onBackground = Color(0xFFEDF2FA),
        surface = Color(0xFF162132),
        onSurface = Color(0xFFEDF2FA),
        surfaceVariant = Color(0xFF202F45),
        onSurfaceVariant = Color(0xFFA2B3CA),
        outline = Color(0xFF314461)
    )

    ThemeMode.GREEN -> lightColorScheme(
        primary = Color(0xFF2D6A4F),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD4EBDF),
        onPrimaryContainer = Color(0xFF0B2E1F),
        secondary = Color(0xFF4E6E63),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFDFECE6),
        onSecondaryContainer = Color(0xFF182F27),
        tertiary = Color(0xFF9E7047),
        background = Color(0xFFF4F8F5),
        onBackground = Color(0xFF14211A),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF14211A),
        surfaceVariant = Color(0xFFE5EFE9),
        onSurfaceVariant = Color(0xFF4B6356),
        outline = Color(0xFFC6D8CD)
    )

    ThemeMode.PURPLE -> darkColorScheme(
        primary = Color(0xFFC2A7EB),
        onPrimary = Color(0xFF261B42),
        primaryContainer = Color(0xFF372C54),
        onPrimaryContainer = Color(0xFFECE6FA),
        secondary = Color(0xFFA8A3BD),
        onSecondary = Color(0xFF1F1C2B),
        secondaryContainer = Color(0xFF2D293B),
        onSecondaryContainer = Color(0xFFE5E2F0),
        tertiary = Color(0xFFDCA7C6),
        background = Color(0xFF15121B),
        onBackground = Color(0xFFF2EFF7),
        surface = Color(0xFF201C28),
        onSurface = Color(0xFFF2EFF7),
        surfaceVariant = Color(0xFF2B2736),
        onSurfaceVariant = Color(0xFFB5AFC7),
        outline = Color(0xFF413B52)
    )

    ThemeMode.MINIMAL -> lightColorScheme(
        primary = Color(0xFF18181B),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE4E4E7),
        onPrimaryContainer = Color(0xFF18181B),
        secondary = Color(0xFF52525B),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFF4F4F5),
        onSecondaryContainer = Color(0xFF27272A),
        tertiary = Color(0xFF3F3F46),
        background = Color(0xFFFFFFFF),
        onBackground = Color(0xFF09090B),
        surface = Color(0xFFFAFAFA),
        onSurface = Color(0xFF09090B),
        surfaceVariant = Color(0xFFF1F1F3),
        onSurfaceVariant = Color(0xFF52525B),
        outline = Color(0xFFD4D4D8)
    )

    ThemeMode.GLASS_25D -> darkColorScheme(
        primary = Color(0xFFE59852),
        onPrimary = Color(0xFF261402),
        primaryContainer = Color(0xFF332315),
        onPrimaryContainer = Color(0xFFFFE4CA),
        secondary = Color(0xFFA8A8B3),
        onSecondary = Color(0xFF1D2026),
        secondaryContainer = Color(0xFF25272F),
        onSecondaryContainer = Color(0xFFE4E7ED),
        tertiary = Color(0xFFF4B474),
        background = Color(0xFF15171C),
        onBackground = Color(0xFFF2F3F7),
        surface = Color(0xFF1E2128),
        onSurface = Color(0xFFF2F3F7),
        surfaceVariant = Color(0xFF282B34),
        onSurfaceVariant = Color(0xFFA6ACB8),
        outline = Color(0xFF3A3E4A)
    )

    ThemeMode.LIQUID_GLASS_NIGHT -> darkColorScheme(
        primary = Color(0xFF64A8FF),
        onPrimary = Color(0xFF031A36),
        primaryContainer = Color(0xFF12294A),
        onPrimaryContainer = Color(0xFFD6E8FF),
        secondary = Color(0xFF9BA8BC),
        onSecondary = Color(0xFF111826),
        secondaryContainer = Color(0xFF182233),
        onSecondaryContainer = Color(0xFFDDE6F4),
        tertiary = Color(0xFFF0A860),
        background = Color(0xFF05070C),
        onBackground = Color(0xFFF1F4FA),
        surface = Color(0xFF0E131C),
        onSurface = Color(0xFFF1F4FA),
        surfaceVariant = Color(0xFF172033),
        onSurfaceVariant = Color(0xFFA3B0C6),
        outline = Color(0xFF2A3650)
    )

    ThemeMode.ARCANE_GLASS -> darkColorScheme(
        primary = Color(0xFFE3B964),
        onPrimary = Color(0xFF241838),
        primaryContainer = Color(0xFF2B1F4A),
        onPrimaryContainer = Color(0xFFF6E0A4),
        secondary = Color(0xFFBDB2D6),
        onSecondary = Color(0xFF1B1530),
        secondaryContainer = Color(0xFF241A40),
        onSecondaryContainer = Color(0xFFF3EBD6),
        tertiary = Color(0xFF9B6BFF),
        background = Color(0xFF0B0716),
        onBackground = Color(0xFFF3EBD6),
        surface = Color(0xFF150F28),
        onSurface = Color(0xFFF3EBD6),
        surfaceVariant = Color(0xFF1F1740),
        onSurfaceVariant = Color(0xFFA99FC2),
        outline = Color(0xFF4A3C6B)
    )
}
