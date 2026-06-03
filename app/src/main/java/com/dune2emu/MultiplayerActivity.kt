package com.dune2emu

import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import java.io.*
import java.net.*

class MultiplayerActivity : AppCompatActivity(), SurfaceHolder.Callback {

    private lateinit var emulator: EmulatorCore
    private lateinit var surfaceView: SurfaceView
    private lateinit var gamepad: GamepadView
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var isHost = false
    private var playerIndex = 0 // 0 = host, 1 = client

    private val TAG = "Multiplayer"
    private val PORT = 12345

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        supportActionBar?.hide()

        val layout = FrameLayout(this)
        surfaceView = SurfaceView(this)
        surfaceView.holder.addCallback(this)
        layout.addView(surfaceView)

        gamepad = GamepadView(this)
        layout.addView(gamepad)
        setContentView(layout)

        emulator = EmulatorCore()
        isHost = intent.getBooleanExtra("isHost", true)
        playerIndex = if (isHost) 0 else 1

        if (isHost) startHost()
        else startClient()
    }

    private fun startHost() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(PORT)
                Log.d(TAG, "Waiting for client...")
                clientSocket = serverSocket?.accept()
                Log.d(TAG, "Client connected!")
                startGame()
                readClientInput()
            } catch (e: Exception) {
                Log.e(TAG, "Host error", e)
            }
        }
    }

    private fun startClient() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val hostIp = intent.getStringExtra("hostIp") ?: return@launch
                clientSocket = Socket(hostIp, PORT)
                Log.d(TAG, "Connected to host!")
                startGame()
                sendMyInput()
            } catch (e: Exception) {
                Log.e(TAG, "Client error", e)
            }
        }
    }

    private suspend fun startGame() = withContext(Dispatchers.Main) {
        val romPath = extractRom()
        val saveDir = File(filesDir, "saves").apply { mkdirs() }

        if (emulator.init(romPath, saveDir.absolutePath)) {
            gamepad.setEmulator(emulator) { player, button, pressed ->
                if (player == playerIndex) {
                    sendInputToPeer(player, button.code, pressed)
                }
                emulator.setButton(player, button.code, pressed)
            }
            emulator.start()
        }
    }

    private fun extractRom(): String {
        val outFile = File(filesDir, "dune2.gen")
        if (!outFile.exists()) {
            assets.open("dune2.gen").use { input ->
                FileOutputStream(outFile).use { output -> input.copyTo(output) }
            }
        }
        return outFile.absolutePath
    }

    private suspend fun sendInputToPeer(player: Int, button: Int, pressed: Boolean) {
        withContext(Dispatchers.IO) {
            try {
                val data = "$player,$button,${if (pressed) 1 else 0}\n"
                clientSocket?.getOutputStream()?.write(data.toByteArray())
            } catch (e: Exception) {
                Log.e(TAG, "Send error", e)
            }
        }
    }

    private fun readClientInput() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val reader = BufferedReader(InputStreamReader(clientSocket?.getInputStream()))
                while (true) {
                    val line = reader.readLine() ?: break
                    val parts = line.split(",")
                    if (parts.size == 3) {
                        val player = parts[0].toInt()
                        val button = parts[1].toInt()
                        val pressed = parts[2] == "1"
                        runOnUiThread {
                            emulator.setButton(player, button, pressed)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Read error", e)
            }
        }
    }

    private suspend fun sendMyInput() {
        // Отправляем свои кнопки хосту
        // Вызывается из gamepad callback
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        emulator.setSurface(holder.surface)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
    override fun surfaceDestroyed(holder: SurfaceHolder) {
        emulator.stop()
    }

    override fun onDestroy() {
        emulator.stop()
        try { serverSocket?.close() } catch (e: Exception) {}
        try { clientSocket?.close() } catch (e: Exception) {}
        super.onDestroy()
    }
}
