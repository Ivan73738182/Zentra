package com.ivangames.zentra

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B0F1A"))
            setPadding(48, 120, 48, 48)
        }
        val title = TextView(this).apply {
            text = "Zentra"
            textSize = 32f
            setTextColor(Color.WHITE)
        }
        val input = EditText(this).apply {
            hint = "Вставь ссылку или подписку"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
        }
        val button = Button(this).apply { text = "Подключить" }
        val status = TextView(this).apply {
            text = "Не подключено"
            textSize = 16f
            setTextColor(Color.LTGRAY)
            setPadding(0, 32, 0, 0)
        }

        button.setOnClickListener {
            val server = LinkParser.parse(input.text.toString())
            if (server == null) {
                status.text = "Не удалось распознать ссылку"
            } else {
                val extra = server.params.entries.joinToString("\n") { "${it.key} = ${it.value}" }
                status.text = "Протокол: ${server.protocol.uppercase()}\n" +
                    "Имя: ${server.name}\n" +
                    "Адрес: ${server.host}:${server.port}\n" +
                    "ID: ${server.id.take(8)}…\n" + extra
            }
        }

        root.addView(title)
        root.addView(input)
        root.addView(button)
        root.addView(status)
        setContentView(root)
    }
}
