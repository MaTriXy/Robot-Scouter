package com.supercilex.robotscouter.util

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.Callable
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

enum class AsyncTaskExecutor : Executor {
    INSTANCE;

    private val mService: ExecutorService = Executors.newCachedThreadPool()

    override fun execute(runnable: Runnable) {
        mService.submit(runnable)
    }

    companion object {
        fun <TResult> execute(callable: Callable<TResult>): Task<TResult> {
            return Tasks.call(INSTANCE, callable)
        }
    }
}
