package com.legend.keyboard

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*

class MainActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val p = getSharedPreferences("legend", MODE_PRIVATE)
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(20), dp(40), dp(20), dp(24))
        root.layoutDirection = View.LAYOUT_DIRECTION_RTL

        fun tv(t: String, sz: Float, c: Int = Color.WHITE): TextView {
            val v = TextView(this)
            v.text = t; v.textSize = sz; v.setTextColor(c); v.setPadding(0, dp(8), 0, dp(8))
            return v
        }
        fun btn(t: String, onClick: () -> Unit): Button {
            val v = Button(this)
            v.text = t; v.setOnClickListener { onClick() }
            return v
        }
        fun sw(t: String, key: String, def: Boolean): Switch {
            val v = Switch(this)
            v.text = t; v.setTextColor(Color.WHITE); v.isChecked = p.getBoolean(key, def)
            v.setPadding(0, dp(6), 0, dp(6))
            v.setOnCheckedChangeListener { _, on -> p.edit().putBoolean(key, on).apply() }
            return v
        }
        val accent = 0xFF00E5FF.toInt()

        root.addView(tv("⌨️ Legend Keyboard", 28f, accent))
        root.addView(tv("كيبورد أسطوري: عربي وإنجليزي، قاموس وتصحيح تلقائي، حافظة ذكية، تعديل النص، إيموجي، وثيمات.", 14f))
        root.addView(btn("1) تفعيل الكيبورد") { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) })
        root.addView(btn("2) اختيار الكيبورد") {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        })

        root.addView(tv("الثيم", 18f, accent))
        Themes.all.forEachIndexed { i, t ->
            root.addView(btn(t.name) {
                p.edit().putInt("theme", i).apply()
                Toast.makeText(this, "تم اختيار ${t.name}", Toast.LENGTH_SHORT).show()
            })
        }

        root.addView(tv("الكتابة الذكية", 18f, accent))
        root.addView(sw("اقتراحات الكلمات (عربي/إنجليزي)", "suggest", true))
        root.addView(sw("التصحيح التلقائي (الرجوع يلغيه)", "autocorrect", true))
        root.addView(sw("مسافتين = نقطة ومسافة", "dblspace", true))
        root.addView(sw("حرف كبير تلقائي بأول الجملة", "autocap", true))

        root.addView(tv("الحافظة", 18f, accent))
        root.addView(sw("حفظ سجل الحافظة (📋 في الكيبورد)", "clip", true))
        root.addView(btn("مسح سجل الحافظة") {
            p.edit().putString("clips", "[]").putInt("ep_clips", p.getInt("ep_clips", 0) + 1).apply()
            Toast.makeText(this, "تم مسح الحافظة", Toast.LENGTH_SHORT).show()
        })

        root.addView(tv("الشكل", 18f, accent))
        root.addView(sw("صف أرقام دائم", "numrow", false))
        root.addView(sw("الهزاز عند الضغط", "haptic", true))
        root.addView(sw("صوت الضغط", "sound", false))
        root.addView(tv("حجم المفاتيح", 14f))
        val rg = RadioGroup(this)
        rg.orientation = RadioGroup.HORIZONTAL
        val names = listOf("صغير", "وسط", "كبير")
        val cur = p.getInt("hscale", 1)
        names.forEachIndexed { i, n ->
            val rb = RadioButton(this)
            rb.id = 100 + i; rb.text = n; rb.setTextColor(Color.WHITE); rb.isChecked = i == cur
            rg.addView(rb)
        }
        rg.setOnCheckedChangeListener { _, id -> p.edit().putInt("hscale", id - 100).apply() }
        root.addView(rg)

        root.addView(btn("مسح الكلمات المتعلَّمة") {
            p.edit().putString("words", "").putInt("ep_words", p.getInt("ep_words", 0) + 1).apply()
            Toast.makeText(this, "تم مسح القاموس الشخصي", Toast.LENGTH_SHORT).show()
        })

        root.addView(tv("جرّب هنا", 18f, accent))
        val et = EditText(this)
        et.hint = "اكتب هنا..."; et.setTextColor(Color.WHITE); et.setHintTextColor(0x88FFFFFF.toInt())
        root.addView(et)
        root.addView(tv(
            "نصائح: اضغط مطولاً على أي حرف للأحرف البديلة والأرقام • اسحب على المسافة لتحريك المؤشر • " +
                "اضغط مطولاً على المسافة لتبديل الكيبورد • ✏️ لوحة التعديل (تحديد/نسخ/لصق) • 📋 سجل الحافظة.",
            12f, 0x99FFFFFF.toInt()
        ))

        val sv = ScrollView(this)
        sv.setBackgroundColor(0xFF120B2E.toInt())
        sv.addView(root)
        setContentView(sv)
    }
}
