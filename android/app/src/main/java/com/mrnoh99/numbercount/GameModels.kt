package com.mrnoh99.numbercount

import androidx.compose.ui.graphics.Color
import kotlin.math.min
import kotlin.random.Random

enum class AppLanguage(val raw: String) {
    KOREAN("ko"),
    ENGLISH("en");

    companion object {
        const val storageKey: String = "appLanguage"
        fun fromRaw(raw: String): AppLanguage = values().firstOrNull { it.raw == raw } ?: KOREAN
    }
}

enum class QuizMode {
    NUMBER_TO_OBJECTS,
    OBJECTS_TO_NUMBER
}

enum class ItemCategory {
    FRUIT,
    CAR,
    VEGETABLE;

    companion object {
        val all = entries.toList()

        const val appStorageKey: String = "selectedThemeCategories"
        const val defaultStorageValue: String = "fruit,car,vegetable"

        fun fromStorage(s: String): Set<ItemCategory> {
            val parts = s.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val decoded = parts.mapNotNull { token ->
                when (token) {
                    "fruit" -> FRUIT
                    "car" -> CAR
                    "vegetable" -> VEGETABLE
                    else -> null
                }
            }.toSet()
            return if (decoded.isEmpty()) all.toSet() else decoded
        }

        fun toStorage(set: Set<ItemCategory>): String {
            val ordered = all.filter { set.contains(it) }
            return if (ordered.isEmpty()) "fruit,car,vegetable" else ordered.joinToString(",") { cat ->
                when (cat) {
                    FRUIT -> "fruit"
                    CAR -> "car"
                    VEGETABLE -> "vegetable"
                }
            }
        }
    }
}

data class Theme(
    val category: ItemCategory,
    val item: String,
    val itemWordSingular: String, // en singular
    val itemWordPlural: String, // en plural
    val itemWordSingularKO: String, // ko singular
    val itemWordPluralKO: String, // ko plural
    val colors: List<Color>,
)

object ThemeCatalog {
    // NOTE: iOS version has a larger set; this scaffold includes a representative set per category.
    private val fruitThemes = listOf(
        Theme(
            category = ItemCategory.FRUIT,
            item = "🍎",
            itemWordSingular = "apple",
            itemWordPlural = "apples",
            itemWordSingularKO = "사과",
            itemWordPluralKO = "사과들",
            colors = listOf(Color.Red, Color(0xFFE53935), Color(0xFFEF9A9A), Color(0xFFD32F2F))
        ),
        Theme(
            category = ItemCategory.FRUIT,
            item = "🍌",
            itemWordSingular = "banana",
            itemWordPlural = "bananas",
            itemWordSingularKO = "바나나",
            itemWordPluralKO = "바나나들",
            colors = listOf(Color.Yellow, Color(0xFFFFA726), Color(0xFFFFB74D), Color(0xFFFF7043))
        ),
        Theme(
            category = ItemCategory.FRUIT,
            item = "🍇",
            itemWordSingular = "grape",
            itemWordPlural = "grapes",
            itemWordSingularKO = "포도",
            itemWordPluralKO = "포도들",
            colors = listOf(Color(0xFF7E57C2), Color(0xFF5E35B1), Color(0xFF9575CD), Color(0xFF512DA8))
        ),
    )

    private val carThemes = listOf(
        Theme(
            category = ItemCategory.CAR,
            item = "🚗",
            itemWordSingular = "car",
            itemWordPlural = "cars",
            itemWordSingularKO = "자동차",
            itemWordPluralKO = "자동차들",
            colors = listOf(Color(0xFFFF9800), Color(0xFFE91E63), Color(0xFFFFC107), Color(0xFFF44336))
        ),
        Theme(
            category = ItemCategory.CAR,
            item = "🚌",
            itemWordSingular = "bus",
            itemWordPlural = "buses",
            itemWordSingularKO = "버스",
            itemWordPluralKO = "버스들",
            colors = listOf(Color(0xFFFFC107), Color(0xFFFF9800), Color(0xFFFFB300), Color(0xFF795548))
        ),
    )

    private val vegetableThemes = listOf(
        Theme(
            category = ItemCategory.VEGETABLE,
            item = "🥕",
            itemWordSingular = "carrot",
            itemWordPlural = "carrots",
            itemWordSingularKO = "당근",
            itemWordPluralKO = "당근들",
            colors = listOf(Color(0xFFFF9800), Color(0xFFFFB74D), Color(0xFFFFD54F), Color(0xFFE65100))
        ),
        Theme(
            category = ItemCategory.VEGETABLE,
            item = "🥦",
            itemWordSingular = "broccoli",
            itemWordPlural = "broccolis",
            itemWordSingularKO = "브로콜리",
            itemWordPluralKO = "브로콜리들",
            colors = listOf(Color(0xFF43A047), Color(0xFF66BB6A), Color(0xFF2E7D32), Color(0xFFA5D6A7))
        ),
        Theme(
            category = ItemCategory.VEGETABLE,
            item = "🍅",
            itemWordSingular = "tomato",
            itemWordPlural = "tomatoes",
            itemWordSingularKO = "토마토",
            itemWordPluralKO = "토마토들",
            colors = listOf(Color.Red, Color(0xFFE53935), Color(0xFFEF5350), Color(0xFFFF7043))
        ),
    )

    val all: List<Theme> = fruitThemes + carThemes + vegetableThemes

    fun pool(forCategories: Set<ItemCategory>): List<Theme> {
        val filtered = all.filter { forCategories.contains(it.category) }
        return if (filtered.isEmpty()) all else filtered
    }
}

// Matches the iOS numColors palette so the target number/border colors are identical.
val numColors: List<Color> = listOf(
    Color(0xFFFF9500), // orange (.orange)
    Color(0xFF30B0C7), // teal (.teal)
    Color(0xFFAF52DE), // purple (.purple)
    Color(0xFFFF3B30), // red (.red)
    Color(0xFF52CF66), // green
    Color(0xFFFFB347), // light orange
    Color(0xFF4A8FD9), // blue
    Color(0xFFFF8FB0), // pink
    Color(0xFF7DC7A3), // teal-green
    Color(0xFF5856D6), // indigo (.indigo)
)

data class GameState(
    val targetNumber: Int,
    val options: List<Int>, // size 4
    val score: Int,
    val theme: Theme,
    val mode: QuizMode,
) {
    companion object {
        fun newRound(
            score: Int = 0,
            prev: Int? = null,
            maxNumber: Int = 10,
            mode: QuizMode = QuizMode.NUMBER_TO_OBJECTS,
            themePool: List<Theme> = ThemeCatalog.all,
        ): GameState {
            val safeMax = max(1, maxNumber)
            var target = Random.nextInt(1, safeMax + 1)

            if (prev != null) {
                var attempts = 0
                while (target == prev && attempts < 20) {
                    target = Random.nextInt(1, safeMax + 1)
                    attempts++
                }
            }

            val pool = (1..safeMax).filter { it != target }.toMutableList()
            pool.shuffle(Random)

            val count = min(3, pool.size)
            val opts = mutableListOf(target).apply { addAll(pool.take(count)) }
            while (opts.size < 4) {
                val extra = Random.nextInt(1, safeMax + 1)
                if (!opts.contains(extra)) opts.add(extra)
            }
            opts.shuffle(Random)

            val theme = (themePool.ifEmpty { ThemeCatalog.all }).random(Random)
            return GameState(
                targetNumber = target,
                options = opts.take(4),
                score = score,
                theme = theme,
                mode = mode,
            )
        }
    }
}

private fun max(a: Int, b: Int): Int = if (a > b) a else b

