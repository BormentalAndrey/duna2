package com.dune2emu

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View

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

    private data class GameButton(
        val rect: RectF, val label: String,
        val button: EmulatorCore.GenesisButton, val color: Int
    )

    private val buttons = mutableListOf<GameButton>()
    private val pressedButtons = mutableSetOf<EmulatorCore.GenesisButton>()

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
        buttons.clear()

        val btnW = w * 0.07f
        val btnH = btnW
        val gap = w * 0.01f

        // === D-PAD ===
        val dpadCX = w * 0.16f
        val dpadCY = h * 0.72f
        val dpadR = w * 0.10f
        val dW = dpadR * 0.6f
        val dH = dpadR * 0.6f

        buttons.add(GameButton(RectF(dpadCX - dW/2, dpadCY - dpadR - dH/2, dpadCX + dW/2, dpadCY - dpadR + dH/2), "▲", EmulatorCore.GenesisButton.UP, neonYellow))
        buttons.add(GameButton(RectF(dpadCX - dW/2, dpadCY + dpadR - dH/2, dpadCX + dW/2, dpadCY + dpadR + dH/2), "▼", EmulatorCore.GenesisButton.DOWN, neonYellow))
        buttons.add(GameButton(RectF(dpadCX - dpadR - dW/2, dpadCY - dH/2, dpadCX - dpadR + dW/2, dpadCY + dH/2), "◄", EmulatorCore.GenesisButton.LEFT, neonYellow))
        buttons.add(GameButton(RectF(dpadCX + dpadR - dW/2, dpadCY - dH/2, dpadCX + dpadR + dW/2, dpadCY + dH/2), "►", EmulatorCore.GenesisButton.RIGHT, neonYellow))

        // === БЛОК КНОПОК (правый край, 2 ряда × 3 колонки) ===
        // Центр блока
        val bx = w * 0.82f
        val by = h * 0.68f

        // ВЕРХНИЙ РЯД: X, Y, Z
        val topY = by - btnH - gap * 2
        buttons.add(GameButton(RectF(bx - btnW - gap, topY, bx - gap, topY + btnH), "X", EmulatorCore.GenesisButton.X, neonPink))
        buttons.add(GameButton(RectF(bx, topY, bx + btnW, topY + btnH), "Y", EmulatorCore.GenesisButton.Y, neonOrange))
        buttons.add(GameButton(RectF(bx + btnW + gap, topY, bx + btnW*2 + gap, topY + btnH), "Z", EmulatorCore.GenesisButton.Z, neonPurple))

        // НИЖНИЙ РЯД: A, B, C
        val botY = by + gap * 2
        buttons.add(GameButton(RectF(bx - btnW - gap, botY, bx - gap, botY + btnH), "A", EmulatorCore.GenesisButton.A, neonRed))
        buttons.add(GameButton(RectF(bx, botY, bx + btnW, botY + btnH), "B", EmulatorCore.GenesisButton.B, neonBlue))
        buttons.add(GameButton(RectF(bx + btnW + gap, botY, bx + btnW*2 + gap, botY + btnH), "C", EmulatorCore.GenesisButton.C, neonGreen))

        // === START ===
        buttons.add(GameButton(RectF(w/2 - btnW*1.2f, h - btnH*1.6f, w/2 + btnW*1.2f, h - btnH*0.5f), "START", EmulatorCore.GenesisButton.START, neonWhite))

        // === MODE ===
        buttons.add(GameButton(RectF(w/2 + btnW*1.4f, h - btnH*1.3f, w/2 + btnW*2.6f, h - btnH*0.6f), "MODE", EmulatorCore.GenesisButton.MODE, neonGray))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(bgDark)

        for (btn in buttons) {
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

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val newPressed = mutableSetOf<EmulatorCore.GenesisButton>()
        val action = event.actionMasked

        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val x = event.getX(i)
                    val y = event.getY(i)
                    for (btn in buttons) {
                        if (btn.rect.contains(x, y)) newPressed.add(btn.button)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                for (i in 0 until event.pointerCount) {
                    if (i == event.actionIndex) continue
                    val x = event.getX(i)
                    val y = event.getY(i)
                    for (btn in buttons) {
                        if (btn.rect.contains(x, y)) newPressed.add(btn.button)
                    }
                }
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
}
