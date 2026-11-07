package com.legend.keyboard

import android.content.Context
import android.content.SharedPreferences

/**
 * قاموس عربي/إنجليزي (قوائم ترددية في assets) + كلمات المستخدم المتعلَّمة.
 * يعطي: إكمال تلقائي (prefix)، تصحيح إملائي (edit distance 1)، وتصحيح تلقائي.
 */
class Dictionary(private val ctx: Context, private val prefs: SharedPreferences) {
    private class Lang(val words: Array<String>, val freq: HashMap<String, Int>)
    class Result(val list: List<String>, val known: Boolean, val auto: String?)

    @Volatile private var en: Lang? = null
    @Volatile private var ar: Lang? = null
    private val user = HashMap<String, Int>()
    private var dirty = false

    fun load() {
        loadUser()
        Thread({
            en = read("en.txt")
            ar = read("ar.txt")
        }, "legend-dict").start()
    }

    fun isReady() = en != null || ar != null

    private fun read(name: String): Lang? = try {
        val m = HashMap<String, Int>(60000)
        ctx.assets.open(name).bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (l in lines) {
                val i = l.lastIndexOf(' ')
                if (i <= 0) continue
                m[l.substring(0, i)] = l.substring(i + 1).toIntOrNull() ?: 1
            }
        }
        val w = m.keys.toTypedArray()
        w.sort()
        Lang(w, m)
    } catch (e: Exception) {
        null
    }

    // ---------- كلمات المستخدم ----------
    private fun loadUser() {
        user.clear()
        val s = prefs.getString("words", "") ?: ""
        for (line in s.lines()) {
            val p = line.split('\t')
            if (p.size == 2) p[1].toIntOrNull()?.let { user[p[0]] = it }
        }
    }

    fun reloadUser() = loadUser()

    fun saveUser() {
        if (!dirty) return
        dirty = false
        prefs.edit().putString("words", user.entries.joinToString("\n") { it.key + "\t" + it.value }).apply()
    }

    /** كلمة جديدة كتبها المستخدم: تُعتبر معروفة بعد مرتين. */
    fun learn(w: String) {
        val k = w.lowercase()
        if (k.length < 2 || k.length > 30) return
        user[k] = (user[k] ?: 0) + 1
        dirty = true
        if (user.size > 6000) user.entries.removeAll { it.value < 2 }
    }

    /** المستخدم قبلها صراحةً (اختارها من الاقتراحات أو تراجع عن تصحيحها). */
    fun accept(w: String) {
        val k = w.lowercase()
        if (k.length < 2 || k.length > 30) return
        user[k] = maxOf(2, user[k] ?: 0)
        dirty = true
    }

    // ---------- البحث ----------
    private val tashkeel = Regex("[\u064B-\u0652\u0640]")

    private fun norm(w: String, arabic: Boolean) = if (arabic) w.replace(tashkeel, "") else w.lowercase()

    private fun isKnown(l: Lang, key: String) = l.freq.containsKey(key) || (user[key] ?: 0) >= 2

    fun lookup(typed: String, arabic: Boolean): Result {
        val lang = (if (arabic) ar else en) ?: return Result(emptyList(), true, null)
        val key = norm(typed, arabic)
        if (key.isEmpty()) return Result(emptyList(), true, null)
        val known = isKnown(lang, key)
        val out = ArrayList<String>(3)
        var auto: String? = null
        if (!known) {
            val corr = corrections(lang, key, if (arabic) AR_ALPHA else EN_ALPHA)
            if (corr.isNotEmpty()) {
                out.addAll(corr.take(2))
                val f = lang.freq[corr[0]] ?: 300000
                if (key.length >= 3 && f >= 2000) auto = corr[0]
            }
        }
        for (c in completions(lang, key, 4)) {
            if (out.size >= 3) break
            if (c != key && c !in out) out.add(c)
        }
        return Result(out, known, auto)
    }

    private fun corrections(lang: Lang, key: String, alpha: String): List<String> {
        val seen = HashSet<String>()
        val scored = ArrayList<Pair<String, Long>>()
        fun consider(c: String) {
            if (c.isEmpty() || !seen.add(c)) return
            val f: Int = lang.freq[c] ?: (if ((user[c] ?: 0) >= 2) 300000 else return)
            var s = f.toLong()
            if (c[0] == key[0]) s *= 4
            scored.add(c to s)
        }
        val n = key.length
        for (i in 0 until n) consider(key.removeRange(i, i + 1))
        for (i in 0 until n - 1) {
            val ch = key.toCharArray()
            val t = ch[i]; ch[i] = ch[i + 1]; ch[i + 1] = t
            consider(String(ch))
        }
        for (i in 0 until n) for (a in alpha) if (a != key[i]) consider(key.substring(0, i) + a + key.substring(i + 1))
        for (i in 0..n) for (a in alpha) consider(key.substring(0, i) + a + key.substring(i))
        return scored.sortedByDescending { it.second }.map { it.first }
    }

    private fun completions(lang: Lang, prefix: String, max: Int): List<String> {
        val w = lang.words
        var lo = 0
        var hi = w.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (w[mid] < prefix) lo = mid + 1 else hi = mid
        }
        val best = ArrayList<Pair<String, Int>>()
        fun offer(word: String, f: Int) {
            if (best.size >= max && f <= best.last().second) return
            var p = best.size
            while (p > 0 && best[p - 1].second < f) p--
            best.add(p, word to f)
            if (best.size > max) best.removeAt(best.size - 1)
        }
        var i = lo
        var steps = 0
        while (i < w.size && w[i].startsWith(prefix) && steps < 6000) {
            offer(w[i], lang.freq[w[i]] ?: 0)
            i++; steps++
        }
        if (prefix.length >= 2) {
            for ((u, c) in user) {
                if (c >= 2 && u != prefix && u.startsWith(prefix) && best.none { it.first == u }) offer(u, 300000 + c)
            }
        }
        return best.map { it.first }
    }

    companion object {
        private const val EN_ALPHA = "abcdefghijklmnopqrstuvwxyz'"
        private const val AR_ALPHA = "ابتثجحخدذرزسشصضطظعغفقكلمنهويأإآةىءؤئ"

        fun applyCase(typed: String, cand: String, arabic: Boolean): String {
            if (arabic || typed.isEmpty()) return cand
            if (typed.length > 1 && typed.all { it.isUpperCase() || !it.isLetter() }) return cand.uppercase()
            if (typed[0].isUpperCase()) return cand.replaceFirstChar { it.uppercase() }
            return cand
        }
    }
}
