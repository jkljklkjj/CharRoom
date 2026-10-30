package core

/**
 * A receipt confirms only the current account's private rows, never another recipient or a group.
 */
suspend fun confirmStoredMessages(
    account: Int,
    rows: List<RecentHistoryMessage>,
    confirm: suspend (List<String>) -> Unit,
) {
    val ids =
        rows.filter { !it.isGroup && it.receiverId == account.toString() }.map { it.id }.distinct()
    for (batch in ids.chunked(200)) confirm(batch)
}

/**
 * Fetch/commit/confirm are deliberately ordered. A failed receipt can safely repeat the same page.
 */
suspend fun drainOfflineHistory(
    account: Int,
    isCurrent: () -> Boolean,
    fetch: suspend () -> List<RecentHistoryMessage>,
    commit: suspend (List<RecentHistoryMessage>) -> Unit,
    confirm: suspend (List<String>) -> Unit,
): Int {
    var total = 0
    var previous = emptySet<String>()
    while (isCurrent()) {
        val rows = fetch()
        if (!isCurrent() || rows.isEmpty()) break
        val ids = rows.map { it.id }.toSet()
        check(ids != previous) { "离线消息确认未生效，请稍后重试" }
        check(rows.all { !it.isGroup && it.receiverId == account.toString() }) { "离线消息不属于当前账号" }
        commit(rows)
        if (isCurrent()) {
            confirmStoredMessages(account, rows, confirm)
            total += rows.size
            previous = ids
        }
    }
    return total
}
