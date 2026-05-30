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
        if (previous != null && previous == snapshot) return

        nextRound(prev = game.targetNumber, categories = categories)
    }

    fun themePoolForRound(categories: Set<ItemCategory>): List<Theme> =
        ThemeCatalog.pool(categories)

    fun nextRound(
        prev: Int? = null,
        categories: Set<ItemCategory> = ItemCategory.all.toSet(),
    ) {
        guidanceJob?.cancel()
        guidanceJob = null

        selectedOption = null
        isCorrect = null
        locked = false
        wrongIndex = null
        shaking = false
        showCelebration = false
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
            playAnswerFeedback(FeedbackKind.CORRECT, appLanguage)
            showCelebration = true
            val newScore = game.score + 1
            val prevTarget = game.targetNumber
            viewModelScope.launch {
                delay(2500L)
                showCelebration = false
                game = GameState.newRound(
                    score = newScore,
                    prev = prevTarget,
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
        } else {
            wrongIndex = idx
            shaking = true
            viewModelScope.launch {
                delay(600L)
                shaking = false
            }
            playAnswerFeedback(FeedbackKind.WRONG, appLanguage) {
                viewModelScope.launch {
                    delay(1000L)
                    guidanceJob?.cancel()
                    guidanceJob = launch {
                        startCountHint(fromWrongAnswerFlow = true, appLanguage = appLanguage)
                    }
                }
            }
        }
    }

    private fun playAnswerFeedback(
        kind: FeedbackKind,
        appLanguage: AppLanguage,
        onFinished: () -> Unit = {},
    ) {
        val hasCustomRecording = feedbackRecorder.hasRecording(kind, appLanguage)
        if (kind == FeedbackKind.CORRECT && !hasCustomRecording) {
            audioController.playCorrectChime()
        }
        if (hasCustomRecording) {
            feedbackRecorder.play(kind, appLanguage, onFinished)
        } else {
            viewModelScope.launch {
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
                    onFinished()
                }
            }
        }
    }

    private suspend fun startCountHint(fromWrongAnswerFlow: Boolean, appLanguage: AppLanguage) {
        feedbackRecorder.stopPlayback()
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
        number < 20 -> "십" + if (number == 10) "" else native[number % 10]
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
