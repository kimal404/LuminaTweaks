package com.luminatweaks.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.concurrent.thread

class LuminaToastService : Service() {

    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile
    private var isRunning = false
    private var workerThread: Thread? = null

    companion object {
        private const val PRIMARY_SOCKET = "/dev/socket/lumina_ipc.sock"
        private const val FALLBACK_SOCKET = "/data/local/tmp/lumina_ipc.sock"

        fun start(context: Context) {
            val intent = Intent(context, LuminaToastService::class.java)
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, LuminaToastService::class.java)
            context.stopService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) {
            isRunning = true
            workerThread = thread(name = "LuminaToastIpcClient") {
                listenToIpcSocket()
            }
        }
        return START_STICKY
    }

    private fun listenToIpcSocket() {
        while (isRunning) {
            var socket: LocalSocket? = null
            var reader: BufferedReader? = null

            try {
                socket = LocalSocket()
                var connected = false

                for (path in listOf(PRIMARY_SOCKET, FALLBACK_SOCKET)) {
                    try {
                        val address = LocalSocketAddress(path, LocalSocketAddress.Namespace.FILESYSTEM)
                        socket.connect(address)
                        connected = true
                        break
                    } catch (_: Exception) {
                    }
                }

                if (!connected) {
                    socket.close()
                    Thread.sleep(3000)
                    continue
                }

                reader = BufferedReader(InputStreamReader(socket.inputStream))

                while (isRunning) {
                    val line = reader.readLine() ?: break
                    val trimmed = line.trim()

                    if (trimmed.startsWith("TOAST ")) {
                        val toastText = trimmed.substring(6).trim()
                        if (toastText.isNotEmpty()) {
                            mainHandler.post {
                                Toast.makeText(applicationContext, toastText, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                try {
                    reader?.close()
                } catch (_: Exception) {}
                try {
                    socket?.close()
                } catch (_: Exception) {}
            }

            if (isRunning) {
                Thread.sleep(2000)
            }
        }
    }

    override fun onDestroy() {
        isRunning = false
        workerThread?.interrupt()
        workerThread = null
        super.onDestroy()
    }
}
