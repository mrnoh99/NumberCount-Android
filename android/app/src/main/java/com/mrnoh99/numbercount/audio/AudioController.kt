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
import kotlinx.coroutines.CancellableContinuation
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

    // 정답/오답 신호음을 FamilyFinder(가족찾기) 앱과 동일한 효과음으로 통일한다.
    @RawRes
    private val correctChimeRes: Int = R.raw.signal_correct

    @RawRes
    private val wrongChimeRes: Int = R.raw.signal_wrong

    private val sfxVolume = 0.85f

    private val isTtsSpeaking = AtomicBoolean(false)

    // 현재 기다리고 있는 발화의 id와 continuation. 리스너는 init에서 한 번만 설치하고,
    // 콜백의 utteranceId가 이 값과 일치할 때만 해당 발화를 깨운다.
    // (매 호출마다 새 리스너를 달면, 앞선 발화의 continuation이 뒤 발화의 완료로 잘못 깨어나거나
    //  영영 깨어나지 못하는 교차-완료 문제가 생긴다.)
    @Volatile
    private var pendingUtteranceId: String? = null

    @Volatile
    private var pendingCont: CancellableContinuation<Unit>? = null

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
    private var wrongChimeSoundId: Int = 0
    private var wrongChimeLoaded = false

    private var tts: TextToSpeech

    init {
        tts = TextToSpeech(context) { status ->
            // Ensure TTS is ready; if not ready, calls to speak() will fail quietly.
            if (status != TextToSpeech.SUCCESS) {
                // No-op: app will still show UI; audio may not be available.
            }
        }

        // 리스너는 한 번만 설치하고, 들어온 utteranceId가 현재 기다리는 발화와 같을 때만 깨운다.
        tts.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                // no-op
            }

            override fun onDone(utteranceId: String?) {
                val cont = pendingCont
                if (utteranceId != null && utteranceId == pendingUtteranceId && cont != null) {
                    pendingUtteranceId = null
                    pendingCont = null
                    isTtsSpeaking.set(false)
                    if (!cont.isCompleted) cont.resume(Unit)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                val cont = pendingCont
                if (utteranceId != null && utteranceId == pendingUtteranceId && cont != null) {
                    pendingUtteranceId = null
                    pendingCont = null
                    isTtsSpeaking.set(false)
                    if (!cont.isCompleted) cont.resumeWithException(IllegalStateException("TTS error"))
                }
            }
        })

        // Prepare the player here; actual playback starts when the app is foregrounded.
        bgmPlayer = createBgmPlayer()

        soundPool = SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(sfxAttributes)
            .build()
            .also { pool ->
                correctChimeSoundId = pool.load(context, correctChimeRes, 1)
                wrongChimeSoundId = pool.load(context, wrongChimeRes, 1)
                pool.setOnLoadCompleteListener { _, sampleId, status ->
                    if (status != 0) return@setOnLoadCompleteListener
                    when (sampleId) {
                        correctChimeSoundId -> correctChimeLoaded = true
                        wrongChimeSoundId -> wrongChimeLoaded = true
                    }
                }
            }
    }

    private fun createBgmPlayer(): MediaPlayer? {
        return try {
            MediaPlayer().apply {
                setAudioAttributes(audioAttributes)
                context.resources.openRawResourceFd(bgmRes).use { afd ->
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                }
                isLooping = true
                val volume = getBgmVolume()
                setVolume(volume, volume)
                setOnErrorListener { player, _, _ ->
                    try {
                        player.release()
                    } catch (_: Exception) {
                    }
                    if (bgmPlayer === player) {
                        bgmPlayer = null
                    }
                    true
                }
                prepare()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun ensureBgmPlayer(): MediaPlayer? {
        val existing = bgmPlayer
        if (existing != null) return existing
        val created = createBgmPlayer()
        bgmPlayer = created
        return created
    }

    private fun releaseBgmPlayer() {
        try {
            bgmPlayer?.let { player ->
                if (player.isPlaying) player.stop()
                player.release()
            }
        } catch (_: Exception) {
        }
        bgmPlayer = null
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
        try {
            ensureBgmPlayer()?.setVolume(v, v)
        } catch (_: Exception) {
        }
    }

    fun pauseBgm() {
        try {
            bgmPlayer?.pause()
        } catch (_: Exception) {
        }
    }

    fun resumeBgm() {
        if (!isBgmEnabled()) return
        val volume = getBgmVolume()
        val player = ensureBgmPlayer() ?: return
        try {
            player.setVolume(volume, volume)
            if (!player.isPlaying) {
                player.start()
            }
        } catch (_: Exception) {
            releaseBgmPlayer()
            val rebuilt = ensureBgmPlayer() ?: return
            try {
                rebuilt.setVolume(volume, volume)
                rebuilt.start()
            } catch (_: Exception) {
                releaseBgmPlayer()
            }
        }
    }

    fun playCorrectChime() {
        if (!correctChimeLoaded || correctChimeSoundId <= 0) return
        soundPool?.play(correctChimeSoundId, sfxVolume, sfxVolume, 1, 0, 1f)
    }

    fun playWrongChime() {
        if (!wrongChimeLoaded || wrongChimeSoundId <= 0) return
        soundPool?.play(wrongChimeSoundId, sfxVolume, sfxVolume, 1, 0, 1f)
    }

    fun release() {
        stopTts()
        try {
            tts.shutdown()
        } catch (_: Exception) {
        }
        releaseBgmPlayer()
        try {
            soundPool?.release()
        } catch (_: Exception) {
        }
        soundPool = null
        correctChimeSoundId = 0
        correctChimeLoaded = false
        wrongChimeSoundId = 0
        wrongChimeLoaded = false
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
                // 이번 발화를 "현재 기다리는 발화"로 등록한다. QUEUE_FLUSH로 직전 발화를 밀어내므로,
                // 늦게 도착한 직전 발화의 onDone(다른 id)은 일치하지 않아 무시된다.
                pendingUtteranceId = utteranceId
                pendingCont = cont

                tts.setSpeechRate(rate.coerceIn(0.1f, 2.0f))

                @Suppress("DEPRECATION")
                val result = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
                } else {
                    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null)
                    TextToSpeech.SUCCESS
                }

                if (result != TextToSpeech.SUCCESS) {
                    if (pendingUtteranceId == utteranceId) {
                        pendingUtteranceId = null
                        pendingCont = null
                    }
                    isTtsSpeaking.set(false)
                    cont.resumeWithException(IllegalStateException("TTS speak failed"))
                }

                cont.invokeOnCancellation {
                    // 이 발화가 여전히 현재 발화일 때만 정리·정지한다.
                    // (뒤 발화가 이미 이어받았다면 그 발화의 재생을 끊지 않는다.)
                    if (pendingUtteranceId == utteranceId) {
                        pendingUtteranceId = null
                        pendingCont = null
                        try {
                            tts.stop()
                        } catch (_: Exception) {
                        }
                    }
                    isTtsSpeaking.set(false)
                }
            } catch (t: Throwable) {
                if (pendingUtteranceId == utteranceId) {
                    pendingUtteranceId = null
                    pendingCont = null
                }
                isTtsSpeaking.set(false)
                if (!cont.isCompleted) cont.resumeWithException(t)
            }
        }
    }
}

