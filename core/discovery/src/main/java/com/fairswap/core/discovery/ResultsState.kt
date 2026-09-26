package com.fairswap.core.discovery

enum class ResultErrorKind { FIRST_PAGE, NEXT_PAGE }

data class ResultsState(
    val items: List<ListingCard> = emptyList(),
    val totalCount: Int = 0,
    val loadedPage: Int = 0,
    val totalPages: Int = 0,
    val isStale: Boolean = false,
    val error: String? = null,
    val errorKind: ResultErrorKind? = null,
) {
    val canLoadMore: Boolean get() = loadedPage in 1 until totalPages
    val nextPage: Int get() = loadedPage + 1

    fun firstPage(page: SearchPage): ResultsState = ResultsState(
        items = page.items.distinctBy { it.id },
        totalCount = page.totalCount,
        loadedPage = page.page,
        totalPages = page.totalPages,
    )

    fun more(page: SearchPage): ResultsState {
        if (page.page != nextPage) return nextPageFailure()
        val known = items.mapTo(mutableSetOf()) { it.id }
        return copy(
            items = items + page.items.filter { known.add(it.id) },
            totalCount = page.totalCount,
            loadedPage = page.page,
            totalPages = page.totalPages,
            error = null,
            errorKind = null,
            isStale = false,
        )
    }

    fun firstPageFailure(): ResultsState = copy(
        isStale = items.isNotEmpty(),
        error = "Unable to refresh listings. Check your connection and retry.",
        errorKind = ResultErrorKind.FIRST_PAGE,
    )

    fun nextPageFailure(): ResultsState = copy(
        error = "Unable to load more listings. Retry when connected.",
        errorKind = ResultErrorKind.NEXT_PAGE,
    )
}
