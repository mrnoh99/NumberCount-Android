package com.mrnoh99.numbercount.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrnoh99.numbercount.AppLanguage
import com.mrnoh99.numbercount.ItemCategory
import com.mrnoh99.numbercount.audio.AudioController
import com.mrnoh99.numbercount.audio.FeedbackKind
import com.mrnoh99.numbercount.audio.FeedbackRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun SettingsRoute(
    context: Context,
    audioController: AudioController,
    feedbackRecorder: FeedbackRecorder,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("numbercount_prefs", Context.MODE_PRIVATE) }

    var appLanguageRaw by remember {
        mutableStateOf(prefs.getString(AppLanguage.storageKey, AppLanguage.KOREAN.raw) ?: AppLanguage.KOREAN.raw)
    }
    var themeCategoriesStorage by remember {
        mutableStateOf(
            prefs.getString(ItemCategory.appStorageKey, ItemCategory.defaultStorageValue)
                ?: ItemCategory.defaultStorageValue
        )
    }

    val bgmEnabledKey = "bgmEnabled"
    val bgmVolumeKey = "bgmVolume"
    var bgmEnabled by remember { mutableStateOf(prefs.getBoolean(bgmEnabledKey, true)) }
    var bgmVolume by remember { mutableStateOf(prefs.getFloat(bgmVolumeKey, 0.12f)) }

    val appLanguage = AppLanguage.fromRaw(appLanguageRaw)
    val selectedCategories = ItemCategory.fromStorage(themeCategoriesStorage)

    val initialPermissionGranted =
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    var recordPermissionGranted by remember { mutableStateOf(initialPermissionGranted) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> recordPermissionGranted = granted },
    )

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
        onBack = onBack,
    )
}

@Composable
fun SettingsScreen(
    appLanguage: AppLanguage,
    selectedCategories: Set<ItemCategory>,
    bgmEnabled: Boolean,
    bgmVolume: Float,
    isRecordPermissionGranted: Boolean,
    audioController: AudioController,
    feedbackRecorder: FeedbackRecorder,
    coroutineScope: CoroutineScope,
    onRequestRecordPermission: () -> Unit,
    onAppLanguageChange: (AppLanguage) -> Unit,
    onSelectedCategoriesChange: (Set<ItemCategory>) -> Unit,
    onBgmEnabledChange: (Boolean) -> Unit,
    onBgmVolumeChange: (Float) -> Unit,
    onBack: () -> Unit,
) {
    val isRecording by feedbackRecorder.isRecording.collectAsState()
    // Observe revision so the rows refresh after a recording is saved or deleted.
    val revision by feedbackRecorder.revision.collectAsState()
    var activeSlot by remember { mutableStateOf<FeedbackSlot?>(null) }

    val hasCorrectKo = remember(revision) { feedbackRecorder.hasRecording(FeedbackKind.CORRECT, AppLanguage.KOREAN) }
    val hasCorrectEn = remember(revision) { feedbackRecorder.hasRecording(FeedbackKind.CORRECT, AppLanguage.ENGLISH) }
    val hasWrongKo = remember(revision) { feedbackRecorder.hasRecording(FeedbackKind.WRONG, AppLanguage.KOREAN) }
    val hasWrongEn = remember(revision) { feedbackRecorder.hasRecording(FeedbackKind.WRONG, AppLanguage.ENGLISH) }

    val cream = Color(0xFFFFF6E6)
    val accent = Color(0xFFE08600)
    val textPrimary = Color(0xFF1A1A1A)
    val textSecondary = Color(0xFF424242)

    val titleStyle = MaterialTheme.typography.headlineSmall.copy(
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        color = textPrimary,
    )
    val introStyle = MaterialTheme.typography.bodyLarge.copy(
        fontSize = 17.sp,
        lineHeight = 24.sp,
        color = textSecondary,
    )
    val sectionTitleStyle = MaterialTheme.typography.titleMedium.copy(
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        lineHeight = 24.sp,
        color = accent,
    )
    val bodyStyle = MaterialTheme.typography.bodyLarge.copy(
        fontSize = 17.sp,
        lineHeight = 24.sp,
        color = textPrimary,
    )
    val hintStyle = MaterialTheme.typography.bodyMedium.copy(
        fontSize = 15.sp,
        lineHeight = 22.sp,
        color = textSecondary,
    )
    val rowTitleStyle = MaterialTheme.typography.titleSmall.copy(
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        color = textPrimary,
    )
    val buttonLabelStyle = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 20.sp,
    )

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(cream)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onBack() }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = if (appLanguage == AppLanguage.KOREAN) "뒤로" else "Back",
                        tint = accent,
                    )
                    Spacer(Modifier.padding(start = 4.dp))
                    Text(
                        text = if (appLanguage == AppLanguage.KOREAN) "완료" else "Done",
                        style = buttonLabelStyle.copy(color = accent),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = if (appLanguage == AppLanguage.KOREAN) "설정" else "Settings",
                style = sectionTitleStyle.copy(fontSize = 20.sp),
            )
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.padding(end = 72.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
        Text(
            text = if (appLanguage == AppLanguage.KOREAN) "숫자세기" else "Number Count",
            style = titleStyle,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (appLanguage == AppLanguage.KOREAN)
                "퀴즈 화면과 같은 느낌으로 옵션을 바꿀 수 있어요."
            else
                "Change options in the same style as the quiz screen.",
            style = introStyle,
        )

        Spacer(Modifier.height(20.dp))

        Text(
            text = if (appLanguage == AppLanguage.KOREAN) "언어" else "Language",
            style = sectionTitleStyle,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ChoiceButton(
                text = "한국어",
                selected = appLanguage == AppLanguage.KOREAN,
                labelStyle = buttonLabelStyle,
            ) { onAppLanguageChange(AppLanguage.KOREAN) }
            ChoiceButton(
                text = "English",
                selected = appLanguage == AppLanguage.ENGLISH,
                labelStyle = buttonLabelStyle,
            ) { onAppLanguageChange(AppLanguage.ENGLISH) }
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = if (appLanguage == AppLanguage.KOREAN) "아이템 종류" else "Item categories",
            style = sectionTitleStyle,
        )
        Spacer(Modifier.height(8.dp))
        CategoryRow(
            label = if (appLanguage == AppLanguage.KOREAN) "과일" else "Fruits",
            selected = selectedCategories.contains(ItemCategory.FRUIT),
            labelStyle = bodyStyle,
            buttonLabelStyle = buttonLabelStyle,
            accent = accent,
            onToggle = {
                onSelectedCategoriesChange(toggleCategory(selectedCategories, ItemCategory.FRUIT))
            },
        )
        Spacer(Modifier.height(10.dp))
        CategoryRow(
            label = if (appLanguage == AppLanguage.KOREAN) "자동차" else "Cars",
            selected = selectedCategories.contains(ItemCategory.CAR),
            labelStyle = bodyStyle,
            buttonLabelStyle = buttonLabelStyle,
            accent = accent,
            onToggle = {
                onSelectedCategoriesChange(toggleCategory(selectedCategories, ItemCategory.CAR))
            },
        )
        Spacer(Modifier.height(10.dp))
        CategoryRow(
            label = if (appLanguage == AppLanguage.KOREAN) "채소" else "Vegetables",
            selected = selectedCategories.contains(ItemCategory.VEGETABLE),
            labelStyle = bodyStyle,
            buttonLabelStyle = buttonLabelStyle,
            accent = accent,
            onToggle = {
                onSelectedCategoriesChange(toggleCategory(selectedCategories, ItemCategory.VEGETABLE))
            },
        )

        Spacer(Modifier.height(20.dp))

        Text(
            text = if (appLanguage == AppLanguage.KOREAN) "배경 음악" else "Background music",
            style = sectionTitleStyle,
        )
        Spacer(Modifier.height(10.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (appLanguage == AppLanguage.KOREAN) "배경 음악 재생" else "Play background music",
                        modifier = Modifier.weight(1f),
                        style = bodyStyle.copy(fontWeight = FontWeight.SemiBold),
                    )
                    Switch(
                        checked = bgmEnabled,
                        onCheckedChange = { checked ->
                            audioController.setBgmEnabled(checked)
                            onBgmEnabledChange(checked)
                        }
                    )
                }

                Spacer(Modifier.height(8.dp))
                Slider(
                    value = bgmVolume.coerceIn(0f, 1f),
                    onValueChange = { v ->
                        audioController.setBgmVolume(v)
                        onBgmVolumeChange(v)
                    },
                    valueRange = 0f..1f
                )

                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (appLanguage == AppLanguage.KOREAN)
                        "끄면 배경 음악만 멈춰요. 정답·오답은 음성으로 안내합니다."
                    else
                        "When off, only background music stops. Spoken feedback still plays.",
                    style = hintStyle,
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = if (appLanguage == AppLanguage.KOREAN) "정답·오답 음성 녹음" else "Record answer sounds",
            style = sectionTitleStyle,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (appLanguage == AppLanguage.KOREAN)
                "버튼을 누르고 있는 동안만 녹음됩니다. 손을 떼면 앞뒤 무음이 잘리고 저장돼요."
            else
                "Hold the button to record. Release to trim silence and save.",
            style = hintStyle,
        )

        Spacer(Modifier.height(12.dp))

        FeedbackRow(
            slot = FeedbackSlot.CORRECT_KO,
            title = if (appLanguage == AppLanguage.KOREAN) "정답 · 한국어" else "Correct · Korean",
            titleStyle = rowTitleStyle,
            buttonLabelStyle = buttonLabelStyle,
            appLanguage = appLanguage,
            hasRecording = hasCorrectKo,
            isRecording = isRecording && activeSlot == FeedbackSlot.CORRECT_KO,
            onRequestPermission = onRequestRecordPermission,
            isPermissionGranted = isRecordPermissionGranted,
            onPlay = { feedbackRecorder.play(FeedbackKind.CORRECT, AppLanguage.KOREAN) {} },
            onDelete = { feedbackRecorder.deleteRecording(FeedbackKind.CORRECT, AppLanguage.KOREAN) },
            onHoldStart = {
                activeSlot = FeedbackSlot.CORRECT_KO
                feedbackRecorder.startRecording(
                    scope = coroutineScope,
                    kind = FeedbackKind.CORRECT,
                    language = AppLanguage.KOREAN
                )
            },
            onHoldEnd = {
                activeSlot = null
                coroutineScope.launch {
                    feedbackRecorder.stopAndSave(
                        scope = coroutineScope,
                        kind = FeedbackKind.CORRECT,
                        language = AppLanguage.KOREAN
                    )
                }
            },
        )

        Spacer(Modifier.height(8.dp))

        FeedbackRow(
            slot = FeedbackSlot.CORRECT_EN,
            title = if (appLanguage == AppLanguage.KOREAN) "정답 · 영어" else "Correct · English",
            titleStyle = rowTitleStyle,
            buttonLabelStyle = buttonLabelStyle,
            appLanguage = appLanguage,
            hasRecording = hasCorrectEn,
            isRecording = isRecording && activeSlot == FeedbackSlot.CORRECT_EN,
            onRequestPermission = onRequestRecordPermission,
            isPermissionGranted = isRecordPermissionGranted,
            onPlay = { feedbackRecorder.play(FeedbackKind.CORRECT, AppLanguage.ENGLISH) {} },
            onDelete = { feedbackRecorder.deleteRecording(FeedbackKind.CORRECT, AppLanguage.ENGLISH) },
            onHoldStart = {
                activeSlot = FeedbackSlot.CORRECT_EN
                feedbackRecorder.startRecording(
                    scope = coroutineScope,
                    kind = FeedbackKind.CORRECT,
                    language = AppLanguage.ENGLISH
                )
            },
            onHoldEnd = {
                activeSlot = null
                coroutineScope.launch {
                    feedbackRecorder.stopAndSave(
                        scope = coroutineScope,
                        kind = FeedbackKind.CORRECT,
                        language = AppLanguage.ENGLISH
                    )
                }
            },
        )

        Spacer(Modifier.height(8.dp))

        FeedbackRow(
            slot = FeedbackSlot.WRONG_KO,
            title = if (appLanguage == AppLanguage.KOREAN) "오답 · 한국어" else "Wrong · Korean",
            titleStyle = rowTitleStyle,
            buttonLabelStyle = buttonLabelStyle,
            appLanguage = appLanguage,
            hasRecording = hasWrongKo,
            isRecording = isRecording && activeSlot == FeedbackSlot.WRONG_KO,
            onRequestPermission = onRequestRecordPermission,
            isPermissionGranted = isRecordPermissionGranted,
            onPlay = { feedbackRecorder.play(FeedbackKind.WRONG, AppLanguage.KOREAN) {} },
            onDelete = { feedbackRecorder.deleteRecording(FeedbackKind.WRONG, AppLanguage.KOREAN) },
            onHoldStart = {
                activeSlot = FeedbackSlot.WRONG_KO
                feedbackRecorder.startRecording(
                    scope = coroutineScope,
                    kind = FeedbackKind.WRONG,
                    language = AppLanguage.KOREAN
                )
            },
            onHoldEnd = {
                activeSlot = null
                coroutineScope.launch {
                    feedbackRecorder.stopAndSave(
                        scope = coroutineScope,
                        kind = FeedbackKind.WRONG,
                        language = AppLanguage.KOREAN
                    )
                }
            },
        )

        Spacer(Modifier.height(8.dp))

        FeedbackRow(
            slot = FeedbackSlot.WRONG_EN,
            title = if (appLanguage == AppLanguage.KOREAN) "오답 · 영어" else "Wrong · English",
            titleStyle = rowTitleStyle,
            buttonLabelStyle = buttonLabelStyle,
            appLanguage = appLanguage,
            hasRecording = hasWrongEn,
            isRecording = isRecording && activeSlot == FeedbackSlot.WRONG_EN,
            onRequestPermission = onRequestRecordPermission,
            isPermissionGranted = isRecordPermissionGranted,
            onPlay = { feedbackRecorder.play(FeedbackKind.WRONG, AppLanguage.ENGLISH) {} },
            onDelete = { feedbackRecorder.deleteRecording(FeedbackKind.WRONG, AppLanguage.ENGLISH) },
            onHoldStart = {
                activeSlot = FeedbackSlot.WRONG_EN
                feedbackRecorder.startRecording(
                    scope = coroutineScope,
                    kind = FeedbackKind.WRONG,
                    language = AppLanguage.ENGLISH
                )
            },
            onHoldEnd = {
                activeSlot = null
                coroutineScope.launch {
                    feedbackRecorder.stopAndSave(
                        scope = coroutineScope,
                        kind = FeedbackKind.WRONG,
                        language = AppLanguage.ENGLISH
                    )
                }
            },
        )
        }
    }
}

private fun toggleCategory(
    selected: Set<ItemCategory>,
    cat: ItemCategory,
): Set<ItemCategory> {
    val next = selected.toMutableSet()
    if (next.contains(cat)) {
        next.remove(cat)
    } else {
        next.add(cat)
    }
    if (next.isEmpty()) {
        next.add(ItemCategory.FRUIT)
    }
    return next
}

private enum class FeedbackSlot { CORRECT_KO, CORRECT_EN, WRONG_KO, WRONG_EN }

@Composable
private fun CategoryRow(
    label: String,
    selected: Boolean,
    labelStyle: TextStyle,
    buttonLabelStyle: TextStyle,
    accent: Color,
    onToggle: () -> Unit,
) {
    val bg = if (selected) accent.copy(alpha = 0.12f) else Color(0xFFF1F1F1)
    val fg = if (selected) accent else Color(0xFF2E2E2E)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (selected) "✓" else " ",
                color = fg,
                style = labelStyle.copy(fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.padding(start = 10.dp))
            Text(
                text = label,
                color = fg,
                style = labelStyle.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold),
            )
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) accent else Color(0xFFE0E0E0))
                    .clickable { onToggle() }
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (selected) "On" else "Off",
                    color = if (selected) Color.White else Color(0xFF424242),
                    style = buttonLabelStyle,
                )
            }
        }
    }
}

@Composable
private fun RowScope.ChoiceButton(
    text: String,
    selected: Boolean,
    labelStyle: TextStyle,
    onClick: () -> Unit,
) {
    val accent = Color(0xFFE08600)
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) accent else Color(0xFFEDEDED))
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) Color.White else Color(0xFF424242),
            style = labelStyle,
        )
    }
}

@Composable
private fun RowScope.ActionButton(
    text: String,
    container: Color,
    enabled: Boolean,
    labelStyle: TextStyle,
    onClick: () -> Unit,
) {
    val bg = if (enabled) container else Color(0xFFCFCFCF)
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = Color.White, style = labelStyle)
    }
}

@Composable
private fun FeedbackRow(
    slot: FeedbackSlot,
    title: String,
    titleStyle: TextStyle,
    buttonLabelStyle: TextStyle,
    appLanguage: AppLanguage,
    hasRecording: Boolean,
    isRecording: Boolean,
    isPermissionGranted: Boolean,
    onRequestPermission: () -> Unit,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = title, style = titleStyle)
        Spacer(Modifier.height(8.dp))

        if (hasRecording) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionButton(
                    text = if (appLanguage == AppLanguage.KOREAN) "재생" else "Play",
                    container = Color(0xFFE08600),
                    enabled = !isRecording,
                    labelStyle = buttonLabelStyle,
                    onClick = onPlay,
                )
                ActionButton(
                    text = if (appLanguage == AppLanguage.KOREAN) "삭제" else "Delete",
                    container = Color(0xFFE53935),
                    enabled = !isRecording,
                    labelStyle = buttonLabelStyle,
                    onClick = onDelete,
                )
            }
        } else {
            HoldToRecordButton(
                isRecording = isRecording,
                isPermissionGranted = isPermissionGranted,
                appLanguage = appLanguage,
                labelStyle = buttonLabelStyle,
                onRequestPermission = onRequestPermission,
                onHoldStart = {
                    if (isPermissionGranted) {
                        onHoldStart()
                    } else {
                        onRequestPermission()
                    }
                },
                onHoldEnd = onHoldEnd,
            )
        }
    }
}

@Composable
private fun HoldToRecordButton(
    isRecording: Boolean,
    isPermissionGranted: Boolean,
    appLanguage: AppLanguage,
    labelStyle: TextStyle,
    onRequestPermission: () -> Unit,
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit,
) {
    val accent = Color(0xFFE08600)
    val bg = if (isRecording) Color(0xFFE53935) else accent

    Card(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bg),
    ) {
        BoxWithHoldArea(
            isRecording = isRecording,
            isPermissionGranted = isPermissionGranted,
            appLanguage = appLanguage,
            labelStyle = labelStyle,
            onHoldStart = onHoldStart,
            onHoldEnd = onHoldEnd,
        )
    }
}

@Composable
private fun BoxWithHoldArea(
    isRecording: Boolean,
    isPermissionGranted: Boolean,
    appLanguage: AppLanguage,
    labelStyle: TextStyle,
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit,
) {
    // Use pointer press/release lifecycle.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .pointerInput(isPermissionGranted, isRecording) {
                detectTapGestures(
                    onPress = {
                        onHoldStart()
                        try {
                            tryAwaitRelease()
                        } finally {
                            onHoldEnd()
                        }
                    }
                )
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = when {
                isRecording -> if (appLanguage == AppLanguage.KOREAN) "녹음 중..." else "Recording..."
                appLanguage == AppLanguage.KOREAN -> "누르는 동안 녹음"
                else -> "Hold to record"
            },
            color = Color.White,
            style = labelStyle,
        )
    }
}

