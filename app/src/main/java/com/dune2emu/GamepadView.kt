package com.dune2emu

import android.content.Context
import android.graphics.*
import android.util.Log
import android.view.MotionEvent
import android.view.View

class GamepadView(context: Context) : View(context) {

    private var emulator: EmulatorCore? = null
    private var multiplayerCallback: ((Int, EmulatorCore.GenesisButton, Boolean) -> Unit)? = null
    private var localPlayerIndex: Int = 0

    // === ТЕХПАНК НОЕН ПАЛИТРА ===
    private val neonYellow = Color.argb(200, 255, 255, 0)
    private val neonRed    = Color.argb(200, 255, 50, 50)
    private val neonBlue   = Color.argb(200, 50, 150, 255)
    private val neonGreen  = Color.argb(200, 50, 255, 50)
    private val neonPink   = Color.argb(200, 255, 100, 255)
    private val neonOrange = Color.argb(200, 255, 180, 0)
    private val neonPurple = Color.argb(200, 180, 50, 255)
    private val neonWhite  = Color.argb(200, 200, 200, 255)
    private val neonGray   = Color.argb(150, 150, 150, 150)
    private val bgDark     = Color.argb(80, 0, 0, 0)

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 18f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.MONOSPACE
        color = Color.WHITE
        setShadowLayer(8f, 0f, 0f, Color.CYAN)
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
        val btnSize = w * 0.08f

        // === D-PAD (жёлтый) ===
        val dpadCX = w * 0.18f
        val dpadCY = h * 0.72f
        val dpadR = w * 0.11f
        val bW = dpadR * 0.65f
        val bH = dpadR * 0.65f

        buttons.add(GameButton(RectF(dpadCX - bW/2, dpadCY - dpadR - bH/2, dpadCX + bW/2, dpadCY - dpadR + bH/2), "▲", EmulatorCore.GenesisButton.UP, neonYellow))
        buttons.add(GameButton(RectF(dpadCX - bW/2, dpadCY + dpadR - bH/2, dpadCX + bW/2, dpadCY + dpadR + bH/2), "▼", EmulatorCore.GenesisButton.DOWN, neonYellow))
        buttons.add(GameButton(RectF(dpadCX - dpadR - bW/2, dpadCY - bH/2, dpadCX - dpadR + bW/2, dpadCY + bH/2), "◄", EmulatorCore.GenesisButton.LEFT, neonYellow))
        buttons.add(GameButton(RectF(dpadCX + dpadR - bW/2, dpadCY - bH/2, dpadCX + dpadR + bW/2, dpadCY + bH/2), "►", EmulatorCore.GenesisButton.RIGHT, neonYellow))

        // === A (красный), B (синий), C (зелёный) ===
        val abcX = w * 0.82f
        val abcY = h * 0.65f
        buttons.add(GameButton(RectF(abcX, abcY, abcX + btnSize, abcY + btnSize), "A", EmulatorCore.GenesisButton.A, neonRed))
        buttons.add(GameButton(RectF(abcX - btnSize*1.3f, abcY - btnSize*0.7f, abcX - btnSize*0.3f, abcY + btnSize*0.3f), "B", EmulatorCore.GenesisButton.B, neonBlue))
        buttons.add(GameButton(RectF(abcX + btnSize*1.3f, abcY - btnSize*0.7f, abcX + btnSize*2.3f, abcY + btnSize*0.3f), "C", EmulatorCore.GenesisButton.C, neonGreen))

        // === X (розовый), Y (оранжевый), Z (фиолетовый) ===
        val xyzY = abcY - btnSize * 1.6f
        buttons.add(GameButton(RectF(abcX, xyzY, abcX + btnSize, xyzY + btnSize), "X", EmulatorCore.GenesisButton.X, neonPink))
        buttons.add(GameButton(RectF(abcX - btnSize*1.3f, xyzY - btnSize*0.7f, abcX - btnSize*0.3f, xyzY + btnSize*0.3f), "Y", EmulatorCore.GenesisButton.Y, neonOrange))
        buttons.add(GameButton(RectF(abcX + btnSize*1.3f, xyzY - btnSize*0.7f, abcX + btnSize*2.3f, xyzY + btnSize*0.3f), "Z", EmulatorCore.GenesisButton.Z, neonPurple))

        // === START (белый) ===
        buttons.add(GameButton(RectF(w/2 - btnSize, h - btnSize*1.5f, w/2 + btnSize, h - btnSize*0.3f), "START", EmulatorCore.GenesisButton.START, neonWhite))

        // === MODE (серый) ===
        buttons.add(GameButton(RectF(w/2 - btnSize*2.2f, h - btnSize*1.2f, w/2 - btnSize*0.8f, h - btnSize*0.5f), "MODE", EmulatorCore.GenesisButton.MODE, neonGray))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(bgDark)

        for (btn in buttons) {
            val pressed = pressedButtons.contains(btn.button)
            borderPaint.color = btn.color
            borderPaint.alpha = if (pressed) 255 else 80
            fillPaint.color = btn.color
            fillPaint.alpha = if (pressed) 60 else 15

            val rx = btn.rect.width() * 0.2f
            val ry = btn.rect.height() * 0.2f
            canvas.drawRoundRect(btn.rect, rx, ry, fillPaint)
            canvas.drawRoundRect(btn.rect, rx, ry, borderPaint)

            if (pressed) {
                borderPaint.alpha = 40
                borderPaint.strokeWidth = 8f
                canvas.drawRoundRect(btn.rect, rx, ry, borderPaint)
                borderPaint.strokeWidth = 3f
            }

            textPaint.color = if (pressed) Color.WHITE else Color.argb(180, 255, 255, 255)
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
