package com.dune2emu

import android.content.Context
import android.graphics.*
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.*
import java.net.*

class MultiplayerActivity : AppCompatActivity(), SurfaceHolder.Callback {

    private lateinit var emulator: EmulatorCore
    private lateinit var surfaceView: SurfaceView
    private lateinit var gamepad: GamepadView
    
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    
    // Единый поток для отправки кнопок и мьютекс для защиты от слияния пакетов
    private var clientOutput: DataOutputStream? = null
    private val outputMutex = Mutex()

    private var isHost = false
    private var playerIndex = 0

    private val TAG = "Multiplayer"
    private val PORT = 12345
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = false }

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
        } catch (e: Exception) { Log.e(TAG, "IP error", e) }
        return "Unknown"
    }

    // ==================== HOST (ИГРАЕТ + СТРИМИТ JPEG) ====================
    private fun startHost() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(PORT)
                val ip = getLocalIpAddress()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MultiplayerActivity, "Host IP: $ip", Toast.LENGTH_LONG).show()
                }
                clientSocket = serverSocket?.accept()
                clientSocket?.tcpNoDelay = true
                Log.d(TAG, "Client connected!")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MultiplayerActivity, "Player 2 Joined!", Toast.LENGTH_SHORT).show()
                    startGameHost()
                }
                launch { hostInputLoop() }
                hostVideoLoop()
            } catch (e: Exception) {
                Log.e(TAG, "Host error", e)
            }
        }
    }

    private suspend fun startGameHost() = withContext(Dispatchers.Main) {
        val romPath = extractRom()
        val saveDir = File(filesDir, "saves").apply { mkdirs() }
        if (emulator.init(romPath, saveDir.absolutePath)) {
            gamepad.setEmulator(emulator, 0)
            emulator.start()
        }
    }

    private suspend fun hostInputLoop() {
        try {
            // Читаем из сокета клиента
            val input = DataInputStream(BufferedInputStream(clientSocket?.getInputStream()))
            while (clientSocket?.isConnected == true) {
                // readUTF() сам дождется данных, не нужно использовать available()
                val msg = input.readUTF()
                val parts = msg.split(",")
                if (parts.size == 3) {
                    val button = parts[1].toInt()
                    val pressed = parts[2] == "1"
                    emulator.setRemoteButton(1, button, pressed)
                }
            }
        } catch (e: EOFException) {
            Log.d(TAG, "Client disconnected normally")
        } catch (e: Exception) { 
            Log.e(TAG, "Host input error", e) 
        }
    }

    private suspend fun hostVideoLoop() {
        val output = DataOutputStream(BufferedOutputStream(clientSocket?.getOutputStream()))
        val jpegStream = ByteArrayOutputStream()
        try {
            while (clientSocket?.isConnected == true) {
                val bitmap = withContext(Dispatchers.Main) { emulator.getCurrentFrameBitmap() }
                if (bitmap != null) {
                    jpegStream.reset()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 60, jpegStream)
                    val data = jpegStream.toByteArray()
                    output.writeInt(data.size)
                    output.write(data)
                    output.flush()
                    
                    bitmap.recycle() // Не забываем чистить память!
                }
                delay(16) // ~60 FPS
            }
        } catch (e: Exception) { Log.e(TAG, "Host video error", e) }
    }

    // ==================== CLIENT (ДИСПЛЕЙ + ГЕЙМПАД) ====================
    private fun startClient() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val hostIp = intent.getStringExtra("hostIp") ?: return@launch
                clientSocket = Socket(hostIp, PORT)
                clientSocket?.tcpNoDelay = true
                Log.d(TAG, "Connected to host!")
                
                // Инициализируем поток один раз при старте
                clientOutput = DataOutputStream(BufferedOutputStream(clientSocket?.getOutputStream()))

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MultiplayerActivity, "Connected!", Toast.LENGTH_SHORT).show()
                    gamepad.setEmulator(emulator, 1) { _, button, pressed ->
                        sendButtonToHost(button.code, pressed)
                    }
                }
                clientVideoLoop()
            } catch (e: Exception) {
                Log.e(TAG, "Client error", e)
            }
        }
    }

    private suspend fun clientVideoLoop() {
        try {
            val input = DataInputStream(BufferedInputStream(clientSocket?.getInputStream()))
            while (clientSocket?.isConnected == true) {
                val size = input.readInt()
                if (size > 0) {
                    val data = ByteArray(size)
                    input.readFully(data)
                    val bitmap = BitmapFactory.decodeByteArray(data, 0, size)
                    val canvas = surfaceView.holder.lockCanvas()
                    if (canvas != null && bitmap != null) {
                        canvas.drawBitmap(bitmap, null, Rect(0, 0, canvas.width, canvas.height), paint)
                        surfaceView.holder.unlockCanvasAndPost(canvas)
                    }
                    bitmap?.recycle() // Очищаем после отрисовки
                }
            }
        } catch (e: EOFException) {
            Log.d(TAG, "Host disconnected normally")
        } catch (e: Exception) { 
            Log.e(TAG, "Client video error", e) 
        }
    }

    private fun sendButtonToHost(buttonCode: Int, pressed: Boolean) {
        // Корутина выполняется быстро и не закрывает сокет
        lifecycleScope.launch(Dispatchers.IO) {
            outputMutex.withLock { // Защита от перекрытия пакетов
                try {
                    val msg = "1,$buttonCode,${if (pressed) 1 else 0}"
                    clientOutput?.writeUTF(msg)
                    clientOutput?.flush()
                } catch (e: Exception) { 
                    Log.e(TAG, "Send error", e) 
                }
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

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (isHost) emulator.setSurface(holder.surface)
    }
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
    override fun surfaceDestroyed(holder: SurfaceHolder) {}
    
    override fun onDestroy() {
        if (isHost) emulator.stop()
        try { clientOutput?.close() } catch (e: Exception) {}
        try { serverSocket?.close() } catch (e: Exception) {}
        try { clientSocket?.close() } catch (e: Exception) {}
        super.onDestroy()
    }
}
