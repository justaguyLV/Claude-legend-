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

/** kind: 0 كلمة مقترحة، 1 الكلمة كما كتبتها، 2 نص من الحافظة */
class Sugg(val text: String, val kind: Int)

class LegendKeyboardView(ctx: Context) : View(ctx) {
    interface Listener {
        fun onText(s: String)
        fun onDelete()
        fun onEnter()
        fun onShift()
        fun onLang()
        fun onMode(t: T)
        fun onTheme()
        fun onSpace()
        fun onSpaceLong()
        fun onAction(id: String)
        fun onSuggestion(slot: Int)
        fun onClip(index: Int)
        fun onReplaceLast(s: String, prevLen: Int)
        fun onCursorMove(steps: Int)
    }

    var listener: Listener? = null
    var rows: List<Row> = emptyList()
        set(v) { field = v; rebuild(); invalidate(); requestLayout() }
    var slots: Array<Sugg?> = arrayOfNulls(3)
        set(v) { field = v; invalidate() }
    var theme: KbTheme = Themes.all[0]
        set(v) { field = v; invalidate() }
    var shift = false
        set(v) { field = v; invalidate() }
    var caps = false
        set(v) { field = v; invalidate() }
    var heightScale = 1f
        set(v) { if (field != v) { field = v; requestLayout() } }
    var haptic = true
    var sound = false

    private val rects = ArrayList<Pair<Key, RectF>>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val ui = Handler(Looper.getMainLooper())
    private var seq = 0

    private inner class Ptr(val id: Int, val key: Key, val rect: RectF, val x0: Float) {
        var curX = x0
        var lastX = x0
        var dragged = false
        var longFired = false
        var seq = 0
        var upper = false
        var committed = ""
        var run: Runnable? = null
    }

    private val ptrs = HashMap<Int, Ptr>()

    // نافذة الأحرف البديلة (ضغطة مطولة)
    private var popKey: Key? = null
    private var popItems: List<String> = emptyList()
    private var popSel = 0
    private var popOwner = -1
    private var popUpper = false
    private var popItemW = 0f
    private val popRect = RectF()

    private val delRepeat = object : Runnable {
        override fun run() { listener?.onDelete(); ui.postDelayed(this, 45) }
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density
    private fun shade(c: Int, f: Float) =
        Color.argb(255, (Color.red(c) * f).toInt(), (Color.green(c) * f).toInt(), (Color.blue(c) * f).toInt())

    override fun onMeasure(w: Int, h: Int) {
        var total = rows.sumOf { it.h.toDouble() }.toFloat()
        if (total <= 0f) total = 4.85f
        setMeasuredDimension(MeasureSpec.getSize(w), (dp(50f * heightScale) * total).toInt())
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

    private fun altLabel(s: String, upper: Boolean): String {
        val o = if (upper) s.uppercase() else s
        return if (o.isNotEmpty() && Character.getType(o[0]) == Character.NON_SPACING_MARK.toInt()) "ـ$o" else o
    }

    private fun fit(s: String, maxW: Float): String {
        if (text.measureText(s) <= maxW) return s
        val n = text.breakText(s, true, maxW - text.measureText("…"), null)
        return if (n <= 0) "" else s.substring(0, n) + "…"
    }

    private fun drawCentered(c: Canvas, s: String, r: RectF) {
        val fm = text.fontMetrics
        c.drawText(s, r.centerX(), r.centerY() - (fm.ascent + fm.descent) / 2, text)
    }

    override fun onDraw(c: Canvas) {
        val t = theme
        paint.shader = LinearGradient(0f, 0f, 0f, height.toFloat(), t.top, t.bottom, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null
        val rad = dp(9f)
        val onAccent = if (Color.luminance(t.accent) > 0.5f) Color.BLACK else Color.WHITE
        for ((k, r) in rects) {
            val pressed = ptrs.values.any { it.key === k }
            if (k.type == T.SPACER) {
                drawSpacer(c, k, r, t)
                continue
            }
            if (k.type == T.SUGG) {
                drawSugg(c, k, r, t, pressed, rad)
                continue
            }
            val flat = k.type == T.THEME || k.type == T.EMOJI || k.type == T.TOOL || k.type == T.EMO
            var tc = t.text
            if (!flat) {
                val hot = pressed || k.type == T.ENTER || k.on
                val bg = when {
                    hot -> t.accent
                    k.type == T.CHAR || k.type == T.SPACE || k.type == T.CLIP -> t.key
                    else -> t.special
                }
                paint.color = shade(bg, 0.65f)
                c.drawRoundRect(RectF(r.left, r.top + dp(1.5f), r.right, r.bottom + dp(1.5f)), rad, rad, paint)
                paint.color = bg
                c.drawRoundRect(r, rad, rad, paint)
                if (hot) tc = onAccent
            } else if (pressed || k.on) {
                paint.color = t.accent; paint.alpha = 90
                c.drawRoundRect(r, rad, rad, paint)
                paint.alpha = 255
            }
            if (k.type == T.SHIFT && (shift || caps) && !pressed) tc = t.accent
            text.color = tc
            if (k.type == T.SPACE) text.alpha = 150
            text.textSize = r.height() * when (k.type) {
                T.CHAR -> 0.42f
                T.EMO -> 0.55f
                T.SPACE -> 0.28f
                T.LANG -> 0.26f
                T.ACT, T.CLIP -> 0.30f
                T.TOOL, T.THEME, T.EMOJI -> 0.42f
                else -> 0.34f
            }
            val label = if (k.type == T.SHIFT && caps) "⇪" else display(k)
            drawCentered(c, fit(label, r.width() - dp(4f)), r)
        }
        if (popKey != null && popItems.isNotEmpty()) drawPopup(c, t, onAccent) else drawBubbles(c, t, onAccent)
    }

    private fun drawSpacer(c: Canvas, k: Key, r: RectF, t: KbTheme) {
        if (k.label.isEmpty()) return
        text.color = t.text; text.alpha = 130
        text.textSize = r.height() * 0.34f
        drawCentered(c, fit(k.label, r.width()), r)
    }

    private fun drawSugg(c: Canvas, k: Key, r: RectF, t: KbTheme, pressed: Boolean, rad: Float) {
        if (slots.all { it == null }) {
            if (k.arg == 1) {
                text.color = t.text; text.alpha = 90
                text.textSize = r.height() * 0.4f
                drawCentered(c, "LEGEND", r)
            }
            return
        }
        val s = slots.getOrNull(k.arg)
        if (pressed && s != null) {
            paint.color = t.accent; paint.alpha = 70
            c.drawRoundRect(r, rad, rad, paint)
            paint.alpha = 255
        }
        if (s != null) {
            text.textSize = r.height() * 0.40f
            text.color = if (k.arg == 1 && s.kind == 0) t.accent else t.text
            val label = when (s.kind) {
                1 -> "\"" + s.text + "\""
                2 -> "📋 " + s.text.replace('\n', ' ')
                else -> s.text
            }
            drawCentered(c, fit(label, r.width() - dp(6f)), r)
        }
        if (k.arg < 2) {
            paint.color = t.text; paint.alpha = 50
            c.drawRect(r.right + dp(1.5f), r.top + r.height() * 0.2f, r.right + dp(2.5f), r.bottom - r.height() * 0.2f, paint)
            paint.alpha = 255
        }
    }

    private fun drawBubbles(c: Canvas, t: KbTheme, onAccent: Int) {
        val rad = dp(10f)
        for (p in ptrs.values) {
            if (p.key.type != T.CHAR) continue
            val w = p.rect.width() * 1.25f
            val h = p.rect.height() * 1.15f
            var left = p.rect.centerX() - w / 2
            if (left < 0f) left = 0f
            if (left + w > width) left = width - w
            val top = (p.rect.top - h - dp(4f)).coerceAtLeast(0f)
            val rr = RectF(left, top, left + w, top + h)
            paint.color = t.accent
            c.drawRoundRect(rr, rad, rad, paint)
            text.color = onAccent
            text.textSize = h * 0.55f
            drawCentered(c, display(p.key), rr)
        }
    }

    private fun drawPopup(c: Canvas, t: KbTheme, onAccent: Int) {
        val rad = dp(10f)
        paint.color = shade(t.special, 0.8f)
        c.drawRoundRect(popRect, rad, rad, paint)
        for (i in popItems.indices) {
            val r = RectF(popRect.left + i * popItemW, popRect.top, popRect.left + (i + 1) * popItemW, popRect.bottom)
            r.inset(dp(2f), dp(3f))
            if (i == popSel) {
                paint.color = t.accent
                c.drawRoundRect(r, rad, rad, paint)
            }
            text.color = if (i == popSel) onAccent else t.text
            text.textSize = popRect.height() * 0.45f
            drawCentered(c, altLabel(popItems[i], popUpper), r)
        }
    }

    private fun find(x: Float, y: Float): Pair<Key, RectF>? {
        val pad = dp(3f)
        for (kr in rects) {
            val k = kr.first
            val r = kr.second
            if (k.type == T.SPACER || (k.type == T.SUGG && slots.all { it == null })) continue
            if (x >= r.left - pad && x <= r.right + pad && y >= r.top - pad && y <= r.bottom + pad) return kr
        }
        return null
    }

    private fun feedback() {
        if (haptic) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
        if (sound) (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD)
    }

    private fun schedule(p: Ptr, delay: Long, f: () -> Unit) {
        val r = Runnable { f() }
        p.run = r
        ui.postDelayed(r, delay)
    }

    private fun press(p: Ptr) {
        val k = p.key
        feedback()
        seq++
        p.seq = seq
        val l = listener ?: return
        when (k.type) {
            T.CHAR -> {
                p.upper = shift || caps
                p.committed = display(k)
                l.onText(p.committed)
                if (k.alts.isNotEmpty()) schedule(p, 380) { openPopup(p) }
            }
            T.EMO -> l.onAction("emo:" + k.out)
            T.SPACE -> schedule(p, 550) { p.longFired = true; l.onSpaceLong() }
            T.DEL -> { l.onDelete(); ui.postDelayed(delRepeat, 400) }
            T.ENTER -> l.onEnter()
            T.SHIFT -> l.onShift()
            T.LANG -> l.onLang()
            T.SYM, T.ABC, T.EMOJI -> l.onMode(k.type)
            T.THEME -> l.onTheme()
            T.TOOL, T.ACT -> l.onAction(k.out)
            T.SUGG -> l.onSuggestion(k.arg)
            T.CLIP -> l.onClip(k.arg)
            T.SPACER -> {}
        }
        invalidate()
    }

    private fun openPopup(p: Ptr) {
        if (!ptrs.containsKey(p.id)) return
        val items = p.key.alts
        if (items.isEmpty()) return
        val n = items.size
        var iw = dp(42f)
        if (iw * n > width) iw = width.toFloat() / n
        var left = p.rect.left - dp(4f)
        if (left + iw * n > width) left = width - iw * n
        if (left < 0f) left = 0f
        val h = p.rect.height() * 1.1f
        val top = (p.rect.top - h - dp(4f)).coerceAtLeast(0f)
        popRect.set(left, top, left + iw * n, top + h)
        popItemW = iw
        popItems = items
        popKey = p.key
        popOwner = p.id
        popUpper = p.upper
        popSel = ((p.curX - left) / iw).toInt().coerceIn(0, n - 1)
        p.longFired = true
        feedback()
        invalidate()
    }

    private fun move(p: Ptr, x: Float, y: Float) {
        p.curX = x
        if (popKey != null && popOwner == p.id) {
            popSel = if (y > p.rect.bottom + dp(36f)) -1
            else ((x - popRect.left) / popItemW).toInt().coerceIn(0, popItems.size - 1)
            invalidate()
            return
        }
        if (p.key.type == T.SPACE && !p.longFired) {
            val step = dp(11f)
            if (!p.dragged && abs(x - p.x0) > dp(14f)) {
                p.dragged = true
                p.run?.let { ui.removeCallbacks(it) }
            }
            if (p.dragged) {
                val n = ((x - p.lastX) / step).toInt()
                if (n != 0) {
                    listener?.onCursorMove(n)
                    p.lastX += n * step
                }
            }
        }
    }

    private fun release(p: Ptr) {
        p.run?.let { ui.removeCallbacks(it) }
        val l = listener
        when (p.key.type) {
            T.DEL -> ui.removeCallbacks(delRepeat)
            T.SPACE -> if (!p.dragged && !p.longFired) l?.onSpace()
            T.CHAR -> if (popKey != null && popOwner == p.id) {
                if (popSel >= 0 && l != null) {
                    val s = popItems[popSel]
                    val o = if (p.upper) s.uppercase() else s
                    if (p.seq == seq) l.onReplaceLast(o, p.committed.length) else l.onText(o)
                }
                popKey = null
                popOwner = -1
            }
            else -> {}
        }
        invalidate()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                val x = e.getX(i)
                val hit = find(x, e.getY(i))
                if (hit != null) {
                    val p = Ptr(e.getPointerId(i), hit.first, hit.second, x)
                    ptrs[p.id] = p
                    press(p)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) {
                    val p = ptrs[e.getPointerId(i)] ?: continue
                    move(p, e.getX(i), e.getY(i))
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                ptrs.remove(e.getPointerId(e.actionIndex))?.let { release(it) }
            }
            MotionEvent.ACTION_CANCEL -> {
                ptrs.values.forEach { p -> p.run?.let { ui.removeCallbacks(it) } }
                ptrs.clear()
                ui.removeCallbacks(delRepeat)
                popKey = null
                popOwner = -1
                invalidate()
            }
        }
        return true
    }
}
