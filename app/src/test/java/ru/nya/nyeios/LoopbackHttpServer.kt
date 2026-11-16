package ru.nya.nyeios

import java.io.BufferedInputStream
import java.io.IOException
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Minimal loopback HTTP/1.1 server for transport tests. android.jar has no
 * `com.sun.net.httpserver`, so the test suite ships its own tiny server instead of
 * pulling in a new dependency. One request per connection (`Connection: close`).
 */
internal class LoopbackHttpServer(
    handled: Map<String, (OutputStream) -> Unit>
) {

    private val routes: Map<String, (OutputStream) -> Unit> = handled
    private val running = AtomicBoolean(true)
    private val serverSocket = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
    private val worker: Thread
    private val lastMethods = java.util.concurrent.ConcurrentHashMap<String, String>()

    init {
        worker = thread(isDaemon = true, name = "loopback-http") {
            while (running.get()) {
                val socket = try {
                    serverSocket.accept()
                } catch (_: IOException) {
                    break
                }
                try {
                    handle(socket)
                } catch (_: IOException) {
                    // Client closed the connection early (deadline test, oversized-body test).
                } finally {
                    try {
                        socket.close()
                    } catch (_: IOException) {
                        // ignore
                    }
                }
            }
        }
    }

    val baseUrl: String = "http://127.0.0.1:${serverSocket.localPort}"

    fun close() {
        running.set(false)
        try {
            serverSocket.close()
        } catch (_: IOException) {
            // ignore
        }
        worker.join(1_000)
    }

    /** HTTP method of the last request seen for [path]; lets tests prove GET vs POST. */
    fun lastMethod(path: String): String? = lastMethods[path]

    private fun handle(socket: Socket) {
        socket.soTimeout = 5_000
        val input = BufferedInputStream(socket.getInputStream())
        val requestLine = readAsciiLine(input) ?: return
        val parts = requestLine.split(' ')
        val method = parts.getOrNull(0) ?: return
        val path = parts.getOrNull(1)?.substringBefore('?') ?: "/"
        lastMethods[path] = method
        while (true) {
            val line = readAsciiLine(input) ?: break
            if (line.isEmpty()) break
        }
        val route = routes[path] ?: { output -> writeResponse(output, 404, "not found") }
        route(socket.getOutputStream())
    }

    private fun readAsciiLine(input: BufferedInputStream): String? {
        val bytes = StringBuilder()
        while (true) {
            val b = input.read()
            if (b < 0) return if (bytes.isEmpty()) null else bytes.toString()
            if (b == '\n'.code) break
            if (b == '\r'.code) continue
            bytes.append(b.toChar())
        }
        return bytes.toString()
    }

    companion object {
        fun writeResponse(output: OutputStream, status: Int, body: String) {
            val bytes = body.toByteArray(Charsets.UTF_8)
            writeResponse(output, status, bytes)
        }

        fun writeResponse(output: OutputStream, status: Int, body: ByteArray) {
            val head = buildString {
                append("HTTP/1.1 ").append(status).append(" STATUS\r\n")
                append("Content-Type: application/json\r\n")
                append("Content-Length: ").append(body.size).append("\r\n")
                append("Connection: close\r\n\r\n")
            }
            output.write(head.toByteArray(Charsets.US_ASCII))
            output.write(body)
            output.flush()
        }
    }
}
