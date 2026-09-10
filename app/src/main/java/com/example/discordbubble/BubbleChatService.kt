package com.example.discordbubble

import android.app.*
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class BubbleChatService : Service() {

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var panelView: View? = null
    private var isPanelOpen = false

    private lateinit var client: OkHttpClient
    private var webSocket: WebSocket? = null
    private var heartbeatHandler: Handler? = null
    private var heartbeatInterval: Long = 0
    private var sequence: Int? = null

    private var token = ""
    private var channelId = ""
    private var unreadCount = 0

    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        const val CHANNEL_ID = "bubble_chat_service"
        const val NOTIF_ID = 1
        const val GATEWAY_URL = "wss://gateway.discord.gg/?v=10&encoding=json"
        // GUILD_MESSAGES (1<<9) + MESSAGE_CONTENT (1<<15)
        const val INTENTS = (1 shl 9) or (1 shl 15)
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        token = intent?.getStringExtra("token") ?: token
        channelId = intent?.getStringExtra("channelId") ?: channelId

        startForegroundNotification()
        if (bubbleView == null) showBubble()
        connectGateway()

        return START_STICKY
    }

    // ---------- Foreground notification ----------

    private fun startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Bubble Chat Service", NotificationManager.IMPORTANCE_MIN
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Discord Bubble Chat aktif")
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

        startForeground(NOTIF_ID, notification)
    }

    // ---------- Bubble UI ----------

    private fun showBubble() {
        val inflater = LayoutInflater.from(this)
        bubbleView = inflater.inflate(R.layout.bubble_layout, null)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 300

        windowManager.addView(bubbleView, params)

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        bubbleView?.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > 8 || Math.abs(dy) > 8) isDragging = true
                    params.x = initialX + dx
                    params.y = initialY + dy
                    windowManager.updateViewLayout(bubbleView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) toggleChatPanel(params)
                    true
                }
                else -> false
            }
        }
    }

    private fun toggleChatPanel(bubbleParams: WindowManager.LayoutParams) {
        if (isPanelOpen) {
            closePanel()
        } else {
            openPanel(bubbleParams)
        }
    }

    private fun openPanel(bubbleParams: WindowManager.LayoutParams) {
        val inflater = LayoutInflater.from(this)
        panelView = inflater.inflate(R.layout.chat_panel, null)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = bubbleParams.x
        params.y = bubbleParams.y + 70

        windowManager.addView(panelView, params)
        isPanelOpen = true
        unreadCount = 0
        updateBadge()

        panelView?.findViewById<ImageView>(R.id.btnClosePanel)?.setOnClickListener {
            closePanel()
        }

        panelView?.findViewById<ImageView>(R.id.btnSend)?.setOnClickListener {
            val et = panelView?.findViewById<EditText>(R.id.etMessage)
            val text = et?.text.toString().trim()
            if (text.isNotEmpty()) {
                sendMessageToDiscord(text)
                et?.setText("")
            }
        }
    }

    private fun closePanel() {
        panelView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) {}
        }
        panelView = null
        isPanelOpen = false
    }

    private fun updateBadge() {
        val badge = bubbleView?.findViewById<TextView>(R.id.bubbleBadge)
        mainHandler.post {
            if (unreadCount > 0 && !isPanelOpen) {
                badge?.visibility = View.VISIBLE
                badge?.text = if (unreadCount > 9) "9+" else unreadCount.toString()
            } else {
                badge?.visibility = View.GONE
            }
        }
    }

    // ---------- Chat rendering ----------

    private fun appendMessageToPanel(author: String, content: String) {
        val container = panelView?.findViewById<LinearLayout>(R.id.chatContainer) ?: return
        val scroll = panelView?.findViewById<ScrollView>(R.id.chatScroll)

        val tv = TextView(this)
        tv.text = "$author: $content"
        tv.setTextColor(resources.getColor(R.color.white, theme))
        tv.textSize = 13f
        tv.setPadding(4, 6, 4, 6)
        container.addView(tv)

        scroll?.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    // ---------- Discord REST: send message ----------

    private fun sendMessageToDiscord(content: String) {
        val body = JSONObject().put("content", content).toString()
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://discord.com/api/v10/channels/$channelId/messages")
            .addHeader("Authorization", "Bot $token")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                // gagal kirim, bisa ditambah retry/toast kalau perlu
            }
            override fun onResponse(call: Call, response: Response) {
                response.close()
            }
        })
    }

    // ---------- Discord Gateway: receive messages ----------

    private fun connectGateway() {
        val request = Request.Builder().url(GATEWAY_URL).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {

            override fun onMessage(ws: WebSocket, text: String) {
                handleGatewayPayload(text)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                stopHeartbeat()
                // coba reconnect setelah 5 detik
                mainHandler.postDelayed({ connectGateway() }, 5000)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                stopHeartbeat()
                mainHandler.postDelayed({ connectGateway() }, 5000)
            }
        })
    }

    private fun handleGatewayPayload(raw: String) {
        val json = JSONObject(raw)
        val op = json.getInt("op")

        if (!json.isNull("s")) sequence = json.optInt("s")

        when (op) {
            10 -> { // Hello
                val data = json.getJSONObject("d")
                heartbeatInterval = data.getLong("heartbeat_interval")
                startHeartbeat()
                identify()
            }
            0 -> { // Dispatch
                val type = json.optString("t")
                if (type == "MESSAGE_CREATE") {
                    val d = json.getJSONObject("d")
                    val msgChannelId = d.optString("channel_id")
                    if (msgChannelId == channelId) {
                        val author = d.getJSONObject("author").optString("username", "unknown")
                        val content = d.optString("content", "")
                        val isBot = d.getJSONObject("author").optBoolean("bot", false)
                        if (!isBot && content.isNotEmpty()) {
                            if (isPanelOpen) {
                                mainHandler.post { appendMessageToPanel(author, content) }
                            } else {
                                unreadCount++
                                updateBadge()
                            }
                        }
                    }
                }
            }
            9 -> { // Invalid session
                mainHandler.postDelayed({ identify() }, 3000)
            }
            7 -> { // Reconnect requested
                webSocket?.close(1000, "reconnect")
                connectGateway()
            }
        }
    }

    private fun identify() {
        val identify = JSONObject()
        identify.put("op", 2)
        val d = JSONObject()
        d.put("token", token)
        d.put("intents", INTENTS)
        val props = JSONObject()
        props.put("os", "android")
        props.put("browser", "bubblechat")
        props.put("device", "bubblechat")
        d.put("properties", props)
        identify.put("d", d)
        webSocket?.send(identify.toString())
    }

    private fun startHeartbeat() {
        stopHeartbeat()
        heartbeatHandler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                val hb = JSONObject()
                hb.put("op", 1)
                hb.put("d", sequence)
                webSocket?.send(hb.toString())
                heartbeatHandler?.postDelayed(this, heartbeatInterval)
            }
        }
        heartbeatHandler?.postDelayed(runnable, heartbeatInterval)
    }

    private fun stopHeartbeat() {
        heartbeatHandler?.removeCallbacksAndMessages(null)
        heartbeatHandler = null
    }

    // ---------- Cleanup ----------

    override fun onDestroy() {
        super.onDestroy()
        webSocket?.close(1000, "service stopped")
        stopHeartbeat()
        bubbleView?.let { try { windowManager.removeView(it) } catch (e: Exception) {} }
        panelView?.let { try { windowManager.removeView(it) } catch (e: Exception) {} }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}