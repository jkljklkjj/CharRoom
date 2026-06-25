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
import component.AppTopBar
import component.dialog.AvatarCropDialog
import component.io.FilePicker
import com.chatlite.i18n.LocalStrings
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

    val s = LocalStrings.current

    val currentUser = (uiState as? ProfileUiState.Success)?.user

    Scaffold(
        topBar = {
            AppTopBar(
                title = s["profile.title"],
                subtitle = s["profile.subtitle"],
                onBack = onBack,
                modifier = Modifier.statusBarsPadding()
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
                                            text = if (viewModel.isEditing.value) s["profile.edit.title"] else s["profile.title"],
                                            style = MaterialTheme.typography.h6
                                        )
                                        Text(
                                            text = if (viewModel.isEditing.value) s["profile.edit.reminder"] else s["profile.edit.click.hint"],
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
                                            Text(s["profile.edit"])
                                        }
                                    } else {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(
                                                onClick = viewModel::resetChanges,
                                                modifier = Modifier.height(40.dp),
                                                shape = RoundedCornerShape(14.dp)
                                            ) {
                                                Text(s["profile.cancel"])
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
                                                    Text(s["profile.saving"])
                                                } else {
                                                    Text(s["profile.save"])
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
                                                val maxSize = 500 * 1024 // 500KB
                                                if (bytes.size > maxSize) {
                                                    viewModel.errorMessage.value = s["profile.avatar.size.limit"]
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
                                        // 使用 clip 确保图片不会超出圆形边界
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Image(
                                                bitmap = viewModel.avatarBitmap.value!!,
                                                contentDescription = s["user.avatar"],
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(CircleShape)
                                            )
                                        }

                                        if (viewModel.isEditing.value && isHoveringAvatar) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(Color.Black.copy(alpha = 0.45f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = s["profile.change.avatar"],
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
                                        text = s["profile.avatar.hint"],
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
                                            text = if (currentUser?.phone.isNullOrBlank()) s["profile.incomplete"] else s["profile.complete"],
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
                                Text(text = s["profile.basic.info"], style = MaterialTheme.typography.subtitle1)
                                Spacer(modifier = Modifier.height(14.dp))

                                OutlinedTextField(
                                    value = viewModel.username.value,
                                    onValueChange = { viewModel.username.value = it },
                                    label = { Text(s["profile.username"]) },
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
                                    label = { Text(s["profile.signature"]) },
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
                                    label = { Text(s["profile.phone"]) },
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
                                Text(text = s["profile.email.security"], style = MaterialTheme.typography.subtitle1)
                                Spacer(modifier = Modifier.height(14.dp))

                                if (!viewModel.isEditing.value) {
                                    OutlinedTextField(
                                        value = viewModel.email.value,
                                        onValueChange = {},
                                        label = { Text(s["profile.current.email"]) },
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
                                        text = s["profile.change.email"],
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
                                            label = { Text(s["profile.new.email"]) },
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
                                                Text(s["profile.send.code"])
                                            }
                                        }
                                    }

                                    if (viewModel.newEmail.value != viewModel.email.value) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        OutlinedTextField(
                                            value = viewModel.emailVerifyCode.value,
                                            onValueChange = { viewModel.emailVerifyCode.value = it },
                                            label = { Text(s["profile.email.verify.code"]) },
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
                                    Text(text = s["profile.change.password"], style = MaterialTheme.typography.subtitle1)
                                    Text(
                                        text = s["profile.password.leave.blank"],
                                        style = MaterialTheme.typography.caption,
                                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                                    )

                                    Spacer(modifier = Modifier.height(14.dp))

                                    OutlinedTextField(
                                        value = viewModel.currentPassword.value,
                                        onValueChange = { viewModel.currentPassword.value = it },
                                        label = { Text(s["profile.current.password"]) },
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
                                            label = { Text(s["profile.new.password"]) },
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
                                            label = { Text(s["profile.confirm.password"]) },
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
                                            contentDescription = s["profile.error"],
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
                                            contentDescription = s["profile.success"],
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