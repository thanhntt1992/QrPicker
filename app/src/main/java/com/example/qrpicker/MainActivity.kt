package com.example.qrpicker

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Patterns
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer

class MainActivity : Activity() {

    private val pickImageCode = 1001
    private lateinit var preview: ImageView
    private lateinit var resultText: TextView
    private lateinit var openButton: Button
    private lateinit var copyButton: Button
    private lateinit var autoOpen: CheckBox
    private var lastResult: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    // ---------- Giao diện (tạo bằng code, không cần file layout) ----------
    private fun buildUi() {
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val pickButton = Button(this).apply {
            text = "Chọn ảnh mã QR"
            setOnClickListener { pickImage() }
        }
        autoOpen = CheckBox(this).apply {
            text = "Tự động mở link sau khi quét"
            isChecked = true
        }
        preview = ImageView(this).apply {
            adjustViewBounds = true
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, (260 * resources.displayMetrics.density).toInt())
                .apply { topMargin = pad }
        }
        resultText = TextView(this).apply {
            textSize = 16f
            setTextIsSelectable(true)
            setPadding(0, pad, 0, pad)
            text = "Chưa có kết quả."
        }
        openButton = Button(this).apply {
            text = "Mở link"
            isEnabled = false
            setOnClickListener { lastResult?.let { openLink(it) } }
        }
        copyButton = Button(this).apply {
            text = "Sao chép nội dung"
            isEnabled = false
            setOnClickListener { lastResult?.let { copyText(it) } }
        }

        listOf(pickButton, autoOpen, preview, resultText, openButton, copyButton).forEach {
            if (it.layoutParams == null) it.layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            root.addView(it)
        }
        setContentView(ScrollView(this).apply { addView(root) })
    }

    // ---------- Chọn ảnh ----------
    private fun pickImage() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        try {
            startActivityForResult(Intent.createChooser(intent, "Chọn ảnh QR"), pickImageCode)
        } catch (e: ActivityNotFoundException) {
            toast("Không tìm thấy ứng dụng chọn ảnh")
        }
    }

    @Deprecated("Dùng API cũ để không phụ thuộc AndroidX")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == pickImageCode && resultCode == RESULT_OK) {
            data?.data?.let { processImage(it) }
        }
    }

    private fun handleIncomingIntent(intent: Intent?) {
        intent ?: return
        val uri: Uri? = when (intent.action) {
            Intent.ACTION_SEND ->
                if (Build.VERSION.SDK_INT >= 33)
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else
                    @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
            Intent.ACTION_VIEW -> intent.data
            else -> null
        }
        uri?.let { processImage(it) }
    }

    // ---------- Giải mã QR ----------
    private fun processImage(uri: Uri) {
        val bitmap = loadBitmap(uri)
        if (bitmap == null) {
            showResult(null, "Không đọc được ảnh.")
            return
        }
        preview.setImageBitmap(bitmap)
        Thread {
            val text = decodeQr(bitmap)
            runOnUiThread {
                if (text == null) showResult(null, "Không tìm thấy mã QR trong ảnh.")
                else {
                    showResult(text, text)
                    if (autoOpen.isChecked && toUrl(text) != null) openLink(text)
                }
            }
        }.start()
    }

    private fun loadBitmap(uri: Uri): Bitmap? = try {
        // Đọc kích thước trước để thu nhỏ ảnh quá lớn, tránh tràn bộ nhớ
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > 2000 || bounds.outHeight / sample > 2000) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    } catch (e: Exception) {
        null
    }

    private fun decodeQr(bitmap: Bitmap): String? {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val source = RGBLuminanceSource(w, h, pixels)

        val hints = mapOf(
            DecodeHintType.TRY_HARDER to true,
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
            DecodeHintType.CHARACTER_SET to "UTF-8"
        )
        val reader = MultiFormatReader().apply { setHints(hints) }

        // Thử nhiều cách: thường, đảo màu (QR nền tối), 2 loại binarizer
        val candidates = listOf(
            BinaryBitmap(HybridBinarizer(source)),
            BinaryBitmap(GlobalHistogramBinarizer(source)),
            BinaryBitmap(HybridBinarizer(source.invert())),
            BinaryBitmap(GlobalHistogramBinarizer(source.invert()))
        )
        for (bin in candidates) {
            try {
                return reader.decodeWithState(bin).text
            } catch (e: NotFoundException) {
                // thử cách tiếp theo
            } catch (e: Exception) {
                // bỏ qua lỗi định dạng / checksum
            } finally {
                reader.reset()
            }
        }
        return null
    }

    // ---------- Xử lý kết quả ----------
    private fun showResult(value: String?, message: String) {
        lastResult = value
        resultText.text = message
        copyButton.isEnabled = value != null
        openButton.isEnabled = value != null && toUrl(value) != null
    }

    /** Trả về Uri nếu nội dung là link, ngược lại trả về null. */
    private fun toUrl(raw: String): Uri? {
        val s = raw.trim()
        val lower = s.lowercase()
        val schemes = listOf("http://", "https://", "market://", "intent://", "tel:", "mailto:", "geo:", "sms:")
        if (schemes.any { lower.startsWith(it) }) return Uri.parse(s)
        if (lower.startsWith("www.") || Patterns.WEB_URL.matcher(s).matches()) return Uri.parse("https://$s")
        return null
    }

    private fun openLink(raw: String) {
        val uri = toUrl(raw) ?: run { toast("Nội dung không phải link"); return }
        try {
            val intent = if (uri.scheme == "intent") Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
            else Intent(Intent.ACTION_VIEW, uri)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            toast("Giả lập chưa có trình duyệt/ứng dụng mở được link này")
        } catch (e: Exception) {
            toast("Không mở được link: ${e.message}")
        }
    }

    private fun copyText(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("QR", text))
        toast("Đã sao chép")
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
