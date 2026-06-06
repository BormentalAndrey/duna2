package com.dune2emu

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainMenuActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Заголовок
        val title = TextView(this).apply {
            text = "DUNE 2"
            textSize = 48f
            setTextColor(Color.WHITE)
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setShadowLayer(15f, 0f, 0f, Color.CYAN)
            setPadding(0, 0, 0, 80)
        }

        // Подзаголовок
        val subtitle = TextView(this).apply {
            text = "EMULATOR"
            textSize = 24f
            setTextColor(Color.argb(200, 255, 255, 0))
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setShadowLayer(10f, 0f, 0f, Color.YELLOW)
            setPadding(0, 0, 0, 100)
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(80, 100, 80, 100)
            gravity = Gravity.CENTER
            setBackgroundColor(Color.argb(200, 5, 5, 15)) // Тёмный фон
        }

        layout.addView(title)
        layout.addView(subtitle)

        // Кнопка "Одиночная игра" — неоновый зелёный
        val btnSingle = createNeonButton("ОДИНОЧНАЯ ИГРА", Color.argb(200, 0, 255, 100)) {
            startActivity(Intent(this@MainMenuActivity, EmulatorActivity::class.java))
        }

        // Кнопка "1 Джойстик" — неоновый голубой
        val btnHost = createNeonButton("1 ДЖОЙСТИК (HOST)", Color.argb(200, 0, 200, 255)) {
            val intent = Intent(this@MainMenuActivity, MultiplayerActivity::class.java)
            intent.putExtra("isHost", true)
            startActivity(intent)
        }

        // Кнопка "2 Джойстик" — неоновый розовый
        val btnJoin = createNeonButton("2 ДЖОЙСТИК (JOIN)", Color.argb(200, 255, 100, 255)) {
            showJoinDialog()
        }

        layout.addView(btnSingle)
        layout.addView(btnHost)
        layout.addView(btnJoin)

        setContentView(layout)
    }

    private fun createNeonButton(text: String, color: Int, onClick: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            textSize = 18f
            setTextColor(Color.WHITE)
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setShadowLayer(8f, 0f, 0f, color)

            // Прозрачный фон с неоновой обводкой
            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 20f
                setColor(Color.argb(30, 0, 0, 0)) // прозрачная заливка
                setStroke(3, color) // неоновая обводка
            }
            background = drawable

            // Отступы
            setPadding(40, 25, 40, 25)

            // Анимация нажатия
            setOnClickListener {
                onClick()
            }

            // Размеры кнопки
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 20, 0, 20)
            }
        }
    }

    private fun showJoinDialog() {
        val input = EditText(this).apply {
            hint = "Введите IP адрес хоста (192.168.1.5)"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.argb(100, 255, 255, 255))
            typeface = Typeface.MONOSPACE
            setBackgroundColor(Color.argb(40, 0, 0, 0))
            setPadding(30, 20, 30, 20)
        }

        val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("JOIN GAME")
            .setView(input)
            .setPositiveButton("CONNECT") { _, _ ->
                val ip = input.text.toString().trim()
                if (ip.isNotEmpty()) {
                    val intent = Intent(this, MultiplayerActivity::class.java)
                    intent.putExtra("isHost", false)
                    intent.putExtra("hostIp", ip)
                    startActivity(intent)
                }
            }
            .setNegativeButton("CANCEL", null)
            .create()

        dialog.show()

        // Стилизация кнопок диалога
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
            setTextColor(Color.argb(200, 0, 255, 100))
            typeface = Typeface.MONOSPACE
        }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.apply {
            setTextColor(Color.argb(200, 255, 100, 100))
            typeface = Typeface.MONOSPACE
        }
    }
}
