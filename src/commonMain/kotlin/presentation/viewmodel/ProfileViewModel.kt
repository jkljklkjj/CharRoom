package presentation.viewmodel

import androidx.compose.runtime.mutableStateOf
import com.chatlite.i18n.currentStrings
import core.FileUploader
import core.loadImageBitmapWithCache
import data.repository.ChatRepository
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import model.User
import model.withAgentAssistant

/** Profile ViewModel - handles profile page business logic */
class ProfileViewModel(
    private val chatRepository: ChatRepository,
    private val chatViewModel: ChatViewModel,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate),
) {
    // UI状态
    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    // 表单状态
    val username = mutableStateOf("")
    val email = mutableStateOf("")
    val phone = mutableStateOf("")
    val signature = mutableStateOf("")
    val currentPassword = mutableStateOf("")
    val newPassword = mutableStateOf("")
    val confirmPassword = mutableStateOf("")
    val newEmail = mutableStateOf("")
    val emailVerifyCode = mutableStateOf("")
    val emailVerifyCountdown = mutableStateOf(0)
    val isSendingEmailCode = mutableStateOf(false)
    val isSaving = mutableStateOf(false)
    val isEditing = mutableStateOf(false)
    val errorMessage = mutableStateOf("")
    val successMessage = mutableStateOf("")
    val avatarBitmap = mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    val isUploadingAvatar = mutableStateOf(false)

    // 初始值，用于还原
    private var initialUsername = ""
    private var initialPhone = ""
    private var initialSignature = ""
    private var initialEmail = ""

    fun loadUserProfile() {
        coroutineScope.launch {
            _uiState.value = ProfileUiState.Loading
            core.recoverOperation({ failure ->
                _uiState.value =
                    ProfileUiState.Error(
                        currentStrings["profile.load.error"].format(
                            failure.message ?: currentStrings["common.unknown.error"]
                        )
                    )
            }) {
                val user = chatRepository.getCurrentUserProfile()
                if (user != null) {
                    username.value = user.username
                    applyProfileFields(user)
                    loadProfileAvatar(user)
                    _uiState.value = ProfileUiState.Success(user)
                } else _uiState.value = ProfileUiState.Error(currentStrings["profile.load.failed"])
            }
        }
    }

    private suspend fun loadProfileAvatar(user: model.User) {
        user.avatarUrl
            ?.takeIf { it.isNotBlank() }
            ?.let { url ->
                avatarBitmap.value = loadImageBitmapWithCache(url, user.avatarKey)
            }
    }

    /** 发送邮箱验证码 */
    fun sendEmailVerifyCode() {
        coroutineScope.launch {
            errorMessage.value = ""
            successMessage.value = ""
            isSendingEmailCode.value = true

            try {
                core.recoverOperation(
                    onFailure = { e ->
                        errorMessage.value =
                            currentStrings["profile.send.failed"].format(
                                e.message ?: currentStrings["common.unknown.error"]
                            )
                    }
                ) {
                    // 验证邮箱格式（通用正则，跨平台兼容）
                    val emailPattern = "[a-zA-Z0-9._-]+@[a-z]+\\.+[a-z]+"
                    if (!newEmail.value.matches(emailPattern.toRegex())) {
                        errorMessage.value = currentStrings["profile.email.invalid"]
                        return@launch
                    }

                    val success = chatRepository.sendEmailUpdateVerifyCode(newEmail.value)
                    if (success) {
                        successMessage.value = currentStrings["profile.email.code.sent"]
                        emailVerifyCountdown.value = 60 // 60秒倒计时
                        // 启动倒计时
                        launch {
                            while (emailVerifyCountdown.value > 0) {
                                kotlinx.coroutines.delay(1000.milliseconds)
                                emailVerifyCountdown.value--
                            }
                        }
                    } else {
                        errorMessage.value = currentStrings["profile.email.code.send.failed"]
                    }
                }
            } finally {
                isSendingEmailCode.value = false
            }
        }
    }

    /** 还原修改 */
    fun resetChanges() {
        username.value = initialUsername
        phone.value = initialPhone
        signature.value = initialSignature
        newEmail.value = initialEmail
        emailVerifyCode.value = ""
        currentPassword.value = ""
        newPassword.value = ""
        confirmPassword.value = ""
        errorMessage.value = ""
        successMessage.value = ""
        isEditing.value = false
    }

    fun saveProfile(onSuccess: () -> Unit = {}) {
        coroutineScope.launch {
            errorMessage.value = ""
            successMessage.value = ""
            isSaving.value = true
            try {
                core.recoverOperation({ failure ->
                    errorMessage.value =
                        currentStrings["profile.update.error"].format(
                            failure.message ?: currentStrings["common.unknown.error"]
                        )
                }) {
                    val invalid = validationError()
                    if (invalid != null) {
                        errorMessage.value = invalid
                        return@launch
                    }
                    val updated =
                        chatRepository.updateUserProfile(
                            username.value,
                            phone.value,
                            signature.value,
                            newPassword.value.takeIf { it.isNotBlank() },
                        )
                    if (!updateEmailIfNeeded()) return@launch
                    if (updated) completeProfileUpdate(onSuccess)
                    else errorMessage.value = currentStrings["profile.update.failed"]
                }
            } finally {
                isSaving.value = false
            }
        }
    }

    private fun validationError(): String? {
        if (newPassword.value.isNotBlank() || confirmPassword.value.isNotBlank()) {
            val passwordError =
                when {
                    currentPassword.value.isBlank() -> currentStrings["profile.password.required"]
                    newPassword.value != confirmPassword.value ->
                        currentStrings["profile.password.mismatch"]
                    newPassword.value.length < 8 -> currentStrings["profile.password.too.short"]
                    else -> null
                }
            if (passwordError != null) return passwordError
        }
        return if (username.value.isBlank()) currentStrings["profile.username.required"] else null
    }

    private suspend fun updateEmailIfNeeded(): Boolean =
        if (newEmail.value == email.value) true
        else if (emailVerifyCode.value.isBlank()) {
            errorMessage.value = currentStrings["profile.code.required"]
            false
        } else {
            val success = chatRepository.updateEmail(newEmail.value, emailVerifyCode.value)
            if (!success) errorMessage.value = currentStrings["profile.email.update.failed"]
            success
        }

    private suspend fun completeProfileUpdate(onSuccess: () -> Unit) {
        successMessage.value = currentStrings["profile.update.success"]
        val user = chatRepository.getCurrentUserProfile()
        if (user != null) {
            applyProfileFields(user)
            emailVerifyCode.value = ""
            val users = chatViewModel.usersFlow.value.toMutableList()
            val index = users.indexOfFirst { it.id == user.id }
            if (index != -1) users[index] = user else users.add(user)
            chatViewModel.updateUsers(users.withAgentAssistant())
        }
        isEditing.value = false
        onSuccess()
    }

    private fun applyProfileFields(user: model.User) {
        email.value = user.email.orEmpty()
        newEmail.value = user.email.orEmpty()
        phone.value = user.phone.orEmpty()
        signature.value = user.signature.orEmpty()
        initialUsername = user.username
        initialPhone = user.phone.orEmpty()
        initialSignature = user.signature.orEmpty()
        initialEmail = user.email.orEmpty()
    }

    /** 上传头像 */
    fun uploadAvatar(imageBytes: ByteArray, fileName: String) {
        coroutineScope.launch {
            errorMessage.value = ""
            successMessage.value = ""
            isUploadingAvatar.value = true

            try {
                core.recoverOperation(
                    onFailure = { e ->
                        errorMessage.value =
                            currentStrings["profile.avatar.upload.failed.with.error"].format(
                                e.message ?: currentStrings["common.unknown.error"]
                            )
                    }
                ) {
                    // 前端先检查文件大小，超过10MB直接提示（避免413错误）
                    val maxSize = 10 * 1024 * 1024 // 10MB
                    if (imageBytes.size > maxSize) {
                        errorMessage.value = currentStrings["profile.avatar.upload.size.limit"]
                        return@launch
                    }

                    // 上传裁剪后的头像
                    val avatarUrl = FileUploader.uploadAvatar(imageBytes, fileName)
                    if (avatarUrl != null) {
                        // Re-load avatar
                        avatarBitmap.value = loadImageBitmapWithCache(avatarUrl, null)
                        successMessage.value = currentStrings["profile.avatar.upload.success"]

                        // 更新用户信息
                        val currentUser = (_uiState.value as? ProfileUiState.Success)?.user
                        currentUser?.let {
                            val updatedUser = it.copy(avatarUrl = avatarUrl)
                            _uiState.value = ProfileUiState.Success(updatedUser)
                        }
                    } else {
                        errorMessage.value = currentStrings["profile.avatar.upload.failed"]
                    }
                }
            } finally {
                isUploadingAvatar.value = false
            }
        }
    }

    /** 进入编辑模式 */
    fun enterEditMode() {
        // 保存当前值作为还原基准
        initialUsername = username.value
        initialPhone = phone.value
        initialSignature = signature.value
        initialEmail = newEmail.value
        isEditing.value = true
    }
}

/** 个人资料UI状态 */
sealed class ProfileUiState {
    object Loading : ProfileUiState()

    data class Success(val user: User) : ProfileUiState()

    data class Error(val message: String) : ProfileUiState()
}
