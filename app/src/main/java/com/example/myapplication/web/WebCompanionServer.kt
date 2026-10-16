package com.example.myapplication.web

import android.content.Context
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
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Random
import java.util.UUID

object WebCompanionServer {

    private var serverSocket: ServerSocket? = null
    private var nsdManager: NsdManager? = null

    var isRunning = false
        private set

    var currentPin = "1234"
        private set

    var persistentToken: String = UUID.randomUUID().toString()
        private set

    private val gson = Gson()

    fun generatePin(): String {
        currentPin = String.format(Locale.US, "%04d", Random().nextInt(10000))
        return currentPin
    }

    fun getLocalIpAddress(context: Context): String {
        var fallbackIp = ""
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val ip = addr.hostAddress ?: ""
                        val name = intf.name.lowercase()
                        if (name.startsWith("wlan") || name.startsWith("wifi") || name.startsWith("swlan") || name.startsWith("ap")) {
                            return ip // Bevorzuge WLAN
                        }
                        if (ip.startsWith("192.168.")) {
                            fallbackIp = ip
                        } else if (fallbackIp.isEmpty() && (ip.startsWith("10.") || ip.startsWith("172."))) {
                            fallbackIp = ip
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        
        if (fallbackIp.isNotEmpty()) return fallbackIp
        return "127.0.0.1"
    }

    var currentPort = 8080
        private set

    fun startServer(context: Context, viewModel: FridgeViewModel, startPort: Int = 8080) {
        if (isRunning) return
        generatePin()

        CoroutineScope(Dispatchers.IO).launch {
            var portToTry = startPort
            var bound = false
            while (!bound && portToTry < startPort + 10) {
                try {
                    // Explizit an 0.0.0.0 binden, damit der Server von allen Geräten im WLAN erreichbar ist
                    serverSocket = ServerSocket(portToTry, 50, InetAddress.getByName("0.0.0.0"))
                    bound = true
                    currentPort = portToTry
                } catch (_: Exception) {
                    portToTry++
                }
            }
            
            if (!bound) {
                isRunning = false
                return@launch
            }
            
            isRunning = true
            registerNsdService(context, currentPort)

            try {
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

    private suspend fun handleConnection(socket: Socket, viewModel: FridgeViewModel) {
        try {
            socket.soTimeout = 8000
            val rawInput = socket.getInputStream()
            val out = socket.getOutputStream()

            // Header zeilenweise bis zur Leerzeile (\r\n\r\n oder \n\n) byte-sicher einlesen
            val headerBytes = java.io.ByteArrayOutputStream()
            var prev1 = -1
            var prev2 = -1
            var prev3 = -1
            var b = rawInput.read()
            while (b != -1) {
                headerBytes.write(b)
                if (prev3 == '\r'.code && prev2 == '\n'.code && prev1 == '\r'.code && b == '\n'.code) {
                    break
                }
                if (prev1 == '\n'.code && b == '\n'.code) {
                    break
                }
                prev3 = prev2
                prev2 = prev1
                prev1 = b
                b = rawInput.read()
            }
            if (headerBytes.size() == 0) return

            val headerStr = headerBytes.toString(Charsets.UTF_8.name())
            val headerLines = headerStr.lines()
            val requestLine = headerLines.firstOrNull()?.trim() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0]
            val rawPath = parts[1]
            val path = rawPath.substringBefore('?')
            val queryString = if (rawPath.contains('?')) rawPath.substringAfter('?') else ""

            var authHeader = ""
            var contentLength = 0
            for (line in headerLines) {
                val lowerLine = line.lowercase()
                if (lowerLine.startsWith("content-length:")) {
                    contentLength = lowerLine.substringAfter(":").trim().toIntOrNull() ?: 0
                }
                if (lowerLine.startsWith("authorization:")) {
                    authHeader = line.substringAfter(":").trim()
                }
            }

            var body = ""
            if (contentLength > 0) {
                val bodyBytes = ByteArray(contentLength)
                var totalRead = 0
                while (totalRead < contentLength) {
                    val count = rawInput.read(bodyBytes, totalRead, contentLength - totalRead)
                    if (count == -1) break
                    totalRead += count
                }
                body = String(bodyBytes, 0, totalRead, Charsets.UTF_8)
            }

            val isProtectedApi = path.startsWith("/api/") && path != "/api/ping" && path != "/api/auth" && path != "/api/verify-session"
            if (method != "OPTIONS" && isProtectedApi) {
                val tokenFromQuery = queryString.split("&").mapNotNull {
                    val kv = it.split("=")
                    if (kv.size == 2 && kv[0] == "token") kv[1] else null
                }.firstOrNull()
                val token = authHeader.removePrefix("Bearer ").trim().ifEmpty { tokenFromQuery ?: "" }
                if (token != persistentToken) {
                    sendResponse(out, 401, "application/json", """{"status":"unauthorized"}""")
                    socket.close()
                    return
                }
            }

            when {
                method == "OPTIONS" -> {
                    sendResponse(out, 204, "text/plain", "")
                }

                method == "GET" && (path == "/" || path == "/index.html") -> {
                    val html = getWebUiHtml()
                    sendResponse(out, 200, "text/html; charset=UTF-8", html)
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
                    sendResponse(out, 200, "application/json", manifest)
                }

                method == "GET" && path == "/sw.js" -> {
                    val sw = """
                        self.addEventListener('install', (e) => { self.skipWaiting(); });
                        self.addEventListener('activate', (e) => { e.waitUntil(clients.claim()); });
                        self.addEventListener('fetch', (e) => { e.respondWith(fetch(e.request)); });
                    """.trimIndent()
                    sendResponse(out, 200, "application/javascript", sw)
                }

                method == "GET" && path == "/api/ping" -> {
                    sendResponse(out, 200, "application/json", """{"status":"ok"}""")
                }

                method == "POST" && path == "/api/auth" -> {
                    val params = try { gson.fromJson(body, Map::class.java) } catch (_: Exception) { null }
                    val pin = params?.get("pin")?.toString() ?: ""
                    if (pin == currentPin) {
                        sendResponse(out, 200, "application/json", """{"status":"ok","token":"$persistentToken"}""")
                    } else {
                        sendResponse(out, 401, "application/json", """{"status":"error","message":"Falsche PIN"}""")
                    }
                }

                method == "POST" && path == "/api/verify-session" -> {
                    val params = try { gson.fromJson(body, Map::class.java) } catch (_: Exception) { null }
                    val token = params?.get("token")?.toString() ?: ""
                    if (token.isNotBlank() && token == persistentToken) {
                        sendResponse(out, 200, "application/json", """{"status":"ok","token":"$persistentToken"}""")
                    } else {
                        sendResponse(out, 401, "application/json", """{"status":"unauthorized"}""")
                    }
                }

                method == "GET" && path == "/api/inventory" -> {
                    val items = viewModel.allFridgeItems.first()
                    val json = gson.toJson(items)
                    sendResponse(out, 200, "application/json", json)
                }

                method == "POST" && path == "/api/inventory/add" -> {
                    val item = try { gson.fromJson(body, FridgeItem::class.java) } catch (_: Exception) { null }
                    if (item != null && item.name.isNotBlank()) {
                        viewModel.addItem(item)
                        sendResponse(out, 200, "application/json", """{"status":"ok"}""")
                    } else {
                        sendResponse(out, 400, "application/json", """{"status":"error"}""")
                    }
                }

                method == "POST" && path == "/api/inventory/update-mhd" -> {
                    val params = try { gson.fromJson(body, Map::class.java) } catch (_: Exception) { null }
                    val id = params?.get("id")?.toString() ?: ""
                    val mhdStr = params?.get("expiryDate")?.toString() ?: ""
                    val items = viewModel.allFridgeItems.first()
                    val target = items.find { it.id == id }
                    if (target != null) {
                        val parsedTime = try {
                            if (mhdStr.isNotBlank()) SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).parse(mhdStr)?.time else null
                        } catch (_: Exception) { null }
                        viewModel.updateItem(target.copy(expiryDate = parsedTime))
                        sendResponse(out, 200, "application/json", """{"status":"ok"}""")
                    } else {
                        sendResponse(out, 404, "application/json", """{"status":"not_found"}""")
                    }
                }

                method == "POST" && path == "/api/inventory/consume" -> {
                    val params = try { gson.fromJson(body, Map::class.java) } catch (_: Exception) { null }
                    val id = params?.get("id")?.toString() ?: ""
                    val items = viewModel.allFridgeItems.first()
                    val target = items.find { it.id == id }
                    if (target != null) {
                        viewModel.consumeItem(target)
                        sendResponse(out, 200, "application/json", """{"status":"ok"}""")
                    } else {
                        sendResponse(out, 404, "application/json", """{"status":"not_found"}""")
                    }
                }

                method == "POST" && path == "/api/inventory/delete" -> {
                    val params = try { gson.fromJson(body, Map::class.java) } catch (_: Exception) { null }
                    val id = params?.get("id")?.toString() ?: ""
                    val items = viewModel.allFridgeItems.first()
                    val target = items.find { it.id == id }
                    if (target != null) {
                        viewModel.removeItem(target)
                        sendResponse(out, 200, "application/json", """{"status":"ok"}""")
                    } else {
                        sendResponse(out, 404, "application/json", """{"status":"not_found"}""")
                    }
                }

                method == "GET" && path == "/api/shopping" -> {
                    val items = viewModel.allShoppingItems.first()
                    val json = gson.toJson(items)
                    sendResponse(out, 200, "application/json", json)
                }

                method == "POST" && path == "/api/shopping/add" -> {
                    val item = try { gson.fromJson(body, ShoppingItem::class.java) } catch (_: Exception) { null }
                    if (item != null && item.name.isNotBlank()) {
                        viewModel.addToShoppingList(item)
                        sendResponse(out, 200, "application/json", """{"status":"ok"}""")
                    } else {
                        sendResponse(out, 400, "application/json", """{"status":"error"}""")
                    }
                }

                method == "POST" && path == "/api/shopping/toggle" -> {
                    val params = try { gson.fromJson(body, Map::class.java) } catch (_: Exception) { null }
                    val id = params?.get("id")?.toString() ?: ""
                    val items = viewModel.allShoppingItems.first()
                    val target = items.find { it.id == id }
                    if (target != null) {
                        viewModel.toggleShoppingItem(target)
                        sendResponse(out, 200, "application/json", """{"status":"ok"}""")
                    } else {
                        sendResponse(out, 404, "application/json", """{"status":"not_found"}""")
                    }
                }

                method == "GET" && path == "/api/history" -> {
                    val history = viewModel.dao.getAllPriceHistory().first()
                    sendResponse(out, 200, "application/json", gson.toJson(history))
                }

                method == "POST" && path == "/api/upload-receipt" -> {
                    val params = try { gson.fromJson(body, Map::class.java) } catch (_: Exception) { null }
                    val textContent = params?.get("text")?.toString() ?: ""
                    val products = KassenzettelParser.parseReceiptText(textContent)
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
                    }
                    sendResponse(out, 200, "application/json", gson.toJson(products))
                }

                method == "GET" && path == "/api/recipes" -> {
                    val inventory = viewModel.allFridgeItems.first()
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
                    sendResponse(out, 200, "application/json", gson.toJson(recipes))
                }

                else -> {
                    sendResponse(out, 404, "text/plain", "Not Found")
                }
            }
            socket.close()
        } catch (e: Exception) {
            e.printStackTrace()
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun sendResponse(out: OutputStream, statusCode: Int, contentType: String, content: String) {
        val statusText = if (statusCode == 200) "OK" else if (statusCode == 204) "No Content" else if (statusCode == 401) "Unauthorized" else if (statusCode == 404) "Not Found" else "Bad Request"
        val bytes = content.toByteArray(Charsets.UTF_8)
        
        val header = StringBuilder()
        header.append("HTTP/1.1 $statusCode $statusText\r\n")
        if (statusCode != 204) {
            header.append("Content-Type: $contentType\r\n")
            header.append("Content-Length: ${bytes.size}\r\n")
        }
        header.append("Access-Control-Allow-Origin: *\r\n")
        header.append("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
        header.append("Access-Control-Allow-Headers: Content-Type, Authorization\r\n")
        header.append("Connection: close\r\n\r\n")
        
        out.write(header.toString().toByteArray(Charsets.UTF_8))
        if (statusCode != 204 && bytes.isNotEmpty()) {
            out.write(bytes)
        }
        out.flush()
    }

    private fun getWebUiHtml(): String {
        return """
            <!DOCTYPE html>
            <html lang="de">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <link rel="manifest" href="/manifest.json">
                <title>Kühlschrank Profi 🍏 PC-Begleiter</title>
                <style>
                    :root { --primary: #2e7d32; --bg: #f5f5f5; --card: #ffffff; --text: #212121; }
                    body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; margin: 0; padding: 0; background-color: var(--bg); color: var(--text); }
                    header { background-color: var(--primary); color: white; padding: 1rem 2rem; display: flex; justify-content: space-between; align-items: center; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
                    h1 { margin: 0; font-size: 1.5rem; }
                    nav button { background: none; border: none; color: white; padding: 0.5rem 1rem; cursor: pointer; font-size: 1rem; border-radius: 4px; }
                    nav button.active { background-color: rgba(255,255,255,0.2); font-weight: bold; }
                    main { max-width: 1000px; margin: 2rem auto; padding: 0 1rem; }
                    .card { background: var(--card); border-radius: 8px; padding: 1.5rem; margin-bottom: 1rem; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
                    table { width: 100%; border-collapse: collapse; margin-top: 1rem; }
                    th, td { text-align: left; padding: 0.75rem; border-bottom: 1px solid #ddd; }
                    th { background-color: #f8f9fa; }
                    .btn { background-color: var(--primary); color: white; border: none; padding: 0.5rem 1rem; border-radius: 4px; cursor: pointer; }
                    .btn-danger { background-color: #d32f2f; }
                    .btn-sm { padding: 0.25rem 0.5rem; font-size: 0.875rem; }
                    .badge { padding: 0.25rem 0.5rem; border-radius: 12px; font-size: 0.75rem; font-weight: bold; color: white; }
                    .badge-green { background-color: #2e7d32; }
                    .badge-yellow { background-color: #f57c00; }
                    .badge-red { background-color: #d32f2f; }
                    .badge-gray { background-color: #757575; }
                    .drop-zone { border: 2px dashed #2e7d32; border-radius: 8px; padding: 2rem; text-align: center; color: #2e7d32; background: #e8f5e9; cursor: pointer; }
                    .filter-bar { display: flex; gap: 0.5rem; margin-bottom: 1rem; flex-wrap: wrap; }
                    .filter-bar input { flex: 1; padding: 0.5rem; border: 1px solid #ccc; border-radius: 4px; }
                    #auth-modal { position: fixed; top: 0; left: 0; width: 100%; height: 100%; background: rgba(0,0,0,0.5); display: flex; justify-content: center; align-items: center; }
                    .modal-content { background: white; padding: 2rem; border-radius: 8px; width: 300px; text-align: center; }
                </style>
                <script>
                    if ('serviceWorker' in navigator) {
                        window.addEventListener('load', () => { navigator.serviceWorker.register('/sw.js'); });
                    }
                </script>
            </head>
            <body>
                <header>
                    <div>
                        <h1>🍏 Kühlschrank Profi PC-Begleiter</h1>
                        <span id="connection-status" style="font-size: 0.8rem; background: rgba(255,255,255,0.2); padding: 2px 8px; border-radius: 10px;">🟡 Verbindung wird hergestellt...</span>
                    </div>
                    <nav>
                        <button id="tab-inv" class="active" onclick="showTab('inv')">📦 Vorrat</button>
                        <button id="tab-receipt" onclick="showTab('receipt')">🧾 Kassenbon-Import</button>
                        <button id="tab-shop" onclick="showTab('shop')">🛒 Einkaufsliste</button>
                        <button id="tab-recipes" onclick="showTab('recipes')">🍳 Rezepte</button>
                        <button id="tab-hist" onclick="showTab('hist')">📈 Historie</button>
                    </nav>
                </header>

                <div id="auth-modal">
                    <div class="modal-content">
                        <h2>Verbindung freigeben</h2>
                        <p>Gib die 4-stellige PIN vom Smartphone ein:</p>
                        <div class="form-group">
                            <input type="password" id="pin-input" maxlength="4" placeholder="0000" style="text-align: center; font-size: 1.5rem; letter-spacing: 0.5rem;">
                        </div>
                        <button class="btn" onclick="authenticate()">Koppeln</button>
                        <p id="pin-error" style="color: red; display: none; margin-top: 1rem;">Falsche PIN!</p>
                    </div>
                </div>

                <main id="app-content" style="display: none;">
                    <section id="section-inv">
                        <div class="card">
                            <h2>Filter & Suche</h2>
                            <div class="filter-bar">
                                <input type="text" id="search-input" placeholder="🔍 Artikel suchen..." oninput="filterInventory()">
                                <button class="btn btn-sm" onclick="filterLocation('')">Alle</button>
                                <button class="btn btn-sm" onclick="filterLocation('Kühlschrank')">Kühlschrank</button>
                                <button class="btn btn-sm" onclick="filterLocation('Gefrierfach')">Gefrierfach</button>
                                <button class="btn btn-sm" onclick="filterLocation('Vorratskammer')">Vorratskammer</button>
                            </div>
                        </div>

                        <div class="card">
                            <h2>Artikel hinzufügen</h2>
                            <div style="display: grid; grid-template-columns: 2fr 1fr 1fr 1fr auto; gap: 0.5rem; align-items: end;">
                                <div class="form-group" style="margin: 0;">
                                    <label>Name</label>
                                    <input type="text" id="add-name" placeholder="z. B. Milch">
                                </div>
                                <div class="form-group" style="margin: 0;">
                                    <label>Menge</label>
                                    <input type="number" id="add-qty" value="1">
                                </div>
                                <div class="form-group" style="margin: 0;">
                                    <label>Einheit</label>
                                    <input type="text" id="add-unit" value="Stk.">
                                </div>
                                <div class="form-group" style="margin: 0;">
                                    <label>Lagerort</label>
                                    <select id="add-loc">
                                        <option value="Kühlschrank">Kühlschrank</option>
                                        <option value="Gefrierfach">Gefrierfach</option>
                                        <option value="Vorratskammer">Vorratskammer</option>
                                    </select>
                                </div>
                                <button class="btn" onclick="addItem()">Hinzufügen</button>
                            </div>
                        </div>

                        <div class="card">
                            <h2>Aktueller Vorrat (MHD-Ampel)</h2>
                            <table>
                                <thead>
                                    <tr>
                                        <th>Status</th>
                                        <th>Artikel</th>
                                        <th>Menge</th>
                                        <th>Lagerort</th>
                                        <th>Ablaufdatum (MHD)</th>
                                        <th>Aktionen</th>
                                    </tr>
                                </thead>
                                <tbody id="inventory-table"></tbody>
                            </table>
                        </div>
                    </section>

                    <section id="section-receipt" style="display: none;">
                        <div class="card">
                            <h2>🧾 Kassenbon Drag-and-Drop Import</h2>
                            <div id="drop-zone" class="drop-zone" ondragover="event.preventDefault()" ondrop="handleFileDrop(event)">
                                <p>📄 Ziehe eine Kassenbon PDF- oder Bild-Datei hierher oder füge den Bon-Text ein:</p>
                                <textarea id="receipt-text" rows="5" placeholder="Kassenbon Text hier hineinkopieren..." style="width: 100%; box-sizing: border-box;"></textarea>
                                <br><br>
                                <button class="btn" onclick="uploadReceiptText()">Kassenbon verarbeiten</button>
                            </div>
                            <div id="receipt-results" style="margin-top: 1rem;"></div>
                        </div>
                    </section>

                    <section id="section-shop" style="display: none;">
                        <div class="card">
                            <h2>Einkaufsartikel hinzufügen</h2>
                            <div style="display: flex; gap: 0.5rem;">
                                <input type="text" id="shop-name" placeholder="Artikelname" style="flex: 1;">
                                <button class="btn" onclick="addShopItem()">Auf Liste setzen</button>
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
                            <h2>🍳 Resteverwerter Rezeptideen</h2>
                            <button class="btn" onclick="loadRecipes()">Automatische Rezeptideen abrufen</button>
                            <div id="recipes-list" style="margin-top: 1rem;"></div>
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

                    const originalFetch = window.fetch;
                    window.fetch = async function(url, options = {}) {
                        if (url.startsWith('/api/') && url !== '/api/auth' && url !== '/api/ping' && url !== '/api/verify-session') {
                            options.headers = options.headers || {};
                            options.headers['Authorization'] = 'Bearer ' + authToken;
                        }
                        const res = await originalFetch(url, options);
                        if (res.status === 401 && url !== '/api/auth' && url !== '/api/verify-session') {
                            updateStatusBanner(false, '🔴 Sitzung abgelaufen. Bitte neu anmelden.');
                            localStorage.removeItem('companion_token');
                            authToken = '';
                            document.getElementById('auth-modal').style.display = 'flex';
                            document.getElementById('app-content').style.display = 'none';
                        }
                        return res;
                    };

                    document.addEventListener('DOMContentLoaded', () => {
                        const urlParams = new URLSearchParams(window.location.search);
                        const tokenFromUrl = urlParams.get('token');
                        if (tokenFromUrl) {
                            authToken = tokenFromUrl;
                            localStorage.setItem('companion_token', authToken);
                            window.history.replaceState({}, document.title, window.location.pathname);
                        }

                        if (authToken) {
                            verifySession();
                        }
                        setInterval(checkHeartbeat, 3000);
                    });

                    async function verifySession() {
                        try {
                            const res = await originalFetch('/api/verify-session', {
                                method: 'POST',
                                headers: { 'Content-Type': 'application/json' },
                                body: JSON.stringify({ token: authToken })
                            });
                            if (res.ok) {
                                document.getElementById('auth-modal').style.display = 'none';
                                document.getElementById('app-content').style.display = 'block';
                                updateStatusBanner(true, '🟢 Verbunden (Sitzung aktiv)');
                                loadInventory();
                            } else {
                                updateStatusBanner(false, '🔴 Sitzung ungültig.');
                                localStorage.removeItem('companion_token');
                                authToken = '';
                            }
                        } catch (e) {
                            updateStatusBanner(false, '🔴 Server nicht erreichbar.');
                        }
                    }

                    async function checkHeartbeat() {
                        try {
                            const res = await originalFetch('/api/ping');
                            if (res.ok) {
                                if (!authToken && document.getElementById('auth-modal').style.display !== 'none') {
                                    updateStatusBanner(true, '🟡 Verbunden (Warte auf PIN-Eingabe)');
                                } else if (authToken && document.getElementById('app-content').style.display === 'none') {
                                    verifySession();
                                } else if (authToken) {
                                    updateStatusBanner(true, '🟢 Verbunden (Sitzung aktiv)');
                                }
                            } else {
                                updateStatusBanner(false, '🔴 Verbindungsverlust. Bitte App prüfen.');
                            }
                        } catch (e) {
                            updateStatusBanner(false, '🔴 Server offline oder Verbindungsverlust.');
                        }
                    }

                    function updateStatusBanner(online, msg) {
                        const status = document.getElementById('connection-status');
                        if (status) {
                            status.innerText = msg;
                            status.style.background = online ? (msg.includes('Warte') ? '#f57c00' : 'rgba(255,255,255,0.3)') : '#d32f2f';
                            status.style.color = 'white';
                        }
                    }

                    async function authenticate() {
                        const pin = document.getElementById('pin-input').value;
                        const res = await originalFetch('/api/auth', {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({ pin: pin })
                        });
                        if (res.ok) {
                            const data = await res.json();
                            if (data.token) {
                                authToken = data.token;
                                localStorage.setItem('companion_token', authToken);
                            }
                            document.getElementById('auth-modal').style.display = 'none';
                            document.getElementById('app-content').style.display = 'block';
                            updateStatusBanner(true, '🟢 Verbunden (Sitzung aktiv)');
                            loadInventory();
                        } else {
                            document.getElementById('pin-error').style.display = 'block';
                        }
                    }

                    function showTab(tab) {
                        ['inv', 'receipt', 'shop', 'recipes', 'hist'].forEach(t => {
                            document.getElementById('section-' + t).style.display = t === tab ? 'block' : 'none';
                            document.getElementById('tab-' + t).classList.toggle('active', t === tab);
                        });
                        if (tab === 'inv') loadInventory();
                        if (tab === 'shop') loadShopping();
                        if (tab === 'recipes') loadRecipes();
                        if (tab === 'hist') loadHistory();
                    }

                    async function loadInventory() {
                        const res = await fetch('/api/inventory');
                        rawInventory = await res.json();
                        renderInventory(rawInventory);
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
                                    badgeText = '🔴 Abgelaufen';
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
                                    <td>${'$'}{item.quantity} ${'$'}{item.unit}</td>
                                    <td>${'$'}{item.storageLocation}</td>
                                    <td>
                                        <input type="date" value="${'$'}{dateVal}" onchange="updateMhd('${'$'}{item.id}', this.value)">
                                    </td>
                                    <td>
                                        <button class="btn btn-sm" onclick="consumeItem('${'$'}{item.id}')">Verbrauchen</button>
                                        <button class="btn btn-danger btn-sm" onclick="deleteItem('${'$'}{item.id}')">Löschen</button>
                                    </td>
                                </tr>
                            `;
                        }).join('');
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
                            body: JSON.stringify({ id: id })
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
                        const files = e.dataTransfer.files;
                        if (files.length > 0) {
                            const file = files[0];
                            if (file.name.endsWith('.pdf') || (file.type && file.type.includes('pdf'))) {
                                alert('PDF-Dateien bitte über den PC-Webservice (Port 8080) hochladen oder den Bon-Text direkt hineinkopieren.');
                                return;
                            }
                            const text = await file.text();
                            if (text.startsWith('%PDF')) {
                                alert('Binärer PDF-Code kann nicht direkt als Text verarbeitet werden.');
                                return;
                            }
                            document.getElementById('receipt-text').value = text;
                            uploadReceiptText();
                        }
                    }

                    async function uploadReceiptText() {
                        const text = document.getElementById('receipt-text').value;
                        if (!text || text.startsWith('%PDF')) {
                            alert('Bitte gib lesbaren Kassenbon-Text ein.');
                            return;
                        }

                        const res = await fetch('/api/upload-receipt', {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({ text: text })
                        });
                        const data = await res.json();
                        const div = document.getElementById('receipt-results');
                        div.innerHTML = '<h3>Erkannte Artikel (' + data.length + '):</h3><ul>' +
                            data.map(p => '<li><strong>' + p.name + '</strong> - ' + p.quantity + 'x - ' + p.price.toFixed(2) + ' €</li>').join('') +
                            '</ul><p style="color: green;">✓ Erfolgreich in den Kühlschrank übernommen!</p>';
                        loadInventory();
                    }

                    async function loadShopping() {
                        const res = await fetch('/api/shopping');
                        const data = await res.json();
                        const tbody = document.getElementById('shopping-table');
                        tbody.innerHTML = data.map(item => `
                            <tr>
                                <td><input type="checkbox" ${'$'}{item.isChecked ? 'checked' : ''} onclick="toggleShop('${'$'}{item.id}')"></td>
                                <td><span style="${'$'}{item.isChecked ? 'text-decoration: line-through;' : ''}">${'$'}{item.name}</span></td>
                                <td>${'$'}{item.quantity} ${'$'}{item.unit}</td>
                                <td><button class="btn btn-sm" onclick="toggleShop('${'$'}{item.id}')">Umschalten</button></td>
                            </tr>
                        `).join('');
                    }

                    async function addShopItem() {
                        const name = document.getElementById('shop-name').value;
                        if (!name) return;
                        await fetch('/api/shopping/add', {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({ name: name, quantity: 1, unit: 'Stk.' })
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
                            <div class="card" style="border-left: 4px solid #2e7d32;">
                                <h3>🍳 ${'$'}{r.title}</h3>
                                <p><strong>Zutaten:</strong> ${'$'}{r.ingredients}</p>
                                <p style="white-space: pre-line;">${'$'}{r.instructions}</p>
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
                                    <td>${'$'}{item.itemName}</td>
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
