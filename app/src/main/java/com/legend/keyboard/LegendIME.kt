package com.legend.keyboard

import android.content.ClipboardManager
import android.content.SharedPreferences
import android.inputmethodservice.InputMethodService
import android.os.SystemClock
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager

class LegendIME : InputMethodService(), LegendKeyboardView.Listener {
    private lateinit var view: LegendKeyboardView
    private lateinit var prefs: SharedPreferences
    private lateinit var dict: Dictionary
    private lateinit var clips: ClipStore
    private var cm: ClipboardManager? = null
    private var clipListener: ClipboardManager.OnPrimaryClipChangedListener? = null

    private var mode = 0 // 0 حروف، 1 رموز، 2 إيموجي، 3 حافظة، 4 تعديل
    private var arabic = false
    private var symPage = 0
    private var emoCat = 1
    private var emoPage = 0
    private var clipPage = 0
    private var selecting = false

    private var suggestOn = true
    private var autoOn = true
    private var dblSpace = true
    private var autoCap = true
    private var clipOn = true
    private var plainField = false

    private var seenWords = 0
    private var seenClips = 0
    private var lastSpaceTime = 0L
    private var freshClip: String? = null
    private var freshTime = 0L
    private var curSlots: Array<Sugg?> = arrayOfNulls(3)

    private class Corr(val orig: String, val rep: String, var sep: String)
    private var lastCorr: Corr? = null

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("legend", MODE_PRIVATE)
        dict = Dictionary(this, prefs).also { it.load() }
        clips = ClipStore(prefs).also { it.load() }
        seenWords = prefs.getInt("ep_words", 0)
        seenClips = prefs.getInt("ep_clips", 0)
        cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val l = ClipboardManager.OnPrimaryClipChangedListener { captureClip() }
        clipListener = l
        cm?.addPrimaryClipChangedListener(l)
    }

    override fun onDestroy() {
        clipListener?.let { cm?.removePrimaryClipChangedListener(it) }
        dict.saveUser()
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        arabic = prefs.getBoolean("ar", false)
        view = LegendKeyboardView(this).also { it.listener = this }
        applyPrefs()
        refreshLayout()
        return view
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (!::view.isInitialized) return
        applyPrefs()
        val type = info?.inputType ?: 0
        val cls = type and InputType.TYPE_MASK_CLASS
        val variation = type and InputType.TYPE_MASK_VARIATION
        plainField = cls != InputType.TYPE_CLASS_TEXT ||
            variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS ||
            variation == InputType.TYPE_TEXT_VARIATION_URI ||
            (type and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0
        mode = if (cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE) 1 else 0
        symPage = 0
        selecting = false
        lastCorr = null
        freshClip = null
        captureClip()
        view.caps = false
        view.shift = false
        refreshLayout()
        updateAutoShift()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        dict.saveUser()
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (::view.isInitialized && mode == 0) updateSuggestions()
    }

    private fun applyPrefs() {
        view.theme = Themes.all[prefs.getInt("theme", 0).coerceIn(0, Themes.all.size - 1)]
        view.haptic = prefs.getBoolean("haptic", true)
        view.sound = prefs.getBoolean("sound", false)
        view.heightScale = when (prefs.getInt("hscale", 1)) { 0 -> 0.88f; 2 -> 1.12f; else -> 1f }
        suggestOn = prefs.getBoolean("suggest", true)
        autoOn = prefs.getBoolean("autocorrect", true)
        dblSpace = prefs.getBoolean("dblspace", true)
        autoCap = prefs.getBoolean("autocap", true)
        clipOn = prefs.getBoolean("clip", true)
        val ew = prefs.getInt("ep_words", 0)
        if (ew != seenWords) { seenWords = ew; dict.reloadUser() }
        val ec = prefs.getInt("ep_clips", 0)
        if (ec != seenClips) { seenClips = ec; clips.load() }
    }

    private fun recents(): List<String> =
        (prefs.getString("recent_emoji", "") ?: "").split(' ').filter { it.isNotEmpty() }

    private fun addRecent(e: String) {
        val l = recents().toMutableList()
        l.remove(e)
        l.add(0, e)
        prefs.edit().putString("recent_emoji", l.take(24).joinToString(" ")).apply()
    }

    private fun refreshLayout() {
        val numRow = prefs.getBoolean("numrow", false)
        view.rows = when (mode) {
            1 -> Layouts.sym(symPage)
            2 -> Layouts.emoji(emoCat, emoPage, recents())
            3 -> Layouts.clipboard(clips.sorted(), clipPage)
            4 -> Layouts.edit(selecting)
            else -> if (arabic) Layouts.ar(numRow) else Layouts.en(numRow)
        }
        updateSuggestions()
    }

    private fun updateAutoShift() {
        if (!autoCap || arabic || mode != 0 || view.caps) return
        val ei = currentInputEditorInfo ?: return
        view.shift = (currentInputConnection?.getCursorCapsMode(ei.inputType) ?: 0) != 0
    }

    // ---------- الحافظة ----------
    private fun captureClip() {
        if (!::prefs.isInitialized || !prefs.getBoolean("clip", true)) return
        try {
            val c = cm?.primaryClip ?: return
            if (c.itemCount == 0) return
            val d = c.description
            if (d?.extras?.getBoolean("android.content.extra.IS_SENSITIVE", false) == true) return
            val t = c.getItemAt(0).coerceToText(this)?.toString() ?: return
            if (clips.add(t)) {
                freshClip = t.trim()
                freshTime = SystemClock.uptimeMillis()
                if (::view.isInitialized) {
                    if (mode == 3) refreshLayout() else if (mode == 0) updateSuggestions()
                }
            }
        } catch (_: Exception) {
        }
    }

    // ---------- الاقتراحات والتصحيح ----------
    private fun isWordChar(c: Char) =
        Character.isLetter(c) || c == '\'' || Character.getType(c) == Character.NON_SPACING_MARK.toInt()

    private fun wordBefore(before: String): String {
        var i = before.length
        while (i > 0 && isWordChar(before[i - 1])) i--
        return before.substring(i).trimStart('\'')
    }

    private fun updateSuggestions() {
        if (!::view.isInitialized) return
        val s = arrayOfNulls<Sugg>(3)
        if (mode == 0) {
            val ic = currentInputConnection
            val w = if (ic != null && suggestOn && !plainField)
                wordBefore(ic.getTextBeforeCursor(48, 0)?.toString() ?: "") else ""
            if (w.isNotEmpty() && dict.isReady()) {
                val r = dict.lookup(w, arabic)
                val list = r.list.map { Dictionary.applyCase(w, it, arabic) }.distinct().filter { it != w }
                if (!r.known) {
                    if (list.isEmpty()) {
                        s[1] = Sugg(w, 1)
                    } else {
                        s[0] = Sugg(w, 1)
                        s[1] = Sugg(list[0], 0)
                        s[2] = list.getOrNull(1)?.let { Sugg(it, 0) }
                    }
                } else {
                    s[1] = list.getOrNull(0)?.let { Sugg(it, 0) }
                    s[0] = list.getOrNull(1)?.let { Sugg(it, 0) }
                    s[2] = list.getOrNull(2)?.let { Sugg(it, 0) }
                }
            } else if (w.isEmpty() && clipOn && !plainField) {
                val fc = freshClip
                if (fc != null && SystemClock.uptimeMillis() - freshTime < 90_000) s[1] = Sugg(fc, 2)
            }
        }
        curSlots = s
        view.slots = s
    }

    private val triggers = " .,!?;:)\n،؟؛"

    private val fixes = mapOf(
        "i" to "I", "im" to "I'm", "ive" to "I've", "dont" to "don't", "cant" to "can't",
        "wont" to "won't", "isnt" to "isn't", "arent" to "aren't", "wasnt" to "wasn't",
        "werent" to "weren't", "didnt" to "didn't", "doesnt" to "doesn't", "couldnt" to "couldn't",
        "wouldnt" to "wouldn't", "shouldnt" to "shouldn't", "havent" to "haven't", "hasnt" to "hasn't",
        "hadnt" to "hadn't", "youre" to "you're", "theyre" to "they're", "thats" to "that's",
        "whats" to "what's", "theres" to "there's", "youll" to "you'll", "youve" to "you've"
    )

    private fun autocorrect(ic: InputConnection): Corr? {
        if (!autoOn || plainField || mode != 0 || !dict.isReady()) return null
        val before = ic.getTextBeforeCursor(48, 0)?.toString() ?: return null
        val w = wordBefore(before)
        if (w.isEmpty()) return null
        val idx = before.length - w.length
        val prev = if (idx > 0) before[idx - 1] else ' '
        if (prev in "@/#:\\") return null
        var rep: String? = null
        if (!arabic) {
            val fix = fixes[w.lowercase()]
            if (fix != null) rep = Dictionary.applyCase(w, fix, false)
        }
        if (rep == null && w.length >= 3) {
            if (w.any { it.isUpperCase() }) {
                val restLower = w.drop(1).none { it.isUpperCase() }
                val rest = before.dropLast(w.length).trimEnd(' ')
                val sentenceStart = rest.isEmpty() || rest.last() in ".!?؟\n"
                if (!(restLower && sentenceStart)) return null
            }
            val r = dict.lookup(w, arabic)
            if (!r.known) {
                val a = r.auto
                if (a != null) rep = Dictionary.applyCase(w, a, arabic) else dict.learn(w)
            }
        }
        if (rep != null && rep != w) {
            ic.beginBatchEdit()
            ic.deleteSurroundingText(w.length, 0)
            ic.commitText(rep, 1)
            ic.endBatchEdit()
            return Corr(w, rep, "")
        }
        return null
    }

    override fun onSuggestion(slot: Int) {
        val s = curSlots.getOrNull(slot) ?: return
        val ic = currentInputConnection ?: return
        lastCorr = null
        if (s.kind == 2) {
            ic.commitText(s.text, 1)
            freshClip = null
        } else {
            val w = wordBefore(ic.getTextBeforeCursor(48, 0)?.toString() ?: "")
            ic.beginBatchEdit()
            ic.deleteSurroundingText(w.length, 0)
            ic.commitText(s.text + " ", 1)
            ic.endBatchEdit()
            if (s.kind == 1) dict.accept(s.text)
        }
        updateAutoShift()
        updateSuggestions()
    }

    // ---------- الإدخال ----------
    override fun onText(s: String) {
        val ic = currentInputConnection ?: return
        freshClip = null
        lastCorr = null
        if (s.length == 1 && triggers.indexOf(s[0]) >= 0) {
            val c = autocorrect(ic)
            ic.commitText(s, 1)
            if (c != null) { c.sep = s; lastCorr = c }
        } else {
            ic.commitText(s, 1)
        }
        updateAutoShift()
        updateSuggestions()
    }

    override fun onSpace() {
        val ic = currentInputConnection ?: return
        val now = SystemClock.uptimeMillis()
        if (dblSpace && !plainField && now - lastSpaceTime < 500) {
            val b = ic.getTextBeforeCursor(2, 0)?.toString()
            if (b != null && b.length == 2 && b[1] == ' ' && b[0].isLetterOrDigit()) {
                lastCorr = null
                ic.beginBatchEdit()
                ic.deleteSurroundingText(1, 0)
                ic.commitText(". ", 1)
                ic.endBatchEdit()
                lastSpaceTime = 0
                updateAutoShift()
                updateSuggestions()
                return
            }
        }
        onText(" ")
        lastSpaceTime = now
    }

    override fun onDelete() {
        val ic = currentInputConnection
        val lc = lastCorr
        if (ic != null && lc != null) {
            lastCorr = null
            val tail = lc.rep + lc.sep
            if (ic.getTextBeforeCursor(tail.length, 0)?.toString() == tail) {
                ic.beginBatchEdit()
                ic.deleteSurroundingText(tail.length, 0)
                ic.commitText(lc.orig, 1)
                ic.endBatchEdit()
                dict.accept(lc.orig)
                updateAutoShift()
                updateSuggestions()
                return
            }
        }
        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        updateAutoShift()
        updateSuggestions()
    }

    override fun onEnter() {
        val ic = currentInputConnection ?: return
        autocorrect(ic)
        lastCorr = null
        val ei = currentInputEditorInfo
        val opts = ei?.imeOptions ?: 0
        val action = opts and EditorInfo.IME_MASK_ACTION
        val noAction = (opts and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        if (ei == null || noAction || action == EditorInfo.IME_ACTION_NONE || action == EditorInfo.IME_ACTION_UNSPECIFIED) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        } else {
            ic.performEditorAction(action)
        }
        updateAutoShift()
        updateSuggestions()
    }

    override fun onReplaceLast(s: String, prevLen: Int) {
        val ic = currentInputConnection ?: return
        lastCorr = null
        ic.beginBatchEdit()
        ic.deleteSurroundingText(prevLen, 0)
        ic.commitText(s, 1)
        ic.endBatchEdit()
        updateAutoShift()
        updateSuggestions()
    }

    override fun onCursorMove(steps: Int) {
        val code = if (steps > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
        repeat(kotlin.math.abs(steps)) { sendKey(code, 0) }
    }

    private fun sendKey(code: Int, meta: Int) {
        val ic = currentInputConnection ?: return
        val t = SystemClock.uptimeMillis()
        ic.sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0, meta))
        ic.sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0, meta))
    }

    override fun onShift() {
        when {
            view.caps -> { view.caps = false; view.shift = false }
            view.shift -> view.caps = true
            else -> view.shift = true
        }
    }

    override fun onLang() {
        arabic = !arabic
        prefs.edit().putBoolean("ar", arabic).apply()
        mode = 0
        view.caps = false
        view.shift = false
        refreshLayout()
        updateAutoShift()
    }

    override fun onMode(t: T) {
        mode = when (t) {
            T.SYM -> { symPage = 0; 1 }
            T.ABC -> 0
            T.EMOJI -> if (mode == 2) 0 else 2
            else -> mode
        }
        refreshLayout()
        updateAutoShift()
    }

    override fun onTheme() {
        val next = (prefs.getInt("theme", 0) + 1) % Themes.all.size
        prefs.edit().putInt("theme", next).apply()
        applyPrefs()
    }

    override fun onSpaceLong() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
    }

    // ---------- الأزرار الخاصة ----------
    override fun onClip(index: Int) {
        val e = clips.sorted().getOrNull(index) ?: return
        currentInputConnection?.commitText(e.text, 1)
        freshClip = null
        mode = 0
        refreshLayout()
        updateAutoShift()
    }

    override fun onAction(id: String) {
        val ic = currentInputConnection
        val sel = if (selecting) KeyEvent.META_SHIFT_ON else 0
        when {
            id.startsWith("emo:") -> {
                val e = id.substring(4)
                ic?.commitText(e, 1)
                addRecent(e)
                lastCorr = null
            }
            id == "clip" -> { mode = if (mode == 3) 0 else 3; clipPage = 0; refreshLayout() }
            id == "edit" -> { mode = if (mode == 4) 0 else 4; refreshLayout() }
            id == "symp" -> { symPage = 1 - symPage; refreshLayout() }
            id.startsWith("cat:") -> { emoCat = id.substring(4).toInt(); emoPage = 0; refreshLayout() }
            id.startsWith("emopg:") -> {
                val pages = Emoji.pages(emoCat, recents())
                emoPage = (emoPage + id.substring(6).toInt()).coerceIn(0, pages - 1)
                refreshLayout()
            }
            id.startsWith("pg:") -> {
                val pages = maxOf(1, (clips.sorted().size + 2) / 3)
                clipPage = (clipPage + id.substring(3).toInt()).coerceIn(0, pages - 1)
                refreshLayout()
            }
            id.startsWith("pin:") -> {
                clips.sorted().getOrNull(id.substring(4).toInt())?.let { clips.togglePin(it) }
                refreshLayout()
            }
            id.startsWith("del:") -> {
                clips.sorted().getOrNull(id.substring(4).toInt())?.let { clips.delete(it) }
                refreshLayout()
            }
            id == "clrclip" -> { clips.clearUnpinned(); clipPage = 0; refreshLayout() }
            id == "sel" -> { selecting = !selecting; refreshLayout() }
            id == "all" -> ic?.performContextMenuAction(android.R.id.selectAll)
            id == "copy" -> ic?.performContextMenuAction(android.R.id.copy)
            id == "cut" -> ic?.performContextMenuAction(android.R.id.cut)
            id == "paste" -> {
                if (ic != null && !ic.performContextMenuAction(android.R.id.paste)) {
                    val t = try { cm?.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString() } catch (_: Exception) { null }
                    if (t != null) ic.commitText(t, 1)
                }
            }
            id == "left" -> sendKey(KeyEvent.KEYCODE_DPAD_LEFT, sel)
            id == "right" -> sendKey(KeyEvent.KEYCODE_DPAD_RIGHT, sel)
            id == "up" -> sendKey(KeyEvent.KEYCODE_DPAD_UP, sel)
            id == "down" -> sendKey(KeyEvent.KEYCODE_DPAD_DOWN, sel)
            id == "home" -> sendKey(KeyEvent.KEYCODE_MOVE_HOME, sel)
            id == "end" -> sendKey(KeyEvent.KEYCODE_MOVE_END, sel)
            id == "wl" -> sendKey(KeyEvent.KEYCODE_DPAD_LEFT, sel or KeyEvent.META_CTRL_ON)
            id == "wr" -> sendKey(KeyEvent.KEYCODE_DPAD_RIGHT, sel or KeyEvent.META_CTRL_ON)
        }
    }
}
