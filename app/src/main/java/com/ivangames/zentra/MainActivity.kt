package com.ivangames.zentra

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private var server: Server? = null

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun c(hex: String) = Color.parseColor(hex)

    private fun rounded(fill: String, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(c(fill))
            cornerRadius = dp(radius).toFloat()
        }

    private fun circle(fill: String, stroke: String, strokeDp: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(c(fill))
            setStroke(dp(strokeDp), c(stroke))
        }

    private fun styleButton(b: Button, fill: String, text: String) {
        b.background = rounded(fill, 14)
        b.setTextColor(c(text))
        b.isAllCaps = false
        b.stateListAnimator = null
        b.textSize = 15f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = c("#0B0F1A")
        window.navigationBarColor = c("#121B33")

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(c("#0B0F1A"), c("#121B33"))
            )
            setPadding(dp(20), dp(48), dp(20), dp(20))
        }

        // Заголовок
        val title = TextView(this).apply {
            text = "ZENTRA"
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.25f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(this).apply {
            text = "Вставь ссылку — и всё готово"
            textSize = 13f
            setTextColor(c("#7C879E"))
            gravity = Gravity.CENTER
        }
        root.addView(title)
        root.addView(subtitle)

        root.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))

        // Большая кнопка
        val ring = View(this).apply { background = circle("#0F1730", "#26365F", 2) }
        val power = TextView(this).apply {
            text = "ПУСК"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.1f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = circle("#162040", "#4F8CFF", 4)
        }
        val powerBox = FrameLayout(this)
        powerBox.addView(ring, FrameLayout.LayoutParams(dp(220), dp(220), Gravity.CENTER))
        powerBox.addView(power, FrameLayout.LayoutParams(dp(170), dp(170), Gravity.CENTER))
        root.addView(powerBox, LinearLayout.LayoutParams(dp(220), dp(220)))

        // Статус
        val status = TextView(this).apply {
            text = "Не подключено"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        val hintText = TextView(this).apply {
            text = "Сервер не выбран"
            textSize = 13f
            setTextColor(c("#7C879E"))
            gravity = Gravity.CENTER
        }
        root.addView(status, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(24) })
        root.addView(hintText)

        // Карточка сервера
        val serverName = TextView(this).apply {
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        }
        val serverInfo = TextView(this).apply {
            textSize = 13f
            setTextColor(c("#7C879E"))
        }
        val serverCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded("#151C2E", 20)
            setPadding(dp(18), dp(14), dp(18), dp(14))
            visibility = View.GONE
            addView(serverName)
            addView(serverInfo)
        }
        root.addView(serverCard, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(20) })

        root.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))

        // Нижняя панель
        val input = EditText(this).apply {
            hint = "vless:// vmess:// trojan://"
            setHintTextColor(c("#5B667D"))
            setTextColor(Color.WHITE)
            textSize = 14f
            maxLines = 2
            background = rounded("#1B2338", 12)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        val pasteBtn = Button(this).apply { text = "Вставить" }
        val applyBtn = Button(this).apply { text = "Применить" }
        styleButton(pasteBtn, "#1B2338", "#FFFFFF")
        styleButton(applyBtn, "#4F8CFF", "#FFFFFF")

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(pasteBtn, LinearLayout.LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(8) })
            addView(applyBtn, LinearLayout.LayoutParams(0, dp(48), 1f))
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded("#151C2E", 20)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            addView(input)
            addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) })
        }
        root.addView(panel, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        // Логика
        fun applyLink() {
            val s = LinkParser.parse(input.text.toString())
            if (s == null) {
                server = null
                serverCard.visibility = View.GONE
                status.text = "Не удалось распознать ссылку"
                status.setTextColor(c("#FF6B6B"))
                hintText.text = "Проверь, что ссылка скопирована целиком"
                power.background = circle("#162040", "#4F8CFF", 4)
            } else {
                server = s
                serverName.text = if (s.name.isNotEmpty()) s.name else s.host
                serverInfo.text = "${s.protocol.uppercase()} · ${s.host}:${s.port}"
                serverCard.visibility = View.VISIBLE
                status.text = "Сервер выбран"
                status.setTextColor(c("#3DDC97"))
                hintText.text = "Нажми ПУСК"
                power.background = circle("#12302A", "#3DDC97", 4)
            }
        }

        pasteBtn.setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val t = cm.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
            input.setText(t)
            applyLink()
        }
        applyBtn.setOnClickListener { applyLink() }
        power.setOnClickListener {
            if (server == null) {
                status.text = "Сначала вставь ссылку"
                status.setTextColor(c("#FF6B6B"))
            } else {
                status.text = "Подключение будет на следующем шаге"
                status.setTextColor(Color.WHITE)
            }
        }

        setContentView(root)
    }
}
