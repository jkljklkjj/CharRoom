package com.chatlite.component.call

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatlite.i18n.LocalStrings

/**
 * 通话界面 — 处理来电、去电、通话中的 UI 展示。
 */
@Composable
fun CallScreen(
    callManager: CallManager,
    onDismiss: () -> Unit
) {
    val s = LocalStrings.current
    val state by remember { mutableStateOf(callManager.state) }

    // 监听状态变化
    LaunchedEffect(callManager) {
        callManager.onStateChanged = { newState ->
            // 通话结束后自动关闭
            if (newState is CallState.Ended) {
                kotlinx.coroutines.delay(2000)
                onDismiss()
            }
        }
    }

    if (state is CallState.Idle) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (val callState = state) {
                is CallState.Calling -> CallingContent(callState, callManager, s["call.cancelling"], s["call.cancel"])
                is CallState.Incoming -> IncomingContent(callState, callManager, s["call.accept"], s["call.reject"])
                is CallState.Connected -> ConnectedContent(callManager, s["call.hangup"])
                is CallState.Ended -> EndedContent(callState)
                else -> {}
            }
        }
    }
}

@Composable
private fun CallingContent(
    state: CallState.Calling,
    callManager: CallManager,
    callingText: String,
    cancelText: String
) {
    Text(
        text = callingText.format(state.calleeName),
        color = Color.White,
        fontSize = 20.sp,
        fontWeight = FontWeight.Medium
    )

    Spacer(modifier = Modifier.height(40.dp))

    // 本地预览（占位）
    Box(
        modifier = Modifier
            .size(120.dp)
            .clip(CircleShape)
            .background(Color(0xFF16213E)),
        contentAlignment = Alignment.Center
    ) {
        Text("📷", fontSize = 40.sp)
    }

    Spacer(modifier = Modifier.height(60.dp))

    Button(
        onClick = { callManager.hangup() },
        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFE94560)),
        modifier = Modifier.size(64.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text("✕", fontSize = 24.sp, color = Color.White)
    }
}

@Composable
private fun IncomingContent(
    state: CallState.Incoming,
    callManager: CallManager,
    acceptText: String,
    rejectText: String
) {
    Text(
        text = "${state.callerName} 邀请你视频通话",
        color = Color.White,
        fontSize = 20.sp,
        fontWeight = FontWeight.Medium
    )

    Spacer(modifier = Modifier.height(40.dp))

    Row(
        horizontalArrangement = Arrangement.spacedBy(40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 拒绝
        Button(
            onClick = { callManager.rejectCall(state.callerId) },
            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFE94560)),
            modifier = Modifier.size(64.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text("✕", fontSize = 24.sp, color = Color.White)
        }

        // 接听
        Button(
            onClick = { callManager.acceptCall(state.callerId) },
            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF2E7D32)),
            modifier = Modifier.size(64.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text("📞", fontSize = 24.sp)
        }
    }
}

@Composable
private fun ConnectedContent(
    callManager: CallManager,
    hangupText: String
) {
    // 远程视频（占位）
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(400.dp)
            .background(Color(0xFF16213E))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("远程视频", color = Color.White.copy(alpha = 0.5f), fontSize = 18.sp)
    }

    Spacer(modifier = Modifier.height(16.dp))

    // 本地小窗预览
    Box(
        modifier = Modifier
            .size(120.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(Color(0xFF0F3460)),
        contentAlignment = Alignment.Center
    ) {
        Text("📷", fontSize = 32.sp)
    }

    Spacer(modifier = Modifier.height(32.dp))

    // 挂断按钮
    Button(
        onClick = { callManager.hangup() },
        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFE94560)),
        modifier = Modifier.size(64.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text("✕", fontSize = 24.sp, color = Color.White)
    }
}

@Composable
private fun EndedContent(state: CallState.Ended) {
    Text(
        text = state.reason.ifEmpty { "通话已结束" },
        color = Color.White.copy(alpha = 0.7f),
        fontSize = 18.sp
    )
}
