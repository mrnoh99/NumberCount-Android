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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            .background(Color(0xFFFFF6E6))
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(16.dp)
        ) {
            val w = maxWidth
            val h = maxHeight
            val isLandscape = w > h
            val isTablet = w >= 768.dp
            val phoneLandscapeSplit = !isTablet && isLandscape
            val wideSplit = isTablet || phoneLandscapeSplit

            val optionHeight = when {
                isTablet && isLandscape -> 220.dp
                isTablet -> 170.dp
                phoneLandscapeSplit -> 120.dp
                else -> 110.dp
            }
            val targetNumberFont = when {
                isTablet && isLandscape -> 140.sp
                isTablet -> 120.sp
                phoneLandscapeSplit -> 86.sp
                else -> 72.sp
            }
            val topSpacing = if (phoneLandscapeSplit) 8.dp else 12.dp

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(topSpacing)
            ) {
                TopBar(
                    appLanguage = appLanguage,
                    maxNumber = maxNumber,
                    quizMode = quizMode,
                    score = game.score,
                    compact = phoneLandscapeSplit,
                    onOpenSettings = { showSettings = true },
                    onSetMaxNumber = { maxNumber = it },
                    onSetMode = { quizMode = it }
                )

                if (wideSplit) {
                    val gap = if (phoneLandscapeSplit) 12.dp else 18.dp
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(gap),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: target panel + hint
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            TargetPanel(
                                quizMode = quizMode,
                                gameTargetNumber = game.targetNumber,
                                theme = game.theme,
                                highlightedCount = highlightedCount,
                                showingCountHint = showingCountHint,
                                hintWord = hintWord,
                                numberFont = targetNumberFont,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .sizeIn(maxWidth = if (isTablet) 520.dp else 360.dp)
                                    .clickable { onQuestionPanelTapped() }
                            )
                        }

                        // Right: options grid 2x2
                        Column(
                            modifier = Modifier
                                .width(if (isTablet) 520.dp else 320.dp)
                                .fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OptionsGrid(
                                optionHeight = optionHeight,
                                optionValues = game.options,
                                optionColors = game.theme.colors,
                                selectedOption = selectedOption,
                                isCorrect = isCorrect,
                                shaking = shaking,
                                wrongIndex = wrongIndex,
                                onTap = { opt, idx -> tapOption(opt, idx) }
                            )
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                } else {
                    // Phone portrait (stack)
                    TargetPanel(
                        quizMode = quizMode,
                        gameTargetNumber = game.targetNumber,
                        theme = game.theme,
                        highlightedCount = highlightedCount,
                        showingCountHint = showingCountHint,
                        hintWord = hintWord,
                        numberFont = targetNumberFont,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onQuestionPanelTapped() }
                    )

                    OptionsGrid(
                        optionHeight = optionHeight,
                        optionValues = game.options,
                        optionColors = game.theme.colors,
                        selectedOption = selectedOption,
                        isCorrect = isCorrect,
                        shaking = shaking,
                        wrongIndex = wrongIndex,
                        onTap = { opt, idx -> tapOption(opt, idx) }
                    )

                    Spacer(modifier = Modifier.weight(1f))
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
    val spacing = if (compact) 8.dp else 12.dp

    Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onOpenSettings) {
                Text(if (appLanguage == AppLanguage.KOREAN) "설정" else "Settings")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onSetMaxNumber(5) }, enabled = maxNumber != 5) { Text("1–5") }
                Button(onClick = { onSetMaxNumber(10) }, enabled = maxNumber != 10) { Text("1–10") }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { onSetMode(QuizMode.OBJECTS_TO_NUMBER) },
                enabled = quizMode != QuizMode.OBJECTS_TO_NUMBER,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (appLanguage == AppLanguage.KOREAN) "갯수 - 숫자" else "Count → Number")
            }
            Button(
                onClick = { onSetMode(QuizMode.NUMBER_TO_OBJECTS) },
                enabled = quizMode != QuizMode.NUMBER_TO_OBJECTS,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (appLanguage == AppLanguage.KOREAN) "숫자 - 갯수" else "Number → Count")
            }
        }

        Text(
            text = (1..min(score, 10)).joinToString(" ") { "★" },
            color = Color(0xFFFFD600),
            fontWeight = FontWeight.Bold,
            fontSize = if (compact) 14.sp else 16.sp
        )
    }
}

@Composable
private fun TargetPanel(
    quizMode: QuizMode,
    gameTargetNumber: Int,
    theme: Theme,
    highlightedCount: Int,
    showingCountHint: Boolean,
    hintWord: String,
    numberFont: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .background(Color.White, RoundedCornerShape(26.dp))
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (quizMode == QuizMode.NUMBER_TO_OBJECTS) {
            val color = theme.colors.firstOrNull() ?: Color(0xFFFF6A00)
            Text(
                text = gameTargetNumber.toString(),
                color = color,
                fontSize = numberFont,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            AnswerCardView(
                count = gameTargetNumber,
                theme = theme,
                highlightedCount = highlightedCount,
                emphasizeHint = showingCountHint
            )
        }

        if (showingCountHint && hintWord.isNotBlank()) {
            Text(
                text = hintWord,
                color = Color(0xFFFF6A00),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun OptionsGrid(
    optionHeight: androidx.compose.ui.unit.Dp,
    optionValues: List<Int>,
    optionColors: List<Color>,
    selectedOption: Int?,
    isCorrect: Boolean?,
    shaking: Boolean,
    wrongIndex: Int?,
    onTap: (Int, Int) -> Unit,
) {
    val opt0 = optionValues[0]
    val opt1 = optionValues[1]
    val opt2 = optionValues[2]
    val opt3 = optionValues[3]

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        OptionCardView(
            value = opt0,
            color = optionColors.getOrElse(0) { Color(0xFFFF9800) },
            selected = selectedOption == opt0,
            correct = isCorrect,
            shaking = shaking && wrongIndex == 0,
            height = optionHeight,
        ) { onTap(opt0, 0) }
        OptionCardView(
            value = opt1,
            color = optionColors.getOrElse(1) { Color(0xFF26A69A) },
            selected = selectedOption == opt1,
            correct = isCorrect,
            shaking = shaking && wrongIndex == 1,
            height = optionHeight,
        ) { onTap(opt1, 1) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        OptionCardView(
            value = opt2,
            color = optionColors.getOrElse(2) { Color(0xFF7E57C2) },
            selected = selectedOption == opt2,
            correct = isCorrect,
            shaking = shaking && wrongIndex == 2,
            height = optionHeight,
        ) { onTap(opt2, 2) }
        OptionCardView(
            value = opt3,
            color = optionColors.getOrElse(3) { Color(0xFFE53935) },
            selected = selectedOption == opt3,
            correct = isCorrect,
            shaking = shaking && wrongIndex == 3,
            height = optionHeight,
        ) { onTap(opt3, 3) }
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

@Composable
private fun AnswerCardView(
    count: Int,
    theme: Theme,
    highlightedCount: Int,
    emphasizeHint: Boolean,
) {
    val pr = when {
        count <= 3 -> count
        count <= 6 -> 3
        else -> 4
    }

    var rem = count
    val rows = mutableListOf<Int>()
    while (rem > 0) {
        val n = min(rem, pr)
        rows.add(n)
        rem -= n
    }

    val iconSize = when {
        count <= 4 -> 36.sp
        count <= 6 -> 32.sp
        else -> 28.sp
    }
    val iconSpacing = when {
        count <= 4 -> 10.dp
        count <= 6 -> 9.dp
        else -> 8.dp
    }

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
private fun RowScope.OptionCardView(
    value: Int,
    color: Color,
    selected: Boolean,
    correct: Boolean?,
    shaking: Boolean,
    height: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    val borderColor = when {
        !selected -> Color(0xFFCCCCCC)
        correct == true -> Color(0xFF22C55E)
        correct == false -> Color(0xFFEF4444)
        else -> Color(0xFFCCCCCC)
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
        height >= 130.dp -> 66.sp
        else -> 54.sp
    }

    Box(
        modifier = Modifier
            .weight(1f)
            .height(height)
            .offset(x = if (shaking) 8.dp else 0.dp)
            .background(bg, RoundedCornerShape(22.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = value.toString(),
            fontSize = numberFont,
            color = color,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
            maxLines = 1,
        )
    }
}

