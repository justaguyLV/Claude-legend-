package com.legend.keyboard

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class DemoActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        )
        val d = resources.displayMetrics.density
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(0xFF1A1A1A.toInt())
        root.setPadding((24 * d).toInt(), (72 * d).toInt(), (24 * d).toInt(), 0)
        root.layoutDirection = View.LAYOUT_DIRECTION_RTL

        val title = TextView(this)
        title.text = "⌨️ Legend Keyboard"
        title.textSize = 24f
        title.setTextColor(0xFF00E5FF.toInt())
        root.addView(title)

        val sub = TextView(this)
        sub.text = "اكتب هنا 👇"
        sub.textSize = 16f
        sub.setTextColor(0x99FFFFFF.toInt())
        sub.setPadding(0, (8 * d).toInt(), 0, (16 * d).toInt())
        root.addView(sub)

        val et = EditText(this)
        et.textSize = 30f
        et.setTextColor(Color.WHITE)
        et.background = null
        et.gravity = Gravity.START or Gravity.TOP
        root.addView(et, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (260 * d).toInt()))
        setContentView(root)
        et.requestFocus()
        et.postDelayed({
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(et, 0)
        }, 600)
    }
}
