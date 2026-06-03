package com.dune2emu

import android.graphics.Bitmap
import android.view.Surface
import java.nio.ByteBuffer

class EmulatorCore {
    private val bridge = RetroBridge()
    private var isRunning = false
    
    fun init(romPath: String, saveDir: String): Boolean {
        return bridge.initEmulator(romPath, saveDir)
    }
    
    fun setSurface(surface: Surface): Boolean {
        return bridge.setSurface(surface)
    }
    
    fun start() {
        if (!isRunning) {
            bridge.startEmulation()
            isRunning = true
        }
    }
    
    fun stop() {
        if (isRunning) {
            bridge.stopEmulation()
            isRunning = false
        }
    }
    
    fun pressButton(player: Int, button: GenesisButton) {
        bridge.setButtonState(player, button.code, true)
    }
    
    fun releaseButton(player: Int, button: GenesisButton) {
        bridge.setButtonState(player, button.code, false)
    }
    
    fun setRemoteButton(player: Int, buttonCode: Int, pressed: Boolean) {
        bridge.setButtonState(player, buttonCode, pressed)
    }
    
    fun saveState(slot: Int): Boolean = bridge.saveState(slot)
    fun loadState(slot: Int): Boolean = bridge.loadState(slot)
    
    // ==================== СТРИМИНГ ВИДЕО ====================
    
    /**
     * Получить текущий кадр эмулятора как Bitmap.
     * Используется хостом для отправки клиенту по сети.
     */
    fun getCurrentFrameBitmap(): Bitmap? {
        val rawData = bridge.getVideoFrame() ?: return null
        val bitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.RGB_565)
        bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(rawData))
        return bitmap
    }
    
    enum class GenesisButton(val code: Int) {
        UP(0), DOWN(1), LEFT(2), RIGHT(3),
        A(4), B(5), C(6), START(7),
        X(8), Y(9), Z(10), MODE(11)
    }
}
