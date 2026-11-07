package com.legend.keyboard

import android.content.ClipboardManager
import android.content.SharedPreferences
import android.inputmethodservice.InputMethodService
import android.os.SystemClock
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager

class LegendIME : InputMethodService(), LegendKeyboardView.Listener {
    private lateinit var view: LegendKeyboardView
    private lateinit var prefs: SharedPreferences
    private lateinit var clips: ClipStore
    private lateinit var dict: Dict

    private var mode = 0 // 0 letters, 1 symbols, 2 emoji, 3 clipboard
    private var arabic = false
    private var clipPage = 0
    private var suggestOn = false
    private var isPassword = false
    private var lastSpace = 0L
    private var lastAuto: Triple<String, String, String>? = null // original, corrected, boundary

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { syncClip() }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("legend", MODE_PRIVATE)
        clips = ClipStore(prefs)
        dict = Dict(this, prefs)
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).addPrimaryClipChangedListener(clipListener)
    }

    override fun onDestroy() {
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).removePrimaryClipChangedListener(clipListener)
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
        dict.reloadLearned()
        applyPrefs()
        val t = info?.inputType ?: 0
        val cls = t and InputType.TYPE_MASK_CLASS
        val v = t and InputType.TYPE_MASK_VARIATION
        isPassword = (cls == InputType.TYPE_CLASS_TEXT && (v == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                v == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD || v == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) ||
                (cls == InputType.TYPE_CLASS_NUMBER && v == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        suggestOn = cls == InputType.TYPE_CLASS_TEXT && !isPassword &&
                v != InputType.TYPE_TEXT_VARIATION_URI && v != InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS &&
                v != InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS &&
                (t and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) == 0 && prefs.getBoolean("sugg", true)
        mode = if (cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE) 1 else 0
        lastAuto = null
        view.caps = false
        view.shift = false
        refreshLayout()
        updateAutoShift()
        refreshSuggestions()
        syncClip()
    }

    override fun onUpdateSelection(os: Int, oe: Int, ns: Int, ne: Int, cs: Int, ce: Int) {
        super.onUpdateSelection(os, oe, ns, ne, cs, ce)
        if (::view.isInitialized) refreshSuggestions()
    }

    private fun applyPrefs() {
        view.theme = Themes.all[prefs.getInt("theme", 0).coerceIn(0, Themes.all.size - 1)]
        view.haptic = prefs.getBoolean("haptic", true)
        view.sound = prefs.getBoolean("sound", false)
        view.heightScale = prefs.getFloat("hscale", 1f)
    }

    private fun refreshLayout() {
        view.rows = when (mode) {
            1 -> Layouts.sym()
            2 -> Layouts.emoji()
            3 -> Layouts.clips(clips.items, clipPage)
            else -> if (arabic) Layouts.ar() else Layouts.en()
        }
        refreshSuggestions()
    }

    private fun updateAutoShift() {
        if (arabic || mode != 0 || view.caps) return
        val ei = currentInputEditorInfo ?: return
        view.shift = (currentInputConnection?.getCursorCapsMode(ei.inputType) ?: 0) != 0
    }

    // ---------- clipboard ----------
    private fun syncClip() {
        if (!::prefs.isInitialized || isPassword || !prefs.getBoolean("clipOn", true)) return
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = try { cm.primaryClip } catch (e: Exception) { null } ?: return
        if (clip.itemCount == 0) return
        val sensitive = clip.description.extras?.getBoolean("android.content.extra.IS_SENSITIVE", false) ?: false
        if (sensitive) return
        val t = try { clip.getItemAt(0).coerceToText(this).toString() } catch (e: Exception) { return }
        if (t.isBlank() || t.length > 3000) return
        if (clips.add(t) && ::view.isInitialized && mode == 3) refreshLayout()
    }

    // ---------- words ----------
    private fun currentWord(): String =
        currentInputConnection?.getTextBeforeCursor(40, 0)?.toString()?.takeLastWhile { it.isLetter() || it == '\'' } ?: ""

    private fun applyCase(typed: String, s: String): String = when {
        typed.length > 1 && typed.all { it.isUpperCase() } -> s.uppercase()
        typed.isNotEmpty() && typed[0].isUpperCase() -> s.replaceFirstChar { it.uppercase() }
        else -> s
    }

    private fun refreshSuggestions() {
        if (!::view.isInitialized) return
        if (!suggestOn || mode != 0 || !dict.ready) { view.suggestions = emptyList(); return }
        val w = currentWord()
        if (w.isEmpty()) { view.suggestions = emptyList(); return }
        val others = dict.candidates(w, 3).map { applyCase(w, it) }.filter { !it.equals(w, true) }
        view.suggestions = listOf(others.getOrNull(0) ?: "", w, others.getOrNull(1) ?: "")
    }

    private fun handleWordEnd(w: String, boundary: String) {
        val ic = currentInputConnection ?: return
        if (!dict.ready || w.length < 3 || dict.valid(w)) return
        if (prefs.getBoolean("autocorrect", true) && !dict.isArabic(w) && w.all { it.isLowerCase() || it == '\'' }) {
            val c = dict.correction(w)
            if (c != null) {
                ic.deleteSurroundingText(w.length, 0)
                ic.commitText(c, 1)
                lastAuto = Triple(w, c, boundary)
                return
            }
        }
        dict.noteWord(w)
    }

    // ---------- listener ----------
    override fun onText(s: String) {
        val ic = currentInputConnection ?: return
        lastAuto = null
        val boundary = s.length == 1 && !s[0].isLetterOrDigit() && s[0] != '\'' &&
                s[0].category != CharCategory.NON_SPACING_MARK
        if (boundary && suggestOn) {
            val w = currentWord()
            if (w.isNotEmpty()) handleWordEnd(w, s)
        }
        if (s == " ") {
            val now = SystemClock.uptimeMillis()
            val before = ic.getTextBeforeCursor(2, 0)?.toString() ?: ""
            if (prefs.getBoolean("dsp", true) && now - lastSpace < 450 && before.length == 2 &&
                before[1] == ' ' && before[0].isLetterOrDigit()) {
                ic.deleteSurroundingText(1, 0); ic.commitText(". ", 1); lastSpace = 0
            } else {
                ic.commitText(" ", 1); lastSpace = now
            }
        } else {
            ic.commitText(s, 1); lastSpace = 0
        }
        updateAutoShift()
        refreshSuggestions()
    }

    override fun onDelete() {
        val ic = currentInputConnection
        val la = lastAuto
        lastAuto = null
        if (la != null && ic != null) {
            val tail = la.second + la.third
            if (ic.getTextBeforeCursor(tail.length, 0)?.toString() == tail) {
                ic.deleteSurroundingText(tail.length, 0)
                ic.commitText(la.first + la.third, 1)
                dict.learn(la.first)
                refreshSuggestions()
                return
            }
        }
        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        updateAutoShift()
        refreshSuggestions()
    }

    override fun onEnter() {
        lastAuto = null
        val ic = currentInputConnection ?: return
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
        refreshSuggestions()
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
            T.SYM -> 1
            T.ABC -> 0
            T.EMOJI -> if (mode == 2) 0 else 2
            T.CLIPS -> if (mode == 3) 0 else 3
            else -> mode
        }
        if (mode == 3) clipPage = 0
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

    override fun onCursor(dir: Int) {
        sendDownUpKeyEvents(if (dir < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT)
    }

    override fun onSuggestion(i: Int) {
        val ic = currentInputConnection ?: return
        val s = view.suggestions.getOrNull(i) ?: return
        if (s.isEmpty()) return
        val w = currentWord()
        lastAuto = null
        ic.beginBatchEdit()
        ic.deleteSurroundingText(w.length, 0)
        ic.commitText("$s ", 1)
        ic.endBatchEdit()
        if (s == w && !dict.valid(w)) dict.learn(w)
        lastSpace = SystemClock.uptimeMillis()
        updateAutoShift()
        refreshSuggestions()
    }

    override fun onClip(idx: Int, long: Boolean) {
        if (long) {
            clips.toggle(idx)
            refreshLayout()
        } else {
            val c = clips.items.getOrNull(idx) ?: return
            currentInputConnection?.commitText(c.text, 1)
        }
    }

    override fun onAction(id: String) {
        val ic = currentInputConnection
        when (id) {
            "selectAll" -> ic?.performContextMenuAction(android.R.id.selectAll)
            "copy" -> ic?.performContextMenuAction(android.R.id.copy)
            "cut" -> ic?.performContextMenuAction(android.R.id.cut)
            "paste" -> ic?.performContextMenuAction(android.R.id.paste)
            "prev" -> { clipPage = maxOf(0, clipPage - 1); refreshLayout() }
            "next" -> {
                val pages = maxOf(1, (clips.items.size + 3) / 4)
                clipPage = minOf(pages - 1, clipPage + 1); refreshLayout()
            }
            "clear" -> { clips.clearUnpinned(); clipPage = 0; refreshLayout() }
        }
    }
}
