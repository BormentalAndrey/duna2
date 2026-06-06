package com.dune2emu

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

class GamepadView(context: Context) : View(context) {

    private var emulator: EmulatorCore? = null
    private var multiplayerCallback: ((Int, EmulatorCore.GenesisButton, Boolean) -> Unit)? = null
    private var localPlayerIndex: Int = 0

    private val neonYellow = Color.argb(200, 255, 255, 0)
    private val neonRed    = Color.argb(200, 255, 50, 50)
    private val neonBlue   = Color.argb(200, 50, 150, 255)
    private val neonGreen  = Color.argb(200, 50, 255, 50)
    private val neonPink   = Color.argb(200, 255, 100, 255)
    private val neonOrange = Color.argb(200, 255, 180, 0)
    private val neonPurple = Color.argb(200, 180, 50, 255)
    private val neonWhite  = Color.argb(200, 200, 200, 255)
    private val neonGray   = Color.argb(150, 150, 150, 150)
    private val bgDark     = Color.argb(60, 0, 0, 0)

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 16f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.MONOSPACE
        color = Color.WHITE
        setShadowLayer(6f, 0f, 0f, Color.CYAN)
    }
    private val dpadLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.argb(60, 255, 255, 255)
    }

    private data class GameButton(
        val rect: RectF, val label: String,
        val button: EmulatorCore.GenesisButton, val color: Int
    )

    private val actionButtons = mutableListOf<GameButton>()
    private val pressedButtons = mutableSetOf<EmulatorCore.GenesisButton>()

    private var dpadCX = 0f
    private var dpadCY = 0f
    private var dpadR = 0f

    // Здесь теперь ключом выступает уникальный PointerId, а не меняющийся Index
    private val activePointers = mutableMapOf<Int, Pair<Float, Float>>()

    fun setEmulator(
        emu: EmulatorCore, playerIndex: Int = 0,
        mpCallback: ((Int, EmulatorCore.GenesisButton, Boolean) -> Unit)? = null
    ) {
        this.emulator = emu
        this.localPlayerIndex = playerIndex
        this.multiplayerCallback = mpCallback
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        setupButtons(w.toFloat(), h.toFloat())
    }

    private fun setupButtons(w: Float, h: Float) {
        actionButtons.clear()
        val btnW = w * 0.07f
        val btnH = btnW
        val gap = w * 0.01f

        dpadCX = w * 0.16f
        dpadCY = h * 0.72f
        dpadR = w * 0.11f

        val bx = w * 0.82f
        val by = h * 0.68f
        val topY = by - btnH - gap * 2
        val botY = by + gap * 2

        actionButtons.add(GameButton(RectF(bx - btnW - gap, topY, bx - gap, topY + btnH), "X", EmulatorCore.GenesisButton.X, neonPink))
        actionButtons.add(GameButton(RectF(bx, topY, bx + btnW, topY + btnH), "Y", EmulatorCore.GenesisButton.Y, neonOrange))
        actionButtons.add(GameButton(RectF(bx + btnW + gap, topY, bx + btnW*2 + gap, topY + btnH), "Z", EmulatorCore.GenesisButton.Z, neonPurple))
        actionButtons.add(GameButton(RectF(bx - btnW - gap, botY, bx - gap, botY + btnH), "A", EmulatorCore.GenesisButton.A, neonRed))
        actionButtons.add(GameButton(RectF(bx, botY, bx + btnW, botY + btnH), "B", EmulatorCore.GenesisButton.B, neonBlue))
        actionButtons.add(GameButton(RectF(bx + btnW + gap, botY, bx + btnW*2 + gap, botY + btnH), "C", EmulatorCore.GenesisButton.C, neonGreen))
        actionButtons.add(GameButton(RectF(w/2 - btnW*1.2f, h - btnH*1.6f, w/2 + btnW*1.2f, h - btnH*0.5f), "START", EmulatorCore.GenesisButton.START, neonWhite))
        actionButtons.add(GameButton(RectF(w/2 + btnW*1.4f, h - btnH*1.3f, w/2 + btnW*2.6f, h - btnH*0.6f), "MODE", EmulatorCore.GenesisButton.MODE, neonGray))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(bgDark)

        borderPaint.color = neonYellow
        borderPaint.alpha = 50
        borderPaint.strokeWidth = 2f
        canvas.drawCircle(dpadCX, dpadCY, dpadR, borderPaint)

        dpadLinePaint.color = Color.argb(60, 255, 255, 255)
        canvas.drawLine(dpadCX - dpadR, dpadCY - dpadR, dpadCX + dpadR, dpadCY + dpadR, dpadLinePaint)
        canvas.drawLine(dpadCX - dpadR, dpadCY + dpadR, dpadCX + dpadR, dpadCY - dpadR, dpadLinePaint)

        drawDpadSector(canvas, EmulatorCore.GenesisButton.UP,    225f, 90f, "▲")
        drawDpadSector(canvas, EmulatorCore.GenesisButton.DOWN,   45f, 90f, "▼")
        drawDpadSector(canvas, EmulatorCore.GenesisButton.LEFT,  135f, 90f, "◄")
        drawDpadSector(canvas, EmulatorCore.GenesisButton.RIGHT, 315f, 90f, "►")

        fillPaint.color = neonYellow
        fillPaint.alpha = 15
        canvas.drawCircle(dpadCX, dpadCY, dpadR * 0.15f, fillPaint)

        for (btn in actionButtons) {
            val pressed = pressedButtons.contains(btn.button)
            borderPaint.color = btn.color
            borderPaint.alpha = if (pressed) 255 else 70
            fillPaint.color = btn.color
            fillPaint.alpha = if (pressed) 50 else 10
            val rx = btn.rect.width() * 0.15f
            val ry = btn.rect.height() * 0.15f
            canvas.drawRoundRect(btn.rect, rx, ry, fillPaint)
            canvas.drawRoundRect(btn.rect, rx, ry, borderPaint)
            if (pressed) {
                borderPaint.alpha = 30
                borderPaint.strokeWidth = 6f
                canvas.drawRoundRect(btn.rect, rx, ry, borderPaint)
                borderPaint.strokeWidth = 3f
            }
            textPaint.color = if (pressed) Color.WHITE else Color.argb(160, 255, 255, 255)
            canvas.drawText(btn.label, btn.rect.centerX(), btn.rect.centerY() + textPaint.textSize / 3, textPaint)
        }
    }

    private fun drawDpadSector(canvas: Canvas, button: EmulatorCore.GenesisButton, startAngle: Float, sweepAngle: Float, label: String) {
        val pressed = pressedButtons.contains(button)
        val path = Path().apply {
            moveTo(dpadCX, dpadCY)
            arcTo(dpadCX - dpadR, dpadCY - dpadR, dpadCX + dpadR, dpadCY + dpadR, startAngle, sweepAngle, false)
            close()
        }
        fillPaint.color = neonYellow
        fillPaint.alpha = if (pressed) 60 else 10
        canvas.drawPath(path, fillPaint)
        borderPaint.color = neonYellow
        borderPaint.alpha = if (pressed) 255 else 60
        borderPaint.strokeWidth = if (pressed) 3f else 1.5f
        canvas.drawPath(path, borderPaint)

        val midAngle = Math.toRadians((startAngle + sweepAngle / 2).toDouble())
        val textR = dpadR * 0.55f
        val tx = dpadCX + (textR * cos(midAngle)).toFloat()
        val ty = dpadCY + (textR * sin(midAngle)).toFloat()
        textPaint.color = Color.argb(if (pressed) 255 else 120, 255, 255, 255)
        canvas.drawText(label, tx, ty + textPaint.textSize / 3, textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val newPressed = mutableSetOf<EmulatorCore.GenesisButton>()

        // ИСПРАВЛЕНИЕ: Используем getPointerId вместо actionIndex
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = event.actionIndex
                val id = event.getPointerId(idx)
                activePointers[id] = Pair(event.getX(idx), event.getY(idx))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val idx = event.actionIndex
                val id = event.getPointerId(idx)
                activePointers.remove(id)
            }
            MotionEvent.ACTION_CANCEL -> {
                activePointers.clear()
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val id = event.getPointerId(i)
                    activePointers[id] = Pair(event.getX(i), event.getY(i))
                }
            }
        }

        for ((_, point) in activePointers) {
            val (x, y) = point
            val dx = x - dpadCX
            val dy = y - dpadCY
            val dist = sqrt(dx * dx + dy * dy)

            // D-Pad: только внутри круга
            if (dist <= dpadR * 1.1f && dist > dpadR * 0.1f) {
                val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()

                // Основное направление
                val mainBtn = when {
                    angle > -135 && angle <= -45 -> EmulatorCore.GenesisButton.UP
                    angle > 45 && angle <= 135 -> EmulatorCore.GenesisButton.DOWN
                    angle > -45 && angle <= 45 -> EmulatorCore.GenesisButton.RIGHT
                    else -> EmulatorCore.GenesisButton.LEFT
                }
                newPressed.add(mainBtn)

                // Диагональ: если палец близко к границе сектора (±25°), добавляем вторую кнопку
                val sectorCenter = when (mainBtn) {
                    EmulatorCore.GenesisButton.UP -> -90f
                    EmulatorCore.GenesisButton.DOWN -> 90f
                    EmulatorCore.GenesisButton.LEFT -> 180f
                    EmulatorCore.GenesisButton.RIGHT -> 0f
                    else -> 0f
                }
                var diff = angle - sectorCenter
                if (diff > 180) diff -= 360
                if (diff < -180) diff += 360

                if (diff > 25f) {
                    // Добавляем кнопку справа по кругу
                    newPressed.add(when (mainBtn) {
                        EmulatorCore.GenesisButton.UP -> EmulatorCore.GenesisButton.RIGHT
                        EmulatorCore.GenesisButton.RIGHT -> EmulatorCore.GenesisButton.DOWN
                        EmulatorCore.GenesisButton.DOWN -> EmulatorCore.GenesisButton.LEFT
                        EmulatorCore.GenesisButton.LEFT -> EmulatorCore.GenesisButton.UP
                        else -> mainBtn
                    })
                } else if (diff < -25f) {
                    // Добавляем кнопку слева по кругу
                    newPressed.add(when (mainBtn) {
                        EmulatorCore.GenesisButton.UP -> EmulatorCore.GenesisButton.LEFT
                        EmulatorCore.GenesisButton.LEFT -> EmulatorCore.GenesisButton.DOWN
                        EmulatorCore.GenesisButton.DOWN -> EmulatorCore.GenesisButton.RIGHT
                        EmulatorCore.GenesisButton.RIGHT -> EmulatorCore.GenesisButton.UP
                        else -> mainBtn
                    })
                }
            }

            // Кнопки действий
            for (btn in actionButtons) {
                if (btn.rect.contains(x, y)) newPressed.add(btn.button)
            }
        }

        for (btn in newPressed) {
            if (!pressedButtons.contains(btn)) {
                emulator?.pressButton(localPlayerIndex, btn)
                multiplayerCallback?.invoke(localPlayerIndex, btn, true)
            }
        }
        for (btn in pressedButtons) {
            if (!newPressed.contains(btn)) {
                emulator?.releaseButton(localPlayerIndex, btn)
                multiplayerCallback?.invoke(localPlayerIndex, btn, false)
            }
        }

        pressedButtons.clear()
        pressedButtons.addAll(newPressed)
        invalidate()
        return true
    }

    // ИСПРАВЛЕНИЕ: Добавлен метод принудительного отпускания всех кнопок
    fun resetButtons() {
        activePointers.clear()
        for (btn in pressedButtons) {
            emulator?.releaseButton(localPlayerIndex, btn)
            multiplayerCallback?.invoke(localPlayerIndex, btn, false)
        }
        pressedButtons.clear()
        invalidate()
    }
}
