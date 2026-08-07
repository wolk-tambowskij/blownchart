package app.blownchart.search

import android.content.SearchRecentSuggestionsProvider
import com.android.launcher3.BuildConfig

class BlownChartRecentSuggestionProvider : SearchRecentSuggestionsProvider() {
    companion object {
        const val AUTHORITY = BuildConfig.APPLICATION_ID + ".search.BlownChartRecentSuggestionProvider"
        const val MODE = DATABASE_MODE_QUERIES
    }

    init {
        setupSuggestions(AUTHORITY, MODE)
    }
}
