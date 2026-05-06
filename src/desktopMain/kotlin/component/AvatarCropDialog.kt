package component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.min
import java.awt.Window
import java.awt.event.MouseWheelEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseWheelListener

/**
 * 桌面端头像裁剪对话框实现
 */
object DesktopAvatarCropDialog : AvatarCropDialogProvider {
    @Composable
    override fun AvatarCropDialog(
        imageBytes: ByteArray,
        originalFileName: String,
        onDismiss: () -> Unit,
        onCropComplete: (ByteArray, String) -> Unit
    ) {
    var imageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    val scope = rememberCoroutineScope()

    // 图片变换参数
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var minScale by remember { mutableStateOf(1f) }
    var isCropping by remember { mutableStateOf(false) }
    var containerSize by remember { mutableStateOf(Size.Zero) }
    var fitScale by remember { mutableStateOf(1f) }

    // 裁剪框大小（正方形）
    val cropSize = 300.dp
    val cropSizePx = with(LocalDensity.current) { cropSize.toPx() }

    // 直接从字节数组加载图片
    LaunchedEffect(imageBytes) {
        scope.launch(Dispatchers.IO) {
            try {
                val inputStream = ByteArrayInputStream(imageBytes)
                val bufferedImage = ImageIO.read(inputStream)
                if (bufferedImage != null) {
                    val argbImage = BufferedImage(
                        bufferedImage.width,
                        bufferedImage.height,
                        BufferedImage.TYPE_INT_ARGB
                    )
                    val graphics = argbImage.createGraphics()
                    graphics.drawImage(bufferedImage, 0, 0, null)
                    graphics.dispose()

                    imageBitmap = argbImage.toComposeImageBitmap()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(0.9f),
            shape = RoundedCornerShape(16.dp),
            elevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 标题栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "头像裁剪",
                        style = MaterialTheme.typography.h6,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }

                if (imageBitmap == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .background(MaterialTheme.colors.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material.CircularProgressIndicator(color = Color.White)
                    }
                } else {
                    val bitmap = imageBitmap!!
                    var cropAreaBounds by remember { mutableStateOf<Rect?>(null) }
                    fun applyWheelZoom(wheelRotation: Int) {
                        val factor = if (wheelRotation < 0) 1.05f else 0.95f
                        val oldScale = scale
                        val newScale = (scale * factor).coerceIn(minScale, 3f)
                        val containerCenter = Offset(containerSize.width / 2f, containerSize.height / 2f)
                        val scaleRatio = if (oldScale == 0f) 1f else newScale / oldScale
                        var newOffset = offset * scaleRatio
                        val dispW = bitmap.width * fitScale
                        val dispH = bitmap.height * fitScale
                        val scaledWidth = dispW * newScale
                        val scaledHeight = dispH * newScale
                        val maxOffsetX = max(0f, (scaledWidth - cropSizePx) / 2f)
                        val maxOffsetY = max(0f, (scaledHeight - cropSizePx) / 2f)
                        val clampedX = newOffset.x.coerceIn(-maxOffsetX, maxOffsetX)
                        val clampedY = newOffset.y.coerceIn(-maxOffsetY, maxOffsetY)
                        scale = newScale
                        offset = Offset(clampedX, clampedY)
                    }

                    DisposableEffect(Unit) {
                        val wheelListener = MouseWheelListener { event ->
                            val bounds = cropAreaBounds
                            val pointerX = event.x.toFloat()
                            val pointerY = event.y.toFloat()
                            if (bounds == null || !bounds.contains(Offset(pointerX, pointerY))) {
                                return@MouseWheelListener
                            }
                            applyWheelZoom(event.wheelRotation)
                        }

                        val attachedWindows = Window.getWindows().filter { it.isShowing }
                        attachedWindows.forEach { window ->
                            window.addMouseWheelListener(wheelListener)
                        }

                        onDispose {
                            attachedWindows.forEach { window ->
                                window.removeMouseWheelListener(wheelListener)
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(modifier = Modifier
                            .fillMaxSize()
                            .onGloballyPositioned { coordinates ->
                                val position = coordinates.positionInWindow()
                                val size = coordinates.size
                                cropAreaBounds = Rect(
                                    position.x,
                                    position.y,
                                    position.x + size.width.toFloat(),
                                    position.y + size.height.toFloat()
                                )
                            }
                            .onSizeChanged { intSize ->
                                containerSize = Size(intSize.width.toFloat(), intSize.height.toFloat())
                            }
                        ) {}

                        LaunchedEffect(bitmap, cropSizePx, containerSize) {
                            if (containerSize.width == 0f || containerSize.height == 0f) return@LaunchedEffect
                            val imageWidth = bitmap.width.toFloat()
                            val imageHeight = bitmap.height.toFloat()
                            fitScale = min(containerSize.width / imageWidth, containerSize.height / imageHeight)

                            val displayedW = imageWidth * fitScale
                            val displayedH = imageHeight * fitScale

                            val scaleX = cropSizePx / displayedW
                            val scaleY = cropSizePx / displayedH
                            minScale = max(scaleX, scaleY)
                            scale = max(scale, minScale)
                        }

                        Image(
                            bitmap = bitmap,
                            contentDescription = "待裁剪头像",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offset.x,
                                    translationY = offset.y,
                                    alpha = 1f
                                )
                                .pointerInput(Unit) {
                                    detectTransformGestures { centroid, pan, zoom, _ ->
                                        val oldScale = scale
                                        val newScale = (scale * zoom).coerceIn(minScale, 3f)

                                        val dispW = bitmap.width * fitScale
                                        val dispH = bitmap.height * fitScale

                                        val scaledWidth = dispW * newScale
                                        val scaledHeight = dispH * newScale

                                        val maxOffsetX = max(0f, (scaledWidth - cropSizePx) / 2f)
                                        val maxOffsetY = max(0f, (scaledHeight - cropSizePx) / 2f)

                                        val containerCenter = Offset(containerSize.width / 2f, containerSize.height / 2f)
                                        val scaleRatio = if (oldScale == 0f) 1f else newScale / oldScale
                                        val focus = centroid

                                        var newOffset = (focus - containerCenter) * (1f - scaleRatio) + offset * scaleRatio

                                        newOffset = Offset(newOffset.x + pan.x, newOffset.y + pan.y)

                                        val clampedX = newOffset.x.coerceIn(-maxOffsetX, maxOffsetX)
                                        val clampedY = newOffset.y.coerceIn(-maxOffsetY, maxOffsetY)

                                        scale = newScale
                                        offset = Offset(clampedX, clampedY)
                                    }
                                }
                        )

                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height
                            val centerLeft = (w - cropSizePx) / 2f
                            val centerTop = (h - cropSizePx) / 2f
                            val centerRect = Rect(centerLeft, centerTop, centerLeft + cropSizePx, centerTop + cropSizePx)

                            val path = Path().apply {
                                addRect(Rect(0f, 0f, w, h))
                                addRect(centerRect)
                                fillType = PathFillType.EvenOdd
                            }

                            drawPath(path, color = Color.Black.copy(alpha = 0.6f))

                            drawRect(
                                color = Color.White.copy(alpha = 0.9f),
                                topLeft = centerRect.topLeft,
                                size = centerRect.size,
                                style = Stroke(width = 2.dp.toPx())
                            )

                            // 绘制九宫格辅助线
                            val lineWidth = 1.dp.toPx()
                            // 竖线
                            drawLine(
                                color = Color.White.copy(alpha = 0.5f),
                                start = Offset(centerRect.left + cropSizePx / 3, centerRect.top),
                                end = Offset(centerRect.left + cropSizePx / 3, centerRect.bottom),
                                strokeWidth = lineWidth
                            )
                            drawLine(
                                color = Color.White.copy(alpha = 0.5f),
                                start = Offset(centerRect.left + cropSizePx * 2 / 3, centerRect.top),
                                end = Offset(centerRect.left + cropSizePx * 2 / 3, centerRect.bottom),
                                strokeWidth = lineWidth
                            )
                            // 横线
                            drawLine(
                                color = Color.White.copy(alpha = 0.5f),
                                start = Offset(centerRect.left, centerRect.top + cropSizePx / 3),
                                end = Offset(centerRect.right, centerRect.top + cropSizePx / 3),
                                strokeWidth = lineWidth
                            )
                            drawLine(
                                color = Color.White.copy(alpha = 0.5f),
                                start = Offset(centerRect.left, centerRect.top + cropSizePx * 2 / 3),
                                end = Offset(centerRect.right, centerRect.top + cropSizePx * 2 / 3),
                                strokeWidth = lineWidth
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.padding(end = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = MaterialTheme.colors.surface
                        )
                    ) {
                        Text("取消")
                    }
                    Button(
                        onClick = {
                            if (imageBitmap != null && !isCropping) {
                                isCropping = true
                                scope.launch(Dispatchers.IO) {
                                    val inputStream = ByteArrayInputStream(imageBytes)
                                    val src = ImageIO.read(inputStream)
                                    if (src != null) {
                                        val srcW = src.width.toFloat()
                                        val srcH = src.height.toFloat()

                                        // 计算按 fitScale 时显示尺寸
                                        val dispW = srcW * fitScale * scale
                                        val dispH = srcH * fitScale * scale

                                        // 居中显示的左上角在容器坐标系中的偏移
                                        val left = (containerSize.width - dispW) / 2f + offset.x
                                        val top = (containerSize.height - dispH) / 2f + offset.y

                                        // 裁剪框在容器中的位置：居中 300x300
                                        val cropLeft = (containerSize.width - cropSizePx) / 2f
                                        val cropTop = (containerSize.height - cropSizePx) / 2f

                                        // 将裁剪框的容器坐标映射回原图坐标
                                        val srcCropX = ((cropLeft - left) * (srcW / dispW)).coerceIn(0f, srcW)
                                        val srcCropY = ((cropTop - top) * (srcH / dispH)).coerceIn(0f, srcH)
                                        val srcCropW = (cropSizePx * (srcW / dispW)).coerceAtMost(srcW - srcCropX)
                                        val srcCropH = (cropSizePx * (srcH / dispH)).coerceAtMost(srcH - srcCropY)

                                        // 裁剪原图
                                        val cropped = src.getSubimage(
                                            srcCropX.toInt(),
                                            srcCropY.toInt(),
                                            srcCropW.toInt(),
                                            srcCropH.toInt()
                                        )

                                        // 缩放到300x300
                                        val scaled = cropped.getScaledInstance(300, 300, java.awt.Image.SCALE_SMOOTH)
                                        val result = java.awt.image.BufferedImage(300, 300, java.awt.image.BufferedImage.TYPE_INT_RGB)
                                        val g = result.createGraphics()
                                        g.drawImage(scaled, 0, 0, null)
                                        g.dispose()

                                        // 导出为JPG
                                        val outputStream = ByteArrayOutputStream()
                                        ImageIO.write(result, "jpg", outputStream)
                                        val jpgBytes = outputStream.toByteArray()

                                        onCropComplete(jpgBytes, originalFileName)
                                        onDismiss()
                                    } else {
                                        onCropComplete(imageBytes, originalFileName)
                                        onDismiss()
                                    }
                                    isCropping = false
                                }
                            }
                        },
                        enabled = imageBitmap != null && !isCropping,
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = MaterialTheme.colors.primary
                        )
                    ) {
                        if (isCropping) {
                            Text("处理中...")
                        } else {
                            Text("确认")
                        }
                    }
                }
            }
        }
    }
}
}
