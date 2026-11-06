package com.legend.keyboard

import android.content.Context
import android.graphics.*
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View

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
    var haptic = true
    var sound = false

    private val rects = ArrayList<Pair<Key, RectF>>()
    private val active = HashMap<Int, Key>()
    private var preview = ""
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val ui = Handler(Looper.getMainLooper())
    private val delRepeat = object : Runnable {
        override fun run() { listener?.onDelete(); ui.postDelayed(this, 45) }
    }
    private val spaceLong = Runnable { listener?.onSpaceLong() }

    private fun dp(v: Float) = v * resources.displayMetrics.density
    private fun shade(c: Int, f: Float) =
        Color.argb(255, (Color.red(c) * f).toInt(), (Color.green(c) * f).toInt(), (Color.blue(c) * f).toInt())

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(MeasureSpec.getSize(w), dp(52f * 4.7f).toInt())
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
                if (preview.isNotEmpty()) {
                    text.color = t.accent; text.textSize = r.height() * 0.6f
                    drawCentered(c, preview, r)
                } else {
                    text.color = t.text; text.alpha = 90; text.textSize = r.height() * 0.4f
                    drawCentered(c, "LEGEND", r)
                }
                continue
            }
            val tool = k.type == T.THEME || k.type == T.EMOJI
            var tc = t.text
            if (!tool) {
                val bg = when {
                    pressed || k.type == T.ENTER -> t.accent
                    k.type == T.CHAR || k.type == T.SPACE -> t.key
                    else -> t.special
                }
                paint.color = shade(bg, 0.65f)
                c.drawRoundRect(RectF(r.left, r.top + dp(1.5f), r.right, r.bottom + dp(1.5f)), rad, rad, paint)
                paint.color = bg
                c.drawRoundRect(r, rad, rad, paint)
                if (pressed || k.type == T.ENTER) tc = onAccent
            } else if (pressed) {
                paint.color = t.accent; paint.alpha = 90
                c.drawRoundRect(r, rad, rad, paint)
                paint.alpha = 255
            }
            if (k.type == T.SHIFT && (shift || caps) && !pressed) tc = t.accent
            text.color = tc
            if (k.type == T.SPACE) text.alpha = 150
            text.textSize = r.height() * when (k.type) {
                T.CHAR -> 0.42f
                T.SPACE -> 0.28f
                T.LANG -> 0.26f
                else -> 0.34f
            }
            drawCentered(c, if (k.type == T.SHIFT && caps) "⇪" else display(k), r)
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

    private fun press(k: Key) {
        feedback()
        val l = listener ?: return
        when (k.type) {
            T.CHAR -> { preview = display(k); l.onText(display(k)) }
            T.SPACE -> { l.onText(" "); ui.postDelayed(spaceLong, 550) }
            T.DEL -> { l.onDelete(); ui.postDelayed(delRepeat, 400) }
            T.ENTER -> l.onEnter()
            T.SHIFT -> l.onShift()
            T.LANG -> l.onLang()
            T.SYM, T.ABC, T.EMOJI -> l.onMode(k.type)
            T.THEME -> l.onTheme()
            T.SPACER -> {}
        }
        invalidate()
    }

    private fun release(k: Key) {
        if (k.type == T.DEL) ui.removeCallbacks(delRepeat)
        if (k.type == T.SPACE) ui.removeCallbacks(spaceLong)
        preview = ""
        invalidate()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                val k = find(e.getX(i), e.getY(i))
                if (k != null) { active[e.getPointerId(i)] = k; press(k) }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                active.remove(e.getPointerId(e.actionIndex))?.let { release(it) }
            }
            MotionEvent.ACTION_CANCEL -> {
                active.values.toList().forEach { release(it) }
                active.clear()
            }
        }
        return true
    }
}
