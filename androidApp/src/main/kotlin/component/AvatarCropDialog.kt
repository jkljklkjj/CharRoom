package component

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.graphicsLayer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Android端头像裁剪对话框实现（修复版本，参考桌面端逻辑）
 */
object AndroidAvatarCropDialog : AvatarCropDialogProvider {
    @Composable
    override fun AvatarCropDialog(
        imageBytes: ByteArray,
        originalFileName: String,
        onDismiss: () -> Unit,
        onCropComplete: (ByteArray, String) -> Unit
    ) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
        var scale by remember { mutableStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        var fitScale by remember { mutableStateOf(1f) }
        var minScale by remember { mutableStateOf(1f) }
        var containerSize by remember { mutableStateOf(Size.Zero) }
        var cropSize by remember { mutableStateOf(0f) }
        var cropRect by remember { mutableStateOf(Rect.Zero) }

        fun clampOffsetForCoverage(targetScale: Float, targetOffset: Offset): Offset {
            val bmp = bitmap ?: return targetOffset
            if (containerSize.width <= 0f || containerSize.height <= 0f || cropRect == Rect.Zero) return targetOffset

            val displayWidth = bmp.width * fitScale * targetScale
            val displayHeight = bmp.height * fitScale * targetScale

            val maxOffsetX = max(0f, (displayWidth - cropRect.width) / 2f)
            val maxOffsetY = max(0f, (displayHeight - cropRect.height) / 2f)

            return Offset(
                x = targetOffset.x.coerceIn(-maxOffsetX, maxOffsetX),
                y = targetOffset.y.coerceIn(-maxOffsetY, maxOffsetY)
            )
        }

        // 加载图片字节
        LaunchedEffect(imageBytes) {
            scope.launch(Dispatchers.IO) {
                val bmp = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                if (bmp != null) {
                    bitmap = bmp.asImageBitmap()
                }
            }
        }

        // 根据容器与图片计算 fitScale、裁剪框与 minScale
        LaunchedEffect(bitmap, containerSize) {
            val bmp = bitmap
            if (bmp != null && containerSize.width > 0f && containerSize.height > 0f) {
                // 裁剪框为容器的80%正方形
                cropSize = min(containerSize.width, containerSize.height) * 0.8f
                cropRect = Rect(
                    left = (containerSize.width - cropSize) / 2f,
                    top = (containerSize.height - cropSize) / 2f,
                    right = (containerSize.width + cropSize) / 2f,
                    bottom = (containerSize.height + cropSize) / 2f
                )

                // fitScale：将原图完整显示到容器内的基准缩放
                fitScale = min(
                    containerSize.width / bmp.width.toFloat(),
                    containerSize.height / bmp.height.toFloat()
                )

                // 计算最小缩放，确保裁剪框被图片覆盖
                val displayedW = bmp.width * fitScale
                val displayedH = bmp.height * fitScale
                val scaleX = if (displayedW > 0f) cropSize / displayedW else 1f
                val scaleY = if (displayedH > 0f) cropSize / displayedH else 1f
                minScale = max(scaleX, scaleY)

                // 初始 scale 设为 minScale，使整图可见且能覆盖裁剪框
                scale = max(1f, minScale)
                offset = clampOffsetForCoverage(scale, Offset.Zero)
            }
        }

        Dialog(onDismissRequest = onDismiss) {
            Card(modifier = Modifier.fillMaxWidth(0.95f), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "头像裁剪", style = MaterialTheme.typography.h6)
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "关闭") }
                    }

                    if (bitmap == null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .background(MaterialTheme.colors.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.material.CircularProgressIndicator()
                        }
                    } else {
                        val bmp = bitmap!!

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .onSizeChanged { size -> containerSize = Size(size.width.toFloat(), size.height.toFloat()) },
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = bmp,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer(
                                        scaleX = fitScale * scale,
                                        scaleY = fitScale * scale,
                                        translationX = offset.x,
                                        translationY = offset.y
                                    )
                                    .pointerInput(Unit) {
                                        detectTransformGestures { centroid, pan, zoom, _ ->
                                            val oldScale = scale
                                            val newScale = max(minScale, scale * zoom)

                                            val containerCenter = Offset(containerSize.width / 2f, containerSize.height / 2f)
                                            val scaleRatio = if (oldScale == 0f) 1f else newScale / oldScale
                                            val focus = centroid

                                            var newOffset = (focus - containerCenter) * (1f - scaleRatio) + offset * scaleRatio
                                            newOffset = Offset(newOffset.x + pan.x, newOffset.y + pan.y)

                                            scale = newScale
                                            offset = clampOffsetForCoverage(newScale, newOffset)
                                        }
                                    }
                            )

                            Canvas(modifier = Modifier.fillMaxSize()) {
                                if (cropRect != Rect.Zero) {
                                    val path = Path().apply {
                                        addRect(Rect(0f, 0f, size.width, size.height))
                                        addRect(cropRect)
                                        fillType = PathFillType.EvenOdd
                                    }
                                    drawPath(path, color = Color.Black.copy(alpha = 0.6f))

                                    drawRect(
                                        color = Color.White,
                                        topLeft = cropRect.topLeft,
                                        size = cropRect.size,
                                        style = Stroke(width = 2.dp.toPx())
                                    )
                                }
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.End) {
                            Button(onClick = onDismiss, modifier = Modifier.padding(end = 8.dp)) { Text("取消") }
                            Button(onClick = {
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val bis = ByteArrayInputStream(imageBytes)
                                        val src = BitmapFactory.decodeStream(bis) ?: return@launch

                                        val srcW = src.width.toFloat()
                                        val srcH = src.height.toFloat()

                                        // 显示尺寸 = 原图尺寸 * fitScale * 当前 scale
                                        val dispW = srcW * fitScale * scale
                                        val dispH = srcH * fitScale * scale

                                        // 图片在容器中的左上角位置（居中+偏移）
                                        val imgLeft = (containerSize.width - dispW) / 2f + offset.x
                                        val imgTop = (containerSize.height - dispH) / 2f + offset.y

                                        // 裁剪框在容器中的位置
                                        val cropLeft = cropRect.left
                                        val cropTop = cropRect.top
                                        val cropWidth = cropRect.width

                                        // 将裁剪框的容器坐标映射回原图坐标
                                        val scaleX = if (dispW > 0f) srcW / dispW else 1f
                                        val scaleY = if (dispH > 0f) srcH / dispH else 1f

                                        val srcCropX = ((cropLeft - imgLeft) * scaleX).coerceIn(0f, srcW)
                                        val srcCropY = ((cropTop - imgTop) * scaleY).coerceIn(0f, srcH)
                                        val srcCropW = (cropWidth * scaleX).coerceAtMost(srcW - srcCropX)
                                        val srcCropH = (cropWidth * scaleY).coerceAtMost(srcH - srcCropY)

                                        val cropped = android.graphics.Bitmap.createBitmap(
                                            src,
                                            srcCropX.toInt(),
                                            srcCropY.toInt(),
                                            srcCropW.toInt().coerceAtLeast(1),
                                            srcCropH.toInt().coerceAtLeast(1)
                                        )
                                        val scaled = android.graphics.Bitmap.createScaledBitmap(cropped, 300, 300, true)
                                        val baos = ByteArrayOutputStream()
                                        scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, baos)
                                        val outBytes = baos.toByteArray()
                                        onCropComplete(outBytes, originalFileName)
                                        onDismiss()
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }) { Text("确认") }
                        }
                    }
                }
            }
        }
    }
}
