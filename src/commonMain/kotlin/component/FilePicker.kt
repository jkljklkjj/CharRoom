package component

import androidx.compose.runtime.Composable

/**
 * 跨平台文件选择器接口
 */
interface FilePickerProvider {
    /**
     * 选择图片
     * @param onResult 回调：图片字节数组，文件名
     */
    fun pickImage(onResult: (ByteArray, String) -> Unit)

    /**
     * 选择文件
     * @param onResult 回调：文件字节数组，文件名，文件大小
     */
    fun pickFile(onResult: (ByteArray, String, Long) -> Unit)

    /**
     * 注册文件选择器（各平台实现需要在Composable上下文中调用此方法初始化）
     */
    @Composable
    fun Register()
}

// 全局文件选择器实现，由各平台初始化
lateinit var FilePicker: FilePickerProvider
