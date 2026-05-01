package com.chatlite.charroom

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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import core.loadImageBitmapFromUrl

@Composable
fun UserDetailScreen(
    user: LocalUser,
    token: String,
    onBack: () -> Unit
) {
    var detailUser by remember { mutableStateOf(user) }
    var avatarBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(user.id, token) {
        isLoading = true
        errorMessage = ""
        val repo = NetworkRepository.getInstance()
        try {
            val loaded = repo.getUserDetail(user.id.toString(), token)
            if (loaded != null) {
                detailUser = loaded
                loaded.avatarUrl?.takeIf { it.isNotBlank() }?.let { url ->
                    avatarBitmap = loadImageBitmapFromUrl(url, url)
                }
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
                title = { Text("用户详情") },
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
                            text = detailUser.username.firstOrNull()?.uppercase() ?: "U",
                            style = MaterialTheme.typography.h3,
                            color = MaterialTheme.colors.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(text = detailUser.username, style = MaterialTheme.typography.h4, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "用户ID：${detailUser.id}",
                    style = MaterialTheme.typography.subtitle2,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(16.dp))

                detailUser.signature?.takeIf { it.isNotBlank() }?.let {
                    Text(text = it, style = MaterialTheme.typography.body1)
                    Spacer(modifier = Modifier.height(16.dp))
                }

                DetailField(label = "邮箱", value = detailUser.email)
                DetailField(label = "电话", value = detailUser.phone)
                DetailField(label = "状态", value = if (detailUser.online) "在线" else "离线")

                Spacer(modifier = Modifier.height(24.dp))
                if (!detailUser.isGroup) {
                    Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                        Text("发送消息")
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailField(label: String, value: String?) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, style = MaterialTheme.typography.caption, color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f))
        Text(text = value ?: "未公开", style = MaterialTheme.typography.body1)
        Spacer(modifier = Modifier.height(12.dp))
    }
}
