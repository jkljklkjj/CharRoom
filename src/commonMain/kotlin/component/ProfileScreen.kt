package component

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import core.ApiService
import core.FileUploader
import core.loadImageBitmapFromUrl
import core.loadImageBitmapWithCache
import kotlinx.coroutines.launch
import model.User
import model.users
import kotlin.time.Duration.Companion.milliseconds

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
    // 头像裁剪相关状态
    var showCropDialog by remember { mutableStateOf(false) }
    var selectedImageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var selectedImageFileName by remember { mutableStateOf("") }
    var isUploadingAvatar by remember { mutableStateOf(false) } // 头像上传状态
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
                    avatarBitmap = loadImageBitmapWithCache(url, u.avatarKey)
                }
            }
            isLoading = false
        }
    }

    // 邮箱验证码倒计时
    LaunchedEffect(emailVerifyCountdown) {
        if (emailVerifyCountdown > 0) {
            kotlinx.coroutines.delay(1000.milliseconds)
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.TopCenter
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 720.dp), // 优化宽度，更适合桌面端
                    elevation = 2.dp, // 降低阴影，更现代
                    shape = RoundedCornerShape(16.dp) // 圆角优化
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 40.dp, vertical = 32.dp) // 调整内边距
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally // 整体居中
                    ) {
                        // 操作按钮区域
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!isEditing) {
                                Text(
                                    text = "个人信息",
                                    style = MaterialTheme.typography.h5, // 更大的标题
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
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
                                    modifier = Modifier.height(40.dp),
                                    shape = RoundedCornerShape(12.dp), // 按钮圆角
                                    colors = ButtonDefaults.buttonColors(
                                        backgroundColor = MaterialTheme.colors.primary
                                    )
                                ) {
                                    Text("编辑资料")
                                }
                            } else {
                                Text(
                                    text = "编辑资料",
                                    style = MaterialTheme.typography.h5,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    color = MaterialTheme.colors.primary
                                )
                                Row {
                                    OutlinedButton(
                                        onClick = {
                                            resetChanges()
                                            isEditing = false
                                        },
                                        modifier = Modifier.height(40.dp).padding(end = 8.dp),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("取消")
                                    }
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                saveProfile()
                                            }
                                        },
                                        modifier = Modifier.height(40.dp),
                                        enabled = !isSaving,
                                        shape = RoundedCornerShape(12.dp)
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
                                            Text("保存修改")
                                        }
                                    }
                                }
                            }
                        }

                        // 头像区域 - 美化
                        var isUploadingAvatar by remember { mutableStateOf(false) }
                        val avatarInteractionSource = remember { MutableInteractionSource() }
                        val isHoveringAvatar by avatarInteractionSource.collectIsHoveredAsState()
                        Box(
                            modifier = Modifier
                                .size(140.dp) // 增大头像尺寸
                                .shadow(
                                    elevation = if (isHoveringAvatar && isEditing) 16.dp else 8.dp,
                                    shape = CircleShape,
                                    clip = true
                                )
                                .clip(CircleShape)
                                .background(MaterialTheme.colors.primary.copy(alpha = 0.1f))
                                .hoverable(interactionSource = avatarInteractionSource, enabled = isEditing) // 悬停效果
                                .clickable(enabled = isEditing && !isUploadingAvatar) { // 只有编辑模式才能上传头像
                                    FilePicker.pickImage { bytes, fileName ->
                                        scope.launch {
                                            errorMessage = ""
                                            successMessage = ""

                                            // 前端先检查文件大小，超过10MB直接提示（避免413错误）
                                            val maxSize = 10 * 1024 * 1024 // 10MB
                                            if (bytes.size > maxSize) {
                                                errorMessage = "图片大小不能超过10MB，请选择更小的图片"
                                                return@launch
                                            }

                                            // 打开裁剪对话框
                                            selectedImageBytes = bytes
                                            selectedImageFileName = fileName
                                            showCropDialog = true
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isUploadingAvatar) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colors.primary,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(40.dp)
                                )
                            } else if (avatarBitmap != null) {
                                Image(
                                    bitmap = avatarBitmap!!,
                                    contentDescription = "头像",
                                    modifier = Modifier.fillMaxSize()
                                )
                                // 编辑模式下悬停显示蒙层
                                if (isEditing && isHoveringAvatar) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.5f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "更换头像",
                                            color = Color.White,
                                            style = MaterialTheme.typography.subtitle2
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    text = username.firstOrNull()?.toString() ?: "U",
                                    style = MaterialTheme.typography.h2, // 更大的默认头像文字
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    color = MaterialTheme.colors.primary
                                )
                            }
                        }
                        if (isEditing) {
                            Text(
                                text = "点击头像可上传新头像",
                                style = MaterialTheme.typography.caption,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.padding(top = 12.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(36.dp))

                        // 基本信息分组
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = 0.dp,
                            shape = RoundedCornerShape(12.dp),
                            backgroundColor = MaterialTheme.colors.surface.copy(alpha = 0.3f)
                        ) {
                            Column(modifier = Modifier.padding(24.dp)) {
                                Text(
                                    text = "基本信息",
                                    style = MaterialTheme.typography.h6,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )

                                // 用户名
                                OutlinedTextField(
                                    value = username,
                                    onValueChange = { username = it },
                                    label = { Text("用户名") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    enabled = isEditing,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = TextFieldDefaults.outlinedTextFieldColors(
                                        focusedBorderColor = MaterialTheme.colors.primary,
                                        unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.12f)
                                    )
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // 个性签名
                                OutlinedTextField(
                                    value = signature,
                                    onValueChange = { signature = it },
                                    label = { Text("个性签名") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = false,
                                    maxLines = 2,
                                    enabled = isEditing,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = TextFieldDefaults.outlinedTextFieldColors(
                                        focusedBorderColor = MaterialTheme.colors.primary,
                                        unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.12f)
                                    )
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // 手机
                                OutlinedTextField(
                                    value = phone,
                                    onValueChange = { phone = it },
                                    label = { Text("手机号码") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    enabled = isEditing,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = TextFieldDefaults.outlinedTextFieldColors(
                                        focusedBorderColor = MaterialTheme.colors.primary,
                                        unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.12f)
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // 邮箱安全分组
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = 0.dp,
                            shape = RoundedCornerShape(12.dp),
                            backgroundColor = MaterialTheme.colors.surface.copy(alpha = 0.3f)
                        ) {
                            Column(modifier = Modifier.padding(24.dp)) {
                                Text(
                                    text = "邮箱与安全",
                                    style = MaterialTheme.typography.h6,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )

                                // 当前邮箱显示
                                if (!isEditing) {
                                    OutlinedTextField(
                                        value = email,
                                        onValueChange = {},
                                        label = { Text("当前邮箱") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        enabled = false,
                                        shape = RoundedCornerShape(12.dp),
                                        colors = TextFieldDefaults.outlinedTextFieldColors(
                                            disabledBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.12f),
                                            disabledTextColor = MaterialTheme.colors.onSurface
                                        )
                                    )
                                } else {
                                    // 邮箱修改区域
                                    Text(
                                        text = "修改邮箱",
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
                                            label = { Text("新邮箱地址") },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                            enabled = isEditing && emailVerifyCountdown == 0 && !isSendingEmailCode,
                                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                                focusedBorderColor = MaterialTheme.colors.primary,
                                                unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.12f)
                                            )
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    sendEmailVerifyCode()
                                                }
                                            },
                                            enabled = isEditing && newEmail.isNotBlank() && newEmail != email && emailVerifyCountdown == 0 && !isSendingEmailCode,
                                            modifier = Modifier.height(56.dp),
                                            shape = RoundedCornerShape(12.dp)
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
                                        Spacer(modifier = Modifier.height(12.dp))
                                        OutlinedTextField(
                                            value = emailVerifyCode,
                                            onValueChange = { emailVerifyCode = it },
                                            label = { Text("邮箱验证码") },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            enabled = isEditing,
                                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                                focusedBorderColor = MaterialTheme.colors.primary,
                                                unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.12f)
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // 修改密码分组 - 仅编辑模式显示
                        if (isEditing) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                elevation = 0.dp,
                                shape = RoundedCornerShape(12.dp),
                                backgroundColor = MaterialTheme.colors.surface.copy(alpha = 0.3f)
                            ) {
                                Column(modifier = Modifier.padding(24.dp)) {
                                    Text(
                                        text = "修改密码（不修改请留空）",
                                        style = MaterialTheme.typography.h6,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                        modifier = Modifier.padding(bottom = 16.dp)
                                    )

                                    OutlinedTextField(
                                        value = currentPassword,
                                        onValueChange = { currentPassword = it },
                                        label = { Text("当前密码") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        visualTransformation = PasswordVisualTransformation(),
                                        enabled = isEditing,
                                        shape = RoundedCornerShape(12.dp),
                                        colors = TextFieldDefaults.outlinedTextFieldColors(
                                            focusedBorderColor = MaterialTheme.colors.primary,
                                            unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.12f)
                                        )
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    // 密码两栏布局
                                    Row(modifier = Modifier.fillMaxWidth()) {
                                        OutlinedTextField(
                                            value = newPassword,
                                            onValueChange = { newPassword = it },
                                            label = { Text("新密码") },
                                            modifier = Modifier.weight(1f).padding(end = 6.dp),
                                            singleLine = true,
                                            visualTransformation = PasswordVisualTransformation(),
                                            enabled = isEditing,
                                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                                focusedBorderColor = MaterialTheme.colors.primary,
                                                unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.12f)
                                            )
                                        )

                                        OutlinedTextField(
                                            value = confirmPassword,
                                            onValueChange = { confirmPassword = it },
                                            label = { Text("确认密码") },
                                            modifier = Modifier.weight(1f).padding(start = 6.dp),
                                            singleLine = true,
                                            visualTransformation = PasswordVisualTransformation(),
                                            enabled = isEditing,
                                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                                focusedBorderColor = MaterialTheme.colors.primary,
                                                unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.12f)
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // 操作提示区域
                        if (errorMessage.isNotBlank() || successMessage.isNotBlank()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                elevation = 0.dp,
                                shape = RoundedCornerShape(12.dp),
                                backgroundColor = if (errorMessage.isNotBlank())
                                    MaterialTheme.colors.error.copy(alpha = 0.1f)
                                else
                                    MaterialTheme.colors.secondary.copy(alpha = 0.1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (errorMessage.isNotBlank()) {
                                        Icon(
                                            imageVector = Icons.Default.Error,
                                            contentDescription = "错误",
                                            tint = MaterialTheme.colors.error,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = errorMessage,
                                            color = MaterialTheme.colors.error,
                                            style = MaterialTheme.typography.body2
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "成功",
                                            tint = MaterialTheme.colors.secondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = successMessage,
                                            color = MaterialTheme.colors.secondary,
                                            style = MaterialTheme.typography.body2
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 头像裁剪对话框
            if (showCropDialog && selectedImageBytes != null) {
                AvatarCropDialog(
                    imageBytes = selectedImageBytes!!,
                    originalFileName = selectedImageFileName,
                    onDismiss = {
                        showCropDialog = false
                        selectedImageBytes = null
                    },
                    onCropComplete = { croppedBytes, fileName ->
                        scope.launch {
                            isUploadingAvatar = true
                            // 上传裁剪后的头像
                            val avatarUrl = FileUploader.uploadAvatar(croppedBytes, fileName)
                            if (avatarUrl != null) {
                                // 重新加载头像
                                avatarBitmap = loadImageBitmapWithCache(avatarUrl, null)
                                successMessage = "头像上传成功"
                                // 更新用户信息缓存
                                user = user?.copy(avatarUrl = avatarUrl)
                            } else {
                                errorMessage = "头像上传失败，请重试"
                            }
                            isUploadingAvatar = false
                        }
                    }
                )
            }
        }
    }
}
