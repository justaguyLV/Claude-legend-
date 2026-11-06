package com.legend.keyboard

import android.content.SharedPreferences
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager

class LegendIME : InputMethodService(), LegendKeyboardView.Listener {
    private lateinit var view: LegendKeyboardView
    private lateinit var prefs: SharedPreferences
    private var mode = 0 // 0 letters, 1 symbols, 2 emoji
    private var arabic = false

    override fun onCreateInputView(): View {
        prefs = getSharedPreferences("legend", MODE_PRIVATE)
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
        val cls = (info?.inputType ?: 0) and InputType.TYPE_MASK_CLASS
        mode = if (cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE) 1 else 0
        view.caps = false
        view.shift = false
        refreshLayout()
        updateAutoShift()
    }

    private fun applyPrefs() {
        view.theme = Themes.all[prefs.getInt("theme", 0).coerceIn(0, Themes.all.size - 1)]
        view.haptic = prefs.getBoolean("haptic", true)
        view.sound = prefs.getBoolean("sound", false)
    }

    private fun refreshLayout() {
        view.rows = when (mode) {
            1 -> Layouts.sym()
            2 -> Layouts.emoji()
            else -> if (arabic) Layouts.ar() else Layouts.en()
        }
    }

    private fun updateAutoShift() {
        if (arabic || mode != 0 || view.caps) return
        val ei = currentInputEditorInfo ?: return
        view.shift = (currentInputConnection?.getCursorCapsMode(ei.inputType) ?: 0) != 0
    }

    override fun onText(s: String) {
        currentInputConnection?.commitText(s, 1)
        updateAutoShift()
    }

    override fun onDelete() {
        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        updateAutoShift()
    }

    override fun onEnter() {
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
}
