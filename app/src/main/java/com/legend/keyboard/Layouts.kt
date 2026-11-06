package com.legend.keyboard

enum class T { CHAR, SHIFT, DEL, ENTER, SPACE, SYM, ABC, LANG, THEME, EMOJI, SPACER }
data class Key(val label: String, val type: T = T.CHAR, val weight: Float = 1f, val out: String = label)
class Row(val keys: List<Key>, val h: Float = 1f)

class KbTheme(val name: String, val top: Int, val bottom: Int, val key: Int, val special: Int, val text: Int, val accent: Int)

object Themes {
    val all = listOf(
        KbTheme("Neon", 0xFF120B2E.toInt(), 0xFF1B1147.toInt(), 0xFF2A1F5C.toInt(), 0xFF3B2C7E.toInt(), 0xFFFFFFFF.toInt(), 0xFF00E5FF.toInt()),
        KbTheme("Midnight", 0xFF0B0F19.toInt(), 0xFF121826.toInt(), 0xFF1F2937.toInt(), 0xFF111827.toInt(), 0xFFE5E7EB.toInt(), 0xFF3B82F6.toInt()),
        KbTheme("Light", 0xFFE5E7EB.toInt(), 0xFFD1D5DB.toInt(), 0xFFFFFFFF.toInt(), 0xFFC7CBD1.toInt(), 0xFF111827.toInt(), 0xFF2563EB.toInt()),
        KbTheme("Gold", 0xFF000000.toInt(), 0xFF000000.toInt(), 0xFF141414.toInt(), 0xFF1E1E1E.toInt(), 0xFFF5E6B3.toInt(), 0xFFFFC107.toInt())
    )
}

object Layouts {
    private fun chars(s: String) = s.map { Key(it.toString()) }
    private fun tool() = Row(listOf(Key("🎨", T.THEME, 1.3f), Key("", T.SPACER, 5f), Key("😀", T.EMOJI, 1.3f)), 0.7f)
    private fun bottom(first: Key, lang: String, comma: String, period: String) = Row(
        listOf(first, Key("🌐 $lang", T.LANG, 1.3f), Key(comma), Key("Legend", T.SPACE, 4f, " "), Key(period), Key("⏎", T.ENTER, 1.5f))
    )
    private val del get() = Key("⌫", T.DEL, 1.5f)

    fun en(): List<Row> = listOf(
        tool(), Row(chars("qwertyuiop")), Row(chars("asdfghjkl")),
        Row(listOf(Key("⇧", T.SHIFT, 1.5f)) + chars("zxcvbnm") + del),
        bottom(Key("123", T.SYM, 1.4f), "EN", ",", ".")
    )

    fun ar(): List<Row> = listOf(
        tool(), Row(chars("ضصثقفغعهخحجد")), Row(chars("شسيبلاتنمكط")),
        Row(chars("ذئءؤرىةوزظ") + del),
        bottom(Key("123", T.SYM, 1.4f), "AR", "،", ".")
    )

    fun sym(): List<Row> = listOf(
        tool(), Row(chars("1234567890")), Row(chars("@#\$%&-+()/")),
        Row(chars("=*\"':;!?؟") + del),
        bottom(Key("ABC", T.ABC, 1.4f), "SYM", ",", ".")
    )

    private fun emojiRow(vararg e: String) = Row(e.map { Key(it) })

    fun emoji(): List<Row> = listOf(
        tool(),
        emojiRow("😂", "❤️", "😍", "😭", "🔥", "👍", "🙏", "😊"),
        emojiRow("😎", "🤔", "😅", "🥺", "😘", "🎉", "💯", "✨"),
        Row(listOf(Key("ABC", T.ABC, 1.5f)) + listOf("😡", "🤣", "👏", "💪", "🙌", "😢").map { Key(it) } + del),
        Row(listOf(Key("ABC", T.ABC, 1.5f), Key("Legend", T.SPACE, 5f, " "), Key("⏎", T.ENTER, 1.5f)))
    )
}
