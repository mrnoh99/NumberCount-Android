package com.example.numbercount.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.numbercount.AppLanguage
import com.example.numbercount.ItemCategory
import com.example.numbercount.audio.AudioController
import com.example.numbercount.audio.FeedbackKind
import com.example.numbercount.audio.FeedbackRecorder
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

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
    val accent = Color(0xFFFF9500)
    val muted = Color(0xFF777777)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(cream)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(20.dp)
    ) {
        Text(
            text = if (appLanguage == AppLanguage.KOREAN) "숫자세기" else "Number Count",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (appLanguage == AppLanguage.KOREAN)
                "퀴즈 화면과 같은 느낌으로 옵션을 바꿀 수 있어요."
            else
                "Change options in the same style as the quiz screen.",
            color = muted,
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(18.dp))

        Text(text = if (appLanguage == AppLanguage.KOREAN) "언어" else "Language", color = accent)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ChoiceButton(
                text = "한국어",
                selected = appLanguage == AppLanguage.KOREAN,
            ) { onAppLanguageChange(AppLanguage.KOREAN) }
            ChoiceButton(
                text = "English",
                selected = appLanguage == AppLanguage.ENGLISH,
            ) { onAppLanguageChange(AppLanguage.ENGLISH) }
        }

        Spacer(Modifier.height(18.dp))

        Text(text = if (appLanguage == AppLanguage.KOREAN) "아이템 종류" else "Item categories", color = accent)
        Spacer(Modifier.height(8.dp))
        CategoryRow(
            label = if (appLanguage == AppLanguage.KOREAN) "과일" else "Fruits",
            selected = selectedCategories.contains(ItemCategory.FRUIT),
            onToggle = {
                onSelectedCategoriesChange(toggleCategory(selectedCategories, ItemCategory.FRUIT))
            },
        )
        Spacer(Modifier.height(8.dp))
        CategoryRow(
            label = if (appLanguage == AppLanguage.KOREAN) "자동차" else "Cars",
            selected = selectedCategories.contains(ItemCategory.CAR),
            onToggle = {
                onSelectedCategoriesChange(toggleCategory(selectedCategories, ItemCategory.CAR))
            },
        )
        Spacer(Modifier.height(8.dp))
        CategoryRow(
            label = if (appLanguage == AppLanguage.KOREAN) "채소" else "Vegetables",
            selected = selectedCategories.contains(ItemCategory.VEGETABLE),
            onToggle = {
                onSelectedCategoriesChange(toggleCategory(selectedCategories, ItemCategory.VEGETABLE))
            },
        )

        Spacer(Modifier.height(18.dp))

        Text(text = if (appLanguage == AppLanguage.KOREAN) "배경 음악" else "Background music", color = accent)
        Spacer(Modifier.height(10.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (appLanguage == AppLanguage.KOREAN) "배경 음악 재생" else "Play background music",
                        modifier = Modifier.weight(1f)
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
                    color = muted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        Text(
            text = if (appLanguage == AppLanguage.KOREAN) "정답·오답 음성 녹음" else "Record answer sounds",
            color = accent
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (appLanguage == AppLanguage.KOREAN)
                "버튼을 누르고 있는 동안만 녹음됩니다. 손을 떼면 앞뒤 무음이 잘리고 저장돼요."
            else
                "Hold the button to record. Release to trim silence and save.",
            color = muted,
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(Modifier.height(12.dp))

        FeedbackRow(
            slot = FeedbackSlot.CORRECT_KO,
            title = if (appLanguage == AppLanguage.KOREAN) "정답 · 한국어" else "Correct · Korean",
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
            title = if (appLanguage == AppLanguage.KOREAN) "정답 · English" else "Correct · English",
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
            title = if (appLanguage == AppLanguage.KOREAN) "오답 · English" else "Wrong · English",
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
    onToggle: () -> Unit,
) {
    val accent = Color(0xFFFF9500)
    val bg = if (selected) accent.copy(alpha = 0.12f) else Color(0xFFF1F1F1)
    val fg = if (selected) accent else Color(0xFF444444)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = if (selected) "✓" else " ", color = fg)
            Spacer(Modifier.padding(start = 10.dp))
            Text(text = label, color = fg, fontWeight = MaterialTheme.typography.labelLarge.fontWeight)
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) accent else Color(0xFFE0E0E0))
                    .clickable { onToggle() }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (selected) "On" else "Off",
                    color = if (selected) Color.White else Color(0xFF555555),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun RowScope.ChoiceButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accent = Color(0xFFFF9500)
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) accent else Color(0xFFEDEDED))
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) Color.White else Color(0xFF555555),
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun RowScope.ActionButton(
    text: String,
    container: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (enabled) container else Color(0xFFCFCFCF)
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FeedbackRow(
    slot: FeedbackSlot,
    title: String,
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
        Text(text = title)
        Spacer(Modifier.height(6.dp))

        if (hasRecording) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionButton(
                    text = "Play",
                    container = Color(0xFFFF9500),
                    enabled = !isRecording,
                    onClick = onPlay,
                )
                ActionButton(
                    text = "Delete",
                    container = Color(0xFFE53935),
                    enabled = !isRecording,
                    onClick = onDelete,
                )
            }
        } else {
            HoldToRecordButton(
                isRecording = isRecording,
                isPermissionGranted = isPermissionGranted,
                onRequestPermission = onRequestPermission,
                onHoldStart = {
                    if (isPermissionGranted) {
                        onHoldStart()
                    } else {
                        onRequestPermission()
                    }
                },
                onHoldEnd = {
                    if (isPermissionGranted) onHoldEnd()
                },
            )
        }
    }
}

@Composable
private fun HoldToRecordButton(
    isRecording: Boolean,
    isPermissionGranted: Boolean,
    onRequestPermission: () -> Unit,
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit,
) {
    val accent = Color(0xFFFF9500)
    val bg = if (isRecording) Color(0xFFE53935) else accent

    Card(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bg),
    ) {
        BoxWithHoldArea(
            isRecording = isRecording,
            onHoldStart = onHoldStart,
            onHoldEnd = onHoldEnd,
        )
    }
}

@Composable
private fun BoxWithHoldArea(
    isRecording: Boolean,
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit,
) {
    // Use pointer press/release lifecycle.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .pointerInput(Unit) {
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
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (isRecording) "Recording..." else "Hold to record",
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}

