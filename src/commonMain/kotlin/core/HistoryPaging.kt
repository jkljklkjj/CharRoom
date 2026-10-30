package core

/** A viewport may ask for older rows only after layout and while positioned at its first row. */
internal fun shouldLoadEarlierHistory(
    initialized: Boolean,
    loading: Boolean,
    hasMore: Boolean,
    firstIndex: Int,
    firstOffset: Int,
): Boolean = initialized && !loading && hasMore && firstIndex == 0 && firstOffset == 0
