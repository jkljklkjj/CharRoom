package cli

import core.*

/** Uses the same seven-day bootstrap as native UI; history recovery never advances seq cursors. */
suspend fun syncCliHistory(account: Int, isCurrent: () -> Boolean,
    recent: suspend (RecentHistoryCursor) -> RecentHistoryPage,
    offline: suspend () -> List<RecentHistoryMessage>,
    commit: suspend (List<RecentHistoryMessage>) -> Unit,
    confirm: suspend (List<String>) -> Unit): Int {
    var count = 0
    restoreRecentHistory(isCurrent, recent) { rows ->
        commit(rows)
        if (isCurrent()) { confirmStoredMessages(account, rows, confirm); count += rows.size }
    }
    if (isCurrent()) count += drainOfflineHistory(account, isCurrent, offline, commit, confirm)
    return count
}
