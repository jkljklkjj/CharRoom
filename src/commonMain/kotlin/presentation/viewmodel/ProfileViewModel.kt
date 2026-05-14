package presentation.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import core.FileUploader
import core.loadImageBitmapWithCache
import data.repository.ChatRepository
import data.repository.GlobalChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import model.User
import model.withAgentAssistant
import kotlin.time.Duration.Companion.milliseconds

/**
 * 个人资料ViewModel
 * 处理个人信息页面的业务逻辑
 */
class ProfileViewModel(
    private val chatRepository: ChatRepository = GlobalChatRepository,
    private val chatViewModel: ChatViewModel = GlobalChatViewModel,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate)
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

    /**
     * 加载用户信息
     */
    fun loadUserProfile() {
        coroutineScope.launch {
            _uiState.value = ProfileUiState.Loading
            try {
                val user = chatRepository.getCurrentUserProfile()
                if (user != null) {
                    username.value = user.username
                    email.value = user.email ?: ""
                    phone.value = user.phone ?: ""
                    signature.value = user.signature ?: ""
                    newEmail.value = user.email ?: ""

                    // 保存初始值
                    initialUsername = user.username
                    initialPhone = user.phone ?: ""
                    initialSignature = user.signature ?: ""
                    initialEmail = user.email ?: ""

                    // 加载头像
                    user.avatarUrl?.takeIf { it.isNotBlank() }?.let { url ->
                        avatarBitmap.value = loadImageBitmapWithCache(url, user.avatarKey)
                    }

                    _uiState.value = ProfileUiState.Success(user)
                } else {
                    _uiState.value = ProfileUiState.Error("加载用户信息失败")
                }
            } catch (e: Exception) {
                _uiState.value = ProfileUiState.Error("加载失败：${e.message ?: "未知错误"}")
            }
        }
    }

    /**
     * 发送邮箱验证码
     */
    fun sendEmailVerifyCode() {
        coroutineScope.launch {
            errorMessage.value = ""
            successMessage.value = ""
            isSendingEmailCode.value = true

            try {
                // 验证邮箱格式（通用正则，跨平台兼容）
                val emailPattern = "[a-zA-Z0-9._-]+@[a-z]+\\.+[a-z]+"
                if (!newEmail.value.matches(emailPattern.toRegex())) {
                    errorMessage.value = "请输入有效的邮箱地址"
                    return@launch
                }

                val success = chatRepository.sendEmailUpdateVerifyCode(newEmail.value)
                if (success) {
                    successMessage.value = "验证码已发送到新邮箱，请查收"
                    emailVerifyCountdown.value = 60 // 60秒倒计时
                    // 启动倒计时
                    launch {
                        while (emailVerifyCountdown.value > 0) {
                            kotlinx.coroutines.delay(1000.milliseconds)
                            emailVerifyCountdown.value--
                        }
                    }
                } else {
                    errorMessage.value = "验证码发送失败，请检查邮箱是否正确"
                }
            } catch (e: Exception) {
                errorMessage.value = "发送失败：${e.message ?: "未知错误"}"
            } finally {
                isSendingEmailCode.value = false
            }
        }
    }

    /**
     * 还原修改
     */
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

    /**
     * 保存个人信息
     */
    fun saveProfile(onSuccess: () -> Unit = {}) {
        coroutineScope.launch {
            errorMessage.value = ""
            successMessage.value = ""
            isSaving.value = true

            try {
                // 验证密码
                if (newPassword.value.isNotBlank() || confirmPassword.value.isNotBlank()) {
                    if (currentPassword.value.isBlank()) {
                        errorMessage.value = "请输入当前密码"
                        return@launch
                    }
                    if (newPassword.value != confirmPassword.value) {
                        errorMessage.value = "两次输入的新密码不一致"
                        return@launch
                    }
                    if (newPassword.value.length < 6) {
                        errorMessage.value = "新密码长度不能少于6位"
                        return@launch
                    }
                }

                // 验证用户名
                if (username.value.isBlank()) {
                    errorMessage.value = "用户名不能为空"
                    return@launch
                }

                // 1. 更新基本信息（用户名、电话、个性签名、密码）
                val profileSuccess = chatRepository.updateUserProfile(
                    username = username.value,
                    phone = phone.value,
                    signature = signature.value,
                    password = newPassword.value.takeIf { it.isNotBlank() }
                )

                // 2. 如果邮箱有修改，更新邮箱
                var emailSuccess = true
                if (newEmail.value != email.value) {
                    if (emailVerifyCode.value.isBlank()) {
                        errorMessage.value = "请输入邮箱验证码"
                        return@launch
                    }
                    emailSuccess = chatRepository.updateEmail(newEmail.value, emailVerifyCode.value)
                    if (!emailSuccess) {
                        errorMessage.value = "邮箱更新失败，请检查验证码是否正确"
                        return@launch
                    }
                }

                if (profileSuccess) {
                    successMessage.value = "个人信息更新成功"

                    // 重新获取用户信息
                    val updatedUser = chatRepository.getCurrentUserProfile()
                    if (updatedUser != null) {
                        // 更新表单
                        email.value = updatedUser.email ?: ""
                        newEmail.value = updatedUser.email ?: ""
                        phone.value = updatedUser.phone ?: ""
                        signature.value = updatedUser.signature ?: ""
                        emailVerifyCode.value = ""

                        // 更新初始值
                        initialUsername = updatedUser.username
                        initialPhone = updatedUser.phone ?: ""
                        initialSignature = updatedUser.signature ?: ""
                        initialEmail = updatedUser.email ?: ""

                        // 更新全局用户列表
                        val currentUsers = chatViewModel.usersFlow.value.toMutableList()
                        val index = currentUsers.indexOfFirst { it.id == updatedUser.id }
                        if (index != -1) {
                            currentUsers[index] = updatedUser
                        } else {
                            currentUsers.add(updatedUser)
                        }
                        chatViewModel.updateUsers(currentUsers.withAgentAssistant())
                    }

                    isEditing.value = false
                    onSuccess()
                } else {
                    errorMessage.value = "更新失败，请检查输入是否正确"
                }
            } catch (e: Exception) {
                errorMessage.value = "更新失败：${e.message ?: "未知错误"}"
            } finally {
                isSaving.value = false
            }
        }
    }

    /**
     * 上传头像
     */
    fun uploadAvatar(imageBytes: ByteArray, fileName: String) {
        coroutineScope.launch {
            errorMessage.value = ""
            successMessage.value = ""
            isUploadingAvatar.value = true

            try {
                // 前端先检查文件大小，超过10MB直接提示（避免413错误）
                val maxSize = 10 * 1024 * 1024 // 10MB
                if (imageBytes.size > maxSize) {
                    errorMessage.value = "图片大小不能超过10MB，请选择更小的图片"
                    return@launch
                }

                // 上传裁剪后的头像
                val avatarUrl = FileUploader.uploadAvatar(imageBytes, fileName)
                if (avatarUrl != null) {
                    // 重新加载头像
                    avatarBitmap.value = loadImageBitmapWithCache(avatarUrl, null)
                    successMessage.value = "头像上传成功"

                    // 更新用户信息
                    val currentUser = (_uiState.value as? ProfileUiState.Success)?.user
                    currentUser?.let {
                        val updatedUser = it.copy(avatarUrl = avatarUrl)
                        _uiState.value = ProfileUiState.Success(updatedUser)
                    }
                } else {
                    errorMessage.value = "头像上传失败，请重试"
                }
            } catch (e: Exception) {
                errorMessage.value = "头像上传失败：${e.message ?: "未知错误"}"
            } finally {
                isUploadingAvatar.value = false
            }
        }
    }

    /**
     * 进入编辑模式
     */
    fun enterEditMode() {
        // 保存当前值作为还原基准
        initialUsername = username.value
        initialPhone = phone.value
        initialSignature = signature.value
        initialEmail = newEmail.value
        isEditing.value = true
    }
}

/**
 * 个人资料UI状态
 */
sealed class ProfileUiState {
    object Loading : ProfileUiState()
    data class Success(val user: User) : ProfileUiState()
    data class Error(val message: String) : ProfileUiState()
}

// 全局单例，兼容旧代码
val GlobalProfileViewModel = ProfileViewModel()
