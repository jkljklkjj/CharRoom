package component

/**
 * 跨平台文件选择器
 */
expect object FilePicker {
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
}
