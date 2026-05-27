package com.example.numbercount.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder.AudioSource
import androidx.annotation.WorkerThread
import com.example.numbercount.AppLanguage
import com.example.numbercount.R
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

enum class FeedbackKind { CORRECT, WRONG }

class FeedbackRecorder(
    private val context: Context,
    private val audioController: AudioController,
) {
    private val feedbackDir: File =
        File(context.filesDir, "feedback").apply { mkdirs() }

    private val sampleRate = 44_100
    private val threshold = 550 // matches iOS int16 threshold-ish
    private val padFrames = (sampleRate * 0.045f).toInt().coerceAtLeast(1)
    private val minFrames = (sampleRate * 0.08f).toInt()

    private var audioRecord: AudioRecord? = null
    private var recordJob: Job? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    private val isRecordingInternal = AtomicBoolean(false)
    private val pcmBytesBuffer = ByteArrayOutputStream()

    fun hasRecording(kind: FeedbackKind, language: AppLanguage): Boolean {
        return recordingFile(kind, language).exists()
    }

    fun deleteRecording(kind: FeedbackKind, language: AppLanguage): Boolean {
        val f = recordingFile(kind, language)
        val ok = f.delete()
        if (ok) _revision.value = _revision.value + 1
        return ok
    }

    fun startRecording(
        scope: CoroutineScope,
        kind: FeedbackKind,
        language: AppLanguage,
    ) {
        // Caller must ensure permission is granted.
        if (_isRecording.value) return

        audioController.pauseBgm()
        audioController.stopTts()

        pcmBytesBuffer.reset()
        isRecordingInternal.set(true)
        _isRecording.value = true

        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, encoding)

        val record = AudioRecord(
            /* audioSource = */ android.media.MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            encoding,
            minBuf,
        )
        audioRecord = record

        record.startRecording()

        recordJob = scope.launch(Dispatchers.IO) {
            val buf = ByteArray(minBuf)
            try {
                while (isActive && isRecordingInternal.get()) {
                    val readBytes = record.read(buf, 0, buf.size)
                    if (readBytes > 0) {
                        pcmBytesBuffer.write(buf, 0, readBytes)
                    }
                }
            } catch (_: CancellationException) {
                // ignore
            }
        }
    }

    suspend fun stopAndSave(
        scope: CoroutineScope,
        kind: FeedbackKind,
        language: AppLanguage,
    ): Boolean {
        if (!_isRecording.value) return false

        isRecordingInternal.set(false)
        _isRecording.value = false

        val job = recordJob
        recordJob = null

        withContext(Dispatchers.IO) {
            try {
                audioRecord?.stop()
            } catch (_: Exception) {
            }
            try {
                audioRecord?.release()
            } catch (_: Exception) {
            }
            audioRecord = null
        }

        try {
            job?.join()
        } catch (_: Exception) {
        }

        val pcmBytes = pcmBytesBuffer.toByteArray()
        val outFile = recordingFile(kind, language)

        val ok = withContext(Dispatchers.Default) {
            trimAndWriteWavPcm16Mono(
                pcmBytes = pcmBytes,
                outFile = outFile,
            )
        }

        if (ok) {
            _revision.value = _revision.value + 1
        } else {
            outFile.delete()
        }

        // Resume BGM if enabled. iOS keeps session mixing, but this scaffold is simpler.
        audioController.resumeBgm()
        return ok
    }

    private fun recordingFile(kind: FeedbackKind, language: AppLanguage): File {
        val safeLang = when (language) {
            AppLanguage.KOREAN -> "ko"
            AppLanguage.ENGLISH -> "en"
        }
        val safeKind = when (kind) {
            FeedbackKind.CORRECT -> "correct"
            FeedbackKind.WRONG -> "wrong"
        }
        return File(feedbackDir, "feedback_${safeKind}_${safeLang}.wav")
    }

    @WorkerThread
    private fun trimAndWriteWavPcm16Mono(
        pcmBytes: ByteArray,
        outFile: File,
    ): Boolean {
        if (pcmBytes.isEmpty()) return false
        if (pcmBytes.size % 2 != 0) return false

        val sampleCount = pcmBytes.size / 2
        val byteBuffer = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
        val shorts = ShortArray(sampleCount)
        byteBuffer.asShortBuffer().get(shorts)

        var start = 0
        while (start < sampleCount && kotlin.math.abs(shorts[start].toInt()) < threshold) {
            start++
        }
        var end = sampleCount - 1
        while (end > start && kotlin.math.abs(shorts[end].toInt()) < threshold) {
            end--
        }

        if (end <= start) return false

        start = (start - padFrames).coerceAtLeast(0)
        end = (end + padFrames).coerceAtMost(sampleCount - 1)

        val outFrames = end - start + 1
        if (outFrames < minFrames) return false

        val trimmed = shorts.copyOfRange(start, end + 1)

        try {
            writeWav16MonoPcm(
                file = outFile,
                pcm16 = trimmed,
                sampleRate = sampleRate,
            )
            return true
        } catch (_: Exception) {
            return false
        }
    }

    private fun writeWav16MonoPcm(file: File, pcm16: ShortArray, sampleRate: Int) {
        val dataSize = pcm16.size * 2
        val riffSize = 36 + dataSize

        val header = ByteArray(44)
        var idx = 0

        fun putString(s: String) {
            val bytes = s.toByteArray(Charsets.US_ASCII)
            for (b in bytes) header[idx++] = b
        }

        fun putIntLE(v: Int) {
            header[idx++] = (v and 0xFF).toByte()
            header[idx++] = ((v shr 8) and 0xFF).toByte()
            header[idx++] = ((v shr 16) and 0xFF).toByte()
            header[idx++] = ((v shr 24) and 0xFF).toByte()
        }

        fun putShortLE(v: Int) {
            header[idx++] = (v and 0xFF).toByte()
            header[idx++] = ((v shr 8) and 0xFF).toByte()
        }

        putString("RIFF")
        putIntLE(riffSize)
        putString("WAVE")
        putString("fmt ")
        putIntLE(16) // PCM chunk size
        putShortLE(1) // AudioFormat PCM=1
        putShortLE(1) // channels mono
        putIntLE(sampleRate)
        val byteRate = sampleRate * 1 * 16 / 8
        putIntLE(byteRate)
        val blockAlign = 1 * 16 / 8
        putShortLE(blockAlign)
        putShortLE(16) // bits per sample
        putString("data")
        putIntLE(dataSize)

        if (file.exists()) file.delete()

        FileOutputStream(file).use { fos ->
            fos.write(header)
            val bb = ByteBuffer.allocate(pcm16.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (s in pcm16) bb.putShort(s)
            fos.write(bb.array())
            fos.flush()
        }
    }

    fun play(
        kind: FeedbackKind,
        language: AppLanguage,
        onDone: () -> Unit,
    ) {
        val file = recordingFile(kind, language)
        if (!file.exists()) return

        audioController.pauseBgm()
        audioController.stopTts()

        val mp = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            setOnCompletionListener {
                try {
                    stop()
                } catch (_: Exception) {
                }
                release()
                audioController.resumeBgm()
                onDone()
            }
        }

        mp.prepareAsync()
        mp.setOnPreparedListener {
            try {
                mp.start()
            } catch (_: Exception) {
                try {
                    mp.release()
                } catch (_: Exception) {
                }
                audioController.resumeBgm()
                onDone()
            }
        }
    }
}

