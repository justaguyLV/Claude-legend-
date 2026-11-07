package com.legend.keyboard

enum class T { CHAR, EMO, SHIFT, DEL, ENTER, SPACE, SYM, ABC, LANG, THEME, EMOJI, TOOL, ACT, SUGG, CLIP, SPACER }

data class Key(
    val label: String,
    val type: T = T.CHAR,
    val weight: Float = 1f,
    val out: String = label,
    val alts: List<String> = emptyList(),
    val arg: Int = 0,
    val on: Boolean = false
)

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

object Emoji {
    val tabs = listOf("🕘", "😀", "👍", "💖", "🐶", "🍔", "⚽")
    private fun s(x: String) = x.trim().split(Regex("\\s+"))

    private val smileys = s("😀 😃 😄 😁 😆 😅 😂 🤣 😊 😇 🙂 🙃 😉 😌 😍 🥰 😘 😗 😙 😚 😋 😛 😜 🤪 😝 🤗 🤭 🤫 🤔 🤐 😐 😑 😶 😏 😒 🙄 😬 😔 😪 😴 😷 🤒 🤕 🤢 🤮 🥵 🥶 😵 🤯 🥳 😎 🤓 🧐 😕 😟 🙁 😮 😯 😲 😳 🥺 😦 😧 😨 😰 😥 😢 😭 😱 😖 😣 😞 😓 😩 😫 🥱 😤 😡 😠 🤬 😈 👿 💀 💩 🤡 👻 👽 🤖")
    private val gestures = s("👍 👎 👌 ✌️ 🤞 🤟 🤘 🤙 👈 👉 👆 👇 ☝️ ✋ 🤚 🖐️ 🖖 👋 🤝 🙏 ✍️ 💪 👏 🙌 👐 🤲 🤜 🤛 ✊ 👊 🤏 🙋 🙆 🙅 🤦 🤷 💁 🙇 🤳 💅 👀 👅 👄 🧠 👶 👦 👧 👨 👩 👴 👵")
    private val hearts = s("❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 ❣️ 💕 💞 💓 💗 💖 💘 💝 💟 ✨ ⭐ 🌟 💫 🔥 💥 💯 ✅ ❌ ❓ ❗ ⚠️ 💢 💤 💦 💨 🎉 🎊 🏆 🥇 🎁 🎈 🔔 🕌 🕋 🌙 ☪️ 📿 ✔️ ➕ ➖ ➡️ ⬅️ ⬆️ ⬇️")
    private val animals = s("🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🙈 🙉 🙊 🐔 🐧 🐦 🐤 🦆 🦅 🦉 🦇 🐺 🐗 🐴 🦄 🐝 🐛 🦋 🐌 🐞 🐜 🐢 🐍 🦎 🐙 🦑 🦀 🐠 🐟 🐬 🐳 🦈 🐊 🐘 🦏 🐪 🐫 🦒 🐎 🐕 🐈 🐓 🦚 🌵 🌴 🌳 🌸 🌹 🌻 🌼 🍀 🍁")
    private val food = s("🍏 🍎 🍐 🍊 🍋 🍌 🍉 🍇 🍓 🍈 🍒 🍑 🥭 🍍 🥥 🥝 🍅 🥑 🍆 🥕 🌽 🥒 🥔 🍞 🥐 🧀 🍳 🥞 🥓 🥩 🍗 🍖 🌭 🍔 🍟 🍕 🥪 🌮 🌯 🥗 🍝 🍜 🍲 🍛 🍣 🍱 🍤 🍙 🍚 🍦 🍧 🍨 🍩 🍪 🎂 🍰 🧁 🍫 🍬 🍭 ☕ 🍵 🥤 🍺 🍷 🥂 🧃 🍼")
    private val activity = s("⚽ 🏀 🏈 ⚾ 🎾 🏐 🏉 🎱 🏓 🏸 🥊 🎯 🎮 🎲 🎵 🎶 🎤 🎧 🎸 🎹 🥁 🎬 🚗 🚕 🚌 🏎️ 🚓 🚑 🚒 🚚 🏍️ 🚲 ✈️ 🚀 🚁 🚢 🚂 🏠 🏢 🏫 🌍 ☀️ ⛅ 🌧️ ❄️ 🌈 ⌚ 📱 💻 📷 💡 🔑 🔒 💰 💳 📌 📎 ✏️ 📚 🎓 💼 ⏰ 🔋 🔌 🛒")

    fun list(cat: Int, recents: List<String>): List<String> = when (cat) {
        0 -> recents
        1 -> smileys
        2 -> gestures
        3 -> hearts
        4 -> animals
        5 -> food
        else -> activity
    }

    const val PER_PAGE = 24
    fun pages(cat: Int, recents: List<String>) = maxOf(1, (list(cat, recents).size + PER_PAGE - 1) / PER_PAGE)
}

object Layouts {
    private const val TOOLBAR_H = 0.85f

    private fun split(s: String) = s.map { it.toString() }
    private fun chars(s: String) = s.map { Key(it.toString()) }
    private val del get() = Key("⌫", T.DEL, 1.5f)

    private val enAcc = mapOf(
        'a' to "àáâäæãåā", 'e' to "èéêëēėę", 'i' to "ìíîïīį", 'o' to "òóôöõøōœ",
        'u' to "ùúûüū", 'c' to "çćč", 'n' to "ñń", 's' to "śšß", 'y' to "ýÿ",
        'z' to "žźż", 'l' to "ł", 'd' to "ð"
    )
    private val arAcc = mapOf(
        'ا' to "أإآٱء", 'ي' to "ىئ", 'و' to "ؤ", 'ه' to "ة", 'ب' to "پ", 'ج' to "چ",
        'ف' to "ڤ", 'ك' to "گ", 'ز' to "ژ", 'ء' to "ًٌٍَُِّْـ"
    )
    private const val DIGITS = "1234567890"
    private const val INDIC = "١٢٣٤٥٦٧٨٩٠"

    private fun enKeys(s: String, digitsOn: Boolean) = s.mapIndexed { i, c ->
        val d = if (digitsOn) DIGITS[i].toString() else ""
        Key(c.toString(), alts = split(d + (enAcc[c] ?: "")))
    }

    private fun arKeys(s: String, digitsOn: Boolean) = s.mapIndexed { i, c ->
        val d = if (digitsOn && i < 10) DIGITS[i].toString() + INDIC[i] else ""
        Key(c.toString(), alts = split(d + (arAcc[c] ?: "")))
    }

    private fun numRow(arabic: Boolean) = Row(DIGITS.mapIndexed { i, c ->
        Key(c.toString(), alts = if (arabic) listOf(INDIC[i].toString()) else emptyList())
    })

    private fun bottom(first: Key, lang: String, comma: String, period: String, arabic: Boolean): Row {
        val commaAlts = if (arabic) split(",;:؛") else split(";:'\"-/")
        val periodAlts = if (arabic) split("؟!،؛:'\"-…") else split("?!,:;'\"-…@")
        return Row(
            listOf(
                first, Key("🌐 $lang", T.LANG, 1.3f), Key(comma, alts = commaAlts),
                Key("Legend", T.SPACE, 4f, " "), Key(period, alts = periodAlts), Key("⏎", T.ENTER, 1.5f)
            )
        )
    }

    fun toolbar(mode: Int, title: String = ""): Row {
        val k = ArrayList<Key>()
        k += Key("🎨", T.THEME, 1.2f)
        k += Key("📋", T.TOOL, 1.2f, "clip", on = mode == 3)
        when (mode) {
            0, 1 -> for (i in 0..2) k += Key("", T.SUGG, 2.4f, arg = i)
            2 -> {
                k += Key("◀", T.TOOL, 1.6f, "emopg:-1")
                k += Key(title, T.SPACER, 2.2f)
                k += Key("▶", T.TOOL, 1.6f, "emopg:1")
            }
            else -> k += Key(title, T.SPACER, 7.2f)
        }
        k += Key("✏️", T.TOOL, 1.2f, "edit", on = mode == 4)
        k += Key("😀", T.EMOJI, 1.2f)
        return Row(k, TOOLBAR_H)
    }

    fun en(numRow: Boolean): List<Row> {
        val rows = ArrayList<Row>()
        rows += toolbar(0)
        if (numRow) rows += numRow(false)
        rows += Row(enKeys("qwertyuiop", !numRow))
        rows += Row(enKeys("asdfghjkl", false))
        rows += Row(listOf(Key("⇧", T.SHIFT, 1.5f)) + enKeys("zxcvbnm", false) + del)
        rows += bottom(Key("123", T.SYM, 1.4f), "EN", ",", ".", false)
        return rows
    }

    fun ar(numRow: Boolean): List<Row> {
        val rows = ArrayList<Row>()
        rows += toolbar(0)
        if (numRow) rows += numRow(true)
        rows += Row(arKeys("ضصثقفغعهخحجد", !numRow))
        rows += Row(arKeys("شسيبلاتنمكط", false))
        rows += Row(arKeys("ذئءؤرىةوزظ", false) + del)
        rows += bottom(Key("123", T.SYM, 1.4f), "AR", "،", ".", true)
        return rows
    }

    fun sym(page: Int): List<Row> {
        val rows = ArrayList<Row>()
        rows += toolbar(1)
        if (page == 0) {
            rows += Row(chars("1234567890"))
            rows += Row(chars("@#\$%&-+()/"))
            rows += Row(listOf(Key("=\\<", T.ACT, 1.4f, "symp")) + chars("*\"':;!?؟") + del)
        } else {
            rows += Row(chars("~`|•√π÷×¶∆"))
            rows += Row(chars("£¢€¥^°={}\\"))
            rows += Row(listOf(Key("123", T.ACT, 1.4f, "symp")) + chars("©®™[]<>_") + del)
        }
        rows += bottom(Key("ABC", T.ABC, 1.4f), "SYM", ",", ".", false)
        return rows
    }

    fun emoji(cat: Int, page: Int, recents: List<String>): List<Row> {
        val all = Emoji.list(cat, recents)
        val pages = Emoji.pages(cat, recents)
        val p = page.coerceIn(0, pages - 1)
        val slice = all.drop(p * Emoji.PER_PAGE).take(Emoji.PER_PAGE)
        val rows = ArrayList<Row>()
        rows += toolbar(2, "${p + 1}/$pages")
        for (r in 0 until 3) {
            val keys = ArrayList<Key>()
            for (c in 0 until 8) {
                val e = slice.getOrNull(r * 8 + c)
                keys += if (e != null) Key(e, T.EMO) else Key("", T.SPACER)
            }
            rows += Row(keys)
        }
        val tabs = Emoji.tabs.mapIndexed { i, t -> Key(t, T.TOOL, 1f, "cat:$i", on = i == cat) }
        rows += Row(listOf(Key("ABC", T.ABC, 1.5f)) + tabs + Key("⌫", T.DEL, 1.5f))
        return rows
    }

    fun clipboard(items: List<ClipEntry>, page: Int): List<Row> {
        val per = 3
        val pages = maxOf(1, (items.size + per - 1) / per)
        val p = page.coerceIn(0, pages - 1)
        val rows = ArrayList<Row>()
        rows += toolbar(3, "الحافظة")
        for (r in 0 until per) {
            val idx = p * per + r
            val e = items.getOrNull(idx)
            if (e != null) {
                rows += Row(
                    listOf(
                        Key(e.text.replace('\n', ' '), T.CLIP, 6f, arg = idx),
                        Key("📌", T.TOOL, 1f, "pin:$idx", on = e.pinned),
                        Key("🗑", T.TOOL, 1f, "del:$idx")
                    )
                )
            } else if (items.isEmpty() && r == 0) {
                rows += Row(listOf(Key("انسخ أي نص وسيظهر هنا", T.SPACER)))
            } else {
                rows += Row(listOf(Key("", T.SPACER)))
            }
        }
        rows += Row(
            listOf(
                Key("ABC", T.ABC, 1.5f), Key("◀", T.ACT, 1f, "pg:-1"),
                Key("${p + 1}/$pages", T.SPACER, 1.5f), Key("▶", T.ACT, 1f, "pg:1"),
                Key("🧹", T.ACT, 1.3f, "clrclip"), Key("⌫", T.DEL, 1.5f)
            )
        )
        return rows
    }

    fun edit(selecting: Boolean): List<Row> {
        fun a(label: String, id: String, on: Boolean = false) = Key(label, T.ACT, 1f, id, on = on)
        return listOf(
            toolbar(4, "تعديل النص"),
            Row(listOf(a("⏮ كلمة", "wl"), a("▲", "up"), a("كلمة ⏭", "wr"), a("الكل", "all"))),
            Row(listOf(a("◀", "left"), a("تحديد", "sel", selecting), a("▶", "right"), a("نسخ", "copy"))),
            Row(listOf(a("بداية", "home"), a("▼", "down"), a("نهاية", "end"), a("قص", "cut"))),
            Row(
                listOf(
                    Key("ABC", T.ABC, 1.4f), Key("Legend", T.SPACE, 3f, " "),
                    Key("لصق", T.ACT, 1.6f, "paste"), Key("⌫", T.DEL, 1.3f), Key("⏎", T.ENTER, 1.3f)
                )
            )
        )
    }
}
