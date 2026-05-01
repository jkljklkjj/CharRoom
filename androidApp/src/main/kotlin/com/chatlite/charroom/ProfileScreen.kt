package com.chatlite.charroom

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import component.FilePicker
import core.loadImageBitmapFromUrl
import core.uploadAvatar
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    token: String,
    currentUserId: Int,
    onBack: () -> Unit
) {
    var user by remember { mutableStateOf<LocalUser?>(null) }
    var avatarBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(currentUserId, token) {
        isLoading = true
        errorMessage = ""
        val repo = NetworkRepository.getInstance()
        try {
            val profile = repo.getUserDetail(currentUserId.toString(), token)
            if (profile != null) {
                user = profile
                profile.avatarUrl?.takeIf { it.isNotBlank() }?.let { url ->
                    avatarBitmap = loadImageBitmapFromUrl(url, url)
                }
            } else {
                errorMessage = "无法加载个人信息"
            }
        } catch (e: Exception) {
            errorMessage = "加载失败：${e.message ?: "未知错误"}"
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("个人资料") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                backgroundColor = MaterialTheme.colors.primary,
                contentColor = MaterialTheme.colors.onPrimary
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Box
            }

            if (errorMessage.isNotBlank()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = errorMessage, color = MaterialTheme.colors.error)
                }
                return@Box
            }

            user?.let { profile ->
                var editableUsername by remember(profile) { mutableStateOf(profile.username) }
                var editablePhone by remember(profile) { mutableStateOf(profile.phone ?: "") }
                var editableSignature by remember(profile) { mutableStateOf(profile.signature ?: "") }
                var isEditing by remember { mutableStateOf(false) }
                var successMessage by remember { mutableStateOf("") }
                var saveLoading by remember { mutableStateOf(false) }

                fun saveProfile() {
                    errorMessage = ""
                    successMessage = ""
                    saveLoading = true
                    scope.launch {
                        try {
                            val success = NetworkRepository.getInstance().updateUserProfile(
                                currentUserId.toString(),
                                token,
                                editableUsername.trim(),
                                editablePhone.trim(),
                                editableSignature.trim().ifBlank { null }
                            )
                            if (!success) throw Exception("保存失败")
                            user = profile.copy(
                                username = editableUsername.trim(),
                                phone = editablePhone.trim().ifBlank { null },
                                signature = editableSignature.trim().ifBlank { null }
                            )
                            successMessage = "个人资料已保存"
                            isEditing = false
                        } catch (e: Exception) {
                            errorMessage = "保存失败：${e.message ?: "网络异常"}"
                        } finally {
                            saveLoading = false
                        }
                    }
                }

                fun showAvatar(bytes: ByteArray) {
                    avatarBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size).asImageBitmap()
                }

                fun pickAvatar() {
                    FilePicker.pickImage { bytes, fileName ->
                        successMessage = ""
                        errorMessage = ""
                        isLoading = true
                        scope.launch {
                            try {
                                val avatarUrl = uploadAvatar(token, bytes, fileName)
                                if (avatarUrl.isNullOrBlank()) throw Exception("上传失败")
                                user = user?.copy(avatarUrl = avatarUrl)
                                showAvatar(bytes)
                                successMessage = "头像已更新"
                            } catch (e: Exception) {
                                errorMessage = "头像上传失败：${e.message ?: "请重试"}"
                            } finally {
                                isLoading = false
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(112.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colors.primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (avatarBitmap != null) {
                            Image(
                                bitmap = avatarBitmap!!,
                                contentDescription = "头像",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(
                                text = profile.username.firstOrNull()?.uppercase() ?: "U",
                                style = MaterialTheme.typography.h3,
                                color = MaterialTheme.colors.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { pickAvatar() },
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("更换头像")
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "用户名",
                        style = MaterialTheme.typography.subtitle2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editableUsername,
                        onValueChange = { editableUsername = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = isEditing
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "邮箱",
                        style = MaterialTheme.typography.subtitle2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = profile.email ?: "未设置",
                        style = MaterialTheme.typography.body1,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "电话",
                        style = MaterialTheme.typography.subtitle2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editablePhone,
                        onValueChange = { editablePhone = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = isEditing
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "个性签名",
                        style = MaterialTheme.typography.subtitle2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editableSignature,
                        onValueChange = { editableSignature = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 96.dp),
                        maxLines = 4,
                        enabled = isEditing
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "状态：${if (profile.online) "在线" else "离线"}",
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "用户ID：${profile.id}",
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                    if (!isEditing) {
                        Button(onClick = { isEditing = true }, modifier = Modifier.height(44.dp)) {
                            Text("编辑资料")
                        }
                    } else {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            OutlinedButton(
                                onClick = {
                                    editableUsername = profile.username
                                    editablePhone = profile.phone ?: ""
                                    editableSignature = profile.signature ?: ""
                                    isEditing = false
                                    errorMessage = ""
                                    successMessage = ""
                                },
                                modifier = Modifier.height(44.dp)
                            ) {
                                Text("取消")
                            }
                            Button(
                                onClick = { saveProfile() },
                                enabled = !saveLoading,
                                modifier = Modifier.height(44.dp)
                            ) {
                                if (saveLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = MaterialTheme.colors.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text("保存")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    if (successMessage.isNotBlank()) {
                        Text(text = successMessage, color = MaterialTheme.colors.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    if (errorMessage.isNotBlank()) {
                        Text(text = errorMessage, color = MaterialTheme.colors.error)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            } ?: run {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "未获取到个人信息")
                }
            }
        }
    }
}

@Composable
private fun ProfileField(label: String, value: String?) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, style = MaterialTheme.typography.caption, color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f))
        Text(text = value ?: "未设置", style = MaterialTheme.typography.body1)
        Spacer(modifier = Modifier.height(12.dp))
    }
}
