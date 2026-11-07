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
        fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
        val accent = 0xFF00E5FF.toInt()

        root.addView(tv("⌨️ Legend Keyboard", 28f, accent))
        root.addView(tv("كيبورد أسطوري: عربي وإنجليزي، قاموس وتصحيح، سجل حافظة، ثيمات.", 14f))
        root.addView(btn("1) تفعيل الكيبورد") { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) })
        root.addView(btn("2) اختيار الكيبورد") {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        })

        root.addView(tv("الثيم", 18f, accent))
        Themes.all.forEachIndexed { i, t ->
            root.addView(btn(t.name) { p.edit().putInt("theme", i).apply(); toast("تم اختيار ${t.name}") })
        }

        root.addView(tv("حجم الكيبورد", 18f, accent))
        val sizes = LinearLayout(this)
        sizes.orientation = LinearLayout.HORIZONTAL
        listOf("صغير" to 0.88f, "وسط" to 1f, "كبير" to 1.12f, "ضخم" to 1.25f).forEach { (n, s) ->
            val bt = btn(n) { p.edit().putFloat("hscale", s).apply(); toast("الحجم: $n") }
            sizes.addView(bt, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        root.addView(sizes)

        root.addView(tv("المزايا", 18f, accent))
        root.addView(sw("الاقتراحات والإكمال", "sugg", true))
        root.addView(sw("التصحيح التلقائي (إنجليزي فقط)", "autocorrect", true))
        root.addView(sw("مسافتين = نقطة", "dsp", true))
        root.addView(sw("سجل الحافظة (كوبي/باست)", "clipOn", true))
        root.addView(sw("الهزاز عند الضغط", "haptic", true))
        root.addView(sw("صوت الضغط", "sound", false))

        root.addView(tv("الخصوصية والبيانات", 18f, accent))
        root.addView(btn("مسح سجل الحافظة كله") { p.edit().remove("clips").apply(); toast("تم المسح") })
        root.addView(btn("مسح الكلمات اللي تعلّمها الكيبورد") { p.edit().remove("learned").remove("wc").apply(); toast("تم المسح") })

        root.addView(tv("جرّب هنا", 18f, accent))
        val et = EditText(this)
        et.hint = "اكتب هنا..."; et.setTextColor(Color.WHITE); et.setHintTextColor(0x88FFFFFF.toInt())
        root.addView(et)
        root.addView(tv(
            "نصائح:\n• اسحب على المسافة لتحريك المؤشر\n• اضغط مطولاً على أي حرف للأرقام والحروف الخاصة\n" +
            "• 📋 فوق يفتح سجل الحافظة، والضغط المطول على عنصر يثبّته 📌\n• اضغط مطولاً على المسافة لتبديل الكيبورد\n" +
            "• لو صحح كلمة غلط اضغط ⌫ مباشرة ويرجع كلمتك", 12f, 0x99FFFFFF.toInt()))

        val sv = ScrollView(this)
        sv.setBackgroundColor(0xFF120B2E.toInt())
        sv.addView(root)
        setContentView(sv)
    }
}
