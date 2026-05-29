package com.mrnoh99.numbercount.ui

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import com.mrnoh99.numbercount.AppAudioViewModel
import com.mrnoh99.numbercount.GameViewModel
import com.mrnoh99.numbercount.numColors
import com.mrnoh99.numbercount.AppLanguage
import com.mrnoh99.numbercount.ItemCategory
import com.mrnoh99.numbercount.QuizMode
import com.mrnoh99.numbercount.Theme
import com.mrnoh99.numbercount.ThemeCatalog
import com.mrnoh99.numbercount.ui.SettingsScreen
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

    val audioViewModel: AppAudioViewModel = viewModel(
        factory = ViewModelProvider.AndroidViewModelFactory.getInstance(
            context.applicationContext as Application
        )
    )
    val audioController = audioViewModel.audioController
    val feedbackRecorder = audioViewModel.feedbackRecorder

    val gameViewModel: GameViewModel = viewModel(
        factory = GameViewModel.factory(audioController, feedbackRecorder)
    )

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

    SideEffect {
        gameViewModel.updateSettings(
            themeCategoriesStorage = themeCategoriesStorage,
            appLanguageRaw = appLanguageRaw,
            categories = selectedCategories,
        )
    }

    val game = gameViewModel.game
    val maxNumber = gameViewModel.maxNumber
    val quizMode = gameViewModel.quizMode
    val selectedOption = gameViewModel.selectedOption
    val isCorrect = gameViewModel.isCorrect
    val locked = gameViewModel.locked
    val wrongIndex = gameViewModel.wrongIndex
    val shaking = gameViewModel.shaking
    val showCelebration = gameViewModel.showCelebration
    val showingCountHint = gameViewModel.showingCountHint
    val highlightedCount = gameViewModel.highlightedCount
    val hintWord = gameViewModel.hintWord

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFFF7E8))
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
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
            val hintFont = if (isTablet) 36.sp else 28.sp
            val layoutMin = minOf(w, h)
            val panelGap = (layoutMin * 0.028f).coerceIn(12.dp, 28.dp)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (wideSplit) {
                            Modifier.safeDrawingPadding()
                        } else {
                            Modifier.windowInsetsPadding(
                                WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)
                            )
                        }
                    )
            ) {
                if (!wideSplit) {
                    Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                    Spacer(modifier = Modifier.weight(1f))
                }
                TopBar(
                    appLanguage = appLanguage,
                    maxNumber = maxNumber,
                    quizMode = quizMode,
                    score = game.score,
                    compact = compactBar,
                    portraitLayout = !wideSplit,
                    omitTopPadding = !wideSplit,
                    layoutWidth = w,
                    layoutHeight = h,
                    onOpenSettings = { showSettings = true },
                    onSetMaxNumber = { gameViewModel.maxNumber = it },
                    onSetMode = { gameViewModel.quizMode = it }
                )

                if (wideSplit) {
                    val rightPanelW = if (phoneLandscape) minOf(w * 0.46f, 320.dp) else minOf(w * 0.46f, 560.dp)
                    val landscapeBottomExtra = (h * 0.028f).coerceIn(10.dp, 22.dp)
                    val maxOptionSide = minOf((rightPanelW - panelGap) / 2f, (h - 120.dp) / 2f).coerceIn(72.dp, 260.dp)
                    val optionSide = if (quizMode == QuizMode.NUMBER_TO_OBJECTS) {
                        val rows = answerRows(game.options.maxOrNull() ?: 1)
                        val desired = 56.dp + 56.dp * rows
                        desired.coerceAtMost(maxOptionSide).coerceAtLeast(minOf(72.dp, maxOptionSide))
                    } else {
                        maxOptionSide
                    }
                    val targetSide = minOf((w - rightPanelW - panelGap) * 0.9f, h * 0.74f).coerceAtMost(560.dp)
                    val targetPanelModifier = if (quizMode == QuizMode.NUMBER_TO_OBJECTS) {
                        Modifier.size(numberPanelSize(targetSide, h, isTablet))
                    } else {
                        Modifier.size(targetSide)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                top = panelGap,
                                bottom = panelGap + landscapeBottomExtra,
                                start = if (phoneLandscape) 12.dp else 28.dp,
                                end = if (phoneLandscape) 12.dp else 28.dp,
                            ),
                        horizontalArrangement = Arrangement.spacedBy(panelGap),
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
                                hintWord = hintWord,
                                hintFont = hintFont,
                                numberFont = targetNumberFont,
                                cornerRadius = 36.dp,
                                modifier = targetPanelModifier
                                    .clickable { gameViewModel.onQuestionPanelTapped(appLanguage) }
                            )
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
                                gap = panelGap,
                                selectedOption = selectedOption,
                                isCorrect = isCorrect,
                                shaking = shaking,
                                wrongIndex = wrongIndex,
                                onTap = { opt, idx ->
                                    gameViewModel.tapOption(opt, idx, appLanguage, selectedCategories)
                                }
                            )
                        }
                    }
                } else {
                    // Stacked layout (phone portrait & tablet portrait)
                    val gridWidth = if (isTablet) minOf(w * 0.82f, 560.dp) else w
                    val optionSide = (gridWidth - panelGap) / 2f
                    val optionHeight = if (quizMode == QuizMode.NUMBER_TO_OBJECTS) {
                        val rows = answerRows(game.options.maxOrNull() ?: 1)
                        val perRow = (h * 0.062f).coerceIn(44.dp, if (isTablet) 68.dp else 56.dp)
                        (h * 0.05f + perRow * rows).coerceIn(
                            (h * 0.13f).coerceAtLeast(100.dp),
                            (h * 0.36f).coerceAtMost(if (isTablet) 320.dp else 300.dp),
                        )
                    } else if (isTablet) {
                        optionSide.coerceIn(w * 0.18f, minOf(w * 0.38f, 240.dp))
                    } else {
                        (h * 0.135f).coerceIn(94.dp, 136.dp)
                    }
                    val targetSide = if (isTablet) {
                        minOf(w * 0.6f, h * 0.34f)
                    } else {
                        minOf(w * 0.82f, h * 0.30f)
                    }
                    val targetPanelModifier = if (quizMode == QuizMode.NUMBER_TO_OBJECTS) {
                        Modifier.size(numberPanelSize(targetSide, h, isTablet))
                    } else {
                        Modifier.size(targetSide)
                    }
                    val gridModifier = if (isTablet) Modifier.width(gridWidth) else Modifier.fillMaxWidth()
                    val gameHorizontalPad = (w * 0.04f).coerceIn(12.dp, 20.dp)

                    Spacer(modifier = Modifier.height(panelGap))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = gameHorizontalPad),
                        contentAlignment = Alignment.Center,
                    ) {
                        TargetPanel(
                            quizMode = quizMode,
                            targetNumber = game.targetNumber,
                            theme = game.theme,
                            borderColor = targetColor,
                            showingCountHint = showingCountHint,
                            highlightedCount = highlightedCount,
                            hintWord = hintWord,
                            hintFont = hintFont,
                            numberFont = targetNumberFont,
                            cornerRadius = if (isTablet) 36.dp else 30.dp,
                            modifier = targetPanelModifier
                                .clickable { gameViewModel.onQuestionPanelTapped(appLanguage) }
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = gameHorizontalPad),
                        contentAlignment = Alignment.Center,
                    ) {
                        OptionsGrid(
                            quizMode = quizMode,
                            theme = game.theme,
                            optionValues = game.options,
                            optionColors = game.theme.colors,
                            optionHeight = optionHeight,
                            gap = panelGap,
                            selectedOption = selectedOption,
                            isCorrect = isCorrect,
                            shaking = shaking,
                            wrongIndex = wrongIndex,
                            onTap = { opt, idx ->
                                gameViewModel.tapOption(opt, idx, appLanguage, selectedCategories)
                            },
                            modifier = gridModifier
                        )
                    }
                }
                if (!wideSplit) {
                    Spacer(modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        }

        if (showCelebration) {
            CelebrationOverlay(
                number = game.targetNumber,
                comment = if (appLanguage == AppLanguage.KOREAN) "그래 잘했다!" else "That's right!",
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

private val AppOrange = Color(0xFFE08600)
private val SegmentTrack = Color(0xFFECECEC)
private val SegmentInactiveText = Color(0xFF424242)
private val HintTextColor = Color(0xFFD97700)

@Composable
private fun TopBar(
    appLanguage: AppLanguage,
    maxNumber: Int,
    quizMode: QuizMode,
    score: Int,
    compact: Boolean,
    portraitLayout: Boolean,
    omitTopPadding: Boolean = false,
    layoutWidth: Dp,
    layoutHeight: Dp,
    onOpenSettings: () -> Unit,
    onSetMaxNumber: (Int) -> Unit,
    onSetMode: (QuizMode) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // On narrow screens (phones) a single row overflows and the difficulty
        // buttons get pushed off-screen, so they stop receiving touches. Stack
        // the controls vertically instead.
        val stacked = maxWidth < 600.dp
        val padH = if (portraitLayout) (layoutWidth * 0.032f).coerceIn(10.dp, 18.dp) else if (compact) 10.dp else 16.dp
        val padV = if (portraitLayout) (layoutHeight * 0.011f).coerceIn(6.dp, 12.dp) else if (compact) 6.dp else 10.dp
        val barPadding = Modifier.padding(
            start = padH,
            end = padH,
            top = if (omitTopPadding) 0.dp else padV,
            bottom = padV,
        )
        val barGap = if (portraitLayout) (layoutHeight * 0.01f).coerceIn(6.dp, 12.dp) else 8.dp
        val rowGap = if (portraitLayout) (layoutWidth * 0.018f).coerceIn(10.dp, 16.dp) else if (compact) 6.dp else 12.dp
        val modeScoreGap = if (portraitLayout) (layoutHeight * 0.01f).coerceIn(6.dp, 10.dp) else if (compact) 6.dp else 8.dp
        val toggleFont = if (portraitLayout) {
            (layoutWidth.value * 0.04f).coerceIn(15f, 19f).sp
        } else if (compact) {
            15.sp
        } else {
            17.sp
        }
        val chipHPad = if (portraitLayout) (layoutWidth * 0.028f).coerceIn(14.dp, 24.dp) else if (compact) 12.dp else 22.dp
        val chipVPad = if (portraitLayout) (layoutHeight * 0.013f).coerceIn(9.dp, 13.dp) else if (compact) 9.dp else 11.dp
        val toggleRowHeight = chipVPad * 2 + 24.dp
        val gearSize = if (portraitLayout) (layoutWidth * 0.04f).coerceIn(22.dp, 28.dp) else if (compact) 18.dp else 24.dp
        val gearPad = if (portraitLayout) (layoutWidth * 0.012f).coerceIn(7.dp, 10.dp) else if (compact) 6.dp else 8.dp
        val starSize = if (portraitLayout) {
            (layoutWidth.value * 0.034f).coerceIn(19f, 24f).sp
        } else if (compact) {
            17.sp
        } else {
            21.sp
        }
        val starRowH = if (portraitLayout) (layoutHeight * 0.032f).coerceIn(22.dp, 28.dp) else if (compact) 20.dp else 24.dp

        if (stacked) {
            val stackedToggleFont = if (portraitLayout) {
                (layoutWidth.value * 0.042f).coerceIn(15f, 18f).sp
            } else {
                15.sp
            }
            val stackedChipHPad = if (portraitLayout) (layoutWidth * 0.038f).coerceIn(12.dp, 20.dp) else 14.dp
            val stackedChipVPad = if (portraitLayout) (layoutHeight * 0.011f).coerceIn(7.dp, 11.dp) else 8.dp
            val stackedToggleRowHeight = stackedChipVPad * 2 + 24.dp
            val stackedGearSize = if (portraitLayout) (layoutWidth * 0.055f).coerceIn(20.dp, 26.dp) else 22.dp
            val stackedGearPad = if (portraitLayout) (layoutWidth * 0.018f).coerceIn(6.dp, 9.dp) else 7.dp
            val stackedStarSize = if (portraitLayout) {
                (layoutWidth.value * 0.048f).coerceIn(17f, 22f).sp
            } else {
                19.sp
            }
            val stackedStarRowH = if (portraitLayout) (layoutHeight * 0.03f).coerceIn(20.dp, 26.dp) else 22.dp

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(barPadding),
                verticalArrangement = Arrangement.spacedBy(barGap),
                horizontalAlignment = if (portraitLayout) Alignment.Start else Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DifficultyToggle(
                        maxNumber,
                        fontSize = stackedToggleFont,
                        hPad = stackedChipHPad,
                        vPad = stackedChipVPad,
                        rowHeight = stackedToggleRowHeight,
                        fillWidth = portraitLayout,
                        onSetMaxNumber = onSetMaxNumber,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    ScoreStars(score, starSize = stackedStarSize, rowHeight = stackedStarRowH)
                    Spacer(modifier = Modifier.width((layoutWidth * 0.02f).coerceIn(6.dp, 12.dp)))
                    SettingsGear(appLanguage, iconSize = stackedGearSize, pad = stackedGearPad, onOpenSettings)
                }
                ModeToggle(
                    appLanguage,
                    quizMode,
                    fontSize = stackedToggleFont,
                    hPad = stackedChipHPad,
                    vPad = stackedChipVPad,
                    rowHeight = stackedToggleRowHeight,
                    fillWidth = portraitLayout,
                    onSetMode = onSetMode,
                )
            }
        } else if (!portraitLayout) {
            // Landscape: difficulty → mode → stars → settings (left to right)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(barPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(rowGap),
            ) {
                DifficultyToggle(
                    maxNumber,
                    fontSize = toggleFont,
                    hPad = chipHPad,
                    vPad = chipVPad,
                    rowHeight = toggleRowHeight,
                    onSetMaxNumber = onSetMaxNumber,
                )
                ModeToggle(
                    appLanguage,
                    quizMode,
                    fontSize = toggleFont,
                    hPad = chipHPad,
                    vPad = chipVPad,
                    rowHeight = toggleRowHeight,
                    onSetMode = onSetMode,
                )
                Spacer(modifier = Modifier.weight(1f))
                ScoreStars(
                    score,
                    starSize = starSize,
                    rowHeight = starRowH,
                )
                SettingsGear(
                    appLanguage,
                    iconSize = gearSize,
                    pad = gearPad,
                    onOpenSettings,
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(barPadding),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(rowGap)
            ) {
                Spacer(modifier = Modifier.weight(1f))
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(modeScoreGap)
                ) {
                    ModeToggle(
                        appLanguage, quizMode,
                        fontSize = toggleFont,
                        hPad = chipHPad,
                        vPad = chipVPad,
                        rowHeight = toggleRowHeight,
                        onSetMode = onSetMode,
                    )
                    ScoreStars(
                        score,
                        starSize = starSize,
                        rowHeight = starRowH
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                DifficultyToggle(
                    maxNumber,
                    fontSize = toggleFont,
                    hPad = chipHPad,
                    vPad = chipVPad,
                    rowHeight = toggleRowHeight,
                    onSetMaxNumber = onSetMaxNumber,
                )
                SettingsGear(
                    appLanguage,
                    iconSize = gearSize,
                    pad = gearPad,
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
    rowHeight: Dp,
    fillWidth: Boolean = false,
    onSetMode: (QuizMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .height(rowHeight)
            .clip(RoundedCornerShape(20.dp))
            .background(SegmentTrack)
    ) {
        SegmentChip(
            text = if (appLanguage == AppLanguage.KOREAN) "갯수 - 숫자" else "Count → Number",
            selected = quizMode == QuizMode.OBJECTS_TO_NUMBER,
            fontSize = fontSize,
            hPad = hPad,
            vPad = vPad,
            fillHeight = true,
            modifier = if (fillWidth) Modifier.weight(1f) else Modifier,
        ) { onSetMode(QuizMode.OBJECTS_TO_NUMBER) }
        SegmentChip(
            text = if (appLanguage == AppLanguage.KOREAN) "숫자 - 갯수" else "Number → Count",
            selected = quizMode == QuizMode.NUMBER_TO_OBJECTS,
            fontSize = fontSize,
            hPad = hPad,
            vPad = vPad,
            fillHeight = true,
            modifier = if (fillWidth) Modifier.weight(1f) else Modifier,
        ) { onSetMode(QuizMode.NUMBER_TO_OBJECTS) }
    }
}

@Composable
private fun DifficultyToggle(
    maxNumber: Int,
    fontSize: TextUnit,
    hPad: Dp,
    vPad: Dp,
    rowHeight: Dp,
    fillWidth: Boolean = false,
    onSetMaxNumber: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .then(if (fillWidth) Modifier.fillMaxWidth(0.5f) else Modifier)
            .height(rowHeight)
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
                fillHeight = true,
                modifier = if (fillWidth) Modifier.weight(1f) else Modifier,
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
    fillHeight: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .then(if (fillHeight) Modifier.fillMaxHeight() else Modifier)
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) AppOrange else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = hPad, vertical = vPad),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = fontSize,
            lineHeight = (fontSize.value * 1.25f).sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            color = if (selected) Color.White else SegmentInactiveText,
            textAlign = TextAlign.Center,
            maxLines = 2,
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
    hintWord: String,
    hintFont: TextUnit,
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
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                if (quizMode == QuizMode.NUMBER_TO_OBJECTS) {
                    val showHintLine = showingCountHint && hintWord.isNotBlank()
                    val numberSize = if (showHintLine) {
                        (numberFont.value * 0.78f).coerceAtLeast(48f).sp
                    } else {
                        numberFont
                    }
                    Text(
                        text = targetNumber.toString(),
                        color = borderColor,
                        fontSize = numberSize,
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
                        iconScale = 1.6f,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            if (quizMode == QuizMode.NUMBER_TO_OBJECTS && showingCountHint && hintWord.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = hintWord,
                        color = HintTextColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = hintFont,
                        lineHeight = (hintFont.value * 1.2f).sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            } else if (quizMode != QuizMode.NUMBER_TO_OBJECTS && showingCountHint && hintWord.isNotBlank()) {
                Text(
                    text = hintWord,
                    color = HintTextColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = hintFont,
                    lineHeight = (hintFont.value * 1.2f).sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                )
            }
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

private fun numberPanelSize(baseSide: Dp, screenHeight: Dp, isTablet: Boolean): Dp {
    val scaled = baseSide * if (isTablet) 0.88f else 0.85f
    val maxByHeight = if (isTablet) screenHeight * 0.44f else screenHeight * 0.38f
    val upper = minOf(scaled, maxByHeight, baseSide)
    val lower = minOf(140.dp, upper)
    return upper.coerceAtLeast(lower)
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
        Box(
            modifier = Modifier.size(300.dp),
            contentAlignment = Alignment.Center,
        ) {
            SparkleBurst()
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PulsingCelebrationIcon(text = "⭐", fontSize = 64.sp)
                PulsingCelebrationIcon(
                    content = {
                        Text(
                            text = number.toString(),
                            fontSize = 84.sp,
                            color = color,
                            fontWeight = FontWeight.Black
                        )
                    }
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = comment,
                    color = Color(0xFF22C55E),
                    fontWeight = FontWeight.Black,
                    fontSize = 42.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private data class SparkleSpec(
    val offsetX: Dp,
    val offsetY: Dp,
    val delayMs: Int,
    val size: TextUnit,
)

@Composable
private fun SparkleBurst() {
    val sparkles = remember {
        listOf(
            SparkleSpec((-98).dp, (-72).dp, 0, 30.sp),
            SparkleSpec(92.dp, (-64).dp, 160, 26.sp),
            SparkleSpec((-88).dp, 58.dp, 320, 24.sp),
            SparkleSpec(86.dp, 68.dp, 480, 28.sp),
            SparkleSpec(0.dp, (-102).dp, 240, 32.sp),
            SparkleSpec((-110).dp, 4.dp, 400, 22.sp),
            SparkleSpec(108.dp, 8.dp, 80, 22.sp),
        )
    }
    sparkles.forEach { spec ->
        PulsingSparkle(spec)
    }
}

@Composable
private fun PulsingSparkle(spec: SparkleSpec) {
    val transition = rememberInfiniteTransition(label = "sparkle")
    val scale by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, delayMillis = spec.delayMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "sparkleScale",
    )
    val alpha by transition.animateFloat(
        initialValue = 0.15f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, delayMillis = spec.delayMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "sparkleAlpha",
    )
    Text(
        text = "✨",
        fontSize = spec.size,
        modifier = Modifier
            .offset(x = spec.offsetX, y = spec.offsetY)
            .scale(scale)
            .alpha(alpha),
    )
}

@Composable
private fun PulsingCelebrationIcon(
    text: String? = null,
    fontSize: TextUnit = 64.sp,
    content: (@Composable () -> Unit)? = null,
) {
    val transition = rememberInfiniteTransition(label = "celebrationPulse")
    val scale by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "celebrationScale",
    )
    Box(modifier = Modifier.scale(scale)) {
        if (content != null) {
            content()
        } else {
            Text(text = text.orEmpty(), fontSize = fontSize)
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

private fun answerRowCounts(count: Int): List<Int> {
    val pr = answerColumns(count)
    var rem = count
    val rows = mutableListOf<Int>()
    while (rem > 0) {
        val n = min(rem, pr)
        rows.add(n)
        rem -= n
    }
    return rows
}

private fun computeAnswerCardLayout(
    count: Int,
    rows: List<Int>,
    maxWidth: Dp,
    maxHeight: Dp,
    iconScale: Float,
    density: androidx.compose.ui.unit.Density,
): Pair<TextUnit, Dp> {
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

    var iconSp = (baseIcon * iconScale).coerceAtMost(72f)
    var spacing = (baseSpacing * iconScale).coerceIn(4f, 18f)

    val maxRowItems = rows.maxOrNull() ?: 1
    val numRows = rows.size
    val itemPadding = 8.dp
    val emojiScale = 1.12f

    fun fits(icon: Float, gap: Dp): Boolean {
        val itemSize = with(density) { icon.sp.toDp() } * emojiScale + itemPadding
        val totalW = itemSize * maxRowItems + gap * (maxRowItems - 1).coerceAtLeast(0)
        val totalH = itemSize * numRows + gap * (numRows - 1).coerceAtLeast(0)
        return totalW <= maxWidth && totalH <= maxHeight
    }

    while (!fits(iconSp, spacing.dp)) {
        when {
            spacing > 2f -> spacing -= 1f
            iconSp > 14f -> iconSp -= 1f
            else -> break
        }
    }

    return iconSp.sp to spacing.dp
}

@Composable
private fun AnswerCardView(
    count: Int,
    theme: Theme,
    highlightedCount: Int,
    emphasizeHint: Boolean,
    iconScale: Float = 1f,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val rows = remember(count) { answerRowCounts(count) }
        val density = LocalDensity.current
        val (iconSize, iconSpacing) = remember(count, maxWidth, maxHeight, iconScale, density) {
            computeAnswerCardLayout(count, rows, maxWidth, maxHeight, iconScale, density)
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
        height >= 200.dp -> 100.sp
        height >= 160.dp -> 88.sp
        height >= 130.dp -> 74.sp
        else -> 58.sp
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
                emphasizeHint = false,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
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

