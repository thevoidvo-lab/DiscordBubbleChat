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
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class BubbleChatService : Service() {

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var panelView: View? = null
    private var isPanelOpen = false

    private lateinit var client: OkHttpClient
    private var token = ""
    private var channelId = ""
    private var unreadCount = 0
    private val lastIds = mutableSetOf<String>()
    private val messageHistory = mutableListOf<Pair<String, String>>()

    private val pollHandler = Handler(Looper.getMainLooper())
    private var isPolling = false

    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        const val CHANNEL_ID = "bubble_chat_service"
        const val NOTIF_ID = 1
        const val POLL_INTERVAL = 3000L
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
        if (!isPolling) startPolling()

        return START_STICKY
    }

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
        if (isPanelOpen) closePanel() else openPanel(bubbleParams)
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

        renderHistory()

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

        fetchMessages()
    }

    private fun renderHistory() {
        val container = panelView?.findViewById<LinearLayout>(R.id.chatContainer) ?: return
        val scroll = panelView?.findViewById<ScrollView>(R.id.chatScroll)
        container.removeAllViews()
        messageHistory.forEach { (a, c) ->
            val tv = TextView(this)
            tv.text = "$a: $c"
            tv.setTextColor(resources.getColor(R.color.white, theme))
            tv.textSize = 13f
            tv.setPadding(4, 6, 4, 6)
            container.addView(tv)
        }
        scroll?.post { scroll.fullScroll(View.FOCUS_DOWN) }
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

    private fun appendMessageToPanel(author: String, content: String) {
        messageHistory.add(author to content)
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
            override fun onFailure(call: Call, e: java.io.IOException) {}
            override fun onResponse(call: Call, response: Response) {
                response.close()
            }
        })
    }

    private fun startPolling() {
        isPolling = true
        val runnable = object : Runnable {
            override fun run() {
                fetchMessages()
                pollHandler.postDelayed(this, POLL_INTERVAL)
            }
        }
        pollHandler.post(runnable)
    }

    private fun fetchMessages() {
        val request = Request.Builder()
            .url("https://discord.com/api/v10/channels/$channelId/messages?limit=15")
            .addHeader("Authorization", "Bot $token")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {}

            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string() ?: return
                response.close()
                try {
                    val arr = JSONArray(bodyStr)
                    val newOnes = mutableListOf<Pair<String, String>>()
                    for (i in arr.length() - 1 downTo 0) {
                        val m = arr.getJSONObject(i)
                        val id = m.getString("id")
                        if (id in lastIds) continue
                        lastIds.add(id)
                        val author = m.getJSONObject("author").optString("username", "unknown")
                        val content = m.optString("content", "")
                        if (content.isEmpty()) continue
                        newOnes.add(author to content)
                    }
                    if (newOnes.isNotEmpty()) {
                        mainHandler.post {
                            if (isPanelOpen) {
                                newOnes.forEach { (a, c) -> appendMessageToPanel(a, c) }
                            } else {
                                newOnes.forEach { (a, c) -> messageHistory.add(a to c) }
                                unreadCount += newOnes.size
                                updateBadge()
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        pollHandler.removeCallbacksAndMessages(null)
        isPolling = false
        bubbleView?.let { try { windowManager.removeView(it) } catch (e: Exception) {} }
        panelView?.let { try { windowManager.removeView(it) } catch (e: Exception) {} }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
