package com.mrnoh99.numbercount

import android.app.Activity
import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore

class NumberCountApplication : Application(), Application.ActivityLifecycleCallbacks {
    private val appViewModelStore = ViewModelStore()
    private var visibleActivityCount = 0

    val audioViewModel: AppAudioViewModel by lazy {
        ViewModelProvider(
            appViewModelStore,
            ViewModelProvider.AndroidViewModelFactory.getInstance(this),
        )[AppAudioViewModel::class.java]
    }

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityStarted(activity: Activity) {
        visibleActivityCount++
        if (visibleActivityCount == 1 && !audioViewModel.feedbackRecorder.isRecording.value) {
            audioViewModel.audioController.resumeBgm()
        }
    }

    override fun onActivityResumed(activity: Activity) {
        if (visibleActivityCount > 0 && !audioViewModel.feedbackRecorder.isRecording.value) {
            audioViewModel.audioController.resumeBgm()
        }
    }

    override fun onActivityStopped(activity: Activity) {
        visibleActivityCount--
        if (visibleActivityCount == 0) {
            audioViewModel.audioController.pauseBgm()
            audioViewModel.audioController.stopTts()
            audioViewModel.feedbackRecorder.stopPlayback()
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: android.os.Bundle?) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: android.os.Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit
}
