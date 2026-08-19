package com.damsan.green.ui.intro

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.damsan.green.BuildConfig
import com.damsan.green.R
import com.damsan.green.data.repository.BrandingSettings
import com.damsan.green.data.repository.SettingsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class SchoolInfoActivity : AppCompatActivity() {

    private val client = OkHttpClient()
    private val settingsService = SettingsService()
    private val chatHistory = mutableListOf<ChatMessage>()
    private var chatAdapter: ChatAdapter? = null
    private var currentChatRecycler: RecyclerView? = null
    private var dynamicSchoolContext = ""

    private val defaultBulletins = listOf(
        "Dam San Green | Thi đua xây dựng trường học Xanh - Sạch - Đẹp, ghi nhận các lượt dọn rác bằng ứng dụng.",
        "Lao động khu nội trú | Các lớp chủ động vệ sinh phòng ở, sân trường và khu vực sinh hoạt chung theo lịch.",
        "Hoạt động Đoàn | Khuyến khích học sinh tham gia ngày thứ bảy xanh, phân loại rác và chăm sóc cảnh quan."
    )

    private val schoolContext = """
        Bạn là trợ lý AI của Trường PTDTNT THPT Đam San tại Đắk Lắk.

        Thông tin nền:
        - Đây là môi trường học tập và sinh hoạt nội trú cho học sinh.
        - Trường triển khai phong trào Dam San Green để xây dựng trường học xanh, sạch, đẹp.
        - Ứng dụng giúp học sinh báo cáo điểm rác, admin duyệt báo cáo, lớp nhận điểm thi đua và theo dõi bảng xếp hạng.
        - Trợ lý cần giải đáp thân thiện cho học sinh, phụ huynh và khách tham quan.

        Nguyên tắc trả lời:
        - Trả lời bằng tiếng Việt, ngắn gọn, rõ ý, tối đa 3-4 câu.
        - Nếu câu hỏi cần thông tin chưa có trong app, hãy nói nhẹ nhàng rằng nên liên hệ nhà trường hoặc giáo viên phụ trách.
        - Ưu tiên nội dung về trường, nội trú, môi trường, hoạt động Đoàn và ứng dụng Dam San Green.
    """.trimIndent()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_school_info)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnAiBubble).setOnClickListener { showAiChatDialog() }
        findViewById<View>(R.id.cardAiInvite).setOnClickListener { showAiChatDialog() }

        renderSchoolContent(BrandingSettings.DEFAULT)
        loadSchoolContent()

        if (intent.getBooleanExtra(EXTRA_OPEN_CHAT, false)) {
            findViewById<View>(R.id.btnAiBubble).post { showAiChatDialog() }
        }
    }

    private fun loadSchoolContent() {
        lifecycleScope.launch {
            val settings = settingsService.getBranding()
            runOnUiThread { renderSchoolContent(settings) }
        }
    }

    private fun renderSchoolContent(settings: BrandingSettings) {
        val schoolName = normalizeSchoolName(settings.schoolName)
        val imageUrls = settings.schoolImageUrls
        val bulletins = settings.schoolBulletins.ifEmpty { defaultBulletins }

        findViewById<TextView>(R.id.tvScreenTitle)?.text = shortSchoolName(schoolName)
        findViewById<TextView>(R.id.tvHeroTitle)?.text = schoolName
        findViewById<TextView>(R.id.tvSchoolNameCard)?.text = schoolName

        val hero = findViewById<ImageView>(R.id.ivSchoolHero)
        if (imageUrls.isNotEmpty()) {
            Glide.with(this)
                .load(imageUrls.first())
                .centerCrop()
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .into(hero)
        } else {
            hero.setImageResource(R.drawable.bg_map_placeholder)
        }

        renderGallery(imageUrls)
        renderBulletins(bulletins)

        dynamicSchoolContext = buildString {
            appendLine("Tên trường hiển thị trong app: $schoolName")
            appendLine("Bản tin hiện có:")
            bulletins.take(5).forEach { appendLine("- ${it.replace("|", ":")}") }
        }
    }

    private fun renderGallery(imageUrls: List<String>) {
        val gallery = findViewById<LinearLayout>(R.id.schoolGallery) ?: return
        gallery.removeAllViews()

        if (imageUrls.isEmpty()) {
            val item = layoutInflater.inflate(R.layout.item_school_gallery_image, gallery, false)
            item.findViewById<ImageView>(R.id.ivSchoolGalleryImage)
                .setImageResource(R.drawable.bg_map_placeholder)
            item.findViewById<TextView>(R.id.tvSchoolGalleryTitle).text = "Ảnh trường"
            item.findViewById<TextView>(R.id.tvSchoolGallerySubtitle).text =
                "Admin có thể thêm URL Cloudinary"
            gallery.addView(item)
            return
        }

        imageUrls.forEachIndexed { index, url ->
            val item = layoutInflater.inflate(R.layout.item_school_gallery_image, gallery, false)
            Glide.with(this)
                .load(url)
                .centerCrop()
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .into(item.findViewById(R.id.ivSchoolGalleryImage))
            item.findViewById<TextView>(R.id.tvSchoolGalleryTitle).text = "Góc trường ${index + 1}"
            item.findViewById<TextView>(R.id.tvSchoolGallerySubtitle).text = "Ảnh giới thiệu Đam San"
            gallery.addView(item)
        }
    }

    private fun renderBulletins(bulletins: List<String>) {
        val list = findViewById<LinearLayout>(R.id.schoolNewsList) ?: return
        list.removeAllViews()

        bulletins.forEach { raw ->
            val parts = raw.split("|", limit = 3).map { it.trim() }
            val title = parts.getOrNull(0).orEmpty().ifBlank { "Bản tin trường" }
            val body = parts.getOrNull(1).orEmpty().ifBlank {
                "Cập nhật hoạt động môi trường, lao động hoặc phong trào Đoàn tại trường."
            }
            val imageUrl = parts.getOrNull(2).orEmpty()
            val item = layoutInflater.inflate(R.layout.item_school_news, list, false)
            item.findViewById<TextView>(R.id.tvNewsTitle).text = title
            item.findViewById<TextView>(R.id.tvNewsBody).text = body
            val newsImage = item.findViewById<ImageView>(R.id.ivNewsImage)
            val iconFrame = item.findViewById<View>(R.id.newsIconFrame)
            if (imageUrl.isNotBlank()) {
                newsImage.visibility = View.VISIBLE
                iconFrame.visibility = View.GONE
                Glide.with(this)
                    .load(imageUrl)
                    .centerCrop()
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .into(newsImage)
            } else {
                newsImage.visibility = View.GONE
                iconFrame.visibility = View.VISIBLE
            }
            list.addView(item)
        }
    }

    private fun showAiChatDialog() {
        ensureWelcomeMessage()

        val dialogView = layoutInflater.inflate(R.layout.dialog_ai_chat, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.setOnDismissListener {
            currentChatRecycler = null
            chatAdapter = null
        }

        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            decorView.setPadding(0, 0, 0, 0)
            setDimAmount(0.42f)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            setLayout((resources.displayMetrics.widthPixels * 0.94f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.recyclerChat)
        chatAdapter = ChatAdapter(chatHistory)
        currentChatRecycler = recyclerView
        recyclerView.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        recyclerView.adapter = chatAdapter
        scrollToBottom()

        val input = dialogView.findViewById<EditText>(R.id.etChatInput)
        dialogView.findViewById<ImageButton>(R.id.btnCloseAi).setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<ImageButton>(R.id.btnSend).setOnClickListener { submitMessage(input) }
        input.setOnEditorActionListener { _, _, event ->
            if (event == null || event.keyCode == KeyEvent.KEYCODE_ENTER) {
                submitMessage(input)
                true
            } else {
                false
            }
        }

        dialogView.findViewById<TextView>(R.id.chipAskSchool).setOnClickListener {
            submitQuickQuestion("Giới thiệu ngắn gọn về Trường Đam San cho khách tham quan.")
        }
        dialogView.findViewById<TextView>(R.id.chipAskDorm).setOnClickListener {
            submitQuickQuestion("Học sinh nội trú ở trường thường sinh hoạt như thế nào?")
        }
        dialogView.findViewById<TextView>(R.id.chipAskGreen).setOnClickListener {
            submitQuickQuestion("Phong trào Dam San Green giúp bảo vệ môi trường ra sao?")
        }
    }

    private fun ensureWelcomeMessage() {
        if (chatHistory.isEmpty()) {
            val welcomeMessage = if (hasGeminiApiKey()) {
                "Xin chào! Mình là trợ lý AI Đam San. Bạn có thể hỏi về trường, đời sống nội trú, hoạt động Đoàn hoặc phong trào Dam San Green."
            } else {
                "Xin chào! Mình đang ở chế độ demo vì chưa cấu hình GEMINI_API_KEY. Bạn vẫn có thể hỏi về trường, nhưng câu trả lời sẽ dựa trên dữ liệu có sẵn trong app."
            }
            chatHistory.add(
                ChatMessage(
                    welcomeMessage,
                    isUser = false
                )
            )
        }
    }

    private fun submitMessage(input: EditText) {
        val message = input.text.toString().trim()
        if (message.isEmpty()) return
        input.text?.clear()
        submitQuickQuestion(message)
    }

    private fun submitQuickQuestion(message: String) {
        addUserMessage(message)
        sendToGemini(message)
    }

    private fun addUserMessage(text: String) {
        chatHistory.add(ChatMessage(text, isUser = true))
        chatAdapter?.notifyItemInserted(chatHistory.size - 1)
        scrollToBottom()
    }

    private fun addBotMessage(text: String) {
        chatHistory.add(ChatMessage(text, isUser = false))
        chatAdapter?.notifyItemInserted(chatHistory.size - 1)
        scrollToBottom()
    }

    private fun scrollToBottom() {
        currentChatRecycler?.post {
            currentChatRecycler?.smoothScrollToPosition(maxOf(0, chatHistory.size - 1))
        }
    }

    private fun sendToGemini(userMessage: String) {
        addBotMessage("Đam San AI đang trả lời...")
        val loadingIndex = chatHistory.size - 1

        lifecycleScope.launch {
            val response = withContext(Dispatchers.IO) {
                callGeminiAPI(userMessage)
            }

            if (loadingIndex in chatHistory.indices) {
                chatHistory[loadingIndex] = ChatMessage(response, isUser = false)
                chatAdapter?.notifyItemChanged(loadingIndex)
            } else {
                addBotMessage(response)
            }
            scrollToBottom()
        }
    }

    private fun callGeminiAPI(userMessage: String): String {
        if (!hasGeminiApiKey()) {
            return answerLocally(
                userMessage,
                "Mình đang ở chế độ demo vì chưa cấu hình GEMINI_API_KEY."
            )
        }

        return try {
            val contentsArray = JSONArray()
            contentsArray.put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", "$schoolContext\n\n$dynamicSchoolContext")))
            })
            contentsArray.put(JSONObject().apply {
                put("role", "model")
                put("parts", JSONArray().put(JSONObject().put("text", "Đã hiểu! Mình sẽ trả lời ngắn gọn, thân thiện và đúng ngữ cảnh Đam San.")))
            })

            chatHistory.takeLast(8).dropLast(1).forEach { msg ->
                contentsArray.put(JSONObject().apply {
                    put("role", if (msg.isUser) "user" else "model")
                    put("parts", JSONArray().put(JSONObject().put("text", msg.text)))
                })
            }

            contentsArray.put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", userMessage)))
            })

            val requestBody = JSONObject().apply {
                put("contents", contentsArray)
                put("generationConfig", JSONObject().apply {
                    put("maxOutputTokens", 320)
                    put("temperature", 0.55)
                })
            }.toString()

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/${BuildConfig.GEMINI_MODEL}:generateContent?key=${BuildConfig.GEMINI_API_KEY}")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return answerLocally(
                userMessage,
                "Mình chưa nhận được phản hồi từ Gemini, nên trả lời tạm theo dữ liệu trong app."
            )
            if (!response.isSuccessful) {
                return answerLocally(
                    userMessage,
                    "Gemini API chưa kết nối được (${response.code}), nên mình trả lời tạm theo dữ liệu trong app."
                )
            }
            val json = JSONObject(responseBody)
            json.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
                .trim()
                .ifBlank {
                    answerLocally(
                        userMessage,
                        "Gemini trả lời rỗng, nên mình trả lời tạm theo dữ liệu trong app."
                    )
                }
        } catch (_: Exception) {
            answerLocally(
                userMessage,
                "Mình chưa kết nối được Gemini, nên trả lời tạm theo dữ liệu trong app."
            )
        }
    }

    private fun hasGeminiApiKey(): Boolean {
        val key = BuildConfig.GEMINI_API_KEY.trim()
        return key.isNotBlank() && !key.equals("YOUR_GEMINI_KEY", ignoreCase = true)
    }

    private fun answerLocally(userMessage: String, prefix: String? = null): String {
        val message = userMessage.lowercase(Locale.getDefault())
        val answer = when {
            listOf("địa chỉ", "ở đâu", "dia chi", "where").any { message.contains(it) } ->
                "Trường Đam San được giới thiệu trong app tại xã EaDrông, tỉnh Đắk Lắk. Nếu cần chỉ đường chính xác, bạn nên xem thêm bản đồ hoặc liên hệ nhà trường."
            listOf("nội trú", "noi tru", "ăn ở", "sinh hoạt").any { message.contains(it) } ->
                "Đam San là môi trường học tập gắn với sinh hoạt nội trú. Học sinh học tập, rèn luyện nề nếp, vệ sinh khu ở và tham gia các hoạt động tập thể tại trường."
            listOf("rác", "môi trường", "xanh", "dam san green", "nhặt").any { message.contains(it) } ->
                "Dam San Green giúp học sinh báo cáo điểm rác, admin duyệt minh chứng và lớp nhận điểm thi đua. Mục tiêu là biến việc giữ trường xanh sạch thành một phong trào có dữ liệu rõ ràng."
            listOf("đoàn", "hoạt động", "lao động", "bản tin").any { message.contains(it) } ->
                "Bản tin trường có thể cập nhật hoạt động lao động, ngày thứ bảy xanh, phong trào Đoàn và các chiến dịch vệ sinh. Admin có thể thêm tin mới trong phần Cài đặt."
            else ->
                "Mình có thể hỗ trợ hỏi đáp về Trường Đam San, đời sống nội trú, phong trào môi trường và app Dam San Green. Với thông tin hành chính chi tiết, bạn nên liên hệ giáo viên hoặc nhà trường để được xác nhận chính xác."
        }
        return if (prefix.isNullOrBlank()) answer else "$prefix\n\n$answer"
    }

    private fun shortSchoolName(name: String): String {
        val lower = name.lowercase(Locale.getDefault())
        return when {
            lower.contains("đam san") || lower.contains("dam san") -> "Trường Đam San"
            name.length > 22 -> name.take(22).trimEnd() + "..."
            else -> name
        }
    }

    private fun normalizeSchoolName(name: String): String {
        val raw = name.ifBlank { BrandingSettings.DEFAULT.schoolName }
        val lower = raw.lowercase(Locale.getDefault())
        return if (lower.contains("ptdtnt") && lower.contains("dam san")) {
            "Trường PTDTNT THPT Đam San"
        } else {
            raw
        }
    }

    companion object {
        const val EXTRA_OPEN_CHAT = "open_ai_chat"
    }
}

data class ChatMessage(val text: String, val isUser: Boolean)

class ChatAdapter(private val messages: List<ChatMessage>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_USER = 1
        const val TYPE_BOT = 2
    }

    override fun getItemViewType(position: Int) =
        if (messages[position].isUser) TYPE_USER else TYPE_BOT

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = android.view.LayoutInflater.from(parent.context)
        return if (viewType == TYPE_USER) {
            UserViewHolder(inflater.inflate(R.layout.item_chat_user, parent, false))
        } else {
            BotViewHolder(inflater.inflate(R.layout.item_chat_bot, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = messages[position]
        when (holder) {
            is UserViewHolder -> holder.bind(msg.text)
            is BotViewHolder -> holder.bind(msg.text)
        }
    }

    override fun getItemCount() = messages.size

    class UserViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bind(text: String) {
            itemView.findViewById<TextView>(R.id.tvMessage).text = text
        }
    }

    class BotViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bind(text: String) {
            itemView.findViewById<TextView>(R.id.tvMessage).text = text
        }
    }
}
