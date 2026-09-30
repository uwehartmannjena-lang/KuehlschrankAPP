package com.example.myapplication.web

import android.content.Context
import android.graphics.Bitmap
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.example.myapplication.FridgeViewModel
import com.example.myapplication.KassenzettelParser
import com.example.myapplication.data.FridgeItem
import com.example.myapplication.data.ShoppingItem
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Random
import java.util.UUID
import android.util.Log
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Base64
import com.example.myapplication.PdfReceiptHelper
import com.example.myapplication.ui.QrUtils
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.android.gms.tasks.Tasks
import java.io.ByteArrayOutputStream

object WebCompanionServer {

    private var serverSocket: ServerSocket? = null
    private var nsdManager: NsdManager? = null
    private var appContext: Context? = null

    var isRunning = false
        private set

    var currentPin = "1234"
        private set

    var persistentToken: String = UUID.randomUUID().toString()
        private set

    var pendingAuthRequest = false
        private set

    @Volatile
    private var isApprovedByPhone = false

    var approvedSessionToken: String? = null

    private val gson = Gson()

    fun generatePin(): String {
        currentPin = String.format(Locale.US, "%04d", Random().nextInt(10000))
        return currentPin
    }

    fun approveSession(token: String) {
        approvedSessionToken = token.trim()
        persistentToken = UUID.randomUUID().toString()
        isApprovedByPhone = true
        Log.d("WebCompanionServer", "Session approved for token: $approvedSessionToken")
    }

    fun approvePhoneConnection() {
        isApprovedByPhone = true
        pendingAuthRequest = false
    }

    fun rejectPhoneConnection() {
        isApprovedByPhone = false
        pendingAuthRequest = false
    }

    fun getLocalIpAddress(context: Context): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val ip = addr.hostAddress ?: ""
                        if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                            return ip
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return "127.0.0.1"
    }

    fun startServer(context: Context, viewModel: FridgeViewModel, port: Int = 8080) {
        if (isRunning) return
        appContext = context.applicationContext
        generatePin()
        registerNsdService(context, port)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                serverSocket = ServerSocket(port)
                isRunning = true

                while (isRunning) {
                    val socket = try { serverSocket?.accept() } catch (_: Exception) { null } ?: break
                    launch(Dispatchers.IO) {
                        handleConnection(socket, viewModel)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isRunning = false
            }
        }
    }

    fun stopServer() {
        isRunning = false
        unregisterNsdService()
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
    }

    private fun registerNsdService(context: Context, port: Int) {
        try {
            val serviceInfo = NsdServiceInfo().apply {
                serviceName = "frischeradar"
                serviceType = "_http._tcp."
                setPort(port)
            }
            nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
            nsdManager?.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, nsdListener)
        } catch (_: Exception) {}
    }

    private fun unregisterNsdService() {
        try {
            nsdManager?.unregisterService(nsdListener)
        } catch (_: Exception) {}
        nsdManager = null
    }

    private val nsdListener = object : NsdManager.RegistrationListener {
        override fun onRegistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {}
        override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {}
        override fun onServiceRegistered(serviceInfo: NsdServiceInfo?) {}
        override fun onServiceUnregistered(serviceInfo: NsdServiceInfo?) {}
    }

    private fun handleConnection(socket: Socket, viewModel: FridgeViewModel) {
        try {
            socket.soTimeout = 8000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val writer = PrintWriter(socket.getOutputStream(), true)

            val requestLine = reader.readLine() ?: return
            Log.d("WebCompanionServer", "Request: $requestLine")
            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0]
            val rawPath = parts[1]
            val path = rawPath.substringBefore("?")

            var contentLength = 0
            var contentType = ""
            val headers = mutableMapOf<String, String>()
            var line: String? = reader.readLine()
            while (!line.isNullOrBlank()) {
                val lower = line.lowercase()
                if (lower.startsWith("content-length:")) {
                    contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                } else if (lower.startsWith("content-type:")) {
                    contentType = line.substringAfter(":").trim()
                } else if (line.contains(":")) {
                    headers[line.substringBefore(":").trim().lowercase()] = line.substringAfter(":").trim()
                }
                line = reader.readLine()
            }

            var body = ""
            var bodyBytes: ByteArray? = null
            if (contentLength > 0) {
                if (path == "/api/upload-receipt") {
                    bodyBytes = ByteArray(contentLength)
                    var totalRead = 0
                    val inputStream = socket.getInputStream()
                    while (totalRead < contentLength) {
                        val read = inputStream.read(bodyBytes, totalRead, contentLength - totalRead)
                        if (read == -1) break
                        totalRead += read
                    }
                } else {
                    val buf = CharArray(contentLength)
                    var read = 0
                    while (read < contentLength) {
                        val r = reader.read(buf, read, contentLength - read)
                        if (r == -1) break
                        read += r
                    }
                    body = String(buf, 0, read)
                }
            }

            when {
                method == "GET" && path == "/" -> {
                    val html = getWebUiHtml()
                    sendResponse(writer, 200, "text/html; charset=utf-8", html)
                }

                method == "GET" && path == "/manifest.json" -> {
                    val manifest = """
                        {
                          "name": "Kühlschrank Profi 🍏 PC-Begleiter",
                          "short_name": "Kühlschrank Profi",
                          "start_url": "/",
                          "display": "standalone",
                          "background_color": "#2e7d32",
                          "theme_color": "#2e7d32",
                          "icons": [
                            {
                              "src": "data:image/svg+xml,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 100 100'><text y='.9em' font-size='90'>🍏</text></svg>",
                              "sizes": "192x192 512x512",
                              "type": "image/svg+xml"
                            }
                          ]
                        }
                    """.trimIndent()
                    sendResponse(writer, 200, "application/json", manifest)
                }

                method == "GET" && path == "/sw.js" -> {
                    val sw = """
                        self.addEventListener('install', (e) => { self.skipWaiting(); });
                        self.addEventListener('activate', (e) => { e.waitUntil(clients.claim()); });
                        self.addEventListener('fetch', (e) => { e.respondWith(fetch(e.request)); });
                    """.trimIndent()
                    sendResponse(writer, 200, "application/javascript", sw)
                }

                method == "GET" && path == "/api/ping" -> {
                    sendResponse(writer, 200, "application/json", """{"status":"ok","token":"$persistentToken"}""")
                }

                method == "GET" && path == "/api/session/qr" -> {
                    val queryToken = if (rawPath.contains("?sessionToken=")) rawPath.substringAfter("?sessionToken=") else ""
                    if (queryToken.isNotBlank()) {
                        val bitmap = QrUtils.generateQrCode(queryToken, 300)
                        if (bitmap != null) {
                            val stream = ByteArrayOutputStream()
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                            val byteArray = stream.toByteArray()
                            socket.getOutputStream().apply {
                                write("HTTP/1.1 200 OK\r\n".toByteArray())
                                write("Content-Type: image/png\r\n".toByteArray())
                                write("Content-Length: ${byteArray.size}\r\n".toByteArray())
                                write("Connection: close\r\n\r\n".toByteArray())
                                write(byteArray)
                                flush()
                            }
                            socket.close()
                            return
                        }
                    }
                    sendResponse(writer, 400, "text/plain", "Bad Request")
                }

                method == "GET" && path == "/api/auth/poll" -> {
                    val queryToken = if (rawPath.contains("?sessionToken=")) rawPath.substringAfter("?sessionToken=").trim() else ""
                    Log.d("WebCompanionServer", "Poll auth queryToken: $queryToken, approved: $approvedSessionToken")
                    if (queryToken.isNotBlank() && queryToken == approvedSessionToken) {
                        sendResponse(writer, 200, "application/json", """{"status":"ok","token":"$persistentToken"}""")
                    } else {
                        sendResponse(writer, 200, "application/json", """{"status":"pending"}""")
                    }
                }

                method == "POST" && path == "/api/auth" -> {
                    val params = try { gson.fromJson(body, Map::class.java) } catch (_: Exception) { null }
                    val pin = params?.get("pin")?.toString() ?: ""
                    val token = params?.get("token")?.toString() ?: ""
                    if (pin == currentPin || (token.isNotBlank() && token == persistentToken)) {
                        sendResponse(writer, 200, "application/json", """{"status":"ok","token":"$persistentToken"}""")
                    } else if (isApprovedByPhone) {
                        isApprovedByPhone = false
                        sendResponse(writer, 200, "application/json", """{"status":"ok","token":"$persistentToken"}""")
                    } else {
                        pendingAuthRequest = true
                        sendResponse(writer, 401, "application/json", """{"status":"error","message":"Freigabe erforderlich"}""")
                    }
                }

                method == "GET" && path == "/api/inventory" -> {
                    val items = runBlocking(Dispatchers.IO) { viewModel.allFridgeItems.first() }
                    val json = gson.toJson(items)
                    sendResponse(writer, 200, "application/json", json)
                }

                method == "POST" && path == "/api/inventory/add" -> {
                    val item = try { gson.fromJson(body, FridgeItem::class.java) } catch (_: Exception) { null }
                    if (item != null && item.name.isNotBlank()) {
                        viewModel.addItem(item)
                        sendResponse(writer, 200, "application/json", """{"status":"ok"}""")
                    } else {
                        sendResponse(writer, 400, "application/json", """{"status":"error"}""")
                    }
                }

                method == "POST" && path == "/api/inventory/update-qty" -> {
                    val params = try { gson.fromJson(body, Map::class.java) } catch (_: Exception) { null }
                    val id = params?.get("id")?.toString() ?: ""
                    val delta = (params?.get("delta") as? Double)?.toInt() ?: 1
                    val items = runBlocking(Dispatchers.IO) { viewModel.allFridgeItems.first() }
                    val target = items.find { it.id == id }
                    if (target != null) {
                        val newQty = (target.quantity + delta).coerceAtLeast(1)
                        viewModel.updateItem(target.copy(quantity = newQty))
                        sendResponse(writer, 200, "application/json", """{"status":"ok"}""")
                    } else {
                        sendResponse(writer, 404, "application/json", """{"status":"not_found"}""")
                    }
                }

                method == "POST" && path == "/api/inventory/freeze" -> {
                    val params = try { gson.fromJson(body, Map::class.java) } catch (_: Exception) { null }
                    val id = params?.get("id")?.toString() ?: ""
                    val items = runBlocking(Dispatchers.IO) { viewModel.allFridgeItems.first() }
                    val target = items.find { it.id == id }
                    if (target != null) {
                        viewModel.updateItem(target.copy(storageLocation = "Gefrierfach", isFrozen = true))
                        sendResponse(writer, 200, "application/json", """{"status":"ok"}""")
                    } else {
                        sendResponse(writer, 404, "application/json", """{"status":"not_found"}""")
                    }
                }

                method == "POST" && path == "/api/inventory/update-loc" -> {
                    val params = try { gson.fromJson(body, Map::class.java) } catch (_: Exception) { null }
                    val idString = params?.get("id")?.toString()
                    val loc = params?.get("storageLocation")?.toString() ?: "Kühlschrank"
                    if (idString != null) {
                        val items = runBlocking(Dispatchers.IO) { viewModel.allFridgeItems.first() }
                        items.find { it.id == idString }?.let { item ->
                            viewModel.updateItem(item.copy(storageLocation = loc))
                        }
                    }
                    sendResponse(writer, 200, "application/json", """{"status":"ok"}""")
                }

                method == "POST" && path == "/api/inventory/update-mhd" -> {
                    val params = try { gson.fromJson(body, Map::class.java) } catch (_: Exception) { null }
                    val id = params?.get("id")?.toString() ?: ""
                    val mhdStr = params?.get("expiryDate")?.toString() ?: ""
                    val items = runBlocking(Dispatchers.IO) { viewModel.allFridgeItems.first() }
                    val target = items.find { it.id == id }
                    if (target != null) {
                        val parsedTime = try {
                            if (mhdStr.isNotBlank()) SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).parse(mhdStr)?.time else null
                        } catch (_: Exception) { null }
                        viewModel.updateItem(target.copy(expiryDate = parsedTime))
                        sendResponse(writer, 200, "application/json", """{"status":"ok"}""")
                    } else {
                        sendResponse(writer, 404, "application/json", """{"status":"not_found"}""")
                    }
                }

                method == "POST" && path == "/api/inventory/consume" -> {
                    val params = try { gson.fromJson(body, Map::class.java) } catch (_: Exception) { null }
                    val id = params?.get("id")?.toString() ?: ""
                    val addToShopping = params?.get("addToShopping") as? Boolean ?: true
                    val items = runBlocking(Dispatchers.IO) { viewModel.allFridgeItems.first() }
                    val target = items.find { it.id == id }
                    if (target != null) {
                        viewModel.consumeItem(target)
                        if (addToShopping) {
                            viewModel.addToShoppingList(ShoppingItem(name = target.name, quantity = 1, unit = target.unit))
                        }
                        sendResponse(writer, 200, "application/json", """{"status":"ok"}""")
                    } else {
                        sendResponse(writer, 404, "application/json", """{"status":"not_found"}""")
                    }
                }

                method == "POST" && path == "/api/inventory/delete" -> {
                    val params = try { gson.fromJson(body, Map::class.java) } catch (_: Exception) { null }
                    val id = params?.get("id")?.toString() ?: ""
                    val items = runBlocking(Dispatchers.IO) { viewModel.allFridgeItems.first() }
                    val target = items.find { it.id == id }
                    if (target != null) {
                        viewModel.removeItem(target)
                        sendResponse(writer, 200, "application/json", """{"status":"ok"}""")
                    } else {
                        sendResponse(writer, 404, "application/json", """{"status":"not_found"}""")
                    }
                }

                method == "GET" && path == "/api/shopping" -> {
                    val items = runBlocking(Dispatchers.IO) { viewModel.allShoppingItems.first() }
                    val json = gson.toJson(items)
                    sendResponse(writer, 200, "application/json", json)
                }

                method == "POST" && path == "/api/shopping/add" -> {
                    val item = try { gson.fromJson(body, ShoppingItem::class.java) } catch (_: Exception) { null }
                    if (item != null && item.name.isNotBlank()) {
                        viewModel.addToShoppingList(item)
                        sendResponse(writer, 200, "application/json", """{"status":"ok"}""")
                    } else {
                        sendResponse(writer, 400, "application/json", """{"status":"error"}""")
                    }
                }

                method == "POST" && path == "/api/shopping/toggle" -> {
                    val params = try { gson.fromJson(body, Map::class.java) } catch (_: Exception) { null }
                    val id = params?.get("id")?.toString() ?: ""
                    val items = runBlocking(Dispatchers.IO) { viewModel.allShoppingItems.first() }
                    val target = items.find { it.id == id }
                    if (target != null) {
                        viewModel.toggleShoppingItem(target)
                        sendResponse(writer, 200, "application/json", """{"status":"ok"}""")
                    } else {
                        sendResponse(writer, 404, "application/json", """{"status":"not_found"}""")
                    }
                }

                method == "GET" && path == "/api/history" -> {
                    val history = runBlocking(Dispatchers.IO) { viewModel.dao.getAllPriceHistory().first() }
                    sendResponse(writer, 200, "application/json", gson.toJson(history))
                }

                method == "POST" && path == "/api/upload-receipt" -> {
                    var parsedText = ""
                    try {
                        if (bodyBytes != null && bodyBytes.isNotEmpty()) {
                            val tempFile = File(appContext!!.cacheDir, "temp_upload.pdf")
                            val success = parseMultipartFile(bodyBytes, contentType, tempFile)
                            val fileToProcess = if (success) tempFile else {
                                tempFile.writeBytes(bodyBytes)
                                tempFile
                            }

                            val lowerType = contentType.lowercase()
                            val isPdf = fileToProcess.readBytes().take(4).toByteArray().contentEquals(byteArrayOf(0x25, 0x50, 0x44, 0x46)) || lowerType.contains("pdf") || fileToProcess.name.endsWith(".pdf")

                            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                            val products = if (isPdf) {
                                val ocrTextChunks = mutableListOf<String>()
                                var pfd: ParcelFileDescriptor? = null
                                var renderer: PdfRenderer? = null
                                try {
                                    pfd = ParcelFileDescriptor.open(fileToProcess, ParcelFileDescriptor.MODE_READ_ONLY)
                                    if (pfd != null) {
                                        renderer = PdfRenderer(pfd)
                                        for (i in 0 until renderer.pageCount) {
                                            var page: PdfRenderer.Page? = null
                                            var bitmap: Bitmap? = null
                                            try {
                                                page = renderer.openPage(i)
                                                val aspectRatio = page.width.toFloat() / page.height.toFloat()
                                                val targetWidth = 1600
                                                val targetHeight = (targetWidth / aspectRatio).toInt().coerceAtLeast(1)
                                                bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

                                                val image = InputImage.fromBitmap(bitmap, 0)
                                                val visionText = Tasks.await(recognizer.process(image))
                                                if (visionText != null && visionText.text.isNotBlank()) {
                                                    ocrTextChunks.add(visionText.text)
                                                }
                                            } catch (e: Throwable) {
                                                e.printStackTrace()
                                            } finally {
                                                try { bitmap?.recycle() } catch (_: Exception) {}
                                                try { page?.close() } catch (_: Exception) {}
                                            }
                                        }
                                    }
                                } catch (e: Throwable) {
                                    e.printStackTrace()
                                } finally {
                                    try { renderer?.close() } catch (_: Exception) {}
                                    try { pfd?.close() } catch (_: Exception) {}
                                }
                                val fullText = ocrTextChunks.joinToString("\n")
                                KassenzettelParser.parseReceiptText(fullText)
                            } else {
                                val bitmap = BitmapFactory.decodeFile(fileToProcess.absolutePath)
                                if (bitmap != null) {
                                    val image = InputImage.fromBitmap(bitmap, 0)
                                    val visionText = Tasks.await(recognizer.process(image))
                                    KassenzettelParser.parseReceiptText(visionText.text)
                                } else {
                                    KassenzettelParser.parseReceiptText(fileToProcess.readText(Charsets.UTF_8))
                                }
                            }

                            val addedProducts = mutableListOf<Map<String, Any>>()
                            products.forEach { p ->
                                viewModel.addItem(
                                    FridgeItem(
                                        name = p.name,
                                        price = p.price,
                                        quantity = p.quantity,
                                        unit = p.unit,
                                        supermarket = p.supermarket ?: "Import"
                                    )
                                )
                                addedProducts.add(mapOf("name" to p.name, "quantity" to p.quantity, "price" to p.price, "category" to (p.category ?: "")))
                            }
                            sendResponse(writer, 200, "application/json", gson.toJson(addedProducts))
                            return
                        } else {
                            val params = try { gson.fromJson(body, Map::class.java) } catch (_: Exception) { null }
                            parsedText = params?.get("text")?.toString() ?: ""
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    val products = KassenzettelParser.parseReceiptText(parsedText)
                    val addedProducts = mutableListOf<Map<String, Any>>()
                    products.forEach { p ->
                        viewModel.addItem(
                            FridgeItem(
                                name = p.name,
                                price = p.price,
                                quantity = p.quantity,
                                unit = p.unit,
                                supermarket = p.supermarket ?: "Import"
                            )
                        )
                        addedProducts.add(mapOf("name" to p.name, "quantity" to p.quantity, "price" to p.price, "category" to (p.category ?: "")))
                    }
                    sendResponse(writer, 200, "application/json", gson.toJson(addedProducts))
                }

                method == "GET" && path == "/api/recipes" -> {
                    val inventory = runBlocking(Dispatchers.IO) { viewModel.allFridgeItems.first() }
                    val names = inventory.map { it.name }.filter { it.isNotBlank() }
                    val sampleIngredients = if (names.isNotEmpty()) names.take(4) else listOf("Kartoffeln", "Möhren", "Käse")
                    val recipes = listOf(
                        mapOf(
                            "title" to "Resten-Pfanne mit " + sampleIngredients.take(2).joinToString(" & "),
                            "ingredients" to sampleIngredients.joinToString(", "),
                            "instructions" to "1. Zutaten klein schneiden.\n2. In einer Pfanne mit etwas Öl 10-15 Min. anbraten.\n3. Nach Geschmack würzen und servieren!"
                        ),
                        mapOf(
                            "title" to "Herzhafter Auflauf mit " + (sampleIngredients.getOrNull(2) ?: "Gemüse"),
                            "ingredients" to sampleIngredients.joinToString(", "),
                            "instructions" to "1. Backofen auf 180°C Umluft vorheizen.\n2. Zutaten in eine Auflaufform geben.\n3. Mit Sahne/Käse überbacken und 25 Min. garen."
                        )
                    )
                    sendResponse(writer, 200, "application/json", gson.toJson(recipes))
                }

                else -> {
                    sendResponse(writer, 404, "text/plain", "Not Found")
                }
            }
            socket.close()
        } catch (_: Exception) {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun parseMultipartFile(bodyBytes: ByteArray, contentType: String, targetFile: File): Boolean {
        try {
            val boundary = contentType.substringAfter("boundary=").trim().removeSurrounding("\"")
            if (boundary.isEmpty()) return false
            val delimiter = "--$boundary".toByteArray(Charsets.ISO_8859_1)
            
            var idx = 0
            while (idx < bodyBytes.size) {
                val start = indexOf(bodyBytes, delimiter, idx)
                if (start == -1) break
                val headerStart = start + delimiter.size
                if (headerStart >= bodyBytes.size) break
                
                if (bodyBytes.size > headerStart + 1 && bodyBytes[headerStart] == '-'.code.toByte() && bodyBytes[headerStart+1] == '-'.code.toByte()) {
                    break
                }
                
                val headerEnd = indexOf(bodyBytes, "\r\n\r\n".toByteArray(Charsets.ISO_8859_1), headerStart)
                if (headerEnd == -1) break
                
                val headersStr = String(bodyBytes, headerStart, headerEnd - headerStart, Charsets.ISO_8859_1)
                val contentStart = headerEnd + 4
                
                val nextDelimiter = indexOf(bodyBytes, delimiter, contentStart)
                val contentEnd = if (nextDelimiter != -1) nextDelimiter - 2 else bodyBytes.size
                
                if (headersStr.contains("name=\"receipt\"") || headersStr.contains("filename=")) {
                    targetFile.writeBytes(bodyBytes.copyOfRange(contentStart, contentEnd))
                    return true
                }
                idx = contentStart
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    private fun indexOf(source: ByteArray, target: ByteArray, start: Int): Int {
        for (i in start..source.size - target.size) {
            var found = true
            for (j in target.indices) {
                if (source[i + j] != target[j]) {
                    found = false
                    break
                }
            }
            if (found) return i
        }
        return -1
    }

    private fun sendResponse(writer: PrintWriter, statusCode: Int, contentType: String, content: String) {
        val statusText = if (statusCode == 200) "OK" else if (statusCode == 401) "Unauthorized" else if (statusCode == 404) "Not Found" else "Bad Request"
        val bytes = content.toByteArray(Charsets.UTF_8)
        writer.print("HTTP/1.1 $statusCode $statusText\r\n")
        writer.print("Content-Type: $contentType\r\n")
        writer.print("Content-Length: ${bytes.size}\r\n")
        writer.print("Access-Control-Allow-Origin: *\r\n")
        writer.print("Connection: close\r\n\r\n")
        writer.print(content)
        writer.flush()
    }

    private fun getWebUiHtml(): String {
        return """
            <!DOCTYPE html>
            <html lang="de">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <link rel="manifest" href="/manifest.json">
                <title>Kühlschrank Profi 🍏 Premium PC-Begleiter</title>
                <style>
                    :root { --primary: #2e7d32; --primary-dark: #1b5e20; --bg: #f4f6f8; --card: #ffffff; --text: #1a1d1e; --accent: #4caf50; }
                    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; margin: 0; padding: 0; background-color: var(--bg); color: var(--text); display: flex; height: 100vh; overflow: hidden; }
                    
                    /* Sidebar Layout */
                    .sidebar { width: 280px; background: linear-gradient(180deg, var(--primary-dark), #124016); color: white; display: flex; flex-direction: column; flex-shrink: 0; box-shadow: 2px 0 10px rgba(0,0,0,0.1); z-index: 10; }
                    .sidebar-header { padding: 2rem 1.5rem; border-bottom: 1px solid rgba(255,255,255,0.1); margin-bottom: 1rem; }
                    .sidebar-header h1 { margin: 0; font-size: 1.5rem; font-weight: 800; display: flex; align-items: center; gap: 0.6rem; color: white; }
                    .sidebar nav { display: flex; flex-direction: column; gap: 0.4rem; padding: 0 1rem; }
                    .sidebar button { background: transparent; color: rgba(255,255,255,0.7); border: none; padding: 1rem 1.2rem; cursor: pointer; font-size: 1rem; font-weight: 500; border-radius: 8px; transition: all 0.2s; text-align: left; display: flex; align-items: center; gap: 0.8rem; }
                    .sidebar button:hover { background: rgba(255,255,255,0.1); color: white; }
                    .sidebar button.active { background: rgba(255,255,255,0.2); color: white; font-weight: 700; box-shadow: 0 2px 5px rgba(0,0,0,0.1); }
                    
                    .content-area { flex: 1; display: flex; flex-direction: column; overflow: hidden; }
                    .header-bar { background: white; padding: 1.2rem 2rem; display: flex; justify-content: space-between; align-items: center; box-shadow: 0 2px 8px rgba(0,0,0,0.05); z-index: 5; }
                    .header-bar h2 { margin: 0; color: var(--primary-dark); font-size: 1.4rem; }
                    
                    main { padding: 2rem; overflow-y: auto; flex: 1; }
                    
                    /* KPI Header Grid */
                    .kpi-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 1rem; margin-bottom: 1.5rem; }
                    .kpi-card { background: white; border-radius: 12px; padding: 1.2rem; box-shadow: 0 2px 8px rgba(0,0,0,0.04); display: flex; align-items: center; gap: 1rem; border: 1px solid #e0e0e0; }
                    .kpi-icon { font-size: 2rem; background: #e8f5e9; padding: 0.8rem; border-radius: 10px; }
                    .kpi-val { font-size: 1.5rem; font-weight: 800; color: var(--primary-dark); }
                    .kpi-label { font-size: 0.85rem; color: #666; font-weight: 500; }

                    .card { background: var(--card); border-radius: 12px; padding: 1.5rem; margin-bottom: 1.5rem; box-shadow: 0 2px 8px rgba(0,0,0,0.05); border: 1px solid #eef0f2; }
                    table { width: 100%; border-collapse: collapse; margin-top: 1rem; }
                    th, td { text-align: left; padding: 0.9rem; border-bottom: 1px solid #edf0f2; font-size: 0.95rem; }
                    th { background-color: #f8fafc; color: #475569; font-weight: 600; text-transform: uppercase; font-size: 0.8rem; letter-spacing: 0.05em; }
                    
                    .btn { background-color: var(--primary); color: white; border: none; padding: 0.6rem 1.2rem; border-radius: 8px; font-weight: 600; cursor: pointer; transition: background 0.2s; display: inline-flex; align-items: center; gap: 0.4rem; }
                    .btn:hover { background-color: var(--primary-dark); }
                    .btn-danger { background-color: #d32f2f; }
                    .btn-danger:hover { background-color: #b71c1c; }
                    .btn-sm { padding: 0.3rem 0.6rem; font-size: 0.85rem; border-radius: 6px; }
                    .btn-qty { background: #e0e0e0; color: #333; font-weight: bold; width: 28px; height: 28px; border-radius: 50%; padding: 0; justify-content: center; }

                    .badge { padding: 0.3rem 0.7rem; border-radius: 20px; font-size: 0.8rem; font-weight: 700; color: white; display: inline-block; }
                    .badge-green { background-color: #2e7d32; }
                    .badge-yellow { background-color: #f57c00; }
                    .badge-red { background-color: #d32f2f; }
                    .badge-gray { background-color: #78909c; }

                    .drop-zone { border: 2px dashed var(--primary); border-radius: 12px; padding: 4rem 2rem; text-align: center; color: var(--primary-dark); background: #f1f8e9; cursor: pointer; transition: all 0.3s ease; }
                    .drop-zone.dragover { background: #dcedc8; border-color: var(--primary-dark); transform: scale(1.02); }
                    .drop-zone input[type="file"] { display: none; }

                    .filter-bar { display: flex; gap: 0.8rem; margin-bottom: 1rem; flex-wrap: wrap; align-items: center; }
                    .filter-bar input { flex: 1; min-width: 250px; padding: 0.7rem 1rem; border: 1px solid #cbd5e1; border-radius: 8px; font-size: 0.95rem; }

                    #auth-modal { position: fixed; top: 0; left: 0; width: 100%; height: 100%; background: rgba(0,0,0,0.6); backdrop-filter: blur(4px); display: flex; justify-content: center; align-items: center; z-index: 1000; }
                    .modal-content { background: white; padding: 2.5rem; border-radius: 16px; width: 320px; text-align: center; box-shadow: 0 10px 25px rgba(0,0,0,0.2); }
                </style>
                <script>
                    if ('serviceWorker' in navigator) {
                        window.addEventListener('load', () => { navigator.serviceWorker.register('/sw.js'); });
                    }
                </script>
            </head>
            <body>
                <div class="sidebar">
                    <div class="sidebar-header">
                        <h1>🍏 Kühlschrank Profi</h1>
                        <span id="connection-status" style="font-size: 0.8rem; background: rgba(0,0,0,0.2); padding: 4px 10px; border-radius: 12px; margin-top: 10px; display: inline-block;">🟡 Auto-Connect wird geprüft...</span>
                    </div>
                    <nav>
                        <button id="tab-inv" class="active" onclick="showTab('inv', 'Vorrat & Frische')">📦 Vorrat & Frische</button>
                        <button id="tab-shop" onclick="showTab('shop', 'Einkaufsliste')">🛒 Einkaufsliste</button>
                        <button id="tab-receipt" onclick="showTab('receipt', 'Kassenbon-Import')">🧾 Kassenbon-Import</button>
                        <button id="tab-recipes" onclick="showTab('recipes', 'Resteverwerter')">🍳 Rezept-Ideen</button>
                        <button id="tab-hist" onclick="showTab('hist', 'Kauf- & Verbrauchs-Statistik')">📈 Statistik & Historie</button>
                    </nav>
                </div>
                <div class="content-area">
                    <div class="header-bar">
                        <h2 id="page-title">Vorrat & Frische</h2>
                        <div class="kpi-grid" style="margin-bottom: 0; display: flex; gap: 1rem;">
                            <div class="kpi-card" style="padding: 0.6rem 1rem; border-radius: 30px;">
                                <div class="kpi-icon" style="padding: 0.4rem; font-size: 1.2rem;">📦</div>
                                <div><div class="kpi-val" id="kpi-total" style="font-size: 1.1rem;">0</div></div>
                            </div>
                            <div class="kpi-card" style="padding: 0.6rem 1rem; border-radius: 30px;">
                                <div class="kpi-icon" style="padding: 0.4rem; font-size: 1.2rem;">💶</div>
                                <div><div class="kpi-val" id="kpi-value" style="font-size: 1.1rem;">0.00 €</div></div>
                            </div>
                            <div class="kpi-card" style="padding: 0.6rem 1rem; border-radius: 30px;">
                                <div class="kpi-icon" style="padding: 0.4rem; font-size: 1.2rem;">⚠️</div>
                                <div><div class="kpi-val" id="kpi-expiring" style="font-size: 1.1rem; color: #f57c00;">0</div></div>
                            </div>
                        </div>
                    </div>

                <div id="auth-modal">
                    <div class="modal-content">
                        <h2>Web-Client koppeln 🔒</h2>
                        <p style="color: #666; font-size: 0.9rem;">Scanne diesen QR-Code mit der Android-App ("Mit PC verbinden"):</p>
                        <div style="margin: 1.5rem 0;">
                            <img id="qr-code-img" src="" style="width: 200px; height: 200px; object-fit: contain;" alt="QR Code" />
                        </div>
                        <p id="pin-error" style="color: #666; font-weight: bold;">Warte auf Scan...</p>
                    </div>
                </div>

                <main id="app-content" style="display: none;">
                    <section id="section-inv">
                        <div class="card">
                            <h2>Echtzeit-Filter & Suche</h2>
                            <div class="filter-bar">
                                <input type="text" id="search-input" placeholder="🔍 Artikel suchen (Echtzeit)..." oninput="filterInventory()">
                                <button class="btn btn-sm" onclick="filterLocation('')">Alle</button>
                                <button class="btn btn-sm" onclick="filterLocation('Kühlschrank')">❄️ Kühlschrank</button>
                                <button class="btn btn-sm" onclick="filterLocation('Gefrierfach')">🧊 Gefrierfach</button>
                                <button class="btn btn-sm" onclick="filterLocation('Vorratskammer')">🥫 Vorratskammer</button>
                            </div>
                        </div>

                        <div class="card">
                            <h2>Schnell-Eingabe</h2>
                            <div style="display: grid; grid-template-columns: 2fr 1fr 1fr 1fr auto; gap: 0.8rem; align-items: end;">
                                <div>
                                    <label style="font-size: 0.85rem; font-weight: 600;">Name</label>
                                    <input type="text" id="add-name" placeholder="z. B. Herta Finesse" style="width: 100%; padding: 0.6rem; border: 1px solid #ccc; border-radius: 6px;">
                                </div>
                                <div>
                                    <label style="font-size: 0.85rem; font-weight: 600;">Menge</label>
                                    <input type="number" id="add-qty" value="1" style="width: 100%; padding: 0.6rem; border: 1px solid #ccc; border-radius: 6px;">
                                </div>
                                <div>
                                    <label style="font-size: 0.85rem; font-weight: 600;">Einheit</label>
                                    <input type="text" id="add-unit" value="Stk." style="width: 100%; padding: 0.6rem; border: 1px solid #ccc; border-radius: 6px;">
                                </div>
                                <div>
                                    <label style="font-size: 0.85rem; font-weight: 600;">Lagerort</label>
                                    <select id="add-loc" style="width: 100%; padding: 0.6rem; border: 1px solid #ccc; border-radius: 6px;">
                                        <option value="Kühlschrank">Kühlschrank</option>
                                        <option value="Gefrierfach">Gefrierfach</option>
                                        <option value="Vorratskammer">Vorratskammer</option>
                                    </select>
                                </div>
                                <button class="btn" onclick="addItem()">+ Hinzufügen</button>
                            </div>
                        </div>

                        <div class="card">
                            <h2>Vorrat mit MHD-Frischeampel</h2>
                            <table>
                                <thead>
                                    <tr>
                                        <th>Frische-Status</th>
                                        <th>Artikel</th>
                                        <th>Menge (+/-)</th>
                                        <th>Lagerort</th>
                                        <th>MHD Ablaufdatum</th>
                                        <th>Schnellaktionen</th>
                                    </tr>
                                </thead>
                                <tbody id="inventory-table"></tbody>
                            </table>
                        </div>
                    </section>

                    <section id="section-receipt" style="display: none;">
                        <div class="card">
                            <h2>🧾 Kassenbon Drag & Drop & Upload</h2>
                            <div id="drop-zone" class="drop-zone" ondragover="event.preventDefault(); this.classList.add('dragover');" ondragleave="this.classList.remove('dragover');" ondrop="handleFileDrop(event)" onclick="document.getElementById('file-input').click()">
                                <p style="font-size: 1.2rem; font-weight: bold; margin-bottom: 0.5rem;">📄 Ziehe deine Kassenbon PDF- oder Bild-Datei hierher oder klicke hier</p>
                                <input type="file" id="file-input" accept=".pdf,image/*" style="display:none;" onchange="handleFileDrop(event)">
                                <p style="color: #666; font-size: 0.9rem; margin-top: 1rem;">Oder füge den Bon-Text manuell ein (Klartext):</p>
                                <textarea id="receipt-text" rows="4" placeholder="Kassenbon Text hier einfügen (Strg+V)..." style="width: 100%; box-sizing: border-box; border-radius: 8px; padding: 0.8rem; border: 1px solid #ccc;" onclick="event.stopPropagation()"></textarea>
                                <br><br>
                                <button class="btn" onclick="event.stopPropagation(); uploadReceiptText()">⚡ Text manuell parsen</button>
                            </div>
                            <div id="receipt-results" style="margin-top: 1.5rem;"></div>
                        </div>
                    </section>

                    <section id="section-shop" style="display: none;">
                        <div class="card">
                            <h2>Einkaufsliste Schnell-Erfassung</h2>
                            <div style="display: grid; grid-template-columns: 3fr 1fr 1fr auto; gap: 0.8rem; align-items: center;">
                                <input type="text" id="shop-name" placeholder="Artikelname eingeben (z.B. Milch)..." style="padding: 0.7rem; border: 1px solid #ccc; border-radius: 8px;" onkeypress="if(event.key==='Enter') addShopItem()">
                                <input type="number" id="shop-qty" value="1" min="1" style="padding: 0.7rem; border: 1px solid #ccc; border-radius: 8px;" placeholder="Menge">
                                <input type="text" id="shop-unit" value="Stk." style="padding: 0.7rem; border: 1px solid #ccc; border-radius: 8px;" placeholder="Einheit">
                                <button class="btn" onclick="addShopItem()">+ Hinzufügen</button>
                            </div>
                        </div>
                        <div class="card">
                            <h2>Einkaufsliste</h2>
                            <table>
                                <thead>
                                    <tr>
                                        <th>Status</th>
                                        <th>Artikel</th>
                                        <th>Menge</th>
                                        <th>Aktion</th>
                                    </tr>
                                </thead>
                                <tbody id="shopping-table"></tbody>
                            </table>
                        </div>
                    </section>

                    <section id="section-recipes" style="display: none;">
                        <div class="card">
                            <h2>🍳 Resteverwerter Rezept-Vorschläge</h2>
                            <button class="btn" onclick="loadRecipes()">🔄 Rezepte anhand Bestand abrufen</button>
                            <div id="recipes-list" style="margin-top: 1.5rem; display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 1rem;"></div>
                        </div>
                    </section>

                    <section id="section-hist" style="display: none;">
                        <div class="card">
                            <h2>Preishistorie</h2>
                            <table>
                                <thead>
                                    <tr>
                                        <th>Datum</th>
                                        <th>Artikel</th>
                                        <th>Menge</th>
                                        <th>Preis</th>
                                    </tr>
                                </thead>
                                <tbody id="history-table"></tbody>
                            </table>
                        </div>
                    </section>
                </main>

                <script>
                    let rawInventory = [];
                    let currentLocationFilter = '';
                    let authToken = localStorage.getItem('companion_token') || '';

                    function apiFetch(url, options = {}) {
                        options.headers = options.headers || {};
                        if (authToken) {
                            options.headers['Authorization'] = 'Bearer ' + authToken;
                        }
                        return fetch(url, options);
                    }

                    document.addEventListener('DOMContentLoaded', () => {
                        const urlParams = new URLSearchParams(window.location.search);
                        const authQuery = urlParams.get('auth');
                        if (authQuery) {
                            authToken = authQuery;
                            localStorage.setItem('companion_token', authToken);
                        }

                        if (authToken) {
                            tryAutoConnect();
                        } else {
                            document.getElementById('app-content').style.display = 'none';
                            document.getElementById('auth-modal').style.display = 'flex';
                            
                            function uuidv4() {
                              return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
                                var r = Math.random() * 16 | 0, v = c == 'x' ? r : (r & 0x3 | 0x8);
                                return v.toString(16);
                              });
                            }
                            window.sessionToken = uuidv4();
                            document.getElementById('qr-code-img').src = '/api/session/qr?sessionToken=' + window.sessionToken;
                            pollAuth();
                        }
                        setInterval(checkHeartbeat, 3000);
                    });

                    async function pollAuth() {
                        try {
                            const res = await fetch('/api/auth/poll?sessionToken=' + window.sessionToken);
                            if (res.ok) {
                                const data = await res.json();
                                if (data.status === 'ok' && data.token) {
                                    authToken = data.token;
                                    localStorage.setItem('companion_token', authToken);
                                    document.getElementById('auth-modal').style.display = 'none';
                                    document.getElementById('app-content').style.display = 'block';
                                    updateStatusBanner(true);
                                    loadInventory();
                                    return; // Stop polling
                                }
                            }
                        } catch (e) {}
                        setTimeout(pollAuth, 2000);
                    }

                    async function tryAutoConnect() {
                        try {
                            const res = await fetch('/api/auth', {
                                method: 'POST',
                                headers: { 'Content-Type': 'application/json' },
                                body: JSON.stringify({ token: authToken })
                            });
                            if (res.ok) {
                                const data = await res.json();
                                if (data.token) {
                                    authToken = data.token;
                                    localStorage.setItem('companion_token', authToken);
                                }
                                document.getElementById('auth-modal').style.display = 'none';
                                document.getElementById('app-content').style.display = 'block';
                                updateStatusBanner(true);
                                loadInventory();
                            } else {
                                updateStatusBanner(false);
                            }
                        } catch (e) {
                            updateStatusBanner(false);
                        }
                    }

                    async function checkHeartbeat() {
                        try {
                            const res = await fetch('/api/ping');
                            if (res.ok) {
                                updateStatusBanner(true);
                                if (document.getElementById('app-content').style.display === 'none') {
                                    tryAutoConnect();
                                }
                            } else {
                                updateStatusBanner(false);
                            }
                        } catch (e) {
                            updateStatusBanner(false);
                        }
                    }

                    function updateStatusBanner(online) {
                        const status = document.getElementById('connection-status');
                        if (status) {
                            if (online) {
                                status.innerText = '🟢 Verbunden (frischeradar.local / Zero-Typing)';
                                status.style.background = 'rgba(255,255,255,0.3)';
                            } else {
                                status.innerText = '🟡 Warten auf Freigabe / Wiederverbindung...';
                                status.style.background = '#d32f2f';
                            }
                        }
                    }

                    async function authenticate() {
                        const pin = document.getElementById('pin-input').value;
                        const res = await fetch('/api/auth', {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({ pin: pin, token: authToken })
                        });
                        if (res.ok) {
                            const data = await res.json();
                            if (data.token) {
                                authToken = data.token;
                                localStorage.setItem('companion_token', authToken);
                            }
                            document.getElementById('auth-modal').style.display = 'none';
                            document.getElementById('app-content').style.display = 'block';
                            updateStatusBanner(true);
                            loadInventory();
                        } else {
                            document.getElementById('pin-error').style.display = 'block';
                        }
                    }

                    function showTab(tab, title) {
                        ['inv', 'receipt', 'shop', 'recipes', 'hist'].forEach(t => {
                            document.getElementById('section-' + t).style.display = t === tab ? 'block' : 'none';
                            document.getElementById('tab-' + t).classList.toggle('active', t === tab);
                        });
                        document.getElementById('page-title').innerText = title;
                        if (tab === 'inv') loadInventory();
                        if (tab === 'shop') loadShopping();
                        if (tab === 'recipes') loadRecipes();
                        if (tab === 'hist') loadHistory();
                    }

                    async function loadInventory() {
                        const res = await fetch('/api/inventory');
                        rawInventory = await res.json();
                        updateKpis(rawInventory);
                        renderInventory(rawInventory);
                    }

                    function updateKpis(data) {
                        const total = data.length;
                        const value = data.reduce((sum, item) => sum + ((item.price || 0) * (item.quantity || 1)), 0);
                        const now = new Date().getTime();
                        const expiring = data.filter(item => item.expiryDate && (item.expiryDate - now) <= (3 * 24 * 60 * 60 * 1000)).length;

                        document.getElementById('kpi-total').innerText = total;
                        document.getElementById('kpi-value').innerText = value.toFixed(2) + ' €';
                        document.getElementById('kpi-expiring').innerText = expiring;
                    }

                    function filterLocation(loc) {
                        currentLocationFilter = loc;
                        filterInventory();
                    }

                    function filterInventory() {
                        const query = document.getElementById('search-input').value.toLowerCase();
                        const filtered = rawInventory.filter(item => {
                            const matchesName = item.name.toLowerCase().includes(query);
                            const matchesLoc = !currentLocationFilter || item.storageLocation === currentLocationFilter;
                            return matchesName && matchesLoc;
                        });
                        renderInventory(filtered);
                    }

                    function renderInventory(data) {
                        const tbody = document.getElementById('inventory-table');
                        const now = new Date().getTime();

                        tbody.innerHTML = data.map(item => {
                            let badgeClass = 'badge-gray';
                            let badgeText = '⚪ Keine Angabe';
                            let dateVal = '';

                            if (item.expiryDate) {
                                const exp = new Date(item.expiryDate);
                                dateVal = exp.toISOString().split('T')[0];
                                const diffDays = Math.ceil((item.expiryDate - now) / (1000 * 60 * 60 * 24));
                                if (diffDays < 0) {
                                    badgeClass = 'badge-red';
                                    badgeText = '🔴 Abgelaufen (' + Math.abs(diffDays) + ' T. über)';
                                } else if (diffDays <= 3) {
                                    badgeClass = 'badge-yellow';
                                    badgeText = '🟡 Bald fällig (' + diffDays + ' T.)';
                                } else {
                                    badgeClass = 'badge-green';
                                    badgeText = '🟢 Haltbar (' + diffDays + ' T.)';
                                }
                            }

                            return `
                                <tr>
                                    <td><span class="badge ${'$'}{badgeClass}">${'$'}{badgeText}</span></td>
                                    <td><strong>${'$'}{item.name}</strong></td>
                                    <td>
                                        <button class="btn btn-qty" onclick="updateQty('${'$'}{item.id}', -1)">-</button>
                                        <span style="margin: 0 6px; font-weight: bold;">${'$'}{item.quantity} ${'$'}{item.unit}</span>
                                        <button class="btn btn-qty" onclick="updateQty('${'$'}{item.id}', 1)">+</button>
                                    </td>
                                    <td>
                                        <select onchange="updateLocation('${'$'}{item.id}', this.value)" style="padding: 0.3rem; border: 1px solid #ccc; border-radius: 4px;">
                                            <option value="Kühlschrank" ${'$'}{item.storageLocation === 'Kühlschrank' ? 'selected' : ''}>Kühlschrank</option>
                                            <option value="Gefrierfach" ${'$'}{item.storageLocation === 'Gefrierfach' ? 'selected' : ''}>Gefrierfach</option>
                                            <option value="Vorratskammer" ${'$'}{item.storageLocation === 'Vorratskammer' ? 'selected' : ''}>Vorratskammer</option>
                                        </select>
                                    </td>
                                    <td>
                                        <input type="date" value="${'$'}{dateVal}" onchange="updateMhd('${'$'}{item.id}', this.value)" style="padding: 0.3rem; border: 1px solid #ccc; border-radius: 4px;">
                                    </td>
                                    <td>
                                        <button class="btn btn-sm" onclick="consumeItem('${'$'}{item.id}')">Verbrauchen</button>
                                        <button class="btn btn-sm" style="background-color: #0288d1;" onclick="freezeItem('${'$'}{item.id}')">Einfrieren 🧊</button>
                                        <button class="btn btn-danger btn-sm" onclick="deleteItem('${'$'}{item.id}')">Löschen</button>
                                    </td>
                                </tr>
                            `;
                        }).join('');
                    }

                    async function updateQty(id, delta) {
                        await fetch('/api/inventory/update-qty', {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({ id: id, delta: delta })
                        });
                        loadInventory();
                    }

                    async function updateLocation(id, loc) {
                        await fetch('/api/inventory/update-loc', {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({ id: id, storageLocation: loc })
                        });
                        loadInventory();
                    }


                    async function freezeItem(id) {
                        await fetch('/api/inventory/freeze', {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({ id: id })
                        });
                        loadInventory();
                    }

                    async function updateMhd(id, dateStr) {
                        await fetch('/api/inventory/update-mhd', {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({ id: id, expiryDate: dateStr })
                        });
                        loadInventory();
                    }

                    async function addItem() {
                        const name = document.getElementById('add-name').value;
                        const qty = parseInt(document.getElementById('add-qty').value) || 1;
                        const unit = document.getElementById('add-unit').value || 'Stk.';
                        const loc = document.getElementById('add-loc').value;
                        if (!name) return;

                        await fetch('/api/inventory/add', {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({ name: name, quantity: qty, unit: unit, storageLocation: loc })
                        });
                        document.getElementById('add-name').value = '';
                        loadInventory();
                    }

                    async function consumeItem(id) {
                        await fetch('/api/inventory/consume', {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({ id: id, addToShopping: true })
                        });
                        loadInventory();
                    }

                    async function deleteItem(id) {
                        await fetch('/api/inventory/delete', {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({ id: id })
                        });
                        loadInventory();
                    }

                    async function handleFileDrop(e) {
                        e.preventDefault();
                        const dropZone = document.getElementById('drop-zone');
                        dropZone.classList.remove('dragover');
                        const files = e.dataTransfer.files || e.target.files;
                        if (files.length > 0) {
                            const file = files[0];
                            const div = document.getElementById('receipt-results');
                            div.innerHTML = "<p style=\"color:var(--primary-dark); font-weight:bold;\">📄 " + file.name + " (" + (file.size/1024).toFixed(1) + " KB) - Bon wird analysiert... ⏳</p>";

                            try {
                                const formData = new FormData();
                                formData.append('receipt', file);
                                const res = await apiFetch('/api/upload-receipt', {
                                    method: 'POST',
                                    body: formData
                                });
                                if (res.ok) {
                                    const data = await res.json();
                                    showReceiptResult(data);
                                } else {
                                    div.innerHTML = "<p style=\"color:red; font-weight:bold;\">Fehler beim Analysieren des Belegs!</p>";
                                }
                            } catch (err) {
                                div.innerHTML = "<p style=\"color:red; font-weight:bold;\">Netzwerkfehler beim Upload.</p>";
                            }
                        }
                    }

                    async function uploadReceiptText() {
                        const text = document.getElementById('receipt-text').value;
                        if (!text) return;
                        const div = document.getElementById('receipt-results');
                        div.innerHTML = '<p style="color:var(--primary-dark); font-weight:bold;">Verarbeite Text... ⏳</p>';

                        const res = await apiFetch('/api/upload-receipt', {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({ text: text })
                        });
                        showReceiptResult(await res.json());
                    }
                    
                    function showReceiptResult(data) {
                        const div = document.getElementById('receipt-results');
                        div.innerHTML = '<h3>Erkannte Artikel (' + data.length + '):</h3><ul>' +
                            data.map(p => '<li><strong>' + p.name + '</strong> - ' + p.quantity + 'x - ' + (p.price || 0).toFixed(2) + ' €</li>').join('') +
                            '</ul><p style="color: green; font-weight: bold;">✓ Erfolgreich in den Kühlschrank übernommen!</p>';
                        loadInventory();
                    }

                    async function loadShopping() {
                        const res = await fetch('/api/shopping');
                        const data = await res.json();
                        const tbody = document.getElementById('shopping-table');
                        tbody.innerHTML = data.map(item => `
                            <tr>
                                <td><input type="checkbox" ${'$'}{item.isChecked ? 'checked' : ''} onclick="toggleShop('${'$'}{item.id}')"></td>
                                <td><span style="${'$'}{item.isChecked ? 'text-decoration: line-through; color: #888;' : 'font-weight: bold;'}">${'$'}{item.name}</span></td>
                                <td>${'$'}{item.quantity} ${'$'}{item.unit}</td>
                                <td><button class="btn btn-sm" onclick="toggleShop('${'$'}{item.id}')">Umschalten</button></td>
                            </tr>
                        `).join('');
                    }

                    async function addShopItem() {
                        const name = document.getElementById('shop-name').value;
                        const qty = parseInt(document.getElementById('shop-qty').value) || 1;
                        const unit = document.getElementById('shop-unit').value || 'Stk.';
                        if (!name) return;
                        await fetch('/api/shopping/add', {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({ name: name, quantity: qty, unit: unit })
                        });
                        document.getElementById('shop-name').value = '';
                        loadShopping();
                    }

                    async function toggleShop(id) {
                        await fetch('/api/shopping/toggle', {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({ id: id })
                        });
                        loadShopping();
                    }

                    async function loadRecipes() {
                        const res = await fetch('/api/recipes');
                        const recipes = await res.json();
                        const div = document.getElementById('recipes-list');
                        div.innerHTML = recipes.map(r => `
                            <div class="card" style="border-left: 6px solid #2e7d32; margin: 0;">
                                <h3 style="margin-top: 0;">🍳 ${'$'}{r.title}</h3>
                                <p><strong>Zutaten aus Vorrat:</strong> ${'$'}{r.ingredients}</p>
                                <p style="white-space: pre-line; background: #f9f9f9; padding: 0.8rem; border-radius: 6px; font-size: 0.9rem;">${'$'}{r.instructions}</p>
                            </div>
                        `).join('');
                    }

                    async function loadHistory() {
                        const res = await fetch('/api/history');
                        const data = await res.json();
                        const tbody = document.getElementById('history-table');
                        tbody.innerHTML = data.map(item => {
                            const date = new Date(item.date).toLocaleDateString('de-DE');
                            return `
                                <tr>
                                    <td>${'$'}{date}</td>
                                    <td><strong>${'$'}{item.itemName}</strong></td>
                                    <td>${'$'}{item.quantity} ${'$'}{item.unit}</td>
                                    <td>${'$'}{item.price.toFixed(2)} €</td>
                                </tr>
                            `;
                        }).join('');
                    }
                </script>
            </body>
            </html>
        """.trimIndent()
    }
}
