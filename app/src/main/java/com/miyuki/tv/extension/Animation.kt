package com.miyuki.tv.extension

import android.view.View
import android.view.animation.AnimationUtils
import com.miyuki.tv.R

fun View.startAnimation(focused: Boolean) {
    val anim = if (focused) R.anim.zoom_120 else R.anim.zoom_100
    startAnimation(AnimationUtils.loadAnimation(context, anim))
}
