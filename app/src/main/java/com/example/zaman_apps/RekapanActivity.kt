package com.example.zaman_apps

import android.animation.ObjectAnimator
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.example.zaman_apps.databinding.ActivityRekapanBinding
import com.example.zaman_apps.utils.SessionHelper
import com.google.android.material.card.MaterialCardView
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class RekapanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRekapanBinding
    private val urlSheet = "https://script.google.com/macros/s/AKfycby4n_8DasDqYLL-K7DQMUb_6pyF7FkuDaSWde98TDMlF0BnneJjVVwadqzuRzq-M1KguA/exec"
    private var detailArrayGlobal: JSONArray? = null
    private var cachedRekapArray: JSONArray? = null

    private val namaBulanList = listOf(
        "Januari", "Februari", "Maret", "April", "Mei", "Juni",
        "Juli", "Agustus", "September", "Oktober", "November", "Desember"
    )
    private var selectedBulan = 1  // 1-12
    private var selectedTahun = 2026

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRekapanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.swipeRefresh.setColorSchemeResources(R.color.brand_primary)
        binding.swipeRefresh.setOnRefreshListener { loadRekap(isManualRefresh = true) }

        val now = Calendar.getInstance()
        selectedBulan = now.get(Calendar.MONTH) + 1
        selectedTahun = now.get(Calendar.YEAR)

        setupFilterSpinner()
        updateHeaderBulan()

        binding.btnTerapkanFilter.setOnClickListener {
            selectedBulan = binding.spinnerBulan.selectedItemPosition + 1
            selectedTahun = (binding.spinnerTahun.selectedItem as String).toInt()
            updateHeaderBulan()
            loadRekap()
        }

        binding.etSearchUser.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterUserList(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        loadRekap()
    }

    private fun setupFilterSpinner() {
        val adapterBulan = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, namaBulanList)
        binding.spinnerBulan.adapter = adapterBulan
        binding.spinnerBulan.setSelection(selectedBulan - 1)

        val tahunSekarang = Calendar.getInstance().get(Calendar.YEAR)
        val daftarTahun = (2022..tahunSekarang + 1).map { it.toString() }.reversed()
        val adapterTahun = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, daftarTahun)
        binding.spinnerTahun.adapter = adapterTahun

        val pos = daftarTahun.indexOf(selectedTahun.toString())
        if (pos >= 0) {
            binding.spinnerTahun.setSelection(pos)
        }
    }

    private fun updateHeaderBulan() {
        val namaBulan = namaBulanList[selectedBulan - 1]
        binding.tvHeaderBulan.text = "REKAP ${namaBulan.uppercase()} $selectedTahun"
    }

    private fun loadRekap(isManualRefresh: Boolean = false) {
        if (!isManualRefresh) {
            binding.overlayLoading.visibility = View.VISIBLE
        }

        val queue = Volley.newRequestQueue(this)
        val request = object : StringRequest(
            Method.POST, urlSheet,
            { response ->
                binding.overlayLoading.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
                try {
                    val json = JSONObject(response)
                    if (json.optString("status") == "success") {
                        detailArrayGlobal = json.optJSONArray("detail")
                        var rekapArray = json.optJSONArray("rekap")

                        // 🔥 Fallback: jika rekapArray dari server kosong tapi detailArrayGlobal ada,
                        // hitung rekap secara lokal dari detail!
                        if ((rekapArray == null || rekapArray.length() == 0) && detailArrayGlobal != null && detailArrayGlobal!!.length() > 0) {
                            rekapArray = hitungRekapDariDetail(detailArrayGlobal!!)
                        }

                        cachedRekapArray = rekapArray
                        binding.tvTotalAktivitas.text = "Total ${detailArrayGlobal?.length() ?: 0} aktivitas • ${rekapArray?.length() ?: 0} user aktif"
                        filterUserList(binding.etSearchUser.text?.toString() ?: "")
                    } else {
                        tampilkanPesanError(json.optString("message", "Gagal memuat rekap"))
                    }
                } catch (e: Exception) {
                    tampilkanPesanError("Gagal membaca respons server")
                }
            },
            {
                binding.overlayLoading.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
                tampilkanPesanError("Koneksi terputus")
            }
        ) {
            override fun getParams(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["action"] = "get_rekap"
                params["requester_role"] = SessionHelper.getRole(this@RekapanActivity)
                // Kirim berbagai variasi format bulan & tahun agar pasti cocok dengan pemicu di Google Apps Script
                params["bulan"] = selectedBulan.toString()
                params["bulan_pad"] = String.format(Locale.US, "%02d", selectedBulan)
                params["month"] = String.format(Locale.US, "%02d", selectedBulan)
                params["tahun"] = selectedTahun.toString()
                params["year"] = selectedTahun.toString()
                return params
            }
        }

        request.retryPolicy = DefaultRetryPolicy(15000, 1, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT)
        queue.add(request)
    }

    private fun hitungRekapDariDetail(detailArray: JSONArray): JSONArray {
        val mapRekap = mutableMapOf<String, Pair<Int, Int>>() // username -> (input, update)

        for (i in 0 until detailArray.length()) {
            val obj = detailArray.getJSONObject(i)
            val username = obj.optString("username").ifEmpty { obj.optString("user") }
            val actionType = obj.optString("action_type").uppercase()
            val timestamp = obj.optString("timestamp")

            if (username.isEmpty()) continue

            if (timestamp.isNotEmpty() && !isDateInMonthAndYear(timestamp, selectedBulan, selectedTahun)) {
                continue
            }

            val current = mapRekap.getOrDefault(username, Pair(0, 0))
            if (actionType.contains("INPUT")) {
                mapRekap[username] = Pair(current.first + 1, current.second)
            } else {
                mapRekap[username] = Pair(current.first, current.second + 1)
            }
        }

        val result = JSONArray()
        for ((username, counts) in mapRekap) {
            val item = JSONObject()
            item.put("username", username)
            item.put("input", counts.first)
            item.put("update", counts.second)
            result.put(item)
        }
        return result
    }

    private fun isDateInMonthAndYear(timestampStr: String, targetMonth: Int, targetYear: Int): Boolean {
        if (timestampStr.isEmpty()) return true

        val dateFormats = listOf(
            "dd/MM/yyyy HH:mm:ss", "dd/MM/yyyy",
            "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd",
            "dd-MM-yyyy HH:mm:ss", "dd-MM-yyyy",
            "MM/dd/yyyy HH:mm:ss", "MM/dd/yyyy"
        )

        val cal = Calendar.getInstance()
        for (format in dateFormats) {
            try {
                val sdf = SimpleDateFormat(format, Locale.getDefault())
                val date = sdf.parse(timestampStr)
                if (date != null) {
                    cal.time = date
                    val m = cal.get(Calendar.MONTH) + 1
                    val y = cal.get(Calendar.YEAR)
                    if (m == targetMonth && y == targetYear) return true
                }
            } catch (_: Exception) { }
        }

        val monthPad = String.format("%02d", targetMonth)
        val monthStr = targetMonth.toString()
        val yearStr = targetYear.toString()

        if (timestampStr.contains(yearStr)) {
            if (timestampStr.contains("-$monthPad-") || timestampStr.contains("/$monthPad/") ||
                timestampStr.contains("-$monthStr-") || timestampStr.contains("/$monthStr/")) {
                return true
            }
        }

        return false
    }

    private fun filterUserList(query: String) {
        val q = query.trim().lowercase()
        if (cachedRekapArray == null) {
            tampilkanRingkasan(null)
            return
        }

        if (q.isEmpty()) {
            tampilkanRingkasan(cachedRekapArray)
            return
        }

        val filtered = JSONArray()
        for (i in 0 until cachedRekapArray!!.length()) {
            val item = cachedRekapArray!!.getJSONObject(i)
            val username = item.optString("username").lowercase()
            val name = item.optString("name").lowercase()
            if (username.contains(q) || name.contains(q)) {
                filtered.put(item)
            }
        }
        tampilkanRingkasan(filtered)
    }

    private fun tampilkanRingkasan(rekapArray: JSONArray?) {
        binding.containerRingkasan.removeAllViews()
        if (rekapArray == null || rekapArray.length() == 0) {
            val tv = TextView(this)
            tv.text = "📭 Belum ada aktivitas pada ${namaBulanList[selectedBulan - 1]} $selectedTahun.\n\nPastikan pilihan Tahun ($selectedTahun) sudah sesuai dengan tanggal data tersimpan."
            tv.textSize = 14f
            tv.gravity = Gravity.CENTER
            tv.setPadding(32, 64, 32, 64)
            tv.setTextColor(Color.parseColor("#888888"))
            binding.containerRingkasan.addView(tv)
            return
        }

        val items = mutableListOf<JSONObject>()
        for (i in 0 until rekapArray.length()) items.add(rekapArray.getJSONObject(i))

        items.sortByDescending { it.optInt("input") + it.optInt("update") }

        items.forEachIndexed { index, item ->
            val username = item.optString("username")
            val input = item.optInt("input")
            val update = item.optInt("update")

            val card = buatCardUser(username, input, update, index + 1)

            card.alpha = 0f
            card.translationY = 80f
            card.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(400)
                .setStartDelay((index * 60).toLong())
                .setInterpolator(OvershootInterpolator(1.2f))
                .start()

            binding.containerRingkasan.addView(card)
        }
    }

    private fun buatCardUser(username: String, input: Int, edit: Int, ranking: Int): View {
        val card = MaterialCardView(this)
        card.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 28 }
        card.radius = 28f
        card.cardElevation = 6f
        card.strokeWidth = 0
        card.strokeColor = Color.parseColor("#EEEEEE")
        card.rippleColor = ColorStateList.valueOf(Color.parseColor("#20000000"))

        val outer = LinearLayout(this)
        outer.orientation = LinearLayout.VERTICAL
        outer.setPadding(36, 30, 36, 30)

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL

        val badge = TextView(this)
        badge.text = "$ranking"
        badge.setTextColor(Color.WHITE)
        badge.textSize = 14f
        badge.setTypeface(null, Typeface.BOLD)
        badge.gravity = Gravity.CENTER
        badge.layoutParams = LinearLayout.LayoutParams(72, 72)
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.OVAL
        bg.setColor(warnaBadge(ranking))
        badge.background = bg
        row.addView(badge)

        val infoCol = LinearLayout(this)
        infoCol.orientation = LinearLayout.VERTICAL
        infoCol.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = 28
        }

        val tvName = TextView(this)
        tvName.text = username
        tvName.textSize = 15f
        tvName.setTypeface(null, Typeface.BOLD)
        tvName.setTextColor(Color.parseColor("#1A1A1A"))
        infoCol.addView(tvName)

        val tvStats = TextView(this)
        tvStats.text = "➕ $input input   ✏️ $edit edit"
        tvStats.textSize = 12.5f
        tvStats.setTextColor(Color.parseColor("#777777"))
        tvStats.setPadding(0, 6, 0, 0)
        infoCol.addView(tvStats)

        row.addView(infoCol)

        val ivArrow = ImageView(this)
        ivArrow.layoutParams = LinearLayout.LayoutParams(48, 48)
        ivArrow.setImageResource(android.R.drawable.arrow_down_float)
        ivArrow.setColorFilter(Color.parseColor("#999999"))
        row.addView(ivArrow)

        outer.addView(row)

        val detailContainer = LinearLayout(this)
        detailContainer.orientation = LinearLayout.VERTICAL
        detailContainer.visibility = View.GONE
        detailContainer.setPadding(0, 24, 0, 0)
        outer.addView(detailContainer)

        card.addView(outer)

        card.setOnClickListener {
            val expanding = detailContainer.visibility == View.GONE
            if (expanding) {
                if (detailContainer.childCount == 0) isiDetailUser(detailContainer, username)
                detailContainer.visibility = View.VISIBLE
                ObjectAnimator.ofFloat(ivArrow, "rotation", 0f, 180f).setDuration(200).start()
            } else {
                detailContainer.visibility = View.GONE
                ObjectAnimator.ofFloat(ivArrow, "rotation", 180f, 0f).setDuration(200).start()
            }
        }

        return card
    }

    private fun warnaBadge(ranking: Int): Int {
        return when (ranking) {
            1 -> Color.parseColor("#FFB300")
            2 -> Color.parseColor("#9E9E9E")
            3 -> Color.parseColor("#D2691E")
            else -> ContextCompat.getColor(this, R.color.brand_primary)
        }
    }

    private fun isiDetailUser(container: LinearLayout, username: String) {
        val detailArray = detailArrayGlobal ?: return
        var found = false

        val divider = View(this)
        divider.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2)
        divider.setBackgroundColor(Color.parseColor("#F0F0F0"))
        container.addView(divider)

        for (i in 0 until detailArray.length()) {
            val item = detailArray.getJSONObject(i)
            if (item.optString("username") != username) continue
            found = true

            val siteId = item.optString("site_id")
            val actionType = item.optString("action_type")
            val timestamp = item.optString("timestamp")
            val isInput = actionType == "INPUT"

            val itemRow = LinearLayout(this)
            itemRow.orientation = LinearLayout.HORIZONTAL
            itemRow.gravity = Gravity.CENTER_VERTICAL
            itemRow.setPadding(4, 18, 4, 18)

            val tag = TextView(this)
            tag.text = if (isInput) " INPUT " else " EDIT "
            tag.textSize = 10f
            tag.setTypeface(null, Typeface.BOLD)
            tag.setTextColor(Color.WHITE)
            val tagBg = GradientDrawable()
            tagBg.cornerRadius = 20f
            tagBg.setColor(if (isInput) Color.parseColor("#4CAF50") else Color.parseColor("#FF9800"))
            tag.background = tagBg
            tag.setPadding(14, 6, 14, 6)
            itemRow.addView(tag)

            val textCol = LinearLayout(this)
            textCol.orientation = LinearLayout.VERTICAL
            textCol.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = 20
            }
            val tvSite = TextView(this)
            tvSite.text = siteId
            tvSite.textSize = 13.5f
            tvSite.setTypeface(null, Typeface.BOLD)
            textCol.addView(tvSite)

            val tvTime = TextView(this)
            tvTime.text = timestamp
            tvTime.textSize = 11.5f
            tvTime.setTextColor(Color.parseColor("#999999"))
            textCol.addView(tvTime)

            itemRow.addView(textCol)
            container.addView(itemRow)
        }

        if (!found) {
            val tv = TextView(this)
            tv.text = "Tidak ada detail."
            tv.setPadding(4, 16, 4, 16)
            container.addView(tv)
        }
    }

    private fun tampilkanPesanError(message: String) {
        binding.containerRingkasan.removeAllViews()
        val tv = TextView(this)
        tv.text = "❌ $message"
        tv.gravity = Gravity.CENTER
        tv.setPadding(0, 48, 0, 48)
        binding.containerRingkasan.addView(tv)
    }
}
