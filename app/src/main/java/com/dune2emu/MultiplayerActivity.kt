package com.dune2emu

import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
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
    private var playerIndex = 0

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
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MultiplayerActivity, "Waiting for Player 2...\nPort: $PORT", Toast.LENGTH_LONG).show()
                }
                Log.d(TAG, "Host waiting on port $PORT...")
                clientSocket = serverSocket?.accept()
                Log.d(TAG, "Client connected!")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MultiplayerActivity, "Player 2 connected!", Toast.LENGTH_SHORT).show()
                }
                startGame()
                readRemoteInput()
            } catch (e: Exception) {
                Log.e(TAG, "Host error", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MultiplayerActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun startClient() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val hostIp = intent.getStringExtra("hostIp")
                if (hostIp.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MultiplayerActivity, "No host IP provided", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MultiplayerActivity, "Connecting to $hostIp...", Toast.LENGTH_SHORT).show()
                }
                clientSocket = Socket(hostIp, PORT)
                Log.d(TAG, "Connected to host!")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MultiplayerActivity, "Connected to host!", Toast.LENGTH_SHORT).show()
                }
                startGame()
                sendMyInput()
            } catch (e: Exception) {
                Log.e(TAG, "Client error", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MultiplayerActivity, "Connection failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private suspend fun startGame() = withContext(Dispatchers.Main) {
        val romPath = extractRom()
        val saveDir = File(filesDir, "saves").apply { mkdirs() }

        if (emulator.init(romPath, saveDir.absolutePath)) {
            // Используем новый метод setEmulator с callback
            gamepad.setEmulator(emulator, playerIndex) { player, button, pressed ->
                // Отправляем свои кнопки удалённому игроку
                sendInputToRemote(player, button.code, pressed)
            }
            emulator.start()
        } else {
            Toast.makeText(this@MultiplayerActivity, "Failed to load ROM", Toast.LENGTH_LONG).show()
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

    private fun sendInputToRemote(player: Int, buttonCode: Int, pressed: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val data = "$player,$buttonCode,${if (pressed) 1 else 0}\n"
                clientSocket?.getOutputStream()?.write(data.toByteArray())
                clientSocket?.getOutputStream()?.flush()
            } catch (e: Exception) {
                Log.e(TAG, "Send error", e)
            }
        }
    }

    private fun readRemoteInput() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val reader = BufferedReader(InputStreamReader(clientSocket?.getInputStream()))
                while (true) {
                    val line = reader.readLine() ?: break
                    val parts = line.split(",")
                    if (parts.size == 3) {
                        val remotePlayer = parts[0].toInt()
                        val buttonCode = parts[1].toInt()
                        val pressed = parts[2] == "1"
                        withContext(Dispatchers.Main) {
                            // Применяем кнопки удалённого игрока
                            emulator.setRemoteButton(remotePlayer, buttonCode, pressed)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Read error", e)
            }
        }
    }

    private suspend fun sendMyInput() {
        // Клиент постоянно слушает свой геймпад (коллбэк уже настроен в startGame)
        // и отправляет кнопки хосту через sendInputToRemote
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
