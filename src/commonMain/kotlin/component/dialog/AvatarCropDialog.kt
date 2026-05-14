package component.dialog

import androidx.compose.runtime.Composable

/**
 * 头像裁剪对话框接口
 */
interface AvatarCropDialogProvider {
    /**
     * 显示头像裁剪对话框
     * @param imageBytes 原始图片字节数组
     * @param originalFileName 原始文件名
     * @param onDismiss 对话框关闭回调
     * @param onCropComplete 裁剪完成回调，返回裁剪后的图片字节数组和新文件名
     */
    @Composable
    fun AvatarCropDialog(
        imageBytes: ByteArray,
        originalFileName: String,
        onDismiss: () -> Unit,
        onCropComplete: (ByteArray, String) -> Unit
    )
}

// 全局头像裁剪对话框实现，由各平台初始化
lateinit var AvatarCropDialogImpl: AvatarCropDialogProvider

/**
 * 跨平台头像裁剪对话框
 * @param imageBytes 原始图片字节数组
 * @param originalFileName 原始文件名
 * @param onDismiss 对话框关闭回调
 * @param onCropComplete 裁剪完成回调，返回裁剪后的图片字节数组和新文件名
 */
@Composable
fun AvatarCropDialog(
    imageBytes: ByteArray,
    originalFileName: String,
    onDismiss: () -> Unit,
    onCropComplete: (ByteArray, String) -> Unit
) {
    AvatarCropDialogImpl.AvatarCropDialog(imageBytes, originalFileName, onDismiss, onCropComplete)
}
