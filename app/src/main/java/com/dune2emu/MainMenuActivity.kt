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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.cn1emu.R

class MainMenuActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Создаем макет и сразу задаем ему фон
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(100, 200, 100, 200)
            gravity = Gravity.CENTER
            
            // Установка картинки i.png как фона
            // R.drawable.i ссылается на app/src/main/res/drawable/i.png
            setBackgroundResource(R.drawable.i)
        }

        val btnSingle = Button(this).apply {
            text = "Одиночная игра"
            setOnClickListener {
                startActivity(Intent(this@MainMenuActivity, EmulatorActivity::class.java))
            }
            // Киберпанк неон — зелёный
            textSize = 18f
            setTextColor(Color.WHITE)
            typeface = Typeface.MONOSPACE
            setShadowLayer(10f, 0f, 0f, Color.argb(200, 0, 255, 100))
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 20f
                setColor(Color.argb(30, 0, 0, 0))
                setStroke(3, Color.argb(200, 0, 255, 100))
            }
            background = bg
            setPadding(30, 20, 30, 20)
        }

        val btnHost = Button(this).apply {
            text = "1 Джостик"
            setOnClickListener {
                val intent = Intent(this@MainMenuActivity, MultiplayerActivity::class.java)
                intent.putExtra("isHost", true)
                startActivity(intent)
            }
            // Киберпанк неон — голубой
            textSize = 18f
            setTextColor(Color.WHITE)
            typeface = Typeface.MONOSPACE
            setShadowLayer(10f, 0f, 0f, Color.argb(200, 0, 200, 255))
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 20f
                setColor(Color.argb(30, 0, 0, 0))
                setStroke(3, Color.argb(200, 0, 200, 255))
            }
            background = bg
            setPadding(30, 20, 30, 20)
        }

        val btnJoin = Button(this).apply {
            text = "2 Джостик"
            setOnClickListener {
                showJoinDialog()
            }
            // Киберпанк неон — розовый
            textSize = 18f
            setTextColor(Color.WHITE)
            typeface = Typeface.MONOSPACE
            setShadowLayer(10f, 0f, 0f, Color.argb(200, 255, 100, 255))
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 20f
                setColor(Color.argb(30, 0, 0, 0))
                setStroke(3, Color.argb(200, 255, 100, 255))
            }
            background = bg
            setPadding(30, 20, 30, 20)
        }

        layout.addView(btnSingle)
        layout.addView(btnHost)
        layout.addView(btnJoin)
        
        setContentView(layout)
    }

    private fun showJoinDialog() {
        val input = EditText(this).apply {
            hint = "Enter host IP address (e.g. 192.168.1.5)"
        }
        AlertDialog.Builder(this)
            .setTitle("Join Multiplayer Game")
            .setView(input)
            .setPositiveButton("Connect") { _, _ ->
                val ip = input.text.toString().trim()
                if (ip.isNotEmpty()) {
                    val intent = Intent(this, MultiplayerActivity::class.java)
                    intent.putExtra("isHost", false)
                    intent.putExtra("hostIp", ip)
                    startActivity(intent)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
