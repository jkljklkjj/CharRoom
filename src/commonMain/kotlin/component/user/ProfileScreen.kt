package component.user

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import component.dialog.AvatarCropDialog
import component.io.FilePicker
import presentation.viewmodel.GlobalProfileViewModel
import presentation.viewmodel.ProfileUiState
import presentation.viewmodel.ProfileViewModel

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
    val uiState by viewModel.uiState.collectAsState()

    var showCropDialog by remember { mutableStateOf(false) }
    var selectedImageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var selectedImageFileName by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.loadUserProfile()
    }

    val currentUser = (uiState as? ProfileUiState.Success)?.user

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colors.surface.copy(alpha = 0.18f),
                shape = RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp),
                elevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(text = "个人资料", style = MaterialTheme.typography.h6)
                        Text(
                            text = "查看和编辑个人信息",
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
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
                        .background(MaterialTheme.colors.background)
                        .padding(padding),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 760.dp)
                            .padding(horizontal = 16.dp, vertical = 16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colors.surface.copy(alpha = 0.20f),
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.08f)),
                            elevation = 0.dp
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = if (viewModel.isEditing.value) "编辑资料" else "个人信息",
                                            style = MaterialTheme.typography.h6
                                        )
                                        Text(
                                            text = if (viewModel.isEditing.value) "修改后记得保存" else "点击编辑后可以更新资料",
                                            style = MaterialTheme.typography.caption,
                                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                                        )
                                    }

                                    if (!viewModel.isEditing.value) {
                                        Button(
                                            onClick = viewModel::enterEditMode,
                                            modifier = Modifier.height(40.dp),
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
                                            Text("编辑资料")
                                        }
                                    } else {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(
                                                onClick = viewModel::resetChanges,
                                                modifier = Modifier.height(40.dp),
                                                shape = RoundedCornerShape(14.dp)
                                            ) {
                                                Text("取消")
                                            }
                                            Button(
                                                onClick = { viewModel.saveProfile(onSuccess = onProfileUpdated) },
                                                modifier = Modifier.height(40.dp),
                                                enabled = !viewModel.isSaving.value,
                                                shape = RoundedCornerShape(14.dp)
                                            ) {
                                                if (viewModel.isSaving.value) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(18.dp),
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

                                Spacer(modifier = Modifier.height(18.dp))

                                val avatarInteractionSource = remember { MutableInteractionSource() }
                                val isHoveringAvatar by avatarInteractionSource.collectIsHoveredAsState()
                                Box(
                                    modifier = Modifier
                                        .size(136.dp)
                                        .shadow(
                                            elevation = if (isHoveringAvatar && viewModel.isEditing.value) 14.dp else 8.dp,
                                            shape = CircleShape,
                                            clip = true
                                        )
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colors.primary.copy(alpha = 0.10f))
                                        .hoverable(
                                            interactionSource = avatarInteractionSource,
                                            enabled = viewModel.isEditing.value
                                        )
                                        .clickable(enabled = viewModel.isEditing.value && !viewModel.isUploadingAvatar.value) {
                                            FilePicker.pickImage { bytes, fileName ->
                                                val maxSize = 10 * 1024 * 1024
                                                if (bytes.size > maxSize) {
                                                    viewModel.errorMessage.value = "图片大小不能超过10MB，请选择更小的图片"
                                                    return@pickImage
                                                }
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
                                            modifier = Modifier.size(36.dp)
                                        )
                                    } else if (viewModel.avatarBitmap.value != null) {
                                        Image(
                                            bitmap = viewModel.avatarBitmap.value!!,
                                            contentDescription = "头像",
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        if (viewModel.isEditing.value && isHoveringAvatar) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(Color.Black.copy(alpha = 0.45f)),
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
                                            style = MaterialTheme.typography.h2,
                                            color = MaterialTheme.colors.primary
                                        )
                                    }
                                }

                                if (viewModel.isEditing.value) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "点击头像可上传新头像",
                                        style = MaterialTheme.typography.caption,
                                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                                    )
                                } else {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Surface(
                                        color = MaterialTheme.colors.secondary.copy(alpha = 0.14f),
                                        shape = RoundedCornerShape(999.dp)
                                    ) {
                                        Text(
                                            text = if (currentUser?.phone.isNullOrBlank()) "资料未完善" else "资料已完善",
                                            style = MaterialTheme.typography.caption,
                                            color = MaterialTheme.colors.secondary,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colors.surface.copy(alpha = 0.20f),
                            shape = RoundedCornerShape(22.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.08f)),
                            elevation = 0.dp
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Text(text = "基本信息", style = MaterialTheme.typography.subtitle1)
                                Spacer(modifier = Modifier.height(14.dp))

                                OutlinedTextField(
                                    value = viewModel.username.value,
                                    onValueChange = { viewModel.username.value = it },
                                    label = { Text("用户名") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    enabled = viewModel.isEditing.value,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = TextFieldDefaults.outlinedTextFieldColors(
                                        focusedBorderColor = MaterialTheme.colors.primary,
                                        unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.12f)
                                    )
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedTextField(
                                    value = viewModel.signature.value,
                                    onValueChange = { viewModel.signature.value = it },
                                    label = { Text("个性签名") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = false,
                                    maxLines = 2,
                                    enabled = viewModel.isEditing.value,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = TextFieldDefaults.outlinedTextFieldColors(
                                        focusedBorderColor = MaterialTheme.colors.primary,
                                        unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.12f)
                                    )
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedTextField(
                                    value = viewModel.phone.value,
                                    onValueChange = { viewModel.phone.value = it },
                                    label = { Text("手机号码") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    enabled = viewModel.isEditing.value,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = TextFieldDefaults.outlinedTextFieldColors(
                                        focusedBorderColor = MaterialTheme.colors.primary,
                                        unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.12f)
                                    )
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colors.surface.copy(alpha = 0.20f),
                            shape = RoundedCornerShape(22.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.08f)),
                            elevation = 0.dp
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Text(text = "邮箱与安全", style = MaterialTheme.typography.subtitle1)
                                Spacer(modifier = Modifier.height(14.dp))

                                if (!viewModel.isEditing.value) {
                                    OutlinedTextField(
                                        value = viewModel.email.value,
                                        onValueChange = {},
                                        label = { Text("当前邮箱") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        enabled = false,
                                        shape = RoundedCornerShape(14.dp),
                                        colors = TextFieldDefaults.outlinedTextFieldColors(
                                            disabledBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.12f),
                                            disabledTextColor = MaterialTheme.colors.onSurface
                                        )
                                    )
                                } else {
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
                                            shape = RoundedCornerShape(14.dp),
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
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
                                            if (viewModel.isSendingEmailCode.value) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(18.dp),
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
                                            shape = RoundedCornerShape(14.dp),
                                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                                focusedBorderColor = MaterialTheme.colors.primary,
                                                unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.12f)
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        if (viewModel.isEditing.value) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colors.surface.copy(alpha = 0.20f),
                                shape = RoundedCornerShape(22.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.08f)),
                                elevation = 0.dp
                            ) {
                                Column(modifier = Modifier.padding(18.dp)) {
                                    Text(text = "修改密码", style = MaterialTheme.typography.subtitle1)
                                    Text(
                                        text = "不修改请留空",
                                        style = MaterialTheme.typography.caption,
                                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                                    )

                                    Spacer(modifier = Modifier.height(14.dp))

                                    OutlinedTextField(
                                        value = viewModel.currentPassword.value,
                                        onValueChange = { viewModel.currentPassword.value = it },
                                        label = { Text("当前密码") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        visualTransformation = PasswordVisualTransformation(),
                                        enabled = viewModel.isEditing.value,
                                        shape = RoundedCornerShape(14.dp),
                                        colors = TextFieldDefaults.outlinedTextFieldColors(
                                            focusedBorderColor = MaterialTheme.colors.primary,
                                            unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.12f)
                                        )
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Row(modifier = Modifier.fillMaxWidth()) {
                                        OutlinedTextField(
                                            value = viewModel.newPassword.value,
                                            onValueChange = { viewModel.newPassword.value = it },
                                            label = { Text("新密码") },
                                            modifier = Modifier.weight(1f).padding(end = 6.dp),
                                            singleLine = true,
                                            visualTransformation = PasswordVisualTransformation(),
                                            enabled = viewModel.isEditing.value,
                                            shape = RoundedCornerShape(14.dp),
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
                                            shape = RoundedCornerShape(14.dp),
                                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                                focusedBorderColor = MaterialTheme.colors.primary,
                                                unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.12f)
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        if (viewModel.errorMessage.value.isNotBlank() || viewModel.successMessage.value.isNotBlank()) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = if (viewModel.errorMessage.value.isNotBlank()) {
                                    MaterialTheme.colors.error.copy(alpha = 0.10f)
                                } else {
                                    MaterialTheme.colors.secondary.copy(alpha = 0.10f)
                                },
                                shape = RoundedCornerShape(18.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.06f)),
                                elevation = 0.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
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

                    if (showCropDialog && selectedImageBytes != null) {
                        AvatarCropDialog(
                            imageBytes = selectedImageBytes!!,
                            originalFileName = selectedImageFileName,
                            onDismiss = {
                                showCropDialog = false
                                selectedImageBytes = null
                                selectedImageFileName = ""
                            },
                            onCropComplete = { croppedBytes, croppedFileName ->
                                viewModel.uploadAvatar(croppedBytes, croppedFileName)
                                showCropDialog = false
                                selectedImageBytes = null
                                selectedImageFileName = ""
                            }
                        )
                    }
                }
            }
        }
    }
}