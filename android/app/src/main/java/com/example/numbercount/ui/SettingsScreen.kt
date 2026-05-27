package com.example.numbercount.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
    var activeSlot by remember { mutableStateOf<FeedbackSlot?>(null) }

    val cream = Color(0xFFFFF6E6)
    val accent = Color(0xFFFF6A00)
    val muted = Color(0xFF777777)

    Column(
        modifier = Modifier
            .background(cream)
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
            Button(
                modifier = Modifier.weight(1f),
                onClick = { onAppLanguageChange(AppLanguage.KOREAN) },
                enabled = appLanguage != AppLanguage.KOREAN
            ) { Text("한국어") }
            Button(
                modifier = Modifier.weight(1f),
                onClick = { onAppLanguageChange(AppLanguage.ENGLISH) },
                enabled = appLanguage != AppLanguage.ENGLISH
            ) { Text("English") }
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
            hasRecording = feedbackRecorder.hasRecording(FeedbackKind.CORRECT, AppLanguage.KOREAN),
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
            hasRecording = feedbackRecorder.hasRecording(FeedbackKind.CORRECT, AppLanguage.ENGLISH),
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
            hasRecording = feedbackRecorder.hasRecording(FeedbackKind.WRONG, AppLanguage.KOREAN),
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
            hasRecording = feedbackRecorder.hasRecording(FeedbackKind.WRONG, AppLanguage.ENGLISH),
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
    val accent = Color(0xFFFF6A00)
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
            Button(onClick = onToggle, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = fg.copy(alpha = 0.0f))) {
                Text(text = if (selected) "On" else "Off", color = fg)
            }
        }
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
                Button(
                    onClick = onPlay,
                    enabled = !isRecording,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                ) { Text("Play") }
                Button(
                    onClick = onDelete,
                    enabled = !isRecording,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                ) { Text("Delete") }
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
    val accent = Color(0xFFFF6A00)
    val bg = if (isRecording) Color(0xFFE53935) else accent.copy(alpha = 0.16f)

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
            fontWeight = MaterialTheme.typography.labelLarge.fontWeight
        )
    }
}

