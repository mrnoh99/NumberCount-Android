package com.example.numbercount

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.numbercount.ui.NumberCountApp

class MainActivity : ComponentActivity() {
    private val audioViewModel: AppAudioViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Cream background: use dark icons on status/navigation bars.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )

        setContent {
            NumberCountApp(
                context = this,
            )
        }
    }

    override fun onStop() {
        super.onStop()
        audioViewModel.audioController.pauseBgm()
        audioViewModel.audioController.stopTts()
    }

    override fun onStart() {
        super.onStart()
        if (!audioViewModel.feedbackRecorder.isRecording.value) {
            audioViewModel.audioController.resumeBgm()
        }
    }
}

