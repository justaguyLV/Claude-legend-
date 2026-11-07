package com.legend.keyboard

enum class T { CHAR, SHIFT, DEL, ENTER, SPACE, SYM, ABC, LANG, THEME, EMOJI, CLIPS, SUG, CLIP, ACT, SPACER }
data class Key(val label: String, val type: T = T.CHAR, val weight: Float = 1f, val out: String = label, val idx: Int = -1)
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

/** Long-press alternatives */
object Alts {
    private val m = HashMap<String, List<String>>()
    init {
        val extra = mapOf(
            "a" to "à á â ä ã å æ", "e" to "è é ê ë ē", "i" to "ì í î ï", "o" to "ò ó ô ö õ ø œ",
            "u" to "ù ú û ü", "c" to "ç ć č", "n" to "ñ ń", "s" to "ß š ś", "y" to "ý ÿ", "z" to "ž ź ż", "l" to "ł", "d" to "ď"
        )
        val top = "qwertyuiop"
        for (c in 'a'..'z') {
            val l = ArrayList<String>()
            val i = top.indexOf(c)
            if (i >= 0) l.add("1234567890"[i].toString())
            extra[c.toString()]?.let { l.addAll(it.split(" ")) }
            if (l.isNotEmpty()) m[c.toString()] = l
        }
        val arDigits = "١٢٣٤٥٦٧٨٩٠"
        "ضصثقفغعهخح".forEachIndexed { i, c -> m[c.toString()] = listOf(arDigits[i].toString()) }
        m["ف"] = listOf("٥", "ڤ"); m["ه"] = listOf("٨", "ة"); m["ج"] = listOf("چ"); m["ب"] = listOf("پ")
        m["ك"] = listOf("گ", "ک"); m["ا"] = listOf("أ", "إ", "آ", "ء", "ٱ"); m["و"] = listOf("ؤ"); m["ي"] = listOf("ئ", "ى")
        m["ء"] = listOf("ـَ", "ـُ", "ـِ", "ـً", "ـٌ", "ـٍ", "ـّ", "ـْ")
        m["."] = listOf("!", "?", ":", ";", "'", "\"", "-", "@", "…")
        m["،"] = listOf("؟", "!", ".", ":", "؛")
    }
    fun get(label: String): List<String>? = m[label.lowercase()]
}

object Layouts {
    private fun chars(s: String) = s.map { Key(it.toString()) }
    private fun tool() = Row(
        listOf(
            Key("🎨", T.THEME, 1f), Key("📋", T.CLIPS, 1f),
            Key("", T.SUG, 2.4f, idx = 0), Key("", T.SUG, 2.4f, idx = 1), Key("", T.SUG, 2.4f, idx = 2),
            Key("😀", T.EMOJI, 1f)
        ), 0.9f
    )
    private fun bottom(first: Key, lang: String, comma: String, period: String) = Row(
        listOf(first, Key("🌐 $lang", T.LANG, 1.3f), Key(comma), Key("Legend", T.SPACE, 4f, " "), Key(period), Key("⏎", T.ENTER, 1.5f))
    )
    private fun del() = Key("⌫", T.DEL, 1.5f)

    fun en(): List<Row> = listOf(
        tool(), Row(chars("qwertyuiop")), Row(chars("asdfghjkl")),
        Row(listOf(Key("⇧", T.SHIFT, 1.5f)) + chars("zxcvbnm") + del()),
        bottom(Key("123", T.SYM, 1.4f), "EN", ",", ".")
    )

    fun ar(): List<Row> = listOf(
        tool(), Row(chars("ضصثقفغعهخحجد")), Row(chars("شسيبلاتنمكط")),
        Row(chars("ذئءؤرىةوزظ") + del()),
        bottom(Key("123", T.SYM, 1.4f), "AR", "،", ".")
    )

    fun sym(): List<Row> = listOf(
        tool(), Row(chars("1234567890")), Row(chars("@#\$%&-+()/")),
        Row(chars("=*\"':;!?؟") + del()),
        bottom(Key("ABC", T.ABC, 1.4f), "SYM", ",", ".")
    )

    private fun emojiRow(vararg e: String) = Row(e.map { Key(it) })

    fun emoji(): List<Row> = listOf(
        tool(),
        emojiRow("😂", "❤️", "😍", "😭", "🔥", "👍", "🙏", "😊"),
        emojiRow("😎", "🤔", "😅", "🥺", "😘", "🎉", "💯", "✨"),
        Row(listOf(Key("ABC", T.ABC, 1.5f)) + listOf("😡", "🤣", "👏", "💪", "🙌", "😢").map { Key(it) } + del()),
        Row(listOf(Key("ABC", T.ABC, 1.5f), Key("Legend", T.SPACE, 5f, " "), Key("⏎", T.ENTER, 1.5f)))
    )

    fun clips(items: List<Clip>, page: Int): List<Row> {
        val per = 4
        val pages = maxOf(1, (items.size + per - 1) / per)
        val p = page.coerceIn(0, pages - 1)
        fun slot(i: Int): Key {
            val g = p * per + i
            val c = items.getOrNull(g) ?: return Key("", T.SPACER)
            val label = (if (c.pinned) "📌 " else "") + c.text.replace(Regex("\\s+"), " ").trim().take(60)
            return Key(label, T.CLIP, 1f, c.text, g)
        }
        val body = if (items.isEmpty()) listOf(
            Row(listOf(Key("الحافظة فاضية، انسخ أي نص وبيظهر هنا", T.SPACER, 1f))),
            Row(listOf(Key("اضغط مطولاً على أي عنصر لتثبيته 📌", T.SPACER, 1f)))
        ) else listOf(Row(listOf(slot(0), slot(1))), Row(listOf(slot(2), slot(3))))
        return listOf(
            tool(),
            Row(listOf(Key("تحديد الكل", T.ACT, 1f, "selectAll"), Key("نسخ", T.ACT, 1f, "copy"), Key("قص", T.ACT, 1f, "cut"), Key("لصق", T.ACT, 1f, "paste"))),
            body[0], body[1],
            Row(listOf(Key("ABC", T.ABC, 1.4f), Key("◀", T.ACT, 1f, "prev"), Key("${p + 1}/$pages", T.SPACER, 1f), Key("▶", T.ACT, 1f, "next"), Key("🗑", T.ACT, 1.2f, "clear"), del()))
        )
    }
}
