package component

/**
 * Android: 用 Intent.ACTION_SEND 调起系统分享
 * 当前作为占位实现，后续可集成 Android Context 传递
 * 思路：获得 Context 后创建 Intent(ACTION_SEND).putExtra(EXTRA_TEXT, text) 并 startActivity
 */
actual fun shareText(text: String) {
    println("[ShareUtil] Android share not yet implemented: $text")
}
