package app.lawnchair.search.engine.provider.web

import android.content.Context
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.search.engine.SearchProvider
import app.lawnchair.search.engine.SearchResult
import com.android.launcher3.model.AllAppsList
import com.patrykmichalik.opto.core.firstBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map

object WebSuggestionProvider : SearchProvider {
    override val id = "web_suggestions"

    override fun search(
        context: Context,
        query: String,
        allApps: AllAppsList?,
    ): Flow<List<SearchResult>> {
        val prefs = PreferenceManager.getInstance(context)
        val prefs2 = PreferenceManager2.getInstance(context)

        if (query.isBlank() || !prefs.searchResultStartPageSuggestion.get()) {
            return emptyFlow()
        }

        // 1. Get the provider ID string from preferences.
        val providerId = prefs2.webSuggestionProvider.firstBlocking()

        // 2. Use our factory to get the correct static provider object.
        val webProvider = WebSearchProvider.fromString(providerId)
            // 3. **CRITICAL:** Configure it with the necessary context/state.
            .configure(context)

        // 4. Now we can safely use it.
        return webProvider.getSuggestions(query)
            .map { suggestions ->
                suggestions.map { suggestion ->
                    SearchResult.WebSuggestion(suggestion = suggestion, provider = webProvider.id)
                }
            }
    }
}
