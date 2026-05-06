# CharRoom

## 平台实现说明

`AvatarCropDialog` 按平台分别实现，但头像上传入口复用共享的 `ProfileScreen.kt`：

- 桌面端裁剪实现：`src/desktopMain/kotlin/component/AvatarCropDialog.kt`
- Android 端：`androidApp/src/main/kotlin/component/AvatarCropDialog.kt`

桌面端的资料页和头像上传流程仍在 `src/commonMain/kotlin/component/ProfileScreen.kt` 中统一管理，点击头像后会弹出桌面裁剪对话框再上传。

如果后续继续拆分平台功能，优先把桌面专用和 Android 专用逻辑放到各自模块中，避免写进 `commonMain`。
