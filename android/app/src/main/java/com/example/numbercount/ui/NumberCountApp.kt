package com.example.numbercount.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import com.example.numbercount.numColors
import com.example.numbercount.AppLanguage
import com.example.numbercount.GameState
import com.example.numbercount.ItemCategory
import com.example.numbercount.QuizMode
import com.example.numbercount.Theme
import com.example.numbercount.ThemeCatalog
import com.example.numbercount.ui.SettingsScreen
import com.example.numbercount.audio.AudioController
import com.example.numbercount.audio.FeedbackKind
import com.example.numbercount.audio.FeedbackRecorder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NumberCountApp(context: Context) {
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("numbercount_prefs", Context.MODE_PRIVATE) }

    var appLanguageRaw by remember {
        mutableStateOf(prefs.getString(AppLanguage.storageKey, AppLanguage.KOREAN.raw) ?: AppLanguage.KOREAN.raw)
    }
    var themeCategoriesStorage by remember {
        mutableStateOf(
            prefs.getString(ItemCategory.appStorageKey, ItemCategory.defaultStorageValue) ?: ItemCategory.defaultStorageValue
        )
    }

    // Audio prefs keys must match AudioController.
    val bgmEnabledKey = "bgmEnabled"
    val bgmVolumeKey = "bgmVolume"
    var bgmEnabled by remember { mutableStateOf(prefs.getBoolean(bgmEnabledKey, true)) }
    var bgmVolume by remember { mutableStateOf(prefs.getFloat(bgmVolumeKey, 0.12f)) }

    val appLanguage = AppLanguage.fromRaw(appLanguageRaw)
    val selectedCategories = ItemCategory.fromStorage(themeCategoriesStorage)

    val audioController = remember { AudioController(context, prefs) }
    val feedbackRecorder = remember { FeedbackRecorder(context, audioController) }

    // Permission for hold-to-record
    val initialPermissionGranted =
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    var recordPermissionGranted by remember { mutableStateOf(initialPermissionGranted) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> recordPermissionGranted = granted }
    )

    var showSettings by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    // Main quiz state (MVP)
    var maxNumber by remember { mutableStateOf(5) }
    var quizMode by remember { mutableStateOf(QuizMode.OBJECTS_TO_NUMBER) }

    var selectedOption by remember { mutableStateOf<Int?>(null) }
    var isCorrect by remember { mutableStateOf<Boolean?>(null) }
    var locked by remember { mutableStateOf(false) }
    var wrongIndex by remember { mutableStateOf<Int?>(null) }
    var shaking by remember { mutableStateOf(false) }

    var showCelebration by remember { mutableStateOf(false) }

    var showingCountHint by remember { mutableStateOf(false) }
    var highlightedCount by remember { mutableStateOf(0) }
    var hintWord by remember { mutableStateOf("") }

    var guidanceJob by remember { mutableStateOf<Job?>(null) }

    fun themePoolForRound(): List<Theme> = ThemeCatalog.pool(selectedCategories)

    var game by remember {
        mutableStateOf(
            GameState.newRound(
                score = 0,
                prev = null,
                maxNumber = maxNumber,
                mode = quizMode,
                themePool = themePoolForRound()
            )
        )
    }

    fun koreanNumberWord(number: Int): String {
        val native = listOf("", "일", "이", "삼", "사", "오", "육", "칠", "팔", "구", "십")
        if (number < 1) return number.toString()
        return when {
            number < native.size -> native[number]
            number < 20 -> "십" + if (number == 10) "" else native[number % 10]
            else -> number.toString()
        }
    }

    fun englishNumberWord(number: Int): String {
        val words = listOf(
            "zero", "one", "two", "three", "four", "five",
            "six", "seven", "eight", "nine", "ten",
            "eleven", "twelve", "thirteen", "fourteen", "fifteen",
            "sixteen", "seventeen", "eighteen", "nineteen", "twenty"
        )
        return words.getOrNull(number) ?: number.toString()
    }

    fun feedbackCorrectPhrase(language: AppLanguage): String =
        if (language == AppLanguage.KOREAN) "그래 잘했다!" else "That's right!"

    fun feedbackWrongPhrase(language: AppLanguage): String =
        if (language == AppLanguage.KOREAN) "틀렸어요." else "Not quite."

    fun voiceRate(language: AppLanguage): Float =
        if (language == AppLanguage.KOREAN) 0.8f else 0.85f

    fun nextRound(prev: Int? = null) {
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
            themePool = themePoolForRound()
        )
    }

    fun playAnswerFeedback(kind: FeedbackKind) {
        val phrase = when (kind) {
            FeedbackKind.CORRECT -> feedbackCorrectPhrase(appLanguage)
            FeedbackKind.WRONG -> feedbackWrongPhrase(appLanguage)
        }

        if (feedbackRecorder.hasRecording(kind, appLanguage)) {
            feedbackRecorder.play(kind, appLanguage) { }
        } else {
            scope.launch {
                audioController.speakBlocking(text = phrase, language = appLanguage, rate = voiceRate(appLanguage))
            }
        }
    }

    suspend fun startCountHint(fromWrongAnswerFlow: Boolean) {
        showingCountHint = true
        highlightedCount = 0
        hintWord = ""

        val total = game.targetNumber
        val leadMs = if (fromWrongAnswerFlow) 480L else 280L
        delay(leadMs)

        audioController.pauseBgm()

        for (n in 1..total) {
            val spoken = if (appLanguage == AppLanguage.ENGLISH) englishNumberWord(n) else koreanNumberWord(n)
            val display = if (appLanguage == AppLanguage.ENGLISH) spoken.uppercase() else n.toString()
            highlightedCount = n
            hintWord = display

            audioController.speakBlocking(text = spoken, language = appLanguage, rate = voiceRate(appLanguage))
            delay(1100L)
        }

        val finalLabel = if (appLanguage == AppLanguage.ENGLISH) game.theme.itemWordSingular else game.theme.itemWordSingularKO
        hintWord = if (appLanguage == AppLanguage.ENGLISH) finalLabel.uppercase() else finalLabel
        audioController.speakBlocking(text = finalLabel, language = appLanguage, rate = voiceRate(appLanguage))

        delay(1150L)

        showingCountHint = false
        highlightedCount = 0
        hintWord = ""
        selectedOption = null
        isCorrect = null
        wrongIndex = null
        locked = false

        audioController.resumeBgm()
    }

    fun onQuestionPanelTapped() {
        if (showCelebration) return
        if (showingCountHint) return
        if (selectedOption != null) return
        if (locked) return
        locked = true
        guidanceJob?.cancel()
        guidanceJob = scope.launch {
            startCountHint(fromWrongAnswerFlow = false)
        }
    }

    fun tapOption(opt: Int, idx: Int) {
        if (locked) return
        if (selectedOption != null) return
        locked = true
        selectedOption = opt
        val ok = opt == game.targetNumber
        isCorrect = ok

        if (ok) {
            playAnswerFeedback(FeedbackKind.CORRECT)
            showCelebration = true
            val newScore = game.score + 1
            scope.launch {
                delay(2500L)
                showCelebration = false
                game = GameState.newRound(
                    score = newScore,
                    prev = game.targetNumber,
                    maxNumber = maxNumber,
                    mode = quizMode,
                    themePool = themePoolForRound(),
                )
                selectedOption = null
                isCorrect = null
                locked = false
                wrongIndex = null
                shaking = false
            }
        } else {
            playAnswerFeedback(FeedbackKind.WRONG)
            wrongIndex = idx
            shaking = true
            scope.launch {
                delay(600L)
                shaking = false
                delay(1000L)
                guidanceJob?.cancel()
                guidanceJob = launch {
                    startCountHint(fromWrongAnswerFlow = true)
                }
            }
        }
    }

    LaunchedEffect(maxNumber, quizMode, themeCategoriesStorage, appLanguageRaw) {
        nextRound(prev = game.targetNumber)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFFF7E8))
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
        ) {
            val w = maxWidth
            val h = maxHeight
            val isLandscape = w > h
            val isTablet = w >= 600.dp
            val tabletLandscape = isTablet && isLandscape
            val phoneLandscape = !isTablet && isLandscape
            val wideSplit = tabletLandscape || phoneLandscape
            val compactBar = phoneLandscape

            val targetColor = numColors[((game.targetNumber - 1).coerceAtLeast(0)) % numColors.size]
            val targetNumberFont = when {
                isTablet -> 120.sp
                phoneLandscape -> 86.sp
                else -> 84.sp
            }
            val hintFont = if (isTablet) 34.sp else 26.sp

            Column(modifier = Modifier.fillMaxSize()) {
                TopBar(
                    appLanguage = appLanguage,
                    maxNumber = maxNumber,
                    quizMode = quizMode,
                    score = game.score,
                    compact = compactBar,
                    onOpenSettings = { showSettings = true },
                    onSetMaxNumber = { maxNumber = it },
                    onSetMode = { quizMode = it }
                )

                if (wideSplit) {
                    val gap = if (phoneLandscape) 12.dp else 24.dp
                    val rightPanelW = if (phoneLandscape) minOf(w * 0.46f, 320.dp) else minOf(w * 0.46f, 560.dp)
                    val maxOptionSide = minOf((rightPanelW - gap) / 2f, (h - 120.dp) / 2f).coerceIn(72.dp, 260.dp)
                    val optionSide = if (quizMode == QuizMode.NUMBER_TO_OBJECTS) {
                        // Grow the answer card with the number of objects, capped to the space.
                        val rows = answerRows(game.options.maxOrNull() ?: 1)
                        (56.dp + 56.dp * rows).coerceIn(90.dp, maxOptionSide)
                    } else {
                        maxOptionSide
                    }
                    val targetSide = minOf((w - rightPanelW - gap) * 0.9f, h * 0.74f).coerceAtMost(560.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = if (phoneLandscape) 12.dp else 28.dp),
                        horizontalArrangement = Arrangement.spacedBy(gap),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            TargetPanel(
                                quizMode = quizMode,
                                targetNumber = game.targetNumber,
                                theme = game.theme,
                                borderColor = targetColor,
                                showingCountHint = showingCountHint,
                                highlightedCount = highlightedCount,
                                numberFont = targetNumberFont,
                                cornerRadius = 36.dp,
                                modifier = Modifier
                                    .size(targetSide)
                                    .clickable { onQuestionPanelTapped() }
                            )
                            if (showingCountHint && hintWord.isNotBlank()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = hintWord,
                                    color = Color(0xFFFF6A00),
                                    fontWeight = FontWeight.Black,
                                    fontSize = hintFont
                                )
                            }
                        }

                        Column(
                            modifier = Modifier
                                .width(rightPanelW)
                                .fillMaxSize(),
                            verticalArrangement = Arrangement.Center
                        ) {
                            OptionsGrid(
                                quizMode = quizMode,
                                theme = game.theme,
                                optionValues = game.options,
                                optionColors = game.theme.colors,
                                optionHeight = optionSide,
                                gap = gap,
                                selectedOption = selectedOption,
                                isCorrect = isCorrect,
                                shaking = shaking,
                                wrongIndex = wrongIndex,
                                onTap = { opt, idx -> tapOption(opt, idx) }
                            )
                        }
                    }
                } else {
                    // Stacked layout (phone portrait & tablet portrait)
                    val gridWidth = if (isTablet) minOf(w * 0.82f, 560.dp) else w
                    val gap = if (isTablet) 16.dp else 12.dp
                    val optionSide = (gridWidth - gap) / 2f
                    val optionHeight = if (quizMode == QuizMode.NUMBER_TO_OBJECTS) {
                        // Grow the answer card with the number of objects it must show.
                        val rows = answerRows(game.options.maxOrNull() ?: 1)
                        val perRow = if (isTablet) 64.dp else 52.dp
                        (44.dp + perRow * rows).coerceIn(110.dp, if (isTablet) 320.dp else 280.dp)
                    } else if (isTablet) {
                        optionSide.coerceIn(120.dp, 240.dp)
                    } else {
                        110.dp
                    }
                    val targetSide = if (isTablet) minOf(w * 0.6f, h * 0.34f) else minOf(w * 0.82f, h * 0.34f)
                    val gridModifier = if (isTablet) Modifier.width(gridWidth) else Modifier.fillMaxWidth()

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.weight(1f))
                        TargetPanel(
                            quizMode = quizMode,
                            targetNumber = game.targetNumber,
                            theme = game.theme,
                            borderColor = targetColor,
                            showingCountHint = showingCountHint,
                            highlightedCount = highlightedCount,
                            numberFont = targetNumberFont,
                            cornerRadius = if (isTablet) 36.dp else 30.dp,
                            modifier = Modifier
                                .size(targetSide)
                                .clickable { onQuestionPanelTapped() }
                        )
                        if (showingCountHint && hintWord.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = hintWord,
                                color = Color(0xFFFF6A00),
                                fontWeight = FontWeight.Black,
                                fontSize = hintFont
                            )
                        }
                        Spacer(modifier = Modifier.height(if (isTablet) 28.dp else 20.dp))
                        OptionsGrid(
                            quizMode = quizMode,
                            theme = game.theme,
                            optionValues = game.options,
                            optionColors = game.theme.colors,
                            optionHeight = optionHeight,
                            gap = gap,
                            selectedOption = selectedOption,
                            isCorrect = isCorrect,
                            shaking = shaking,
                            wrongIndex = wrongIndex,
                            onTap = { opt, idx -> tapOption(opt, idx) },
                            modifier = gridModifier
                        )
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        if (showCelebration) {
            CelebrationOverlay(
                number = game.targetNumber,
                comment = feedbackCorrectPhrase(appLanguage),
                color = game.theme.colors.firstOrNull() ?: Color(0xFFFF6A00)
            )
        }

        if (showSettings) {
            ModalBottomSheet(
                onDismissRequest = { showSettings = false },
                sheetState = sheetState
            ) {
                SettingsScreen(
                    appLanguage = appLanguage,
                    selectedCategories = selectedCategories,
                    bgmEnabled = bgmEnabled,
                    bgmVolume = bgmVolume,
                    isRecordPermissionGranted = recordPermissionGranted,
                    audioController = audioController,
                    feedbackRecorder = feedbackRecorder,
                    coroutineScope = scope,
                    onRequestRecordPermission = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    onAppLanguageChange = { newLang ->
                        appLanguageRaw = newLang.raw
                        prefs.edit().putString(AppLanguage.storageKey, newLang.raw).apply()
                    },
                    onSelectedCategoriesChange = { set ->
                        val storage = ItemCategory.toStorage(set)
                        themeCategoriesStorage = storage
                        prefs.edit().putString(ItemCategory.appStorageKey, storage).apply()
                    },
                    onBgmEnabledChange = { checked -> bgmEnabled = checked },
                    onBgmVolumeChange = { v -> bgmVolume = v },
                )
            }
        }
    }
}

private val AppOrange = Color(0xFFFF9500)
private val SegmentTrack = Color(0xFFE8E8E8)
private val SegmentInactiveText = Color(0xFF808080)

@Composable
private fun TopBar(
    appLanguage: AppLanguage,
    maxNumber: Int,
    quizMode: QuizMode,
    score: Int,
    compact: Boolean,
    onOpenSettings: () -> Unit,
    onSetMaxNumber: (Int) -> Unit,
    onSetMode: (QuizMode) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // On narrow screens (phones) a single row overflows and the difficulty
        // buttons get pushed off-screen, so they stop receiving touches. Stack
        // the controls vertically instead.
        val stacked = maxWidth < 600.dp

        if (stacked) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DifficultyToggle(maxNumber, fontSize = 14.sp, hPad = 14.dp, vPad = 7.dp, onSetMaxNumber)
                    Spacer(modifier = Modifier.weight(1f))
                    ScoreStars(score, starSize = 18.sp, rowHeight = 22.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    SettingsGear(appLanguage, iconSize = 22.dp, pad = 7.dp, onOpenSettings)
                }
                ModeToggle(appLanguage, quizMode, fontSize = 16.sp, hPad = 18.dp, vPad = 9.dp, onSetMode)
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (compact) 10.dp else 16.dp, vertical = if (compact) 6.dp else 10.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 12.dp)
            ) {
                Spacer(modifier = Modifier.weight(1f))
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)
                ) {
                    ModeToggle(
                        appLanguage, quizMode,
                        fontSize = if (compact) 14.sp else 18.sp,
                        hPad = if (compact) 12.dp else 22.dp,
                        vPad = if (compact) 8.dp else 10.dp,
                        onSetMode
                    )
                    ScoreStars(
                        score,
                        starSize = if (compact) 16.sp else 20.sp,
                        rowHeight = if (compact) 20.dp else 24.dp
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                DifficultyToggle(
                    maxNumber,
                    fontSize = if (compact) 13.sp else 14.sp,
                    hPad = if (compact) 12.dp else 14.dp,
                    vPad = if (compact) 6.dp else 7.dp,
                    onSetMaxNumber
                )
                SettingsGear(
                    appLanguage,
                    iconSize = if (compact) 18.dp else 24.dp,
                    pad = if (compact) 6.dp else 8.dp,
                    onOpenSettings
                )
            }
        }
    }
}

@Composable
private fun ModeToggle(
    appLanguage: AppLanguage,
    quizMode: QuizMode,
    fontSize: TextUnit,
    hPad: Dp,
    vPad: Dp,
    onSetMode: (QuizMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(SegmentTrack)
    ) {
        SegmentChip(
            text = if (appLanguage == AppLanguage.KOREAN) "갯수 - 숫자" else "Count → Number",
            selected = quizMode == QuizMode.OBJECTS_TO_NUMBER,
            fontSize = fontSize,
            hPad = hPad,
            vPad = vPad,
        ) { onSetMode(QuizMode.OBJECTS_TO_NUMBER) }
        SegmentChip(
            text = if (appLanguage == AppLanguage.KOREAN) "숫자 - 갯수" else "Number → Count",
            selected = quizMode == QuizMode.NUMBER_TO_OBJECTS,
            fontSize = fontSize,
            hPad = hPad,
            vPad = vPad,
        ) { onSetMode(QuizMode.NUMBER_TO_OBJECTS) }
    }
}

@Composable
private fun DifficultyToggle(
    maxNumber: Int,
    fontSize: TextUnit,
    hPad: Dp,
    vPad: Dp,
    onSetMaxNumber: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(SegmentTrack)
    ) {
        listOf(5, 10).forEach { n ->
            SegmentChip(
                text = "1–$n",
                selected = maxNumber == n,
                fontSize = fontSize,
                hPad = hPad,
                vPad = vPad,
            ) { onSetMaxNumber(n) }
        }
    }
}

@Composable
private fun ScoreStars(score: Int, starSize: TextUnit, rowHeight: Dp) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.height(rowHeight),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(min(score, 10)) {
            Text(text = "★", color = Color(0xFFFFCC00), fontSize = starSize)
        }
    }
}

@Composable
private fun SettingsGear(
    appLanguage: AppLanguage,
    iconSize: Dp,
    pad: Dp,
    onOpenSettings: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(AppOrange.copy(alpha = 0.12f))
            .clickable { onOpenSettings() }
            .padding(pad),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Settings,
            contentDescription = if (appLanguage == AppLanguage.KOREAN) "설정" else "Settings",
            tint = AppOrange,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
private fun SegmentChip(
    text: String,
    selected: Boolean,
    fontSize: TextUnit,
    hPad: Dp,
    vPad: Dp,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) AppOrange else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = hPad, vertical = vPad),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else SegmentInactiveText,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}

@Composable
private fun TargetPanel(
    quizMode: QuizMode,
    targetNumber: Int,
    theme: Theme,
    borderColor: Color,
    showingCountHint: Boolean,
    highlightedCount: Int,
    numberFont: TextUnit,
    cornerRadius: Dp,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(cornerRadius)
    val strokeColor = if (showingCountHint) AppOrange else borderColor
    val strokeWidth = if (showingCountHint) 8.dp else 6.dp

    Box(
        modifier = modifier
            .shadow(elevation = 10.dp, shape = shape, clip = false)
            .background(Color.White, shape)
            .border(strokeWidth, strokeColor, shape)
            .padding(18.dp),
        contentAlignment = Alignment.Center
    ) {
        if (quizMode == QuizMode.NUMBER_TO_OBJECTS) {
            Text(
                text = targetNumber.toString(),
                color = borderColor,
                fontSize = numberFont,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        } else {
            AnswerCardView(
                count = targetNumber,
                theme = theme,
                highlightedCount = highlightedCount,
                emphasizeHint = showingCountHint,
                iconScale = 1.6f
            )
        }
    }
}

@Composable
private fun OptionsGrid(
    quizMode: QuizMode,
    theme: Theme,
    optionValues: List<Int>,
    optionColors: List<Color>,
    optionHeight: Dp,
    gap: Dp,
    selectedOption: Int?,
    isCorrect: Boolean?,
    shaking: Boolean,
    wrongIndex: Int?,
    onTap: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fallbackColors = listOf(
        Color(0xFFFF9800), Color(0xFF26A69A), Color(0xFF7E57C2), Color(0xFFE53935)
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        for (rowStart in listOf(0, 2)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(gap),
                modifier = Modifier.fillMaxWidth()
            ) {
                for (idx in rowStart until rowStart + 2) {
                    val value = optionValues[idx]
                    OptionCell(
                        quizMode = quizMode,
                        theme = theme,
                        value = value,
                        color = optionColors.getOrElse(idx) { fallbackColors[idx % fallbackColors.size] },
                        selected = selectedOption == value,
                        correct = isCorrect,
                        shaking = shaking && wrongIndex == idx,
                        height = optionHeight,
                    ) { onTap(value, idx) }
                }
            }
        }
    }
}

@Composable
private fun CelebrationOverlay(number: Int, comment: String, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x88000000))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "✅", fontSize = 64.sp)
            Text(
                text = number.toString(),
                fontSize = 84.sp,
                color = color,
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = comment, color = Color(0xFF22C55E), fontWeight = FontWeight.Bold)
        }
    }
}

private fun answerColumns(count: Int): Int = when {
    count <= 3 -> count.coerceAtLeast(1)
    count <= 6 -> 3
    else -> 4
}

private fun answerRows(count: Int): Int {
    val pr = answerColumns(count)
    return ((count + pr - 1) / pr).coerceAtLeast(1)
}

@Composable
private fun AnswerCardView(
    count: Int,
    theme: Theme,
    highlightedCount: Int,
    emphasizeHint: Boolean,
    iconScale: Float = 1f,
) {
    val pr = answerColumns(count)

    var rem = count
    val rows = mutableListOf<Int>()
    while (rem > 0) {
        val n = min(rem, pr)
        rows.add(n)
        rem -= n
    }

    val baseIcon = when {
        count <= 4 -> 36f
        count <= 6 -> 32f
        else -> 28f
    }
    val baseSpacing = when {
        count <= 4 -> 10f
        count <= 6 -> 9f
        else -> 8f
    }
    val iconSize = (baseIcon * iconScale).coerceAtMost(72f).sp
    val iconSpacing = (baseSpacing * iconScale).coerceIn(6f, 18f).dp

    Column(verticalArrangement = Arrangement.spacedBy(iconSpacing)) {
        var indexStart = 1
        rows.forEach { rowCount ->
            Row(horizontalArrangement = Arrangement.spacedBy(iconSpacing)) {
                repeat(rowCount) { col ->
                    val index = indexStart + col
                    val active = emphasizeHint && index <= highlightedCount
                    val bg = if (active) theme.colors[(index - 1) % theme.colors.size].copy(alpha = 0.35f) else Color.Transparent
                    Box(
                        modifier = Modifier
                            .background(bg, RoundedCornerShape(999.dp))
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = theme.item, fontSize = iconSize, fontWeight = FontWeight.Black)
                    }
                }
            }
            indexStart += rowCount
        }
    }
}

@Composable
private fun RowScope.OptionCell(
    quizMode: QuizMode,
    theme: Theme,
    value: Int,
    color: Color,
    selected: Boolean,
    correct: Boolean?,
    shaking: Boolean,
    height: Dp,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    val borderWidth: Dp
    val borderColor: Color
    when {
        !selected -> { borderWidth = 2.5.dp; borderColor = Color(0xFFD1D1D1) }
        correct == true -> { borderWidth = 4.dp; borderColor = Color(0xFF22C55E) }
        correct == false -> { borderWidth = 4.dp; borderColor = Color(0xFFEF4444) }
        else -> { borderWidth = 2.5.dp; borderColor = Color(0xFFD1D1D1) }
    }
    val bg = when {
        !selected -> Color.White
        correct == true -> Color(0xFF22C55E).copy(alpha = 0.12f)
        correct == false -> Color(0xFFEF4444).copy(alpha = 0.12f)
        else -> Color.White
    }

    val numberFont = when {
        height >= 200.dp -> 96.sp
        height >= 160.dp -> 84.sp
        height >= 130.dp -> 70.sp
        else -> 54.sp
    }

    Box(
        modifier = Modifier
            .weight(1f)
            .height(height)
            .offset(x = if (shaking) 8.dp else 0.dp)
            .shadow(elevation = 8.dp, shape = shape, clip = false)
            .background(bg, shape)
            .border(borderWidth, borderColor, shape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        if (quizMode == QuizMode.NUMBER_TO_OBJECTS) {
            AnswerCardView(
                count = value,
                theme = theme,
                highlightedCount = 0,
                emphasizeHint = false
            )
        } else {
            Text(
                text = value.toString(),
                fontSize = numberFont,
                color = color,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

