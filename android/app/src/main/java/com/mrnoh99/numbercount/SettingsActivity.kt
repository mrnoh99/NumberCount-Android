package com.mrnoh99.numbercount

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mrnoh99.numbercount.ui.SettingsRoute

class SettingsActivity : ComponentActivity() {
    private val audioViewModel: AppAudioViewModel
        get() = (application as NumberCountApplication).audioViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )

        setContent {
            SettingsRoute(
                context = this,
                audioController = audioViewModel.audioController,
                feedbackRecorder = audioViewModel.feedbackRecorder,
                onBack = { finish() },
            )
        }
    }

    override fun onStop() {
        super.onStop()
        audioViewModel.audioController.pauseBgm()
        audioViewModel.audioController.stopTts()
    }
}
