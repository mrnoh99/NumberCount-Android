package com.example.numbercount

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import com.example.numbercount.audio.AudioController
import com.example.numbercount.audio.FeedbackRecorder

class AppAudioViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("numbercount_prefs", Context.MODE_PRIVATE)

    val audioController = AudioController(application, prefs)
    val feedbackRecorder = FeedbackRecorder(application, audioController)

    override fun onCleared() {
        audioController.release()
        super.onCleared()
    }
}
