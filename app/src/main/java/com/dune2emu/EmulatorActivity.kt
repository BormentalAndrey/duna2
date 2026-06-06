package com.dune2emu

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class EmulatorActivity : AppCompatActivity(), SurfaceHolder.Callback {

    private lateinit var emulator: EmulatorCore
    private lateinit var surfaceView: SurfaceView
    private lateinit var gamepad: GamepadView

    private val TAG = "EmulatorActivity"
    private val ROM_NAME = "dune2.gen" // Имя файла в папке assets

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Настройка полноэкранного режима (Immersive Mode)
        setupFullScreen()

        // 2. Инициализация ядра эмулятора (без блокировки, только Kotlin-обертка)
        emulator = EmulatorCore()

        // 3. Программное создание UI (без XML)
        val layout = FrameLayout(this)

        surfaceView = SurfaceView(this)
        surfaceView.holder.addCallback(this)
        layout.addView(surfaceView)

        gamepad = GamepadView(this)
        // ИСПРАВЛЕНИЕ: Мгновенно привязываем эмулятор к геймпаду. 
        // Теперь касания экрана безопасны в любую секунду работы приложения.
        gamepad.setEmulator(emulator)
        layout.addView(gamepad)

        setContentView(layout)
    }

    private fun setupFullScreen() {
        supportActionBar?.hide()

        // Критично для Android 15+: разрешаем отрисовку под вырезом камеры.
        // Без этого SurfaceView может получать некорректные insets, 
        // что приводит к несовпадению размеров буфера и нативным крашам в C++.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        // Современный способ скрытия статус-бара и кнопок навигации (работает на Android 11+)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        // Как только Surface готова, привязываем ее к C++ и запускаем асинхронную загрузку
        if (emulator.setSurface(holder.surface)) {
            loadROMAndStart()
        } else {
            showError("Не удалось привязать Surface к ядру эмулятора")
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // Здесь можно передавать новые размеры экрана в ядро при повороте устройства
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        // Обязательно останавливаем нативный поток рендера перед уничтожением Surface,
        // чтобы избежать SIGSEGV при попытке EGL/OpenGL отрисовать кадр в уничтоженное окно.
        emulator.stop()
    }

    /**
     * Асинхронная загрузка с использованием Kotlin Coroutines.
     * Файловые операции выполняются в Dispatchers.IO, чтобы не блокировать UI.
     */
    private fun loadROMAndStart() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val romPath = extractRomFromAssets(ROM_NAME)

                if (romPath != null) {
                    val saveDir = File(filesDir, "saves").apply { mkdirs() }

                    // Возвращаемся в главный поток (Main) для обновления UI и запуска
                    withContext(Dispatchers.Main) {
                        if (emulator.init(romPath, saveDir.absolutePath)) {
                            // Привязка gamepad.setEmulator(emulator) отсюда убрана,
                            // так как она теперь выполняется мгновенно в onCreate()
                            emulator.start()
                        } else {
                            showError("Ошибка инициализации ядра эмулятора")
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        showError("Не удалось извлечь ROM-файл из архива")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Критическая ошибка при загрузке", e)
                withContext(Dispatchers.Main) {
                    showError("Ошибка загрузки: ${e.message}")
                }
            }
        }
    }

    /**
     * Копирует файл из assets во внутреннюю память приложения.
     * Оптимизировано: файл не перезаписывается, если он уже существует и имеет правильный размер.
     */
    private fun extractRomFromAssets(fileName: String): String? {
        val outFile = File(filesDir, fileName)

        try {
            assets.open(fileName).use { inputStream ->
                val assetSize = inputStream.available().toLong()

                // Проверка на наличие и совпадение размера файла
                if (outFile.exists() && outFile.length() == assetSize) {
                    Log.d(TAG, "ROM уже распакован: ${outFile.absolutePath}")
                    return outFile.absolutePath
                }

                Log.d(TAG, "Распаковка ROM из assets...")
                FileOutputStream(outFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            return outFile.absolutePath
        } catch (e: IOException) {
            Log.e(TAG, "Ошибка чтения/копирования assets", e)
            return null
        }
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onPause() {
        super.onPause()
        
        // ИСПРАВЛЕНИЕ: Отпускаем все кнопки перед уходом в фон
        if (::gamepad.isInitialized) {
            gamepad.resetButtons()
        }
        
        // Хорошая практика: приостанавливать эмуляцию, если пользователь свернул приложение
        emulator.stop()
    }

    override fun onResume() {
        super.onResume()
        // Восстанавливаем эмуляцию при возврате в приложение (если Surface уже готова)
        if (surfaceView.holder.surface.isValid) {
            emulator.start()
        }
    }

    override fun onDestroy() {
        emulator.stop()
        super.onDestroy()
    }
}
