package component.user

import component.dialog.AvatarCropDialog
import component.io.FilePicker
import presentation.viewmodel.ProfileViewModel
import presentation.viewmodel.ProfileUiState
import presentation.viewmodel.GlobalProfileViewModel

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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/**
 * 个人信息页面
 * UI层只负责渲染和事件转发，业务逻辑由ProfileViewModel处理
 */
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onProfileUpdated: () -> Unit = {},
    viewModel: ProfileViewModel = GlobalProfileViewModel
) {
    // 观察ViewModel状态
    val uiState by viewModel.uiState.collectAsState()

    // 头像裁剪相关状态
    var showCropDialog by remember { mutableStateOf(false) }
    var selectedImageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var selectedImageFileName by remember { mutableStateOf("") }

    // 加载用户信息
    LaunchedEffect(Unit) {
        viewModel.loadUserProfile()
    }

    // 获取当前用户
    val currentUser = (uiState as? ProfileUiState.Success)?.user

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
        when (uiState) {
            is ProfileUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is ProfileUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = (uiState as ProfileUiState.Error).message,
                        color = MaterialTheme.colors.error
                    )
                }
            }
            is ProfileUiState.Success -> {
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
                                if (!viewModel.isEditing.value) {
                                    Text(
                                        text = "个人信息",
                                        style = MaterialTheme.typography.h5, // 更大的标题
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                    )
                                    Button(
                                        onClick = viewModel::enterEditMode,
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
                                            onClick = viewModel::resetChanges,
                                            modifier = Modifier.height(40.dp).padding(end = 8.dp),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text("取消")
                                        }
                                        Button(
                                            onClick = {
                                                viewModel.saveProfile(onSuccess = onProfileUpdated)
                                            },
                                            modifier = Modifier.height(40.dp),
                                            enabled = !viewModel.isSaving.value,
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            if (viewModel.isSaving.value) {
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
                            val avatarInteractionSource = remember { MutableInteractionSource() }
                            val isHoveringAvatar by avatarInteractionSource.collectIsHoveredAsState()
                            Box(
                                modifier = Modifier
                                    .size(140.dp) // 增大头像尺寸
                                    .shadow(
                                        elevation = if (isHoveringAvatar && viewModel.isEditing.value) 16.dp else 8.dp,
                                        shape = CircleShape,
                                        clip = true
                                    )
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colors.primary.copy(alpha = 0.1f))
                                    .hoverable(interactionSource = avatarInteractionSource, enabled = viewModel.isEditing.value) // 悬停效果
                                    .clickable(enabled = viewModel.isEditing.value && !viewModel.isUploadingAvatar.value) { // 只有编辑模式才能上传头像
                                        FilePicker.pickImage { bytes, fileName ->
                                            // 前端先检查文件大小，超过10MB直接提示（避免413错误）
                                            val maxSize = 10 * 1024 * 1024 // 10MB
                                            if (bytes.size > maxSize) {
                                                viewModel.errorMessage.value = "图片大小不能超过10MB，请选择更小的图片"
                                                return@pickImage
                                            }

                                            // 打开裁剪对话框
                                            selectedImageBytes = bytes
                                            selectedImageFileName = fileName
                                            showCropDialog = true
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (viewModel.isUploadingAvatar.value) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colors.primary,
                                        strokeWidth = 3.dp,
                                        modifier = Modifier.size(40.dp)
                                    )
                                } else if (viewModel.avatarBitmap.value != null) {
                                    Image(
                                        bitmap = viewModel.avatarBitmap.value!!,
                                        contentDescription = "头像",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    // 编辑模式下悬停显示蒙层
                                    if (viewModel.isEditing.value && isHoveringAvatar) {
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
                                        text = viewModel.username.value.firstOrNull()?.toString() ?: "U",
                                        style = MaterialTheme.typography.h2, // 更大的默认头像文字
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                        color = MaterialTheme.colors.primary
                                    )
                                }
                            }
                            if (viewModel.isEditing.value) {
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
                                        value = viewModel.username.value,
                                        onValueChange = { viewModel.username.value = it },
                                        label = { Text("用户名") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        enabled = viewModel.isEditing.value,
                                        shape = RoundedCornerShape(12.dp),
                                        colors = TextFieldDefaults.outlinedTextFieldColors(
                                            focusedBorderColor = MaterialTheme.colors.primary,
                                            unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.12f)
                                        )
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // 个性签名
                                    OutlinedTextField(
                                        value = viewModel.signature.value,
                                        onValueChange = { viewModel.signature.value = it },
                                        label = { Text("个性签名") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = false,
                                        maxLines = 2,
                                        enabled = viewModel.isEditing.value,
                                        shape = RoundedCornerShape(12.dp),
                                        colors = TextFieldDefaults.outlinedTextFieldColors(
                                            focusedBorderColor = MaterialTheme.colors.primary,
                                            unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.12f)
                                        )
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // 手机
                                    OutlinedTextField(
                                        value = viewModel.phone.value,
                                        onValueChange = { viewModel.phone.value = it },
                                        label = { Text("手机号码") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                        enabled = viewModel.isEditing.value,
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
                                    if (!viewModel.isEditing.value) {
                                        OutlinedTextField(
                                            value = viewModel.email.value,
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
                                                value = viewModel.newEmail.value,
                                                onValueChange = { viewModel.newEmail.value = it },
                                                label = { Text("新邮箱地址") },
                                                modifier = Modifier.weight(1f),
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                                enabled = viewModel.isEditing.value && viewModel.emailVerifyCountdown.value == 0 && !viewModel.isSendingEmailCode.value,
                                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                                colors = TextFieldDefaults.outlinedTextFieldColors(
                                                    focusedBorderColor = MaterialTheme.colors.primary,
                                                    unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.12f)
                                                )
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Button(
                                                onClick = viewModel::sendEmailVerifyCode,
                                                enabled = viewModel.isEditing.value && viewModel.newEmail.value.isNotBlank() && viewModel.newEmail.value != viewModel.email.value && viewModel.emailVerifyCountdown.value == 0 && !viewModel.isSendingEmailCode.value,
                                                modifier = Modifier.height(56.dp),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                if (viewModel.isSendingEmailCode.value) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(20.dp),
                                                        color = MaterialTheme.colors.onPrimary,
                                                        strokeWidth = 2.dp
                                                    )
                                                } else if (viewModel.emailVerifyCountdown.value > 0) {
                                                    Text("${viewModel.emailVerifyCountdown.value}s")
                                                } else {
                                                    Text("发送验证码")
                                                }
                                            }
                                        }

                                        if (viewModel.newEmail.value != viewModel.email.value) {
                                            Spacer(modifier = Modifier.height(12.dp))
                                            OutlinedTextField(
                                                value = viewModel.emailVerifyCode.value,
                                                onValueChange = { viewModel.emailVerifyCode.value = it },
                                                label = { Text("邮箱验证码") },
                                                modifier = Modifier.fillMaxWidth(),
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                enabled = viewModel.isEditing.value,
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
                            if (viewModel.isEditing.value) {
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
                                            value = viewModel.currentPassword.value,
                                            onValueChange = { viewModel.currentPassword.value = it },
                                            label = { Text("当前密码") },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true,
                                            visualTransformation = PasswordVisualTransformation(),
                                            enabled = viewModel.isEditing.value,
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
                                                value = viewModel.newPassword.value,
                                                onValueChange = { viewModel.newPassword.value = it },
                                                label = { Text("新密码") },
                                                modifier = Modifier.weight(1f).padding(end = 6.dp),
                                                singleLine = true,
                                                visualTransformation = PasswordVisualTransformation(),
                                                enabled = viewModel.isEditing.value,
                                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                                colors = TextFieldDefaults.outlinedTextFieldColors(
                                                    focusedBorderColor = MaterialTheme.colors.primary,
                                                    unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.12f)
                                                )
                                            )

                                            OutlinedTextField(
                                                value = viewModel.confirmPassword.value,
                                                onValueChange = { viewModel.confirmPassword.value = it },
                                                label = { Text("确认密码") },
                                                modifier = Modifier.weight(1f).padding(start = 6.dp),
                                                singleLine = true,
                                                visualTransformation = PasswordVisualTransformation(),
                                                enabled = viewModel.isEditing.value,
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
                            if (viewModel.errorMessage.value.isNotBlank() || viewModel.successMessage.value.isNotBlank()) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    elevation = 0.dp,
                                    shape = RoundedCornerShape(12.dp),
                                    backgroundColor = if (viewModel.errorMessage.value.isNotBlank())
                                        MaterialTheme.colors.error.copy(alpha = 0.1f)
                                    else
                                        MaterialTheme.colors.secondary.copy(alpha = 0.1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (viewModel.errorMessage.value.isNotBlank()) {
                                            Icon(
                                                imageVector = Icons.Default.Error,
                                                contentDescription = "错误",
                                                tint = MaterialTheme.colors.error,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = viewModel.errorMessage.value,
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
                                                text = viewModel.successMessage.value,
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
                            viewModel.uploadAvatar(croppedBytes, fileName)
                            showCropDialog = false
                            selectedImageBytes = null
                        }
                    )
                }
            }
        }
    }
}
