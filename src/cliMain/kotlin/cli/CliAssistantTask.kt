package cli

import kotlinx.coroutines.*

/** One request per session. Cancellation invalidates even an uncooperative late response. */
class CliAssistantTask(private val scope: CoroutineScope, private val request: suspend (String) -> String,
    private val notify: (String) -> Unit) {
    private var task: Job? = null
    private var generation = 0L
    @Synchronized fun start(text: String): Boolean {
        if (task?.isActive == true) return false
        val current = ++generation
        task = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val reply = withTimeout(35_000) { request(text) }
                synchronized(this@CliAssistantTask) {
                    if (generation == current && isActive) notify(reply.ifBlank { "助手暂未返回内容。" })
                }
            } catch (error: Exception) {
                if (error is CancellationException) {
                    if (error is TimeoutCancellationException) synchronized(this@CliAssistantTask) {
                        if (generation == current) notify("助手等待超时，可以继续输入命令。")
                    }
                    throw error
                }
                synchronized(this@CliAssistantTask) { if (generation == current) notify("助手请求未完成，请稍后重试。") }
            }
        }.also { it.start() }
        return true
    }
    @Synchronized fun cancel(): Boolean {
        val running = task?.isActive == true
        ++generation
        task?.cancel(); task = null
        return running
    }
    @Synchronized fun isRunning() = task?.isActive == true
}
