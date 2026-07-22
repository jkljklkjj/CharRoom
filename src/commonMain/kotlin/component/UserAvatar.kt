package component

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
    val avatarBitmapState = remember(user.id) {
        val cached = if (!user.avatarUrl.isNullOrBlank()) getCachedImage(user.avatarUrl) else null
        mutableStateOf(cached)
    }

    var isAvatarLoading by remember { mutableStateOf(false) }
    val avatarBitmap = avatarBitmapState.value

    LaunchedEffect(user.avatarUrl, user.avatarKey) {
        if (!isAvatarLoading && avatarBitmap == null && !user.avatarUrl.isNullOrBlank()) {
            isAvatarLoading = true
            avatarBitmapState.value = loadImageBitmapWithCache(user.avatarUrl, user.avatarKey)
            isAvatarLoading = false
        }
    }

    Crossfade(
        targetState = avatarBitmap,
        animationSpec = tween(200, easing = FastOutSlowInEasing)
    ) { bitmap ->
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = s["user.avatar"],
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .clickable { onClick?.invoke() }
            )
        } else {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(MaterialTheme.colors.primary)
                    .clickable { onClick?.invoke() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = user.username.firstOrNull()?.toString() ?: "U",
                    color = Color.White,
                    style = MaterialTheme.typography.caption
                )
            }
        }
    }
}
