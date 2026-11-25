package com.ivangames.zentra

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.VpnService
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.zxing.integration.android.IntentIntegrator
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class MainActivity : Activity() {

    private var server: Server? = null
    private var currentLink: String = ""
    private var subLinks: List<String> = emptyList()
    private var subInfo: SubInfo? = null
    private val pings = ConcurrentHashMap<String, Long>()
    private var onScan: (String) -> Unit = {}

    // SharedPreferences для сохранения ссылки
    private val prefs by lazy { getSharedPreferences("zentra_prefs", Context.MODE_PRIVATE) }
    private fun saveLink(link: String) {
        prefs.edit().putString("saved_link", link).apply()
    }
    private fun getSavedLink(): String? {
        return prefs.getString("saved_link", null)
    }

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

    private fun styleButton(b: Button, fill: String) {
        b.background = rounded(fill, 14)
        b.setTextColor(Color.WHITE)
        b.isAllCaps = false
        b.stateListAnimator = null
        b.textSize = 14f
        b.setPadding(0, 0, 0, 0)
    }

    private fun mp(top: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(top) }

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
            setPadding(dp(20), dp(40), dp(20), dp(20))
        }

        val title = TextView(this).apply {
            text = "ZENTRA"
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.25f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(this).apply {
            text = "Вставь ссылку или отсканируй QR"
            textSize = 13f
            setTextColor(c("#7C879E"))
            gravity = Gravity.CENTER
        }
        root.addView(title)
        root.addView(subtitle)
        root.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))

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
        root.addView(status, mp(20))
        root.addView(hintText)

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
            setPadding(dp(18), dp(12), dp(18), dp(12))
            visibility = View.GONE
            addView(serverName)
            addView(serverInfo)
        }
        root.addView(serverCard, mp(16))

        val infoText = TextView(this).apply {
            textSize = 13f
            setTextColor(c("#9FB0D0"))
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        root.addView(infoText, mp(10))

        val pingBtn = Button(this).apply { text = "⚡ Проверить пинг" }
        styleButton(pingBtn, "#1B2338")
        root.addView(
            pingBtn,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44))
                .apply { topMargin = dp(8) }
        )

        root.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))

        val input = EditText(this).apply {
            hint = "Ссылка"
            setHintTextColor(c("#5B667D"))
            setTextColor(Color.WHITE)
            textSize = 14f
            maxLines = 2
            background = rounded("#1B2338", 12)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        val pasteBtn = Button(this).apply { text = "Вставить" }
        val qrBtn = Button(this).apply { text = "QR" }
        val applyBtn = Button(this).apply { text = "Применить" }
        styleButton(pasteBtn, "#1B2338")
        styleButton(qrBtn, "#1B2338")
        styleButton(applyBtn, "#4F8CFF")

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(pasteBtn, LinearLayout.LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(8) })
            addView(qrBtn, LinearLayout.LayoutParams(0, dp(48), 0.7f).apply { rightMargin = dp(8) })
            addView(applyBtn, LinearLayout.LayoutParams(0, dp(48), 1f))
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded("#151C2E", 20)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            addView(input)
            addView(row, mp(10))
        }
        root.addView(panel, mp(0))

        // ---------- логика ----------

        fun showInfo() {
            val i = subInfo
            if (i == null) {
                infoText.visibility = View.GONE
                return
            }
            val used = String.format(Locale.US, "%.2f GB", i.used / 1073741824.0)
            val total = if (i.total > 0) String.format(Locale.US, "%.0f GB", i.total / 1073741824.0) else "∞"
            var t = "Трафик: " + used + " / " + total
            if (i.expire > 0) {
                t += "  ·  до " + SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(i.expire * 1000))
            }
            infoText.text = t
            infoText.visibility = View.VISIBLE
        }

        fun pingText(link: String): String {
            val v = pings[link] ?: return ""
            return if (v < 0) "  ·  нет ответа" else "  ·  " + v + " мс"
        }

        fun showServer() {
            val s = server
            if (s == null) {
                serverCard.visibility = View.GONE
                return
            }
            serverName.text = if (s.name.isNotEmpty()) s.name else s.host
            serverInfo.text = s.protocol.uppercase() + " · " + s.host + ":" + s.port + pingText(currentLink)
            serverCard.visibility = View.VISIBLE
        }

        fun selectLink(link: String) {
            val s = LinkParser.parse(link)
            if (s == null) {
                server = null
                currentLink = ""
                showServer()
                status.text = "Не удалось распознать ссылку"
                status.setTextColor(c("#FF6B6B"))
                hintText.text = "Проверь, что ссылка скопирована целиком"
                power.background = circle("#162040", "#4F8CFF", 4)
                return
            }
            server = s
            currentLink = link
            showServer()
            status.text = "Сервер выбран"
            status.setTextColor(c("#3DDC97"))
            hintText.text = if (ZentraVpnService.running) "Нажми СТОП и ПУСК, чтобы переключиться"
            else if (subLinks.size > 1) "Нажми ПУСК · карточка — выбор из " + subLinks.size
            else "Нажми ПУСК"
            power.background = circle("#12302A", "#3DDC97", 4)
        }

        fun applyLink() {
            var raw = input.text.toString().trim()
            if (raw.isEmpty()) return
            
            // Гарантируем, что панель НЕ исчезает после нажатия
            panel.visibility = View.VISIBLE 

            if (!raw.startsWith("http") && LinkParser.parse(raw) == null && raw.contains("http")) {
                val cut = raw.substring(raw.indexOf("http"))
                raw = try { java.net.URLDecoder.decode(cut, "UTF-8") } catch (e: Exception) { cut }
            }
            val found = raw.lines().map { it.trim() }.filter { LinkParser.parse(it) != null }
            if (found.size > 1) {
                subLinks = found
                subInfo = null
                pings.clear()
                showInfo()
                selectLink(found[0])
                saveLink(raw) // Сохраняем
                return
            }
            if (raw.startsWith("http://") || raw.startsWith("https://")) {
                status.text = "Загружаю подписку…"
                status.setTextColor(Color.WHITE)
                Subscription.fetch(raw) { res ->
                    runOnUiThread {
                        subLinks = res.links
                        subInfo = res.info
                        pings.clear()
                        showInfo()
                        if (res.links.isEmpty()) {
                            server = null
                            showServer()
                            status.text = "Не удалось загрузить подписку"
                            status.setTextColor(c("#FF6B6B"))
                            hintText.text = "Проверь ссылку и интернет"
                        } else {
                            selectLink(res.links[0])
                            saveLink(raw) // Сохраняем
                        }
                        panel.visibility = View.VISIBLE // Возвращаем панель
                    }
                }
                return
            }
            subLinks = emptyList()
            subInfo = null
            showInfo()
            selectLink(raw)
            saveLink(raw) // Сохраняем обычную ссылку
        }

        fun runPing() {
            val list = if (subLinks.isNotEmpty()) subLinks else listOf(currentLink).filter { it.isNotEmpty() }
            if (list.isEmpty()) {
                status.text = "Сначала вставь ссылку"
                status.setTextColor(c("#FF6B6B"))
                return
            }
            pingBtn.text = "Проверяю…"
            pingBtn.isEnabled = false
            val left = AtomicInteger(list.size)
            for (l in list) {
                Thread {
                    val p = LinkParser.parse(l)
                    var ms = -1L
                    if (p != null) {
                        try {
                            val t0 = System.nanoTime()
                            Socket().use { it.connect(InetSocketAddress(p.host, p.port), 3000) }
                            ms = (System.nanoTime() - t0) / 1000000
                        } catch (e: Exception) {
                            ms = -1L
                        }
                    }
                    pings[l] = ms
                    if (left.decrementAndGet() == 0) {
                        runOnUiThread {
                            pingBtn.text = "⚡ Проверить пинг"
                            pingBtn.isEnabled = true
                            showServer()
                        }
                    }
                }.start()
            }
        }

        onScan = { t ->
            input.setText(t.trim())
            Toast.makeText(this, "QR отсканирован! Применяю...", Toast.LENGTH_SHORT).show()
            applyLink()
        }

        ZentraVpnService.listener = { on, msg ->
            runOnUiThread {
                // Панель скрываем ТОЛЬКО когда реально подключено (on == true)
                panel.visibility = if (on) View.GONE else View.VISIBLE
                if (on) {
                    status.text = "Подключено"
                    status.setTextColor(c("#3DDC97"))
                    hintText.text = "VPN работает"
                    power.text = "СТОП"
                } else {
                    status.text = if (msg.isEmpty()) "Отключено" else "Ошибка"
                    status.setTextColor(if (msg.isEmpty()) Color.WHITE else c("#FF6B6B"))
                    hintText.text = if (msg.isEmpty()) "Нажми ПУСК" else msg
                    power.text = "ПУСК"
                }
            }
        }

        power.setOnClickListener {
            if (ZentraVpnService.running) {
                startService(Intent(this, ZentraVpnService::class.java).setAction(ZentraVpnService.ACTION_STOP))
            } else if (server == null) {
                status.text = "Сначала вставь ссылку"
                status.setTextColor(c("#FF6B6B"))
            } else {
                val prep = VpnService.prepare(this)
                if (prep != null) startActivityForResult(prep, 100) else startVpn()
            }
        }

        serverCard.setOnClickListener {
            if (subLinks.size > 1) {
                val names = subLinks.map { l ->
                    val p = LinkParser.parse(l)
                    val n = if (p == null) "?" else if (p.name.isNotEmpty()) p.name else p.host
                    (if (l == currentLink) "● " else "○ ") + n + pingText(l)
                }.toTypedArray()
                AlertDialog.Builder(this)
                    .setTitle("Выбери сервер")
                    .setItems(names) { _, i -> selectLink(subLinks[i]) }
                    .show()
            }
        }

        pingBtn.setOnClickListener { runPing() }

        pasteBtn.setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val t = cm.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
            input.setText(t)
            applyLink()
        }

        qrBtn.setOnClickListener {
            IntentIntegrator(this)
                .setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
                .setOrientationLocked(false)
                .setBeepEnabled(false)
                .setPrompt("Наведи на QR-код ссылки")
                .initiateScan()
        }

        applyBtn.setOnClickListener { applyLink() }

        // АВТОЗАГРУЗКА: если есть сохраненная ссылка, применяем её сразу
        val savedLink = getSavedLink()
        if (!savedLink.isNullOrEmpty()) {
            input.setText(savedLink)
            applyLink()
        }

        if (ZentraVpnService.running) ZentraVpnService.listener?.invoke(true, "")

        setContentView(root)
    }

    private fun startVpn() {
        val s = server ?: return
        val intent = Intent(this, ZentraVpnService::class.java)
            .setAction(ZentraVpnService.ACTION_START)
            .putExtra(ZentraVpnService.EXTRA_CONFIG, ConfigBuilder.build(s))
        startService(intent)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK) {
            startVpn()
            return
        }
        val r = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (r != null && r.contents != null) onScan(r.contents)
    }
}
