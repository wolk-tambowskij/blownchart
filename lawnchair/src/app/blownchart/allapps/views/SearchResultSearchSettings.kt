package app.blownchart.allapps.views

import android.content.Context
import android.util.AttributeSet
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import app.blownchart.preferences2.PreferenceManager2
import app.blownchart.search.adapter.SearchTargetCompat
import app.blownchart.ui.preferences.PreferenceActivity
import app.blownchart.ui.preferences.destinations.SearchRoute
import app.blownchart.ui.preferences.navigation.Search
import com.android.launcher3.R
import com.patrykmichalik.opto.core.firstBlocking

class SearchResultSearchSettings(context: Context, attrs: AttributeSet?) :
    LinearLayout(context, attrs),
    SearchResultView {

    private lateinit var iconButton: ImageButton

    override fun onFinishInflate() {
        super.onFinishInflate()
        iconButton = ViewCompat.requireViewById(this, R.id.search_settings)
        iconButton.setOnClickListener {
            context.startActivity(PreferenceActivity.createIntent(context, Search(SearchRoute.DRAWER_SEARCH)))
        }
    }

    override val isQuickLaunch: Boolean = false
    override fun launch(): Boolean = false

    override fun bind(
        target: SearchTargetCompat,
        shortcuts: List<SearchTargetCompat>,
    ) {
        // Locking the app drawer is meant to keep its contents/behavior from being tampered
        // with - jumping straight from a search result into the drawer's own search settings
        // is exactly that kind of tampering, so hide the entry point outright while locked
        // rather than leaving it clickable.
        iconButton.isVisible = !PreferenceManager2.getInstance(context).lockAppDrawer.firstBlocking()
    }
}
