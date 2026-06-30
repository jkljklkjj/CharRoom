package component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import core.state.GlobalAppState
import core.getTokenQuota
import core.getTokenPrices
import core.purchaseTokens
import model.QuotaInfo
import model.TokenPrices
// QR code library removed for Android compatibility
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Token 配额管理页面
 */
@Composable
fun TokenQuotaScreen(
    onBack: () -> Unit
) {
    val s = com.chatlite.i18n.LocalStrings.current
    val scope = rememberCoroutineScope()
    val token = GlobalAppState.currentToken ?: ""

    var loading by remember { mutableStateOf(true) }
    var quota by remember { mutableStateOf<QuotaInfo?>(null) }
    var prices by remember { mutableStateOf<TokenPrices?>(null) }
    var purchaseAmount by remember { mutableStateOf(10) }
    var qrUrl by remember { mutableStateOf("") }
    var purchaseId by remember { mutableStateOf(0L) }
    var errMsg by remember { mutableStateOf("") }

    // 加载配额信息
    LaunchedEffect(Unit) {
        loading = true
        try {
            quota = withContext(Dispatchers.IO) { getTokenQuota(token) }
            prices = withContext(Dispatchers.IO) { getTokenPrices(token) }
        } catch (_: Exception) { }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s["app.settings.tokenQuota"]) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←")
                    }
                }
            )
        },
        backgroundColor = MaterialTheme.colors.background
    ) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val q = quota ?: return@Scaffold

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // 余额
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                elevation = 4.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("账户余额", fontSize = 13.sp, color = Color.Gray)
                    Text(
                        text = String.format("%.2f", q.balanceYuan),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colors.primary
                    )
                    Text("元", fontSize = 14.sp, color = Color.Gray)
                }
            }

            Spacer(Modifier.height(16.dp))

            // 本周免费
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                elevation = 2.dp
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("本周免费额度", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("免费剩余", fontSize = 13.sp, color = Color.Gray)
                        Text(
                            String.format("%.2f", q.freeRemainingYuan) + " 元",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colors.primary
                        )
                    }
                    Spacer(Modifier.height(4.dp))

                    // 进度条
                    val pct = q.weeklyFreePct.coerceIn(0.0, 100.0)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0xFFEEEEEE))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(pct.toFloat() / 100f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(5.dp))
                                .background(MaterialTheme.colors.primary)
                        )
                    }

                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("本周已用: ${String.format("%.2f", q.weeklyCostFen / 100.0)} 元", fontSize = 11.sp, color = Color.Gray)
                        Text("in ${q.weeklyInputUsed} / out ${q.weeklyOutputUsed}", fontSize = 11.sp, color = Color.Gray)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // 充值
            Text("充值", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Spacer(Modifier.height(8.dp))

            val presets = listOf(10, 20, 50, 100, 200)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.forEach { amount ->
                    Button(
                        onClick = { purchaseAmount = amount },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = if (purchaseAmount == amount)
                                MaterialTheme.colors.primary else Color(0xFFEEEEEE),
                            contentColor = if (purchaseAmount == amount)
                                Color.White else Color.DarkGray
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("${amount}元", fontSize = 13.sp)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = if (purchaseAmount !in presets) purchaseAmount.toString() else "",
                onValueChange = {
                    purchaseAmount = it.toIntOrNull() ?: purchaseAmount
                },
                label = { Text("自定义金额") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            if (purchaseAmount > 0) {
                Text(
                    "约可消耗 输入${String.format("%.0f", purchaseAmount / 1.2 * 100)}万 / 输出${String.format("%.0f", purchaseAmount / 2.5 * 100)}万",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            // 支付二维码
            if (qrUrl.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    elevation = 2.dp
                ) {
                    Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("请使用微信扫码支付", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Spacer(Modifier.height(20.dp))
                        // 支付链接（二维码跨平台兼容性差，改用 URL 展示）
                        Text(
                            text = qrUrl,
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            softWrap = true
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("请使用微信扫描二维码支付", fontSize = 13.sp, color = Color.Gray)
                        Spacer(Modifier.height(20.dp))
                        Button(
                            onClick = { scope.launch { confirmPay(purchaseId, token, scope, onBack) } },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("已完成支付", fontSize = 15.sp)
                        }
                    }
                }
            } else {
                Button(
                    onClick = {
                        scope.launch {
                            val fen = purchaseAmount * 100
                            val result = withContext(Dispatchers.IO) { purchaseTokens(token, fen) }
                            if (result != null) {
                                qrUrl = result.codeUrl
                                purchaseId = result.purchaseId
                            } else {
                                errMsg = "创建订单失败"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    enabled = purchaseAmount > 0
                ) {
                    Text("立即充值", fontSize = 15.sp)
                }
            }

            if (errMsg.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(errMsg, color = MaterialTheme.colors.error, fontSize = 13.sp)
            }
        }
    }
}

private suspend fun confirmPay(purchaseId: Long, token: String, scope: kotlinx.coroutines.CoroutineScope, onBack: () -> Unit) {
    if (purchaseId <= 0) return
    try {
        withContext(Dispatchers.IO) {
            core.confirmPurchase(token, purchaseId)
        }
        // 刷新页面
        scope.launch {
            onBack()
        }
    } catch (_: Exception) { }
}

/**
 * 打开支付链接 — 平台相关
 * Desktop: 用 java.awt.Desktop 打开浏览器
 * Android: 调用微信 SDK
 */
fun openPaymentUrl(url: String) { }
