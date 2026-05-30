package com.mrnoh99.numbercount

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore

class NumberCountApplication : Application() {
    private val appViewModelStore = ViewModelStore()

    val audioViewModel: AppAudioViewModel by lazy {
        ViewModelProvider(
            appViewModelStore,
            ViewModelProvider.AndroidViewModelFactory.getInstance(this),
        )[AppAudioViewModel::class.java]
    }
}
