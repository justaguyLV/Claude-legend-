package com.legend.keyboard

import android.content.Context
import android.graphics.*
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class LegendKeyboardView(ctx: Context) : View(ctx) {
    interface Listener {
        fun onText(s: String)
        fun onDelete()
        fun onEnter()
        fun onShift()
        fun onLang()
        fun onMode(t: T)
        fun onTheme()
        fun onSpaceLong()
        fun onCursor(dir: Int)
        fun onSuggestion(i: Int)
        fun onClip(idx: Int, long: Boolean)
        fun onAction(id: String)
    }

    var listener: Listener? = null
    var rows: List<Row> = emptyList()
        set(v) { field = v; rebuild(); invalidate() }
    var theme: KbTheme = Themes.all[0]
        set(v) { field = v; invalidate() }
    var shift = false
        set(v) { field = v; invalidate() }
    var caps = false
        set(v) { field = v; invalidate() }
    var suggestions: List<String> = emptyList()
        set(v) { if (field != v) { field = v; invalidate() } }
    var heightScale = 1f
        set(v) { field = v; requestLayout() }
    var haptic = true
    var sound = false

    private val rects = ArrayList<Pair<Key, RectF>>()
    private val active = HashMap<Int, Key>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val ui = Handler(Looper.getMainLooper())

    private var longKey: Key? = null
    private var longPointer = -1
    private var popupFor: Key? = null
    private var popupAlts: List<String> = emptyList()
    private var popupRects: List<RectF> = emptyList()
    private var popupSel = 0
    private var popupPointer = -1
    private var spaceStartX = 0f
    private var spaceLastX = 0f
    private var spaceDragging = false
    private var spaceLongFired = false
    private var clipLongFired = false

    private val delRepeat = object : Runnable {
        override fun run() { listener?.onDelete(); ui.postDelayed(this, 45) }
    }
    private val spaceLong = Runnable { spaceLongFired = true; listener?.onSpaceLong() }
    private val charLong = Runnable { showPopup() }
    private val clipLong = Runnable {
        val k = longKey
        if (k != null && k.type == T.CLIP) {
            clipLongFired = true
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            listener?.onClip(k.idx, true)
        }
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density
    private fun shade(c: Int, f: Float) =
        Color.argb(255, (Color.red(c) * f).toInt(), (Color.green(c) * f).toInt(), (Color.blue(c) * f).toInt())

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(MeasureSpec.getSize(w), dp(52f * 4.9f * heightScale).toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { rebuild() }

    private fun rebuild() {
        rects.clear()
        if (width == 0 || height == 0 || rows.isEmpty()) return
        val total = rows.sumOf { it.h.toDouble() }.toFloat()
        val unit = height / total
        val pad = dp(3f)
        var y = 0f
        for (row in rows) {
            val rh = row.h * unit
            val sw = row.keys.sumOf { it.weight.toDouble() }.toFloat()
            var x = 0f
            for (k in row.keys) {
                val w = width * k.weight / sw
                rects.add(k to RectF(x + pad, y + pad, x + w - pad, y + rh - pad))
                x += w
            }
            y += rh
        }
    }

    private fun display(k: Key) = if (k.type == T.CHAR && (shift || caps)) k.label.uppercase() else k.label

    private fun drawCentered(c: Canvas, s: String, r: RectF) {
        val fm = text.fontMetrics
        c.drawText(s, r.centerX(), r.centerY() - (fm.ascent + fm.descent) / 2, text)
    }

    private fun fit(s: String, maxW: Float): String {
        if (text.measureText(s) <= maxW) return s
        var t = s
        while (t.isNotEmpty() && text.measureText("$t…") > maxW) t = t.dropLast(1)
        return "$t…"
    }

    override fun onDraw(c: Canvas) {
        val t = theme
        paint.shader = LinearGradient(0f, 0f, 0f, height.toFloat(), t.top, t.bottom, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null
        val rad = dp(9f)
        val onAccent = if (Color.luminance(t.accent) > 0.5f) Color.BLACK else Color.WHITE

        for ((k, r) in rects) {
            val pressed = active.values.any { it === k }
            if (k.type == T.SPACER) {
                if (k.label.isNotEmpty()) {
                    text.color = t.text; text.alpha = 150; text.textSize = r.height() * 0.3f
                    drawCentered(c, fit(k.label, r.width() - dp(8f)), r)
                }
                continue
            }
            if (k.type == T.SUG) {
                val s = suggestions.getOrNull(k.idx) ?: ""
                if (s.isEmpty()) continue
                paint.color = t.key; paint.alpha = if (pressed) 255 else 120
                c.drawRoundRect(r, rad, rad, paint); paint.alpha = 255
                text.color = if (k.idx == 0) t.accent else t.text
                text.textSize = r.height() * 0.38f
                drawCentered(c, fit(s, r.width() - dp(10f)), r)
                continue
            }
            val tool = k.type == T.THEME || k.type == T.EMOJI || k.type == T.CLIPS
            var tc = t.text
            if (!tool) {
                val bg = when {
                    pressed || k.type == T.ENTER -> t.accent
                    k.type == T.CHAR || k.type == T.SPACE || k.type == T.CLIP -> t.key
                    else -> t.special
                }
                paint.color = shade(bg, 0.65f)
                c.drawRoundRect(RectF(r.left, r.top + dp(1.5f), r.right, r.bottom + dp(1.5f)), rad, rad, paint)
                paint.color = bg
                c.drawRoundRect(r, rad, rad, paint)
                if (pressed || k.type == T.ENTER) tc = onAccent
            } else if (pressed) {
                paint.color = t.accent; paint.alpha = 90
                c.drawRoundRect(r, rad, rad, paint); paint.alpha = 255
            }
            if (k.type == T.SHIFT && (shift || caps) && !pressed) tc = t.accent
            text.color = tc
            if (k.type == T.SPACE) text.alpha = 150
            text.textSize = r.height() * when (k.type) {
                T.CHAR -> 0.42f
                T.SPACE -> 0.28f
                T.LANG -> 0.26f
                T.CLIP, T.ACT -> 0.3f
                else -> 0.34f
            }
            val label = if (k.type == T.SHIFT && caps) "⇪" else display(k)
            drawCentered(c, if (k.type == T.CLIP || k.type == T.ACT) fit(label, r.width() - dp(10f)) else label, r)
        }

        // key preview bubble
        for ((k, r) in rects) {
            if (k.type != T.CHAR || popupFor === k || active.values.none { it === k }) continue
            val w = r.width() * 1.25f
            val h = r.height()
            val left = (r.centerX() - w / 2).coerceIn(0f, max(0f, width - w))
            val top = max(0f, r.top - h - dp(4f))
            val b = RectF(left, top, left + w, top + h)
            paint.color = shade(t.accent, 0.6f)
            c.drawRoundRect(RectF(b.left, b.top + dp(2f), b.right, b.bottom + dp(2f)), rad, rad, paint)
            paint.color = t.accent
            c.drawRoundRect(b, rad, rad, paint)
            text.color = onAccent; text.textSize = h * 0.6f
            drawCentered(c, display(k), b)
        }

        // long-press popup
        if (popupFor != null && popupRects.isNotEmpty()) {
            val box = RectF(popupRects.first().left, popupRects.first().top, popupRects.last().right, popupRects.first().bottom)
            paint.color = shade(t.key, 0.8f)
            c.drawRoundRect(RectF(box.left - dp(3f), box.top - dp(3f), box.right + dp(3f), box.bottom + dp(3f)), rad, rad, paint)
            popupRects.forEachIndexed { i, cell ->
                if (i == popupSel) { paint.color = t.accent; c.drawRoundRect(cell, rad, rad, paint) }
                text.color = if (i == popupSel) onAccent else t.text
                text.textSize = cell.height() * 0.5f
                drawCentered(c, popupAlts[i], cell)
            }
        }
    }

    private fun find(x: Float, y: Float): Key? {
        val pad = dp(3f)
        for ((k, r) in rects) {
            if (k.type == T.SPACER) continue
            if (x >= r.left - pad && x <= r.right + pad && y >= r.top - pad && y <= r.bottom + pad) return k
        }
        return null
    }

    private fun feedback() {
        if (haptic) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
        if (sound) (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD)
    }

    private fun showPopup() {
        val k = longKey ?: return
        val alts = Alts.get(k.label) ?: return
        val r = rects.firstOrNull { it.first === k }?.second ?: return
        popupFor = k
        popupAlts = alts.map { if (shift || caps) it.uppercase() else it }
        val n = popupAlts.size
        val cw = min(dp(42f), width.toFloat() / n)
        val total = cw * n
        val h = r.height() * 0.95f
        val left = (r.centerX() - total / 2).coerceIn(0f, max(0f, width - total))
        val top = max(0f, r.top - h - dp(8f))
        popupRects = List(n) { RectF(left + it * cw, top, left + (it + 1) * cw, top + h) }
        popupSel = 0
        popupPointer = longPointer
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        invalidate()
    }

    private fun press(k: Key, pid: Int, x: Float) {
        feedback()
        val l = listener ?: return
        when (k.type) {
            T.CHAR -> { longKey = k; longPointer = pid; if (Alts.get(k.label) != null) ui.postDelayed(charLong, 380) }
            T.SPACE -> {
                spaceStartX = x; spaceLastX = x; spaceDragging = false; spaceLongFired = false
                ui.postDelayed(spaceLong, 550)
            }
            T.DEL -> { l.onDelete(); ui.postDelayed(delRepeat, 400) }
            T.ENTER -> l.onEnter()
            T.SHIFT -> l.onShift()
            T.LANG -> l.onLang()
            T.SYM, T.ABC, T.EMOJI, T.CLIPS -> l.onMode(k.type)
            T.THEME -> l.onTheme()
            T.SUG -> l.onSuggestion(k.idx)
            T.ACT -> l.onAction(k.out)
            T.CLIP -> { clipLongFired = false; longKey = k; ui.postDelayed(clipLong, 450) }
            T.SPACER -> {}
        }
        invalidate()
    }

    private fun release(k: Key) {
        when (k.type) {
            T.DEL -> ui.removeCallbacks(delRepeat)
            T.SPACE -> {
                ui.removeCallbacks(spaceLong)
                if (!spaceDragging && !spaceLongFired) listener?.onText(" ")
            }
            T.CHAR -> {
                ui.removeCallbacks(charLong)
                if (popupFor === k) {
                    val a = popupAlts.getOrNull(popupSel)
                    popupFor = null; popupRects = emptyList(); popupAlts = emptyList()
                    if (a != null) listener?.onText(if (a.length == 2 && a[0] == 'ـ') a.substring(1) else a)
                } else listener?.onText(display(k))
            }
            T.CLIP -> {
                ui.removeCallbacks(clipLong)
                if (!clipLongFired) listener?.onClip(k.idx, false)
            }
            else -> {}
        }
        if (longKey === k) longKey = null
        invalidate()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                val k = find(e.getX(i), e.getY(i))
                if (k != null) { active[e.getPointerId(i)] = k; press(k, e.getPointerId(i), e.getX(i)) }
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) {
                    val id = e.getPointerId(i)
                    val x = e.getX(i)
                    val k = active[id] ?: continue
                    if (popupFor != null && id == popupPointer) {
                        val idx = popupRects.indexOfFirst { x < it.right }.let { if (it < 0) popupRects.size - 1 else it }
                        if (idx != popupSel) { popupSel = idx; invalidate() }
                    } else if (k.type == T.SPACE) {
                        if (!spaceDragging && abs(x - spaceStartX) > dp(18f)) { spaceDragging = true; ui.removeCallbacks(spaceLong) }
                        if (spaceDragging) {
                            val step = dp(13f)
                            while (x - spaceLastX > step) { listener?.onCursor(1); spaceLastX += step }
                            while (spaceLastX - x > step) { listener?.onCursor(-1); spaceLastX -= step }
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                active.remove(e.getPointerId(e.actionIndex))?.let { release(it) }
            }
            MotionEvent.ACTION_CANCEL -> {
                val ks = active.values.toList()
                active.clear()
                ks.forEach { release(it) }
            }
        }
        return true
    }
}
