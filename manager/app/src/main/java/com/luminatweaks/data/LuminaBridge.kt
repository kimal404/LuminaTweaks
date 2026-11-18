/*
 * Copyright (C) 2026 kimal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.luminatweaks.data

import android.content.Context
import android.net.LocalServerSocket
import android.net.LocalSocket
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Zero-root transport for the two session files.
 *
 * The Manager cannot open `/data/adb` (mode `0700`, `adb_data_file`), and
 * spawning `su` for every read and write is expensive. This bridge flips the
 * connection direction: the app hosts a `LocalServerSocket` inside its own
 * private directory, while the root `lumina-binder` helper connects out to it
 * and performs the privileged file I/O on the app's behalf.
 *
 * The socket is opened once per process and kept warm, so screen entry and
 * every interaction cost a single write/read pair instead of a shell fork.
 */
object LuminaBridge {
    const val SOCKET_FILE_NAME = "lumina_bridge.sock"

    // A single SESSION_READ can return several hundred KB of JSON. The local
    // socket is fast, but a busy device or a cold helper can still take longer
    // than a couple of seconds end to end, and a timeout reads as "no data".
    private const val REQUEST_TIMEOUT_MS = 8_000L
    private const val SOCKET_TIMEOUT_MS = 8_000
    private const val HEADER_LIMIT = 256
    private const val MAX_PAYLOAD = 4 * 1024 * 1024
    private const val RECONNECT_DELAY_MS = 120L

    private val EMPTY = ByteArray(0)
    private val started = AtomicBoolean(false)
    private val requests = Channel<Request>(capacity = 32)

    private class Request(
        val command: String,
        val payload: ByteArray,
        val result: CompletableDeferred<ByteArray?>
    )

    fun start(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            acceptLoop(File(appContext.filesDir, SOCKET_FILE_NAME))
        }
    }

    suspend fun readSessions(context: Context): String? =
        request(context, "SESSION_READ")?.toString(Charsets.UTF_8)

    suspend fun writeSessions(context: Context, json: String): Boolean =
        request(context, "SESSION_WRITE", json.toByteArray(Charsets.UTF_8)) != null

    suspend fun readRecordApps(context: Context): String? =
        request(context, "RECORD_APPS_READ")?.toString(Charsets.UTF_8)

    suspend fun writeRecordApps(context: Context, json: String): Boolean =
        request(context, "RECORD_APPS_WRITE", json.toByteArray(Charsets.UTF_8)) != null

    suspend fun readAngleStatus(context: Context): String? =
        request(context, "ANGLE_STATUS")?.toString(Charsets.UTF_8)

    private suspend fun request(
        context: Context,
        command: String,
        payload: ByteArray = EMPTY
    ): ByteArray? {
        start(context)
        val pending = Request(command, payload, CompletableDeferred())
        if (requests.trySend(pending).isFailure) return null
        return withTimeoutOrNull(REQUEST_TIMEOUT_MS) { pending.result.await() }
    }

    private suspend fun acceptLoop(file: File) {
        while (currentCoroutineContext().isActive) {
            val server = try {
                if (file.exists()) file.delete()
                LocalServerSocket(file.absolutePath)
            } catch (_: Throwable) {
                delay(500)
                continue
            }

            try {
                val client = server.accept()
                serve(client)
            } catch (cancel: CancellationException) {
                runCatching { server.close() }
                throw cancel
            } catch (_: Throwable) {
            } finally {
                runCatching { server.close() }
                runCatching { file.delete() }
                failPending()
            }

            // A peer that disconnects immediately must not spin the loop.
            delay(RECONNECT_DELAY_MS)
        }
    }

    private suspend fun serve(client: LocalSocket) {
        try {
            runCatching { client.soTimeout = SOCKET_TIMEOUT_MS }
            val input = BufferedInputStream(client.inputStream)
            val output = client.outputStream

            while (currentCoroutineContext().isActive) {
                val pending = requests.receive()
                if (pending.result.isCompleted) continue
                try {
                    writeRequest(output, pending)
                    pending.result.complete(readResponse(input))
                } catch (failure: Throwable) {
                    pending.result.complete(null)
                    throw failure
                }
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Throwable) {
        } finally {
            runCatching { client.close() }
        }
    }

    private fun failPending() {
        while (true) {
            val pending = requests.tryReceive().getOrNull() ?: return
            pending.result.complete(null)
        }
    }

    private fun writeRequest(output: OutputStream, request: Request) {
        val header = "${request.command} ${request.payload.size}\n".toByteArray(Charsets.US_ASCII)
        output.write(header)
        if (request.payload.isNotEmpty()) output.write(request.payload)
        output.flush()
    }

    private fun readResponse(input: InputStream): ByteArray? {
        val header = readHeader(input) ?: return null
        val separator = header.indexOf(' ')
        val status = if (separator < 0) header.trim() else header.substring(0, separator)
        if (status != "OK") return null

        val length = if (separator < 0) 0 else header.substring(separator + 1).trim().toIntOrNull() ?: 0
        if (length == 0) return EMPTY
        if (length < 0 || length > MAX_PAYLOAD) return null

        val payload = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read = input.read(payload, offset, length - offset)
            if (read <= 0) throw IOException("bridge closed mid-payload")
            offset += read
        }
        return payload
    }

    private fun readHeader(input: InputStream): String? {
        val builder = StringBuilder(32)
        while (true) {
            val byte = input.read()
            if (byte == -1) return if (builder.isEmpty()) null else builder.toString()
            if (byte == '\n'.code) return builder.toString()
            if (builder.length >= HEADER_LIMIT) throw IOException("bridge header too long")
            builder.append(Char(byte))
        }
    }
}