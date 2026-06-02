package com.mrnoh99.numbercount.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
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
import com.mrnoh99.numbercount.GameViewModel
import com.mrnoh99.numbercount.R
import com.mrnoh99.numbercount.numColors
import com.mrnoh99.numbercount.AppLanguage
import com.mrnoh99.numbercount.ItemCategory
import com.mrnoh99.numbercount.QuizMode
import com.mrnoh99.numbercount.Theme
import com.mrnoh99.numbercount.NumberCountApplication
import com.mrnoh99.numbercount.SettingsActivity
import com.mrnoh99.numbercount.ThemeCatalog
import kotlin.math.min
import kotlin.random.Random

@Composable
fun NumberCountApp(context: Context) {
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

    val audioViewModel = remember {
        (context.applicationContext as NumberCountApplication).audioViewModel
    }
    val audioController = audioViewModel.audioController
    val feedbackRecorder = audioViewModel.feedbackRecorder

    val gameViewModel: GameViewModel = viewModel(
        factory = GameViewModel.factory(audioController, feedbackRecorder)
    )

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, prefs) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                appLanguageRaw = prefs.getString(AppLanguage.storageKey, AppLanguage.KOREAN.raw)
                    ?: AppLanguage.KOREAN.raw
                themeCategoriesStorage = prefs.getString(
                    ItemCategory.appStorageKey,
                    ItemCategory.defaultStorageValue,
                ) ?: ItemCategory.defaultStorageValue
                bgmEnabled = prefs.getBoolean(bgmEnabledKey, true)
                bgmVolume = prefs.getFloat(bgmVolumeKey, 0.12f)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
    val showWrongImage = gameViewModel.showWrongImage
    val feedbackInteractive = gameViewModel.feedbackInteractive
    val showingCountHint = gameViewModel.showingCountHint
    val highlightedCount = gameViewModel.highlightedCount
    val hintWord = gameViewModel.hintWord

    // 정답/오답이 확정되는 순간 FamilyFinder와 동일하게 결과별 진동을 한 번 재생한다.
    LaunchedEffect(isCorrect) {
        when (isCorrect) {
            true -> vibrateForResult(context, correct = true)
            false -> vibrateForResult(context, correct = false)
            null -> {}
        }
    }

    val haptic = LocalHapticFeedback.current

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
                    onOpenSettings = {
                        context.startActivity(Intent(context, SettingsActivity::class.java))
                    },
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
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        gameViewModel.onQuestionPanelTapped(appLanguage)
                                    }
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
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    gameViewModel.onQuestionPanelTapped(appLanguage)
                                }
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

        // FamilyFinder(가족찾기)와 동일한 정답/오답 제시 그림 + 파란 "다음" 버튼.
        if (showCelebration) {
            FeedbackImageOverlay(
                resId = R.drawable.praise,
                contentDescription = "정답",
                interactive = feedbackInteractive,
                onNext = { gameViewModel.proceedFromCorrect(selectedCategories) },
            )
        }
        if (showWrongImage) {
            FeedbackImageOverlay(
                resId = R.drawable.wrong,
                contentDescription = "오답",
                interactive = feedbackInteractive,
                onNext = { gameViewModel.proceedFromWrong(appLanguage) },
            )
        }
    }
}

/** 시스템 진동기를 가져온다(없으면 null). */
private fun obtainVibrator(context: Context): Vibrator? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

/**
 * 정답/오답에 따라 서로 다른 진동 패턴을 재생한다(FamilyFinder와 동일).
 * - 정답: 가볍고 경쾌한 두 번의 짧은 진동
 * - 오답: 묵직한 한 번의 긴 진동
 */
private fun vibrateForResult(context: Context, correct: Boolean) {
    val vibrator = obtainVibrator(context)?.takeIf { it.hasVibrator() } ?: return
    val effect = if (correct) {
        VibrationEffect.createWaveform(longArrayOf(0, 40, 80, 40), -1)
    } else {
        VibrationEffect.createOneShot(220, VibrationEffect.DEFAULT_AMPLITUDE)
    }
    vibrator.vibrate(effect)
}

/**
 * FamilyFinder와 동일하게 정답/오답 그림을 어두운 배경 위에 가로로 꽉 차게 보여주고,
 * 음성이 끝나면(interactive) 가운데 아래쪽에 파란 "다음" 버튼을 띄워 눌러서 진행한다.
 */
@Composable
private fun FeedbackImageOverlay(
    resId: Int,
    contentDescription: String,
    interactive: Boolean,
    onNext: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x88000000))
            .padding(24.dp),
        contentAlignment = Alignment.Center,
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
                Image(
                    painter = painterResource(resId),
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(24.dp)),
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                if (interactive) {
                    NextButton(onClick = onNext)
                }
            }
        }
    }
}

/** 올려주신 파란 버튼 이미지(btn_blue) 위에 글자를 얹은 어린이용 다음 버튼(FamilyFinder와 동일). */
@Composable
private fun NextButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .size(160.dp)
            // 포커스/터치 시 ripple·하이라이트가 생기지 않도록 indication 제거.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.btn_blue),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
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
        if (score >= 10) {
            Text(text = "★", color = Color(0xFF2196F3), fontSize = starSize)
        } else {
            repeat(score) {
                Text(text = "★", color = Color(0xFFFFCC00), fontSize = starSize)
            }
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
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(AppOrange.copy(alpha = 0.12f))
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onOpenSettings()
            }
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

    val haptic = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .weight(1f)
            .height(height)
            .offset(x = if (shaking) 8.dp else 0.dp)
            .shadow(elevation = 8.dp, shape = shape, clip = false)
            .background(bg, shape)
            .border(borderWidth, borderColor, shape)
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
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

