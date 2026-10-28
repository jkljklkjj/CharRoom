package component

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt
import androidx.compose.ui.layout.ContentScale
import com.chatlite.i18n.LocalStrings
import core.loadImageBitmapWithCache
import core.getCachedImage
import model.User

@Composable
fun UserAvatar(
    user: User,
    size: Dp = 40.dp,
    onClick: (() -> Unit)? = null
) {
    val s = LocalStrings.current
    val palette = QingliaoColors
    val targetSize = with(LocalDensity.current) { size.toPx().roundToInt() }
    val avatarShape = RoundedCornerShape(size * 0.32f)
    val avatarModifier = Modifier.size(size).clip(avatarShape)
        .then(if (onClick != null) Modifier.clickable(onClickLabel = s["user.avatar"], onClick = onClick) else Modifier)
    val avatarBitmapState = remember(user.id, user.avatarUrl, user.avatarKey, targetSize) {
        val cached = if (!user.avatarUrl.isNullOrBlank()) getCachedImage(user.avatarUrl, user.avatarKey, targetSize) else null
        mutableStateOf(cached)
    }

    val avatarBitmap = avatarBitmapState.value

    LaunchedEffect(user.id, user.avatarUrl, user.avatarKey, targetSize) {
        avatarBitmapState.value = if (user.avatarUrl.isNullOrBlank()) null
        else loadImageBitmapWithCache(user.avatarUrl, user.avatarKey, targetSize)
    }

    Crossfade(
        targetState = avatarBitmap,
        animationSpec = tween(200, easing = FastOutSlowInEasing)
    ) { bitmap ->
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = s["user.avatar"],
                contentScale = ContentScale.Crop,
                modifier = avatarModifier
            )
        } else {
            Box(
                modifier = avatarModifier.background(palette.apricot),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = user.username.firstOrNull()?.toString() ?: "U",
                    color = palette.onApricot,
                    style = if (size >= 44.dp) MaterialTheme.typography.h6 else MaterialTheme.typography.caption
                )
            }
        }
    }
}
