package com.miyuki.tv.extra

import android.os.Handler
import android.os.Looper

class AsyncSleep {
    interface Task {
        fun onCountDown(count: Int) {}
        fun onFinish()
    }

    fun task(task: Task): AsyncSleep {
        this.task = task
        return this
    }

    private var task: Task? = null

    fun start(seconds: Int) {
        val handler = Handler(Looper.getMainLooper())
        var remaining = seconds
        val runnable = object : Runnable {
            override fun run() {
                if (remaining <= 0) {
                    task?.onFinish()
                } else {
                    task?.onCountDown(remaining)
                    remaining--
                    handler.postDelayed(this, 1000)
                }
            }
        }
        handler.post(runnable)
    }
}
