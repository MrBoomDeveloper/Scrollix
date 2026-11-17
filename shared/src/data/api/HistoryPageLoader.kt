package com.mrboomdev.scrollix.data.api

internal data class HistoryLoadResult<T>(
    val items: List<T>,
    val hasMore: Boolean
)

internal class HistoryPageLoader<T>(
    private val maxRequests: Int = 8,
    private val idOf: (T) -> Long
) {
    suspend fun load(
        fromMessageId: Long,
        limit: Int,
        accept: (T) -> Boolean = { true },
        fetch: suspend (fromMessageId: Long, limit: Int) -> List<T>
    ): HistoryLoadResult<T> {
        val target = limit.coerceIn(1, 100)
        val collected = LinkedHashMap<Long, T>()
        var cursor = fromMessageId
        var hasMore = true
        var requests = 0

        while (collected.size < target && requests < maxRequests) {
            val remaining = target - collected.size
            val page = fetch(cursor, remaining)
            requests++

            if (page.isEmpty()) {
                hasMore = false
                break
            }

            val oldestId = page.minOfOrNull(idOf) ?: break

            for (item in page) {
                if (accept(item)) {
                    collected[idOf(item)] = item
                }
            }

            if (oldestId <= 0 || oldestId == cursor) {
                hasMore = false
                break
            }

            cursor = oldestId
        }

        return HistoryLoadResult(
            items = collected.values.toList(),
            hasMore = hasMore
        )
    }
}
