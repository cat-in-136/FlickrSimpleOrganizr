package io.github.cat_in_136.flickrsimpleorganizr

import android.content.Context
import android.util.AttributeSet
import android.view.View

class SquareImageView : android.support.v7.widget.AppCompatImageView {
    constructor(context: Context) : super(context) {}

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {}

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {}

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = View.getDefaultSize(suggestedMinimumWidth, widthMeasureSpec)
        setMeasuredDimension(width, width)
    }
}
