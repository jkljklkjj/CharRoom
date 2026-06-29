package core.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * LRU 淘汰的消息缓存，自带 [StateFlow] 响应式同步。
 *
 * **职责**：维护一份按时间戳排序的消息列表，同时按 LRU 访问顺序淘汰。
 * - 消息列表始终保持时间戳排序（供显示用）
 * - 超出上限时按 LRU 顺序淘汰（从 [lruTracker] 头部，即最久未访问的）
 * - [touch] 只更新 LRU 追踪器，不改消息列表排序
 * - 每次变更自动同步到 [state]，外部直接观察即可，无需手动赋值
 */
class MessageLruCache<T : Any>(
    val maxSize: Int,
    private val id: (T) -> String,
    private val timestamp: (T) -> Long
) {
    // 按时间戳排序的消息
    private val _items = mutableListOf<T>()

    // 响应式状态：每次变更自动同步
    private val _state = MutableStateFlow<List<T>>(emptyList())
    val state: StateFlow<List<T>> = _state.asStateFlow()

    /** 当前列表快照（供 `ChatState.updateMessage*` 等只读场景使用，不触发 StateFlow 更新） */
    val snapshot: List<T> get() = _items.toList()
    val size: Int get() = _items.size

    // LRU 访问顺序追踪器
    private val lru = linkedSetOf<String>()

    // ── 公共 API ──────────────────────────────────

    /**
     * 插入或更新消息。
     * - 已存在：替换内容，提升 LRU 位置
     * - 不存在：按时间戳排序插入
     * 超限时自动 LRU 淘汰。
     */
    fun add(message: T) {
        val msgId = id(message)
        val existingIdx = _items.indexOfFirst { id(it) == msgId }

        if (existingIdx >= 0) {
            _items[existingIdx] = message
        } else {
            // 找插入位置（二分法：列表已按时间戳有序）
            var lo = 0
            var hi = _items.size
            val ts = timestamp(message)
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                if (timestamp(_items[mid]) <= ts) lo = mid + 1 else hi = mid
            }
            _items.add(lo, message)
        }

        lru.remove(msgId); lru.add(msgId)
        evictAndEmit()
    }

    /**
     * 批量前置插入（历史消息）。去重后归并合并（O(n)），避免全量排序。
     *
     * @return 实际插入且未被 LRU 淘汰的消息（调用方可直接用此列表更新会话状态，
     *         无需再通过 [snapshot] 回查哪些消息存活了）
     */
    fun prepend(messages: List<T>): List<T> {
        if (messages.isEmpty()) return emptyList()
        val existingIds = _items.map { id(it) }.toSet()
        val toAdd = messages.filter { id(it) !in existingIds }
        if (toAdd.isEmpty()) return emptyList()

        // 两个列表都已按时间戳有序 → 归并合并 O(n+m)
        val merged = mutableListOf<T>()
        var i = 0; var j = 0
        while (i < toAdd.size && j < _items.size) {
            if (timestamp(toAdd[i]) <= timestamp(_items[j])) {
                merged.add(toAdd[i++])
            } else {
                merged.add(_items[j++])
            }
        }
        while (i < toAdd.size) merged.add(toAdd[i++])
        while (j < _items.size) merged.add(_items[j++])

        _items.clear(); _items.addAll(merged)
        toAdd.forEach { lru.remove(id(it)); lru.add(id(it)) }
        evictAndEmit()

        // 返回去重后且未被淘汰的消息（调用方无需回查 snapshot）
        val survivingIds = _items.map { id(it) }.toSet()
        return toAdd.filter { id(it) in survivingIds }
    }

    /**
     * 标记某条消息为最近访问（不影响时间排序）。
     */
    fun touch(messageId: String) {
        if (lru.remove(messageId)) {
            lru.add(messageId)
        }
        // 列表内容不变，不发射
    }

    /**
     * 删除指定消息。
     */
    fun remove(messageId: String): Boolean {
        val idx = _items.indexOfFirst { id(it) == messageId }
        if (idx < 0) return false
        _items.removeAt(idx)
        lru.remove(messageId)
        emit()
        return true
    }

    /**
     * 清空所有数据。
     */
    fun clear() {
        _items.clear()
        lru.clear()
        emit()
    }

    // ── 内部 ───────────────────────────────────────

    /** 发射当前列表到 StateFlow（仅一次）。 */
    private fun emit() {
        _state.value = _items.toList()
    }

    /**
     * LRU 淘汰：保留最近访问的 [maxSize] 条，一次 filter 完成淘汰。
     * - 从 LRU 尾部（最近访问）往前保留 [maxSize] 条
     * - 其余全部移除，O(n) 一次扫描
     */
    private fun evictAndEmit() {
        if (_items.size <= maxSize) { emit(); return }
        val keep = lru.toList().takeLast(maxSize).toSet()
        _items.removeAll { id(it) !in keep }
        lru.retainAll(keep)
        emit()
    }
}
