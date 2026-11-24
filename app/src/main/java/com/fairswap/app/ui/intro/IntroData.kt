package com.fairswap.app.ui.intro

import androidx.compose.ui.graphics.Color
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

data class IntroPalette(
    val id: String,
    val bg: Color,
    val bg2: Color,
    val accent: Color,
    val textColor: Color,
    val art: Color,
    val spark: Color,
)

data class GreetingItem(
    val text: String,
    val langTag: String,
)

object IntroData {
    val PALETTES = listOf(
        IntroPalette("midnight", Color(0xFF0B1526), Color(0xFF1A2C45), Color(0xFFF8BC80), Color(0xFFFAF1E7), Color(0xFFDFA977), Color(0xFFF4AE73)),
        IntroPalette("lavender", Color(0xFF29233E), Color(0xFF514768), Color(0xFFEAC7BD), Color(0xFFFFF4EF), Color(0xFFD7B8CC), Color(0xFFF8C6B1)),
        IntroPalette("sage", Color(0xFF183C37), Color(0xFF32615A), Color(0xFFD9CFAC), Color(0xFFFAF7E8), Color(0xFFB9D3BC), Color(0xFFF0D2A5)),
        IntroPalette("peach", Color(0xFF513E45), Color(0xFF825C62), Color(0xFFF6C7B4), Color(0xFFFFF0E7), Color(0xFFF1B8A6), Color(0xFFF8DBC5)),
        IntroPalette("blue", Color(0xFF21364F), Color(0xFF476684), Color(0xFFC4E5EB), Color(0xFFF1FCFF), Color(0xFFB4D4E7), Color(0xFFE3D0B9)),
        IntroPalette("orchid", Color(0xFF343B54), Color(0xFF73739B), Color(0xFFE6B8CE), Color(0xFFFFF1F5), Color(0xFFC3C8EA), Color(0xFFE6B8CE)),
        IntroPalette("sandstone", Color(0xFF514638), Color(0xFF856B58), Color(0xFFE8CE9C), Color(0xFFFFF3DF), Color(0xFFD9B8A1), Color(0xFFE8CE9C)),
        IntroPalette("monsoon", Color(0xFF153D49), Color(0xFF386C78), Color(0xFFBCE4DB), Color(0xFFF0F9F7), Color(0xFFAAD6E5), Color(0xFFBCE4DB)),
    )

    val GREETINGS = listOf(
        GreetingItem("नमस्ते", "hi"),
        GreetingItem("வணக்கம்", "ta"),
        GreetingItem("নমস্কার", "bn"),
        GreetingItem("నమస్కారం", "te"),
        GreetingItem("ਸਤ ਸ੍ਰੀ ਅਕਾਲ", "pa"),
        GreetingItem("ನಮಸ್ಕಾರ", "kn"),
        GreetingItem("નમસ્તે", "gu"),
        GreetingItem("നമസ്കാരം", "ml"),
        GreetingItem("নমস্কাৰ", "as"),
        GreetingItem("ନମସ୍କାର", "or"),
        GreetingItem("नमस्कार", "mr"),
        GreetingItem("آداب", "ur"),
        GreetingItem("नमस्ते", "ne"),
        GreetingItem("प्रणाम", "sa"),
        GreetingItem("ᱡᱚᱦᱟᱨ", "sat"),
        GreetingItem("ꯈꯨꯔꯨꯝꯖꯔꯤ", "mni"),
        GreetingItem("जौहोर", "kok"),
        GreetingItem("नमस्ते", "mai"),
        GreetingItem("नमस्कार", "doi"),
        GreetingItem("سلام", "ks"),
        GreetingItem("آداب", "sd"),
        GreetingItem("नमस्ते", "brx"),
    )

    val TAGLINES = listOf(
        "India’s marketplace. Buy, sell, exchange and share.",
        "India’s marketplace. Fairer ways to buy and sell.",
        "India’s marketplace. Local deals, fair exchanges.",
        "India’s marketplace. Reuse more, waste less.",
    )

    private val taglineIndex = AtomicInteger(0)

    fun selectPalette(random: Random = Random): IntroPalette {
        return PALETTES[random.nextInt(PALETTES.size)]
    }

    /**
     * Always starts with 'नमस्ते', then picks 14 other distinct normalized greeting strings
     * for exactly 15 unique visible greeting slides.
     */
    fun selectGreetings(random: Random = Random): List<GreetingItem> {
        val first = GREETINGS.first() // 'नमस्ते'
        val normalizedSeen = mutableSetOf(first.text.trim())
        val selected = mutableListOf(first)

        val candidates = GREETINGS.drop(1).shuffled(random)
        for (item in candidates) {
            val normalized = item.text.trim()
            if (normalized !in normalizedSeen) {
                normalizedSeen.add(normalized)
                selected.add(item)
                if (selected.size == 15) break
            }
        }
        return selected
    }

    /**
     * Round-robin / shuffle-bag tagline selection avoiding consecutive repeats across process.
     */
    fun selectTagline(): String {
        val idx = Math.floorMod(taglineIndex.getAndIncrement(), TAGLINES.size)
        return TAGLINES[idx]
    }
}
