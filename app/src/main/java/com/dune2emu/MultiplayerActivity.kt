package com.dune2emu

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Bundle
import android.text.format.Formatter
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
    private var syncJob: Job? = null

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

        if (isHost) startHost() else startClient()
    }

    private fun getLocalIpAddress(): String {
        try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ip = wifiManager.connectionInfo.ipAddress
            if (ip != 0) return Formatter.formatIpAddress(ip)
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val ni = interfaces.nextElement()
                val addresses = ni.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: "Unknown"
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get IP error", e)
        }
        return "Unknown"
    }

    private fun startHost() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(PORT)
                val ip = getLocalIpAddress()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MultiplayerActivity, "Your IP: $ip\nPort: $PORT", Toast.LENGTH_LONG).show()
                }
                clientSocket = serverSocket?.accept()
                Log.d(TAG, "Client connected!")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MultiplayerActivity, "Player 2 connected!", Toast.LENGTH_SHORT).show()
                }
                startGame()
                // Host: эмулирует и отправляет сейвстейты
                runHostSyncLoop()
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
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MultiplayerActivity, "Connected!", Toast.LENGTH_SHORT).show()
                }
                startGame()
                // Client: получает сейвстейты и рендерит, отправляет кнопки
                runClientSyncLoop()
            } catch (e: Exception) {
                Log.e(TAG, "Client error", e)
            }
        }
    }

    private suspend fun startGame() = withContext(Dispatchers.Main) {
        val romPath = extractRom()
        val saveDir = File(filesDir, "saves").apply { mkdirs() }

        if (emulator.init(romPath, saveDir.absolutePath)) {
            gamepad.setEmulator(emulator, playerIndex) { player, button, pressed ->
                sendInputToRemote(player, button.code, pressed)
            }
            emulator.start()
        } else {
            Toast.makeText(this@MultiplayerActivity, "Failed to load ROM", Toast.LENGTH_LONG).show()
        }
    }

    // ==================== HOST SYNC LOOP ====================
    private suspend fun runHostSyncLoop() {
        withContext(Dispatchers.IO) {
            try {
                val output = DataOutputStream(BufferedOutputStream(clientSocket?.getOutputStream()))
                val input = DataInputStream(BufferedInputStream(clientSocket?.getInputStream()))
                val stateFile = File(filesDir, "sync.state")

                while (clientSocket?.isConnected == true) {
                    // 1. Читаем кнопки клиента (неблокирующе)
                    if (input.available() > 0) {
                        val msg = input.readUTF()
                        val parts = msg.split(",")
                        if (parts.size == 3) {
                            val player = parts[0].toInt()
                            val button = parts[1].toInt()
                            val pressed = parts[2] == "1"
                            withContext(Dispatchers.Main) {
                                emulator.setRemoteButton(player, button, pressed)
                            }
                        }
                    }

                    // 2. Сохраняем сейвстейт
                    withContext(Dispatchers.Main) {
                        emulator.saveState(0)
                    }

                    // 3. Отправляем сейвстейт клиенту
                    if (stateFile.exists()) {
                        val data = stateFile.readBytes()
                        output.writeInt(data.size)
                        output.write(data)
                        output.flush()
                    }

                    Thread.sleep(16) // ~60 FPS
                }
            } catch (e: Exception) {
                Log.e(TAG, "Host sync error: ${e.message}")
            }
        }
    }

    // ==================== CLIENT SYNC LOOP ====================
    private suspend fun runClientSyncLoop() {
        withContext(Dispatchers.IO) {
            try {
                val input = DataInputStream(BufferedInputStream(clientSocket?.getInputStream()))
                val output = DataOutputStream(BufferedOutputStream(clientSocket?.getOutputStream()))
                val stateFile = File(filesDir, "sync.state")

                while (clientSocket?.isConnected == true) {
                    // 1. Получаем сейвстейт от хоста
                    val size = input.readInt()
                    val data = ByteArray(size)
                    input.readFully(data)
                    stateFile.writeBytes(data)

                    // 2. Загружаем сейвстейт (клиент не эмулирует сам!)
                    withContext(Dispatchers.Main) {
                        emulator.loadState(0)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Client sync error: ${e.message}")
            }
        }
    }

    // ==================== ОТПРАВКА КНОПОК ====================
    private fun sendInputToRemote(player: Int, buttonCode: Int, pressed: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val msg = "$player,$buttonCode,${if (pressed) 1 else 0}"
                val output = DataOutputStream(clientSocket?.getOutputStream())
                output.writeUTF(msg)
                output.flush()
            } catch (e: Exception) {
                Log.e(TAG, "Send error: ${e.message}")
            }
        }
    }

    // ==================== ROM ====================
    private fun extractRom(): String {
        val outFile = File(filesDir, "dune2.gen")
        if (!outFile.exists()) {
            assets.open("dune2.gen").use { input ->
                FileOutputStream(outFile).use { output -> input.copyTo(output) }
            }
        }
        return outFile.absolutePath
    }

    // ==================== SURFACE ====================
    override fun surfaceCreated(holder: SurfaceHolder) {
        emulator.setSurface(holder.surface)
    }
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
    override fun surfaceDestroyed(holder: SurfaceHolder) {
        emulator.stop()
    }

    override fun onDestroy() {
        syncJob?.cancel()
        emulator.stop()
        try { serverSocket?.close() } catch (e: Exception) {}
        try { clientSocket?.close() } catch (e: Exception) {}
        super.onDestroy()
    }
}
