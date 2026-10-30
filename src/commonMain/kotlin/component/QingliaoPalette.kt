package component

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Paper, apricot and ink have distinct roles; vivid orange is reserved for small accents. */
@Immutable
data class QingliaoPalette(
    val paper: Color,
    val surface: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    val accent: Color,
    val action: Color,
    val onAction: Color,
    val apricot: Color,
    val onApricot: Color,
    val message: Color,
    val onMessage: Color,
)

private val LightPaper =
    QingliaoPalette(
        paper = Color(0xFFFAF7F2),
        surface = Color(0xFFFFFDF9),
        ink = Color(0xFF342B25),
        muted = Color(0xFF75665B),
        line = Color(0xFFE8DED3),
        accent = Color(0xFFF47B35),
        action = Color(0xFFB94D1D),
        onAction = Color.White,
        apricot = Color(0xFFFFEAD8),
        onApricot = Color(0xFF98451E),
        message = Color(0xFFFFDFC4),
        onMessage = Color(0xFF59341F),
    )

private val DarkPaper =
    QingliaoPalette(
        paper = Color(0xFF211D1A),
        surface = Color(0xFF2D2722),
        ink = Color(0xFFF4EAE0),
        muted = Color(0xFFC2B2A4),
        line = Color(0xFF463B32),
        accent = Color(0xFFF69250),
        action = Color(0xFFFFB17A),
        onAction = Color(0xFF43230F),
        apricot = Color(0xFF483122),
        onApricot = Color(0xFFFFBD8D),
        message = Color(0xFF573924),
        onMessage = Color(0xFFFFE5D0),
    )

fun qingliaoPalette(dark: Boolean): QingliaoPalette = if (dark) DarkPaper else LightPaper

val QingliaoColors: QingliaoPalette
    @Composable get() = qingliaoPalette(!MaterialTheme.colors.isLight)

val QingliaoTypography =
    Typography(
        defaultFontFamily = FontFamily.SansSerif,
        h1 = TextStyle(fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.Medium),
        h2 = TextStyle(fontSize = 30.sp, lineHeight = 39.sp, fontWeight = FontWeight.Medium),
        h3 = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Medium),
        h4 = TextStyle(fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Medium),
        h5 = TextStyle(fontSize = 23.sp, lineHeight = 31.sp, fontWeight = FontWeight.Medium),
        h6 = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
        subtitle1 = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
        subtitle2 = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
        body1 = TextStyle(fontSize = 16.sp, lineHeight = 25.sp),
        body2 = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
        button = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
        caption = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
        overline = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    )

@Composable
fun QingliaoTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colors = if (darkTheme) CoolOrangeDarkColors else CoolOrangeLightColors,
        typography = QingliaoTypography,
        shapes = CoolOrangeShapes,
        content = content,
    )
}
