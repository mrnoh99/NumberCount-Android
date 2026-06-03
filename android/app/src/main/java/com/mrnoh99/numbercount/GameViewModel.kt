package com.mrnoh99.numbercount

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mrnoh99.numbercount.audio.AudioController
import com.mrnoh99.numbercount.audio.FeedbackKind
import com.mrnoh99.numbercount.audio.FeedbackRecorder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class GameViewModel(
    private val audioController: AudioController,
    private val feedbackRecorder: FeedbackRecorder,
) : ViewModel() {
    var maxNumber by mutableStateOf(5)
    var quizMode by mutableStateOf(QuizMode.OBJECTS_TO_NUMBER)

    var selectedOption by mutableStateOf<Int?>(null)
    var isCorrect by mutableStateOf<Boolean?>(null)
    var locked by mutableStateOf(false)
    var wrongIndex by mutableStateOf<Int?>(null)
    var shaking by mutableStateOf(false)

    var showCelebration by mutableStateOf(false)
    // 오답일 때 FamilyFinder와 동일한 전체화면 오답 그림(wrong.jpg)을 표시할지 여부.
    var showWrongImage by mutableStateOf(false)
    // 정답/오답 음성이 끝나 파란 "다음" 버튼을 눌러 진행할 수 있는 상태인지 여부.
    var feedbackInteractive by mutableStateOf(false)

    // 정답 시 다음 라운드에 반영할 점수(파란 버튼을 누를 때 적용).
    private var pendingCorrectScore: Int? = null

    var showingCountHint by mutableStateOf(false)
    var highlightedCount by mutableStateOf(0)
    var hintWord by mutableStateOf("")

    var game by mutableStateOf(
        GameState.newRound(
            score = 0,
            prev = null,
            maxNumber = maxNumber,
            mode = quizMode,
            themePool = ThemeCatalog.all,
        )
    )

    private var guidanceJob: Job? = null

    // 정답/오답 피드백(신호음→음성)과 그 후속 동작을 담는 코루틴. 라운드가 바뀌면 취소한다.
    private var feedbackJob: Job? = null

    // 오답 흔들림 애니메이션 리셋 타이머. 라운드가 바뀌면 취소한다.
    private var shakingJob: Job? = null

    // 라운드가 바뀔 때마다 증가시킨다. 이전 라운드에서 시작된 피드백 콜백
    // (코루틴 또는 녹음 재생 완료 콜백)이 새 라운드에 끼어들지 못하게 막는 토큰.
    private var roundToken: Int = 0

    private data class SettingsSnapshot(
        val maxNumber: Int,
        val quizMode: QuizMode,
        val themeCategoriesStorage: String,
        val appLanguageRaw: String,
    )

    private var settingsSnapshot: SettingsSnapshot? = null

    fun updateSettings(
        themeCategoriesStorage: String,
        appLanguageRaw: String,
        categories: Set<ItemCategory>,
    ) {
        val snapshot = SettingsSnapshot(
            maxNumber = maxNumber,
            quizMode = quizMode,
            themeCategoriesStorage = themeCategoriesStorage,
            appLanguageRaw = appLanguageRaw,
        )
        val previous = settingsSnapshot
        settingsSnapshot = snapshot
        if (previous == null || previous == snapshot) return

        nextRound(prev = game.targetNumber, categories = categories)
    }

    fun themePoolForRound(categories: Set<ItemCategory>): List<Theme> =
        ThemeCatalog.pool(categories)

    fun nextRound(
        prev: Int? = null,
        categories: Set<ItemCategory> = ItemCategory.all.toSet(),
    ) {
        // 이전 라운드의 진행 중 작업(세기 힌트·피드백 음성)과 그 콜백을 모두 무효화한다.
        roundToken++
        guidanceJob?.cancel()
        guidanceJob = null
        feedbackJob?.cancel()
        feedbackJob = null
        shakingJob?.cancel()
        shakingJob = null
        feedbackRecorder.stopPlayback()

        selectedOption = null
        isCorrect = null
        locked = false
        wrongIndex = null
        shaking = false
        showCelebration = false
        showWrongImage = false
        feedbackInteractive = false
        pendingCorrectScore = null
        showingCountHint = false
        highlightedCount = 0
        hintWord = ""

        game = GameState.newRound(
            score = game.score,
            prev = prev,
            maxNumber = maxNumber,
            mode = quizMode,
            themePool = themePoolForRound(categories),
        )
    }

    fun onQuestionPanelTapped(appLanguage: AppLanguage) {
        if (showCelebration) return
        if (showingCountHint) return
        if (selectedOption != null) return
        if (locked) return
        locked = true
        guidanceJob?.cancel()
        guidanceJob = viewModelScope.launch {
            startCountHint(fromWrongAnswerFlow = false, appLanguage = appLanguage)
        }
    }

    fun tapOption(
        opt: Int,
        idx: Int,
        appLanguage: AppLanguage,
        categories: Set<ItemCategory>,
    ) {
        if (locked) return
        if (selectedOption != null) return
        locked = true
        selectedOption = opt
        val ok = opt == game.targetNumber
        isCorrect = ok

        if (ok) {
            // 정답: 그림을 띄우고, 음성이 끝나면 파란 "다음" 버튼을 눌러 다음 라운드로 진행한다.
            showCelebration = true
            feedbackInteractive = false
            pendingCorrectScore = game.score + 1
            playAnswerFeedback(FeedbackKind.CORRECT, appLanguage) {
                feedbackInteractive = true
            }
        } else {
            // 오답: 그림을 잠깐 보여준 뒤, 별도 버튼 없이 기존처럼 "세기 힌트(가르쳐주기) → 재시도" 흐름으로 자동 진행한다.
            wrongIndex = idx
            shaking = true
            showWrongImage = true
            feedbackInteractive = false
            shakingJob?.cancel()
            shakingJob = viewModelScope.launch {
                delay(600L)
                shaking = false
            }
            playAnswerFeedback(FeedbackKind.WRONG, appLanguage) {
                // 오답 음성이 끝나면 잠깐 뒤 "세기 힌트(가르쳐주기)" 흐름을 시작한다.
                // 이 후속 작업도 guidanceJob에 담아, 라운드가 바뀌면 함께 취소되게 한다.
                guidanceJob?.cancel()
                guidanceJob = viewModelScope.launch {
                    delay(1000L)
                    startCountHint(fromWrongAnswerFlow = true, appLanguage = appLanguage)
                }
            }
        }
    }

    /** 정답 그림에서 파란 "다음" 버튼을 눌렀을 때: 다음 라운드로 진행. */
    fun proceedFromCorrect(categories: Set<ItemCategory>) {
        if (!feedbackInteractive) return
        // 새 라운드로 진행하므로 이전 라운드의 피드백/힌트 콜백을 무효화한다.
        roundToken++
        guidanceJob?.cancel()
        guidanceJob = null
        feedbackJob?.cancel()
        feedbackJob = null
        shakingJob?.cancel()
        shakingJob = null
        feedbackRecorder.stopPlayback()
        feedbackInteractive = false
        showCelebration = false
        val newScore = pendingCorrectScore ?: (game.score + 1)
        pendingCorrectScore = null
        game = GameState.newRound(
            score = newScore,
            prev = game.targetNumber,
            maxNumber = maxNumber,
            mode = quizMode,
            themePool = themePoolForRound(categories),
        )
        selectedOption = null
        isCorrect = null
        locked = false
        wrongIndex = null
        shaking = false
    }

    private fun playAnswerFeedback(
        kind: FeedbackKind,
        appLanguage: AppLanguage,
        onFinished: () -> Unit = {},
    ) {
        // 이 피드백이 속한 라운드를 기억해 두고, 콜백이 도착했을 때 여전히 같은 라운드일 때만 실행한다.
        // (녹음 재생 완료 콜백은 코루틴이 아니라 취소가 안 되므로 토큰으로 가드한다.)
        val token = roundToken
        val finishIfCurrent = { if (token == roundToken) onFinished() }

        val hasCustomRecording = feedbackRecorder.hasRecording(kind, appLanguage)
        // FamilyFinder와 동일하게 정답/오답 모두 신호음을 먼저 들려준 뒤 음성을 재생한다.
        when (kind) {
            FeedbackKind.CORRECT -> audioController.playCorrectChime()
            FeedbackKind.WRONG -> audioController.playWrongChime()
        }
        if (hasCustomRecording) {
            feedbackRecorder.play(kind, appLanguage, finishIfCurrent)
        } else {
            feedbackJob?.cancel()
            feedbackJob = viewModelScope.launch {
                val phrase = when (kind) {
                    FeedbackKind.CORRECT -> feedbackCorrectPhrase(appLanguage)
                    FeedbackKind.WRONG -> feedbackWrongPhrase(appLanguage)
                }
                try {
                    audioController.speakBlocking(
                        text = phrase,
                        language = appLanguage,
                        rate = voiceRate(appLanguage),
                    )
                } finally {
                    finishIfCurrent()
                }
            }
        }
    }

    private suspend fun startCountHint(fromWrongAnswerFlow: Boolean, appLanguage: AppLanguage) {
        feedbackRecorder.stopPlayback()
        showWrongImage = false
        showingCountHint = true
        highlightedCount = 0
        hintWord = ""

        val total = game.targetNumber
        val leadMs = if (fromWrongAnswerFlow) 480L else 280L
        delay(leadMs)

        audioController.pauseBgm()
        try {
            val itemLabel = if (appLanguage == AppLanguage.ENGLISH) {
                game.theme.itemWordSingular
            } else {
                game.theme.itemWordSingularKO
            }
            hintWord = if (appLanguage == AppLanguage.ENGLISH) itemLabel.uppercase() else itemLabel
            audioController.speakBlocking(
                text = itemLabel,
                language = appLanguage,
                rate = voiceRate(appLanguage),
            )
            delay(250L)

            for (n in 1..total) {
                val spoken = if (appLanguage == AppLanguage.ENGLISH) englishNumberWord(n) else koreanNumberWord(n)
                val display = if (appLanguage == AppLanguage.ENGLISH) spoken.uppercase() else n.toString()
                highlightedCount = n
                hintWord = display

                audioController.speakBlocking(
                    text = spoken,
                    language = appLanguage,
                    rate = voiceRate(appLanguage),
                )
                delay(150L)
            }
        } finally {
            showingCountHint = false
            highlightedCount = 0
            hintWord = ""
            selectedOption = null
            isCorrect = null
            wrongIndex = null
            locked = false
            audioController.resumeBgm()
        }
    }

    override fun onCleared() {
        guidanceJob?.cancel()
        feedbackJob?.cancel()
        shakingJob?.cancel()
        super.onCleared()
    }

    companion object {
        fun factory(
            audioController: AudioController,
            feedbackRecorder: FeedbackRecorder,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                GameViewModel(audioController, feedbackRecorder) as T
        }
    }
}

private fun koreanNumberWord(number: Int): String {
    val native = listOf("", "일", "이", "삼", "사", "오", "육", "칠", "팔", "구", "십")
    if (number < 1) return number.toString()
    return when {
        number < native.size -> native[number]
        number < 20 -> "십" + native[number % 10]
        else -> number.toString()
    }
}

private fun englishNumberWord(number: Int): String {
    val words = listOf(
        "zero", "one", "two", "three", "four", "five",
        "six", "seven", "eight", "nine", "ten",
        "eleven", "twelve", "thirteen", "fourteen", "fifteen",
        "sixteen", "seventeen", "eighteen", "nineteen", "twenty",
    )
    return words.getOrNull(number) ?: number.toString()
}

private fun feedbackCorrectPhrase(language: AppLanguage): String =
    if (language == AppLanguage.KOREAN) "그래 잘했다!" else "That's right!"

private fun feedbackWrongPhrase(language: AppLanguage): String =
    if (language == AppLanguage.KOREAN) "틀렸어요." else "Not quite."

private fun voiceRate(language: AppLanguage): Float =
    if (language == AppLanguage.KOREAN) 0.8f else 0.85f
