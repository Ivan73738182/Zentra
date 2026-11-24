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
            setTextColor(Color.GRAY)
            setPadding(0, 32, 0, 0)
        }
        button.setOnClickListener {
            status.text = "Ссылка: " + input.text.toString().take(60)
        }

        root.addView(title)
        root.addView(input)
        root.addView(button)
        root.addView(status)
        setContentView(root)
    }
}
