package com.example.android_bouncybubbleworldview_example

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import kotlin.math.roundToInt

class BottomSheetContainerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : ViewGroup(context, attrs, defStyleAttr, defStyleRes) {

    var onBottomSheetHeightChange: ((height: Int) -> Unit)? = null

    lateinit var bottomSheet: View
        private set

    override fun onFinishInflate() {
        super.onFinishInflate()
        bottomSheet = this.children.first().apply {
            layoutParams.height = 350f.dpToPx.roundToInt()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        measureChild(bottomSheet, widthMeasureSpec, heightMeasureSpec)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        bottomSheet.layout(0, this.measuredHeight - bottomSheet.measuredHeight, this.measuredWidth, this.measuredHeight)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val bottomSheetHeight: Int = (this.height - event.y.coerceAtLeast(0f).coerceAtMost(this.height.toFloat())).roundToInt()
        bottomSheet.layoutParams.height = bottomSheetHeight
        bottomSheet.requestLayout()
        onBottomSheetHeightChange?.invoke(bottomSheetHeight)
        return true
    }
}
