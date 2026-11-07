package com.legend.keyboard

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import kotlin.math.abs

class Dict(private val ctx: Context, private val prefs: SharedPreferences) {
    @Volatile var ready = false
    private var en: List<String> = emptyList()
    private var ar: List<String> = emptyList()
    private var enSet: Set<String> = emptySet()
    private var arSet: Set<String> = emptySet()
    private val learned = HashSet<String>()
    private var counts = JSONObject()

    init {
        reloadLearned()
        Thread {
            try {
                en = read("words_en.txt"); ar = read("words_ar.txt")
                enSet = en.toHashSet(); arSet = ar.toHashSet()
            } catch (e: Exception) { }
            ready = true
        }.start()
    }

    private fun read(n: String) = ctx.assets.open(n).bufferedReader().use { it.readLines() }

    fun reloadLearned() {
        learned.clear()
        learned.addAll(prefs.getStringSet("learned", emptySet()) ?: emptySet())
        counts = try { JSONObject(prefs.getString("wc", "{}")) } catch (e: Exception) { JSONObject() }
    }

    private fun persist() {
        prefs.edit().putStringSet("learned", HashSet(learned)).putString("wc", counts.toString()).apply()
    }

    fun isArabic(w: String) = w.any { it in '\u0600'..'\u06FF' }

    fun valid(w: String): Boolean {
        val l = w.lowercase()
        return l in learned || (if (isArabic(w)) l in arSet else l in enSet)
    }

    fun learn(w: String) {
        val l = w.lowercase()
        if (l.length < 2) return
        learned.add(l); persist()
    }

    fun noteWord(w: String) {
        val l = w.lowercase()
        if (l.length < 3 || valid(l)) return
        val c = counts.optInt(l, 0) + 1
        if (c >= 2) { learned.add(l); counts.remove(l) } else counts.put(l, c)
        if (counts.length() > 3000) counts = JSONObject()
        persist()
    }

    fun correction(w: String): String? {
        if (!ready) return null
        val l = w.lowercase()
        for (c in if (isArabic(w)) ar else en) if (within1(l, c)) return c
        return null
    }

    fun candidates(w: String, n: Int): List<String> {
        if (!ready) return emptyList()
        val l = w.lowercase()
        val list = if (isArabic(w)) ar else en
        val out = LinkedHashSet<String>()
        if (!valid(w) && l.length >= 2) {
            var c = 0
            for (x in list) if (within1(l, x)) { out.add(x); if (++c >= 2) break }
        }
        for (x in learned) if (x.length > l.length && x.startsWith(l)) { out.add(x); if (out.size >= n) break }
        var c = 0
        for (x in list) if (x.length > l.length && x.startsWith(l)) { out.add(x); if (++c >= n) break }
        return out.toList().take(n)
    }

    private fun within1(a: String, b: String): Boolean {
        val la = a.length; val lb = b.length
        if (abs(la - lb) > 1) return false
        if (la == lb) {
            var i = 0
            while (i < la && a[i] == b[i]) i++
            if (i == la) return false
            if (a.substring(i + 1) == b.substring(i + 1)) return true
            return i + 1 < la && a[i] == b[i + 1] && a[i + 1] == b[i] && a.substring(i + 2) == b.substring(i + 2)
        }
        val s = if (la < lb) a else b
        val l = if (la < lb) b else a
        var i = 0
        while (i < s.length && s[i] == l[i]) i++
        return s.substring(i) == l.substring(i + 1)
    }
}
