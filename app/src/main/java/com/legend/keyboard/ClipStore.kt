package com.legend.keyboard

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class Clip(val text: String, var pinned: Boolean)

class ClipStore(private val prefs: SharedPreferences) {
    val items = ArrayList<Clip>()

    init {
        try {
            val a = JSONArray(prefs.getString("clips", "[]"))
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                items.add(Clip(o.getString("t"), o.optBoolean("p")))
            }
        } catch (e: Exception) { }
    }

    private fun save() {
        val a = JSONArray()
        items.forEach { a.put(JSONObject().put("t", it.text).put("p", it.pinned)) }
        prefs.edit().putString("clips", a.toString()).apply()
    }

    private fun firstUnpinned(): Int = items.indexOfFirst { !it.pinned }.let { if (it < 0) items.size else it }

    /** returns true if the list changed */
    fun add(t: String): Boolean {
        if (items.any { it.text == t && it.pinned }) return false
        val top = items.getOrNull(firstUnpinned())
        if (top != null && top.text == t) return false
        items.removeAll { it.text == t && !it.pinned }
        items.add(firstUnpinned(), Clip(t, false))
        while (items.count { !it.pinned } > 40) items.removeAt(items.indexOfLast { !it.pinned })
        save()
        return true
    }

    fun toggle(idx: Int) {
        val c = items.getOrNull(idx) ?: return
        items.removeAt(idx)
        c.pinned = !c.pinned
        items.add(if (c.pinned) 0 else firstUnpinned(), c)
        save()
    }

    fun clearUnpinned() {
        items.removeAll { !it.pinned }
        save()
    }
}
