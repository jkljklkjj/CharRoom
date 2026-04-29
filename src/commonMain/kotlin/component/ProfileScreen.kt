package component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import core.ApiService
import core.FileUploader
import core.loadImageBitmapFromUrl
import kotlinx.coroutines.launch
import model.User
import model.users

/**
 * 个人信息页面
 */
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onProfileUpdated: () -> Unit = {}
) {
    var user by remember { mutableStateOf<User?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isEditing by remember { mutableStateOf(false) } // 编辑模式状态
    var avatarBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    // 表单字段
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var signature by remember { mutableStateOf("") }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    // 邮箱修改相关
    var newEmail by remember { mutableStateOf("") }
    var emailVerifyCode by remember { mutableStateOf("") }
    var emailVerifyCountdown by remember { mutableStateOf(0) }
    var isSendingEmailCode by remember { mutableStateOf(false) }
    // 初始值（用于还原）
    var initialUsername by remember { mutableStateOf("") }
    var initialPhone by remember { mutableStateOf("") }
    var initialSignature by remember { mutableStateOf("") }
    var initialNewEmail by remember { mutableStateOf("") }
    // 状态提示
    var errorMessage by remember { mutableStateOf("") }
    var successMessage by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // 发送邮箱验证码
    suspend fun sendEmailVerifyCode() {
        errorMessage = ""
        successMessage = ""
        isSendingEmailCode = true

        try {
            // 验证邮箱格式（通用正则，跨平台兼容）
            val emailPattern = "[a-zA-Z0-9._-]+@[a-z]+\\.+[a-z]+"
            if (!newEmail.matches(emailPattern.toRegex())) {
                errorMessage = "请输入有效的邮箱地址"
                return
            }

            val success = ApiService.sendEmailUpdateVerifyCode(newEmail)
            if (success) {
                successMessage = "验证码已发送到新邮箱，请查收"
                emailVerifyCountdown = 60 // 60秒倒计时
            } else {
                errorMessage = "验证码发送失败，请检查邮箱是否正确"
            }
        } catch (e: Exception) {
            errorMessage = "发送失败：${e.message ?: "未知错误"}"
        } finally {
            isSendingEmailCode = false
        }
    }

    // 还原修改
    fun resetChanges() {
        username = initialUsername
        phone = initialPhone
        signature = initialSignature
        newEmail = initialNewEmail
        emailVerifyCode = ""
        currentPassword = ""
        newPassword = ""
        confirmPassword = ""
        errorMessage = ""
        successMessage = ""
    }

    // 保存个人信息
    suspend fun saveProfile() {
        errorMessage = ""
        successMessage = ""
        isSaving = true

        try {
            // 验证密码
            if (newPassword.isNotBlank() || confirmPassword.isNotBlank()) {
                if (currentPassword.isBlank()) {
                    errorMessage = "请输入当前密码"
                    return
                }
                if (newPassword != confirmPassword) {
                    errorMessage = "两次输入的新密码不一致"
                    return
                }
                if (newPassword.length < 6) {
                    errorMessage = "新密码长度不能少于6位"
                    return
                }
            }

            // 验证用户名
            if (username.isBlank()) {
                errorMessage = "用户名不能为空"
                return
            }

            // 1. 更新基本信息（用户名、电话、个性签名、密码）
            val profileSuccess = ApiService.updateUserProfile(
                username = username,
                phone = phone,
                signature = signature,
                password = newPassword.takeIf { it.isNotBlank() }
            )

            // 2. 如果邮箱有修改，更新邮箱
            var emailSuccess = true
            if (newEmail != email) {
                if (emailVerifyCode.isBlank()) {
                    errorMessage = "请输入邮箱验证码"
                    return
                }
                emailSuccess = ApiService.updateEmail(newEmail, emailVerifyCode)
                if (!emailSuccess) {
                    errorMessage = "邮箱更新失败，请检查验证码是否正确"
                    return
                }
            }

            if (profileSuccess) {
                successMessage = "个人信息更新成功"

                // 使用 let 处理非空情况
                ApiService.getCurrentUserProfile()?.let { updatedUser ->
                    user = updatedUser
                    email = updatedUser.email ?: ""
                    newEmail = updatedUser.email ?: ""
                    phone = updatedUser.phone ?: ""
                    signature = updatedUser.signature ?: ""
                    emailVerifyCode = ""

                    // 更新初始值
                    initialUsername = updatedUser.username
                    initialPhone = updatedUser.phone ?: ""
                    initialSignature = updatedUser.signature ?: ""
                    initialNewEmail = updatedUser.email ?: ""

                    // 更新全局用户列表
                    users = users.map { existingUser ->
                        if (existingUser.id == updatedUser.id) updatedUser else existingUser
                    }

                    onProfileUpdated()
                }

                isEditing = false
            } else {
                errorMessage = "更新失败，请检查输入是否正确"
            }
        } catch (e: Exception) {
            errorMessage = "更新失败：${e.message ?: "未知错误"}"
        } finally {
            isSaving = false
        }
    }

    // 加载用户信息
    LaunchedEffect(Unit) {
        scope.launch {
            user = ApiService.getCurrentUserProfile()
            val u = user
            if (u != null) {
                username = u.username
                email = u.email ?: ""
                newEmail = u.email ?: ""
                phone = u.phone ?: ""
                signature = u.signature ?: ""
                // 保存初始值
                initialUsername = u.username
                initialPhone = u.phone ?: ""
                initialSignature = u.signature ?: ""
                initialNewEmail = u.email ?: ""
                // 加载头像
                val url = u.avatarUrl
                if (url != null && url.isNotBlank()) {
                    avatarBitmap = loadImageBitmapFromUrl(url, u.avatarKey)
                }
            }
            isLoading = false
        }
    }

    // 邮箱验证码倒计时
    LaunchedEffect(emailVerifyCountdown) {
        if (emailVerifyCountdown > 0) {
            kotlinx.coroutines.delay(1000)
            emailVerifyCountdown--
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("个人信息") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                backgroundColor = MaterialTheme.colors.primary
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 操作按钮区域
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isEditing) {
                        Text(
                            text = "查看模式",
                            style = MaterialTheme.typography.subtitle2,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                        )
                        Button(
                            onClick = {
                                // 进入编辑模式时保存当前值作为还原基准
                                initialUsername = username
                                initialPhone = phone
                                initialSignature = signature
                                initialNewEmail = newEmail
                                isEditing = true
                            },
                            modifier = Modifier.height(40.dp)
                        ) {
                            Text("编辑")
                        }
                    } else {
                        Text(
                            text = "编辑模式",
                            style = MaterialTheme.typography.subtitle2,
                            color = MaterialTheme.colors.secondary
                        )
                        Row {
                            OutlinedButton(
                                onClick = {
                                    resetChanges()
                                    isEditing = false
                                },
                                modifier = Modifier.height(40.dp).padding(end = 8.dp)
                            ) {
                                Text("还原")
                            }
                            Button(
                                onClick = {
                                    scope.launch {
                                        saveProfile()
                                    }
                                },
                                modifier = Modifier.height(40.dp),
                                enabled = !isSaving
                            ) {
                                if (isSaving) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = MaterialTheme.colors.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("保存中...")
                                } else {
                                    Text("确认修改")
                                }
                            }
                        }
                    }
                }

                // 头像区域
                var isUploadingAvatar by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colors.primary.copy(alpha = 0.1f))
                        .clickable(enabled = isEditing && !isUploadingAvatar) { // 只有编辑模式才能上传头像
                            FilePicker.pickImage { bytes, fileName ->
                                scope.launch {
                                    isUploadingAvatar = true
                                    errorMessage = ""
                                    successMessage = ""
                                    // 上传头像
                                    val avatarUrl = FileUploader.uploadImage(bytes, fileName)
                                    if (avatarUrl != null) {
                                        // 重新加载头像
                                        avatarBitmap = loadImageBitmapFromUrl(avatarUrl, null)
                                        successMessage = "头像上传成功"
                                        // 更新用户信息缓存
                                        user = user?.copy(avatarUrl = avatarUrl)
                                    } else {
                                        errorMessage = "头像上传失败，请重试"
                                    }
                                    isUploadingAvatar = false
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isUploadingAvatar) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colors.primary,
                            strokeWidth = 3.dp
                        )
                    } else if (avatarBitmap != null) {
                        Image(
                            bitmap = avatarBitmap!!,
                            contentDescription = "头像",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            text = username.firstOrNull()?.toString() ?: "U",
                            style = MaterialTheme.typography.h3,
                            color = MaterialTheme.colors.primary
                        )
                    }
                }
                Text(
                    text = "点击更换头像",
                    style = MaterialTheme.typography.caption,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))

                // 表单字段
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("用户名") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = isEditing
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 邮箱修改区域
                Text(
                    text = "邮箱",
                    style = MaterialTheme.typography.subtitle2,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newEmail,
                        onValueChange = { newEmail = it },
                        label = { Text("新邮箱") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        enabled = isEditing && emailVerifyCountdown == 0 && !isSendingEmailCode
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                sendEmailVerifyCode()
                            }
                        },
                        enabled = isEditing && newEmail.isNotBlank() && newEmail != email && emailVerifyCountdown == 0 && !isSendingEmailCode,
                        modifier = Modifier.height(56.dp)
                    ) {
                        if (isSendingEmailCode) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colors.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else if (emailVerifyCountdown > 0) {
                            Text("${emailVerifyCountdown}s")
                        } else {
                            Text("发送验证码")
                        }
                    }
                }

                if (newEmail != email) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = emailVerifyCode,
                        onValueChange = { emailVerifyCode = it },
                        label = { Text("邮箱验证码") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        enabled = isEditing
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("手机号码") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    enabled = isEditing
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = signature,
                    onValueChange = { signature = it },
                    label = { Text("个性签名") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 3,
                    enabled = isEditing
                )

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = "修改密码（不修改请留空）",
                    style = MaterialTheme.typography.subtitle2,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = { currentPassword = it },
                    label = { Text("当前密码") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = isEditing
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("新密码") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = isEditing
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("确认新密码") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = isEditing
                )

                Spacer(modifier = Modifier.height(32.dp))

                // 提示信息在底部
                // 错误/成功提示
                if (errorMessage.isNotBlank()) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colors.error,
                        style = MaterialTheme.typography.caption,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                if (successMessage.isNotBlank()) {
                    Text(
                        text = successMessage,
                        color = MaterialTheme.colors.secondary,
                        style = MaterialTheme.typography.caption,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}
