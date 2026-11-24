package com.volumebutton

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout

/**
 * Ultra-lightweight native view for the floating volume overlay.
 * Constructed 100% programmatically without XML inflation, LayoutInflater, or reflection.
 * Renders in LAYER_TYPE_SOFTWARE to completely avoid initializing heavy GPU/Vulkan/EGL
 * texture and shader caches, keeping idle memory footprint in the ~15-20 MB range.
 */
class FloatingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val density = context.resources.displayMetrics.density

    private fun dp(value: Int): Int = (value * density + 0.5f).toInt()

    // Root pill container
    val overlayRoot = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(4), dp(4), dp(4), dp(4))
        background = createPanelBackground()
        isClickable = true
        isFocusable = true
    }

    // Volume Up (+) Button
    val btnVolumeUp = ImageView(context).apply {
        layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        setImageResource(R.drawable.ic_plus)
        scaleType = ImageView.ScaleType.FIT_CENTER
        setPadding(dp(10), dp(10), dp(10), dp(10))
        background = createCircleBackground()
        contentDescription = context.getString(R.string.cd_volume_up)
        isClickable = true
        isFocusable = true
    }

    // Center Speaker Handle
    val handleContainer = FrameLayout(context).apply {
        layoutParams = LinearLayout.LayoutParams(dp(48), dp(40)).apply {
            topMargin = dp(2)
            bottomMargin = dp(2)
        }
        background = createCircleBackground()
        isClickable = true
        isFocusable = true
    }

    val imgHandleIcon = ImageView(context).apply {
        layoutParams = FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER)
        setImageResource(R.drawable.ic_volume_handle)
        contentDescription = context.getString(R.string.cd_handle)
    }

    // Volume Down (-) Button
    val btnVolumeDown = ImageView(context).apply {
        layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        setImageResource(R.drawable.ic_minus)
        scaleType = ImageView.ScaleType.FIT_CENTER
        setPadding(dp(10), dp(10), dp(10), dp(10))
        background = createCircleBackground()
        contentDescription = context.getString(R.string.cd_volume_down)
        isClickable = true
        isFocusable = true
    }

    init {
        handleContainer.addView(imgHandleIcon)
        overlayRoot.addView(btnVolumeUp)
        overlayRoot.addView(handleContainer)
        overlayRoot.addView(btnVolumeDown)

        addView(overlayRoot, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        setOpacity(IDLE_OPACITY)
    }

    fun setOpacity(alpha: Float) {
        overlayRoot.alpha = alpha.coerceIn(0.1f, 1.0f)
    }

    fun animateVisualTapFeedback() {
        handleContainer.animate()
            .scaleX(0.90f)
            .scaleY(0.90f)
            .setDuration(70)
            .withEndAction {
                handleContainer.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(70)
                    .start()
            }
            .start()
    }

    private fun createPanelBackground(): Drawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(24).toFloat()
            setColor(Color.parseColor("#E60F172A"))
            setStroke(dp(1), Color.parseColor("#4DFFFFFF"))
        }
    }

    private fun createCircleBackground(): Drawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#26FFFFFF"))
        }
    }

    companion object {
        const val IDLE_OPACITY = 0.85f
        const val ACTIVE_OPACITY = 1.0f
    }
}
