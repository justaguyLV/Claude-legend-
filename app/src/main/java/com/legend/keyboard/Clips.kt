package com.legend.keyboard

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class ClipEntry(val text: String, val pinned: Boolean, val ts: Long)

/** سجل الحافظة: يحفظ آخر 40 نسخة + المثبتات في SharedPreferences الخاصة بالتطبيق. */
class ClipStore(private val prefs: SharedPreferences) {
    private var items = ArrayList<ClipEntry>()

    fun load() {
        items = ArrayList()
        try {
            val a = JSONArray(prefs.getString("clips", "[]"))
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                items.add(ClipEntry(o.getString("t"), o.optBoolean("p"), o.optLong("ts")))
            }
        } catch (_: Exception) {
        }
    }

    private fun save() {
        val a = JSONArray()
        for (c in items) a.put(JSONObject().put("t", c.text).put("p", c.pinned).put("ts", c.ts))
        prefs.edit().putString("clips", a.toString()).apply()
    }

    fun sorted(): List<ClipEntry> =
        items.sortedWith(compareByDescending<ClipEntry> { it.pinned }.thenByDescending { it.ts })

    /** يرجع true إذا كان النص جديداً (ليس هو الأحدث أصلاً). */
    fun add(raw: String): Boolean {
        val t = raw.trim()
        if (t.isEmpty() || t.length > 3000) return false
        val newest = items.maxByOrNull { it.ts }
        if (newest != null && newest.text == t) return false
        val old = items.firstOrNull { it.text == t }
        items.removeAll { it.text == t }
        items.add(ClipEntry(t, old?.pinned == true, System.currentTimeMillis()))
        val unpinned = items.filter { !it.pinned }.sortedByDescending { it.ts }
        if (unpinned.size > 40) {
            val drop = unpinned.drop(40).toSet()
            items.removeAll { it in drop }
        }
        save()
        return true
    }

    fun togglePin(e: ClipEntry) {
        val i = items.indexOfFirst { it.text == e.text }
        if (i >= 0) { items[i] = items[i].copy(pinned = !items[i].pinned); save() }
    }

    fun delete(e: ClipEntry) {
        items.removeAll { it.text == e.text }
        save()
    }

    fun clearUnpinned() {
        items.removeAll { !it.pinned }
        save()
    }
}
