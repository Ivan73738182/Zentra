package com.ivangames.zentra

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import android.widget.HorizontalScrollView
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

    private val serverList = mutableListOf<Server>()
    private val serverLinks = mutableListOf<String>()
    private var selectedIdx = -1
    private var subInfo: SubInfo? = null
    private val pings = ConcurrentHashMap<String, Long>()

    private val prefs by lazy { getSharedPreferences("zentra_prefs", Context.MODE_PRIVATE) }

    private lateinit var status: TextView
    private lateinit var hintText: TextView
    private lateinit var power: TextView
    private lateinit var infoText: TextView
    private lateinit var pingBtn: Button
    private lateinit var scrollRow: LinearLayout

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun c(hex: String) = Color.parseColor(hex)

    private fun rounded(fill: String, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(c(fill))
            cornerRadius = dp(radius).toFloat()
        }

    private fun roundedStroke(fill: String, stroke: String, strokeDp: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(c(fill))
            setStroke(dp(strokeDp), c(stroke))
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

        // ---------- верхний ряд: заголовок + кнопка "+" ----------
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = "ZENTRA"
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.25f
            setTextColor(Color.WHITE)
        }
        val plusBtn = TextView(this).apply {
            text = "+"
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = circle("#162040", "#4F8CFF", 2)
        }
        header.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(plusBtn, LinearLayout.LayoutParams(dp(44), dp(44)))
        val subtitle = TextView(this).apply {
            text = "Вставь ссылку или отсканируй QR"
            textSize = 13f
            setTextColor(c("#7C879E"))
            gravity = Gravity.CENTER
        }
        root.addView(header)
        root.addView(subtitle)
        root.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))

        // ---------- кнопка питания ----------
        val ring = View(this).apply { background = circle("#0F1730", "#26365F", 2) }
        power = TextView(this).apply {
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

        status = TextView(this).apply {
            text = "Не подключено"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        hintText = TextView(this).apply {
            text = "Сервер не выбран"
            textSize = 13f
            setTextColor(c("#7C879E"))
            gravity = Gravity.CENTER
        }
        root.addView(status, mp(20))
        root.addView(hintText)

        // ---------- горизонтальный список серверов ----------
        scrollRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(scrollRow)
            visibility = View.GONE
        }
        root.addView(scroll, mp(16))

        infoText = TextView(this).apply {
            textSize = 13f
            setTextColor(c("#9FB0D0"))
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        root.addView(infoText, mp(10))

        pingBtn = Button(this).apply { text = "⚡ Проверить пинг" }
        styleButton(pingBtn, "#1B2338")
        root.addView(
            pingBtn,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44))
                .apply { topMargin = dp(8) }
        )

        root.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))

        // ---------- логика ----------

        fun showInfo() {
            val i = subInfo
            if (i == null) { infoText.visibility = View.GONE; return }
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

        fun updateCards() {
            scrollRow.removeAllViews()
            if (serverList.isEmpty()) { scroll.visibility = View.GONE; return }
            scroll.visibility = View.VISIBLE
            for (i in serverList.indices) {
                val s = serverList[i]
                val sel = i == selectedIdx
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = if (sel) roundedStroke("#12302A", "#3DDC97", 2, 20)
                                 else rounded("#151C2E", 20)
                    setPadding(dp(16), dp(12), dp(16), dp(12))
                    addView(TextView(this@MainActivity).apply {
                        text = if (s.name.isNotEmpty()) s.name else s.host
                        textSize = 15f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(Color.WHITE)
                    })
                    addView(TextView(this@MainActivity).apply {
                        text = s.protocol.uppercase() + " · " + s.host + ":" + s.port + pingText(serverLinks[i])
                        textSize = 12f
                        setTextColor(c("#7C879E"))
                    })
                    setOnClickListener { selectServer(i) }
                }
                scrollRow.addView(card, LinearLayout.LayoutParams(dp(200), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    rightMargin = dp(10)
                })
            }
        }

        fun selectServer(i: Int) {
            if (i !in serverList.indices) return
            selectedIdx = i
            updateCards()
            status.text = "Сервер выбран"
            status.setTextColor(c("#3DDC97"))
            hintText.text = if (ZentraVpnService.running) "Нажми СТОП и ПУСК, чтобы переключиться"
                            else "Нажми ПУСК"
            power.background = circle("#12302A", "#3DDC97", 4)
        }

        fun addServers(links: List<String>) {
            for (l in links) {
                val s = LinkParser.parse(l) ?: continue
                serverList.add(s)
                serverLinks.add(l)
            }
            if (selectedIdx < 0 && serverList.isNotEmpty()) selectedIdx = 0
            saveAll()
            updateCards()
            if (selectedIdx >= 0) selectServer(selectedIdx)
        }

        fun applyText(rawIn: String) {
            var raw = rawIn.trim()
            if (raw.isEmpty()) return
            if (!raw.startsWith("http") && LinkParser.parse(raw) == null && raw.contains("http")) {
                val cut = raw.substring(raw.indexOf("http"))
                raw = try { java.net.URLDecoder.decode(cut, "UTF-8") } catch (e: Exception) { cut }
            }
            val found = raw.lines().map { it.trim() }.filter { LinkParser.parse(it) != null }
            if (found.isNotEmpty()) { addServers(found); return }
            if (raw.startsWith("http://") || raw.startsWith("https://")) {
                status.text = "Загружаю подписку…"
                status.setTextColor(Color.WHITE)
                Subscription.fetch(raw) { res ->
                    runOnUiThread {
                        subInfo = res.info
                        pings.clear()
                        showInfo()
                        if (res.links.isEmpty()) {
                            status.text = "Не удалось загрузить подписку"
                            status.setTextColor(c("#FF6B6B"))
                            hintText.text = "Проверь ссылку и интернет"
                        } else {
                            addServers(res.links)
                        }
                    }
                }
                return
            }
            status.text = "Не удалось распознать ссылку"
            status.setTextColor(c("#FF6B6B"))
            hintText.text = "Проверь, что ссылка скопирована целиком"
        }

        fun runPing() {
            if (serverLinks.isEmpty()) {
                status.text = "Сначала добавь ссылку"
                status.setTextColor(c("#FF6B6B"))
                return
            }
            pingBtn.text = "Проверяю…"
            pingBtn.isEnabled = false
            val left = AtomicInteger(serverLinks.size)
            for (l in serverLinks) {
                Thread {
                    val p = LinkParser.parse(l)
                    var ms = -1L
                    if (p != null) {
                        try {
                            val t0 = System.nanoTime()
                            Socket().use { it.connect(InetSocketAddress(p.host, p.port), 3000) }
                            ms = (System.nanoTime() - t0) / 1000000
                        } catch (e: Exception) { ms = -1L }
                    }
                    pings[l] = ms
                    if (left.decrementAndGet() == 0) {
                        runOnUiThread {
                            pingBtn.text = "⚡ Проверить пинг"
                            pingBtn.isEnabled = true
                            updateCards()
                        }
                    }
                }.start()
            }
        }

        fun showAddDialog(prefill: String?) {
            val input = EditText(this).apply {
                hint = "Ссылка или подписка"
                setHintTextColor(c("#5B667D"))
                setTextColor(Color.WHITE)
                textSize = 14f
                maxLines = 3
                background = rounded("#1B2338", 12)
                setPadding(dp(14), dp(12), dp(14), dp(12))
                if (prefill != null) setText(prefill)
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
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = rounded("#151C2E", 20)
                setPadding(dp(16), dp(16), dp(16), dp(16))
                addView(input)
                addView(row, mp(10))
            }
            val dlg = AlertDialog.Builder(this).setView(box).create()
            pasteBtn.setOnClickListener {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val t = cm.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                input.setText(t)
            }
            qrBtn.setOnClickListener {
                dlg.dismiss()
                ensureCameraThenScan()
            }
            applyBtn.setOnClickListener {
                val txt = input.text.toString()
                dlg.dismiss()
                applyText(txt)
            }
            dlg.show()
        }

        plusBtn.setOnClickListener { showAddDialog(null) }
        pingBtn.setOnClickListener { runPing() }

        power.setOnClickListener {
            if (ZentraVpnService.running) {
                startService(Intent(this, ZentraVpnService::class.java).setAction(ZentraVpnService.ACTION_STOP))
            } else if (selectedIdx < 0) {
                status.text = "Сначала добавь ссылку"
                status.setTextColor(c("#FF6B6B"))
            } else {
                val prep = VpnService.prepare(this)
                if (prep != null) startActivityForResult(prep, 100) else startVpn()
            }
        }

        ZentraVpnService.listener = { on, msg ->
            runOnUiThread {
                if (on) {
                    status.text = "Подключено"
                    status.setTextColor(c("#3DDC97"))
                    hintText.text = "VPN работает"
                    power.text = "СТОП"
                    power.background = circle("#12302A", "#3DDC97", 4)
                } else {
                    status.text = if (msg.isEmpty()) "Отключено" else "Ошибка"
                    status.setTextColor(if (msg.isEmpty()) Color.WHITE else c("#FF6B6B"))
                    hintText.text = if (msg.isEmpty()) "Нажми ПУСК" else msg
                    power.text = "ПУСК"
                    power.background = circle("#162040", "#4F8CFF", 4)
                }
            }
        }

        loadAll()
        if (ZentraVpnService.running) ZentraVpnService.listener?.invoke(true, "")

        setContentView(root)
    }

    private fun saveAll() {
        prefs.edit().putString("all_links", serverLinks.joinToString("\n")).apply()
    }

    private fun loadAll() {
        val saved = prefs.getString("all_links", "") ?: ""
        if (saved.isBlank()) return
        val links = saved.lines().map { it.trim() }.filter { it.isNotEmpty() }
        for (l in links) {
            val s = LinkParser.parse(l) ?: continue
            serverList.add(s)
            serverLinks.add(l)
        }
        if (serverList.isNotEmpty()) selectedIdx = 0
        updateCards()
        if (selectedIdx >= 0) selectServer(selectedIdx)
    }

    private fun ensureCameraThenScan() {
        if (checkSelfPermission(android.Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 200)
            return
        }
        startScan()
    }

    private fun startScan() {
        IntentIntegrator(this)
            .setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
            .setOrientationLocked(false)
            .setBeepEnabled(false)
            .setPrompt("Наведи на QR-код")
            .initiateScan()
    }

    private fun startVpn() {
        val s = serverList.getOrNull(selectedIdx) ?: return
        val intent = Intent(this, ZentraVpnService::class.java)
            .setAction(ZentraVpnService.ACTION_START)
            .putExtra(ZentraVpnService.EXTRA_CONFIG, ConfigBuilder.build(s))
        startService(intent)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 200 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startScan()
        } else if (requestCode == 200) {
            Toast.makeText(this, "Нет доступа к камере", Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK) { startVpn(); return }
        val r = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (r != null && r.contents != null) {
            Toast.makeText(this, "QR отсканирован!", Toast.LENGTH_SHORT).show()
            showAddDialog(r.contents)
        }
    }
}
