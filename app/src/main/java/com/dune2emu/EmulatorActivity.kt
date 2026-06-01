package com.dune2emu

import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream

class EmulatorActivity : AppCompatActivity(), SurfaceHolder.Callback {
    
    private lateinit var emulator: EmulatorCore
    private lateinit var surfaceView: SurfaceView
    private lateinit var gamepad: GamepadView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        supportActionBar?.hide()
        
        // Создание layout
        val layout = FrameLayout(this)
        
        surfaceView = SurfaceView(this)
        surfaceView.holder.addCallback(this)
        layout.addView(surfaceView)
        
        gamepad = GamepadView(this)
        layout.addView(gamepad)
        
        setContentView(layout)
        
        emulator = EmulatorCore()
    }
    
    private fun loadROM() {
        try {
            // Копирование ROM из assets
            val romFile = File(filesDir, "dune2.bin")
            if (!romFile.exists()) {
                assets.open("dune2.bin").use { input ->
                    FileOutputStream(romFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            
            // Создание директории сохранений
            val saveDir = File(filesDir, "saves").apply { mkdirs() }
            
            // Инициализация эмулятора
            if (emulator.init(romFile.absolutePath, saveDir.absolutePath)) {
                gamepad.setEmulator(emulator)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    override fun surfaceCreated(holder: SurfaceHolder) {
        if (emulator.setSurface(holder.surface)) {
            loadROM()
            emulator.start()
        }
    }
    
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
    
    override fun surfaceDestroyed(holder: SurfaceHolder) {
        emulator.stop()
    }
    
    override fun onDestroy() {
        emulator.stop()
        super.onDestroy()
    }
}
