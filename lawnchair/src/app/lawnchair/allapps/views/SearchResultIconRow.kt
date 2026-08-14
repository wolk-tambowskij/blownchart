package app.lawnchair.allapps.views

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ImageSpan
import android.text.style.StyleSpan
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import app.lawnchair.allapps.views.SearchResultView.Companion.FLAG_HIDE_SUBTITLE
import app.lawnchair.font.FontManager
import app.lawnchair.search.adapter.CALCULATOR
import app.lawnchair.search.adapter.HISTORY
import app.lawnchair.search.adapter.SETTINGS
import app.lawnchair.search.adapter.SearchTargetCompat
import app.lawnchair.search.adapter.WEB_SUGGESTION
import app.lawnchair.util.copyToClipboard
import com.android.app.search.LayoutType
import com.android.launcher3.R
import com.android.launcher3.views.BubbleTextHolder

class SearchResultIconRow(context: Context, attrs: AttributeSet?) :
    LinearLayout(context, attrs),
    SearchResultView,
    BubbleTextHolder {

    private var isSmall = false
    private lateinit var icon: SearchResultIcon
    private lateinit var title: TextView
    private lateinit var subtitle: TextView
    private var delimiter: View? = null
    private lateinit var shortcutIcons: Array<SearchResultIcon>

    private var boundId = ""
    private var flags = 0

    override fun onFinishInflate() {
        super.onFinishInflate()
        isSmall = id == R.id.search_result_small_icon_row
        icon = ViewCompat.requireViewById(this, R.id.icon)
        icon.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        val iconSize = icon.iconSize
        icon.layoutParams.apply {
            width = iconSize
            height = iconSize
        }
        icon.setTextVisibility(false)
        title = ViewCompat.requireViewById(this, R.id.title)
        subtitle = ViewCompat.requireViewById(this, R.id.subtitle)
        subtitle.isVisible = false
        FontManager.INSTANCE.get(context).setCustomFont(title, R.id.font_heading)
        FontManager.INSTANCE.get(context).setCustomFont(subtitle, R.id.font_body)
        delimiter = findViewById(R.id.delimiter)
        setOnClickListener(icon)

        shortcutIcons = listOf(
            R.id.shortcut_0,
            R.id.shortcut_1,
            R.id.shortcut_2,
        )
            .mapNotNull { findViewById<SearchResultIcon>(it) }
            .toTypedArray()
        shortcutIcons.forEach {
            it.setTextVisibility(false)
            it.layoutParams.apply {
                width = icon.iconSize
                height = icon.iconSize
            }
        }
    }

    override val isQuickLaunch get() = icon.isQuickLaunch || hasFlag(flags, SearchResultView.FLAG_QUICK_LAUNCH)
    override val titleText get() = if (icon.titleText != "") icon.titleText else title.text

    override fun launch(): Boolean {
        performClick()
        return true
    }

    override fun bind(target: SearchTargetCompat, shortcuts: List<SearchTargetCompat>) {
        if (boundId == target.id) return
        boundId = target.id
        flags = getFlags(target.extras)

        icon.bind(target) {
            title.text = it.title
            tag = it
        }
        val isSuggestion = (target.layoutType == LayoutType.HORIZONTAL_MEDIUM_TEXT || target.layoutType == LayoutType.WIDGET_LIVE) &&
            target.resultType == SearchTargetCompat.RESULT_TYPE_SUGGESTIONS &&
            (target.packageName == WEB_SUGGESTION || target.packageName == HISTORY)

        val isSetting = target.layoutType == LayoutType.ICON_SLICE &&
            target.resultType == SearchTargetCompat.RESULT_TYPE_SETTING_TILE &&
            target.packageName == SETTINGS

        val isCalculator = target.layoutType == LayoutType.CALCULATOR &&
            target.resultType == SearchTargetCompat.RESULT_TYPE_CALCULATOR &&
            target.packageName == CALCULATOR

        bindShortcuts(shortcuts)
        // Plain app targets never carry a searchAction (see SearchTargetFactory /
        // SearchResultIcon.bind's plain-app branch), so their folder-name label - the only
        // subtitle they can have - travels via extras instead.
        val folderName = target.extras.getString("folder_name")
        val subtitleText = target.searchAction?.subtitle
            ?: folderName?.let { buildFolderSubtitle(it, target.extras.getString("folder_parent_name")) }
        var showDelimiter = true
        if (isSmall) {
            val textRows = ViewCompat.requireViewById<LinearLayout>(this, R.id.text_rows)
            if (target.layoutType == LayoutType.HORIZONTAL_MEDIUM_TEXT) {
                showDelimiter = false
                layoutParams.height = resources.getDimensionPixelSize(R.dimen.search_result_row_medium_height)
                textRows.orientation = VERTICAL
                subtitle.isSingleLine = true
                subtitle.setPadding(0, 0, 0, 0)
            } else if (folderName != null) {
                // A folder path ("Parent → Folder") can easily run longer than the single
                // shared line title+delimiter+subtitle otherwise squeeze into - stack title and
                // subtitle on their own lines instead, letting the path wrap onto up to two
                // lines within the extra vertical room the icon's own height already provides,
                // rather than truncating it on the right.
                showDelimiter = false
                layoutParams.height = WRAP_CONTENT
                textRows.orientation = VERTICAL
                subtitle.isSingleLine = false
                subtitle.maxLines = 2
                subtitle.setPadding(0, 0, 0, 0)
            } else {
                layoutParams.height = resources.getDimensionPixelSize(R.dimen.search_result_small_row_height)
                textRows.orientation = HORIZONTAL
                subtitle.isSingleLine = true
                val subtitleStartPadding = resources.getDimensionPixelSize(R.dimen.search_result_subtitle_padding_start)
                subtitle.setPaddingRelative(subtitleStartPadding, 0, 0, 0)
            }
        }
        setSubtitleText(subtitleText, showDelimiter)
        if (shouldHandleClick(target) && !isSmall) {
            setOnClickListener {
                target.searchAction?.intent?.let { intent -> handleSearchTargetClick(context, intent) }
            }
        }
        if (isSuggestion || isSetting) {
            layoutParams.height = resources.getDimensionPixelSize(R.dimen.search_result_small_row_height)
            setOnClickListener {
                target.searchAction?.intent?.let { intent -> handleSearchTargetClick(context, intent) }
            }
        }
        if (isCalculator) {
            setOnClickListener {
                copyToClipboard(
                    context = context,
                    text = target.extras.getString("result").toString(),
                    toastMessage = context.getString(R.string.calculator_search_result_copied_toast),
                )
            }
        }
    }

    // A folder's own row in the search result subtitle: a solid folder icon (matching the one
    // shown in the folder list), sized to the text's cap-height rather than its full line height,
    // a space, then the folder name - or, for an app inside a nested subfolder, "Parent → Folder"
    // with the parent name bolded (the immediate/nested folder is context here, not the top-level
    // answer to "which folder is this app in").
    private fun buildFolderSubtitle(folderName: String, parentName: String?): CharSequence {
        val text = if (parentName != null) "$parentName → $folderName" else folderName
        // Index 0 is replaced by the icon span below; index 1 is a plain space gap before the text.
        val builder = SpannableStringBuilder("  ").append(text)

        val iconDrawable = ContextCompat.getDrawable(context, R.drawable.ic_folder_solid)?.mutate()
        if (iconDrawable != null) {
            iconDrawable.setTint(Color.BLACK)
            val capHeightBounds = Rect()
            subtitle.paint.getTextBounds("H", 0, 1, capHeightBounds)
            val height = capHeightBounds.height()
            // ic_folder_solid's viewport is exactly the drawn glyph (no built-in padding), so its
            // 20:16 aspect ratio must be preserved here or the folder shape would be stretched.
            val width = height * 20 / 16
            iconDrawable.setBounds(0, 0, width, height)
            builder.setSpan(
                ImageSpan(iconDrawable, ImageSpan.ALIGN_BASELINE),
                0,
                1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }

        val boldEnd = if (parentName != null) 2 + parentName.length else builder.length
        builder.setSpan(StyleSpan(Typeface.BOLD), 2, boldEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return builder
    }

    private fun setSubtitleText(subtitleText: CharSequence?, showDelimiter: Boolean) {
        if (subtitleText.isNullOrEmpty() || icon.hasFlag(FLAG_HIDE_SUBTITLE)) {
            subtitle.isVisible = false
            delimiter?.isVisible = false
        } else {
            subtitle.text = subtitleText
            subtitle.isVisible = true
            delimiter?.isVisible = showDelimiter
        }
    }

    private fun bindShortcuts(shortcuts: List<SearchTargetCompat>) {
        shortcutIcons.forEachIndexed { index, icon ->
            if (index < shortcuts.size) {
                icon.isVisible = true
                icon.bind(shortcuts[index], emptyList())
            } else {
                icon.isVisible = false
            }
        }
    }

    override fun getBubbleText() = icon
}
