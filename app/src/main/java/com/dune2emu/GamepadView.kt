package com.dune2emu

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View

class GamepadView(context: Context) : View(context) {
    
    private var emulator: EmulatorCore? = null
    
    private val dpadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 255, 255, 255)
    }
    
    private val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 100, 100, 200)
    }
    
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 24f
        textAlign = Paint.Align.CENTER
    }
    
    private data class Button(val rect: RectF, val label: String, val button: EmulatorCore.GenesisButton)
    private val buttons = mutableListOf<Button>()
    
    private val pressedButtons = mutableSetOf<EmulatorCore.GenesisButton>()
    
    fun setEmulator(emu: EmulatorCore) {
        this.emulator = emu
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        setupButtons(w.toFloat(), h.toFloat())
    }
    
    private fun setupButtons(w: Float, h: Float) {
        buttons.clear()
        
        // D-Pad
        val dpadCenterX = w * 0.2f
        val dpadCenterY = h * 0.75f
        val dpadSize = 120f
        
        buttons.add(Button(
            RectF(dpadCenterX - 30, dpadCenterY - dpadSize/2 - 30,
                  dpadCenterX + 30, dpadCenterY - dpadSize/2 + 30),
            "↑", EmulatorCore.GenesisButton.UP
        ))
        buttons.add(Button(
            RectF(dpadCenterX - 30, dpadCenterY + dpadSize/2 - 30,
                  dpadCenterX + 30, dpadCenterY + dpadSize/2 + 30),
            "↓", EmulatorCore.GenesisButton.DOWN
        ))
        buttons.add(Button(
            RectF(dpadCenterX - dpadSize/2 - 30, dpadCenterY - 30,
                  dpadCenterX - dpadSize/2 + 30, dpadCenterY + 30),
            "←", EmulatorCore.GenesisButton.LEFT
        ))
        buttons.add(Button(
            RectF(dpadCenterX + dpadSize/2 - 30, dpadCenterY - 30,
                  dpadCenterX + dpadSize/2 + 30, dpadCenterY + 30),
            "→", EmulatorCore.GenesisButton.RIGHT
        ))
        
        // Action buttons
        val btnSize = 70f
        val btnStartX = w * 0.8f
        val btnStartY = h * 0.7f
        
        buttons.add(Button(
            RectF(btnStartX, btnStartY, btnStartX + btnSize, btnStartY + btnSize),
            "A", EmulatorCore.GenesisButton.A
        ))
        buttons.add(Button(
            RectF(btnStartX - btnSize * 1.2f, btnStartY - btnSize * 0.5f,
                  btnStartX - btnSize * 1.2f + btnSize, btnStartY - btnSize * 0.5f + btnSize),
            "B", EmulatorCore.GenesisButton.B
        ))
        buttons.add(Button(
            RectF(btnStartX + btnSize * 1.2f, btnStartY - btnSize * 0.5f,
                  btnStartX + btnSize * 1.2f + btnSize, btnStartY - btnSize * 0.5f + btnSize),
            "C", EmulatorCore.GenesisButton.C
        ))
        
        // Start button
        buttons.add(Button(
            RectF(w/2 - 50, h - 80, w/2 + 50, h - 40),
            "START", EmulatorCore.GenesisButton.START
        ))
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        for (button in buttons) {
            val paint = if (pressedButtons.contains(button.button)) {
                buttonPaint.apply { alpha = 255 }
            } else {
                buttonPaint.apply { alpha = 150 }
            }
            
            canvas.drawRoundRect(button.rect, 15f, 15f, paint)
            canvas.drawText(button.label,
                button.rect.centerX(),
                button.rect.centerY() + 8,
                textPaint)
        }
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        val newPressed = mutableSetOf<EmulatorCore.GenesisButton>()
        
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                for (button in buttons) {
                    if (button.rect.contains(x, y)) {
                        newPressed.add(button.button)
                        
                        // Нажатие новой кнопки
                        if (!pressedButtons.contains(button.button)) {
                            emulator?.pressButton(0, button.button)
                        }
                    }
                }
                
                // Отпускание кнопок, которые больше не нажаты
                for (pressed in pressedButtons) {
                    if (!newPressed.contains(pressed)) {
                        emulator?.releaseButton(0, pressed)
                    }
                }
                
                pressedButtons.clear()
                pressedButtons.addAll(newPressed)
            }
            
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                for (pressed in pressedButtons) {
                    emulator?.releaseButton(0, pressed)
                }
                pressedButtons.clear()
            }
        }
        
        invalidate()
        return true
    }
}
