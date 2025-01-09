package app.lawnchair.ui.popup

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.Context
import android.graphics.BitmapFactory
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import app.lawnchair.LawnchairLauncher
import app.lawnchair.views.component.IconFrame
import app.lawnchair.wallpaper.model.WallpaperViewModel
import app.lawnchair.wallpaper.model.WallpaperViewModelFactory
import app.lawnchair.wallpaper.service.Wallpaper
import com.android.launcher3.R
import com.android.launcher3.util.Themes
import com.android.launcher3.views.ActivityContext
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WallpaperCarouselView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val viewModel: WallpaperViewModel
    private val deviceProfile = ActivityContext.lookupContext<LawnchairLauncher>(context).deviceProfile
    private var currentItemIndex = 0
    private val iconFrame = IconFrame(context).apply {
        setIcon(R.drawable.ic_tick)
        setBackgroundWithRadius(bgColor = Themes.getColorAccent(context), cornerRadius = 100F)
    }
    private val loadingView: ProgressBar = ProgressBar(context).apply { isIndeterminate = true }

    init {
        orientation = HORIZONTAL
        addView(loadingView)
        val factory = WallpaperViewModelFactory(context)
        viewModel = ViewModelProvider(context as ViewModelStoreOwner, factory)[WallpaperViewModel::class.java]

        observeWallpapers()
    }

    private fun observeWallpapers() {
        viewModel.wallpapers.observe(context as LifecycleOwner) { wallpapers ->
            if (wallpapers.isEmpty()) {
                visibility = GONE
                loadingView.visibility = GONE
            } else {
                visibility = VISIBLE
                displayWallpapers(wallpapers)
            }
        }
    }

    private fun displayWallpapers(wallpapers: List<Wallpaper>) {
        removeAllViews()
        val isLandscape = deviceProfile.isLandscape
        val totalWidth = width.takeIf { it > 0 } ?: (deviceProfile.widthPx * if (isLandscape) 0.5 else 0.8).toInt()
        val firstItemWidth = totalWidth * 0.5
        val remainingWidth = totalWidth - firstItemWidth
        val marginBetweenItems = totalWidth * 0.02
        val itemWidth = (remainingWidth - (marginBetweenItems * (wallpapers.size - 1))) / (wallpapers.size - 1)

        wallpapers.forEachIndexed { index, wallpaper ->
            val cardView = createCardView(index, firstItemWidth, itemWidth, marginBetweenItems, wallpaper)
            addView(cardView)

            // Load the wallpaper image only if necessary
            loadWallpaperImage(wallpaper, cardView, index == currentItemIndex)
        }

        loadingView.visibility = GONE
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createCardView(
        index: Int,
        firstItemWidth: Double,
        itemWidth: Double,
        marginBetweenItems: Double,
        wallpaper: Wallpaper,
    ): CardView {
        return CardView(context).apply {
            radius = Themes.getDialogCornerRadius(context) / 2
            layoutParams = LayoutParams(
                when (index) {
                    currentItemIndex -> firstItemWidth.toInt()
                    else -> itemWidth.toInt()
                },
                LayoutParams.MATCH_PARENT,
            ).apply {
                setMargins(
                    if (index > 0) marginBetweenItems.toInt() else 0,
                    0,
                    0,
                    0,
                )
            }

            setOnTouchListener { _, _ ->
                if (index != currentItemIndex) {
                    animateWidthTransition(index, firstItemWidth, itemWidth)
                } else {
                    setWallpaper(wallpaper)
                }
                true
            }
        }
    }

    private fun loadWallpaperImage(wallpaper: Wallpaper, cardView: CardView, isCurrent: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            val wallpaperFile = File(wallpaper.imagePath)
            val bitmap = if (wallpaperFile.exists()) {
                BitmapFactory.decodeFile(wallpaper.imagePath)
            } else {
                null
            }

            withContext(Dispatchers.Main) {
                val imageView = ImageView(context).apply {
                    setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_deepshortcut_placeholder))
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }
                cardView.addView(imageView)

                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap)
                    if (isCurrent) addIconFrameToCenter(cardView)
                } else {
                    Log.e("WallpaperCarouselView", "File not found: ${wallpaper.imagePath}")
                    imageView.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_deepshortcut_placeholder))
                }
            }
        }
    }

    private fun setWallpaper(wallpaper: Wallpaper) {
        val currentCardView = getChildAt(currentItemIndex) as CardView
        val loadingSpinner = ProgressBar(context).apply {
            isIndeterminate = true
            layoutParams = FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            }
        }

        currentCardView.removeView(iconFrame)
        currentCardView.addView(loadingSpinner)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val wallpaperManager = WallpaperManager.getInstance(context)
                val bitmap = BitmapFactory.decodeFile(wallpaper.imagePath)

                wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM)
                viewModel.updateWallpaperRank(wallpaper)

                withContext(Dispatchers.Main) {
                    currentCardView.removeView(loadingSpinner)
                    addIconFrameToCenter(currentCardView)
                }
            } catch (e: Exception) {
                Log.e("WallpaperCarouselView", "Failed to set wallpaper: ${e.message}")
                withContext(Dispatchers.Main) {
                    currentCardView.removeView(loadingSpinner)
                    addIconFrameToCenter(currentCardView)
                }
            }
        }
    }

    private fun addIconFrameToCenter(cardView: CardView) {
        if (iconFrame.parent != null) {
            (iconFrame.parent as ViewGroup).removeView(iconFrame)
        }

        val params = FrameLayout.LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = Gravity.CENTER
        }

        cardView.addView(iconFrame, params)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (viewModel.wallpapers.value?.isNotEmpty() == true) {
            displayWallpapers(viewModel.wallpapers.value!!)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val isLandscape = deviceProfile.isLandscape
        val valWidth = if (isLandscape) (deviceProfile.widthPx * 0.5).toInt() else (deviceProfile.widthPx * 0.8).toInt()
        val width = MeasureSpec.makeMeasureSpec(valWidth, MeasureSpec.EXACTLY)
        super.onMeasure(width, heightMeasureSpec)
    }

    private fun animateWidthTransition(
        newIndex: Int,
        firstItemWidth: Double,
        itemWidth: Double,
    ) {
        currentItemIndex = newIndex
        for (i in 0 until childCount) {
            val cardView = getChildAt(i) as? CardView ?: continue
            val targetWidth = if (i == currentItemIndex) firstItemWidth.toInt() else itemWidth.toInt()

            if (cardView.layoutParams.width != targetWidth) {
                val animator = ValueAnimator.ofInt(cardView.layoutParams.width, targetWidth).apply {
                    duration = 300L
                    addUpdateListener { animation ->
                        val animatedValue = animation.animatedValue as Int
                        cardView.layoutParams = cardView.layoutParams.apply { width = animatedValue }
                        cardView.requestLayout()
                    }
                }
                animator.start()
            }
            if (i == currentItemIndex) {
                addIconFrameToCenter(cardView)
            }
        }
    }
}
