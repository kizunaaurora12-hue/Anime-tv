package com.miyuki.tv.extra

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

open class OnSwipeTouchListener : View.OnTouchListener {
    private var gestureDetector: GestureDetector? = null

    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        if (gestureDetector == null)
            gestureDetector = GestureDetector(v?.context, GestureListener())
        return gestureDetector?.onTouchEvent(event) ?: false
    }

    open fun onSwipeLeft()  {}
    open fun onSwipeRight() {}
    open fun onSwipeUp()    {}
    open fun onSwipeDown()  {}
    open fun onClick()      {}

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        private val SWIPE_THRESHOLD     = 100
        private val SWIPE_VELOCITY_THRESHOLD = 100

        override fun onDown(e: MotionEvent) = true

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            onClick()
            return true
        }

        override fun onFling(
            e1: MotionEvent?, e2: MotionEvent,
            velocityX: Float, velocityY: Float
        ): Boolean {
            val diffX = e2.x - (e1?.x ?: 0f)
            val diffY = e2.y - (e1?.y ?: 0f)
            return if (abs(diffX) > abs(diffY)) {
                if (abs(diffX) > SWIPE_THRESHOLD && abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffX > 0) onSwipeRight() else onSwipeLeft()
                    true
                } else false
            } else {
                if (abs(diffY) > SWIPE_THRESHOLD && abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffY > 0) onSwipeDown() else onSwipeUp()
                    true
                } else false
            }
        }
    }
}
