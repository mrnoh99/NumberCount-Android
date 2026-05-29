package com.mrnoh99.numbercount.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.speech.tts.TextToSpeech
import androidx.annotation.RawRes
import com.mrnoh99.numbercount.AppLanguage
import com.mrnoh99.numbercount.R
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AudioController(
    private val context: Context,
    private val prefs: android.content.SharedPreferences,
) {
    private val bgmEnabledKey = "bgmEnabled"
    private val bgmVolumeKey = "bgmVolume"

    @RawRes
    private val bgmRes: Int = R.raw.waltz_for_you

    @RawRes
    private val correctChimeRes: Int = R.raw.correct_chime

    private val sfxVolume = 0.85f

    private val isTtsSpeaking = AtomicBoolean(false)

    private val audioAttributes: AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

    private val sfxAttributes: AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

    private var bgmPlayer: MediaPlayer? = null
    private var soundPool: SoundPool? = null
    private var correctChimeSoundId: Int = 0
    private var correctChimeLoaded = false

    private var tts: TextToSpeech

    init {
        val enabled = prefs.getBoolean(bgmEnabledKey, true)
        val volume = prefs.getFloat(bgmVolumeKey, 0.12f).coerceIn(0f, 1f)

        tts = TextToSpeech(context) { status ->
            // Ensure TTS is ready; if not ready, calls to speak() will fail quietly.
            if (status != TextToSpeech.SUCCESS) {
                // No-op: app will still show UI; audio may not be available.
            }
        }

        bgmPlayer = MediaPlayer.create(context, bgmRes)?.apply {
            setAudioAttributes(audioAttributes)
            isLooping = true
            setVolume(volume, volume)
            if (enabled) start()
        }

        soundPool = SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(sfxAttributes)
            .build()
            .also { pool ->
                correctChimeSoundId = pool.load(context, correctChimeRes, 1)
                pool.setOnLoadCompleteListener { _, sampleId, status ->
                    if (sampleId == correctChimeSoundId && status == 0) {
                        correctChimeLoaded = true
                    }
                }
            }
    }

    fun isBgmEnabled(): Boolean = prefs.getBoolean(bgmEnabledKey, true)

    fun getBgmVolume(): Float = prefs.getFloat(bgmVolumeKey, 0.12f).coerceIn(0f, 1f)

    fun setBgmEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(bgmEnabledKey, enabled).apply()
        if (enabled) {
            resumeBgm()
        } else {
            pauseBgm()
        }
    }

    fun setBgmVolume(volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        prefs.edit().putFloat(bgmVolumeKey, v).apply()
        bgmPlayer?.setVolume(v, v)
    }

    fun pauseBgm() {
        bgmPlayer?.pause()
    }

    fun resumeBgm() {
        val enabled = isBgmEnabled()
        if (!enabled) return
        val v = prefs.getFloat(bgmVolumeKey, 0.12f).coerceIn(0f, 1f)
        bgmPlayer?.apply {
            setVolume(v, v)
            if (!isPlaying) start()
        }
    }

    fun playCorrectChime() {
        if (!correctChimeLoaded || correctChimeSoundId <= 0) return
        soundPool?.play(correctChimeSoundId, sfxVolume, sfxVolume, 1, 0, 1f)
    }

    fun release() {
        stopTts()
        try {
            tts.shutdown()
        } catch (_: Exception) {
        }
        try {
            bgmPlayer?.let { player ->
                if (player.isPlaying) player.stop()
                player.release()
            }
        } catch (_: Exception) {
        }
        bgmPlayer = null
        try {
            soundPool?.release()
        } catch (_: Exception) {
        }
        soundPool = null
        correctChimeSoundId = 0
        correctChimeLoaded = false
    }

    fun stopTts() {
        try {
            tts.stop()
        } catch (_: Exception) {
        }
    }

    suspend fun speakBlocking(text: String, language: AppLanguage, rate: Float) {
        val locale = when (language) {
            AppLanguage.KOREAN -> Locale.KOREAN
            AppLanguage.ENGLISH -> Locale.US
        }
        try {
            tts.language = locale
        } catch (_: Exception) {
        }

        val utteranceId = UUID.randomUUID().toString()
        isTtsSpeaking.set(true)

        suspendCancellableCoroutine<Unit> { cont ->
            try {
                // Set a dedicated listener for this utterance.
                tts.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(utteranceId: String) {
                        // no-op
                    }

                    override fun onDone(utteranceId: String) {
                        if (!cont.isCompleted) {
                            isTtsSpeaking.set(false)
                            cont.resume(Unit)
                        }
                    }

                    override fun onError(utteranceId: String) {
                        if (!cont.isCompleted) {
                            isTtsSpeaking.set(false)
                            cont.resumeWithException(IllegalStateException("TTS error"))
                        }
                    }
                })

                tts.setSpeechRate(rate.coerceIn(0.1f, 2.0f))

                @Suppress("DEPRECATION")
                val result = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
                } else {
                    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null)
                    TextToSpeech.SUCCESS
                }

                if (result != TextToSpeech.SUCCESS) {
                    isTtsSpeaking.set(false)
                    cont.resumeWithException(IllegalStateException("TTS speak failed"))
                }

                cont.invokeOnCancellation {
                    try {
                        tts.stop()
                    } catch (_: Exception) {
                    }
                    isTtsSpeaking.set(false)
                }
            } catch (t: Throwable) {
                isTtsSpeaking.set(false)
                if (!cont.isCompleted) cont.resumeWithException(t)
            }
        }
    }
}

