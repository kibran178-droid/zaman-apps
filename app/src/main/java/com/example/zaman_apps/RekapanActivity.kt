package com.example.zaman_apps

import android.animation.ObjectAnimator
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
import android.widget.FrameLayout
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
import com.example.zaman_apps.utils.ServerConfig
import com.example.zaman_apps.utils.SessionHelper
import com.google.android.material.card.MaterialCardView
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class RekapanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRekapanBinding
    private val urlSheet = ServerConfig.SCRIPT_URL
    private var detailArrayGlobal: JSONArray? = null
    private var cachedRekapArray: JSONArray? = null

    // Pagination State
    private var currentPage = 1
    private val pageSize = 10
    private var currentSortedList = listOf<JSONObject>()

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
        setupListeners()

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

    private fun setupListeners() {
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

        // Pagination Navigation
        binding.btnPrevPage.setOnClickListener {
            if (currentPage > 1) {
                currentPage--
                renderPageItems()
            }
        }

        binding.btnNextPage.setOnClickListener {
            val totalPages = Math.ceil(currentSortedList.size.toDouble() / pageSize).toInt()
            if (currentPage < totalPages) {
                currentPage++
                renderPageItems()
            }
        }
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
        if (rekapArray == null || rekapArray.length() == 0) {
            currentSortedList = emptyList()
            currentPage = 1
            renderPageItems()
            return
        }

        val items = mutableListOf<JSONObject>()
        for (i in 0 until rekapArray.length()) items.add(rekapArray.getJSONObject(i))

        // Sort descending by total activity
        items.sortByDescending { it.optInt("input") + it.optInt("update") }
        currentSortedList = items
        currentPage = 1
        renderPageItems()
    }

    private fun renderPageItems() {
        binding.containerRingkasan.removeAllViews()

        if (currentSortedList.isEmpty()) {
            binding.cardPagination.visibility = View.GONE
            val tvEmpty = TextView(this).apply {
                text = "Belum ada aktivitas pada ${namaBulanList[selectedBulan - 1]} $selectedTahun.\n\nPastikan pilihan Tahun ($selectedTahun) sudah sesuai dengan tanggal data."
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(32, 64, 32, 64)
                setTextColor(ContextCompat.getColor(this@RekapanActivity, R.color.text_secondary))
            }
            binding.containerRingkasan.addView(tvEmpty)
            return
        }

        val totalPages = Math.ceil(currentSortedList.size.toDouble() / pageSize).toInt().coerceAtLeast(1)
        if (currentPage > totalPages) currentPage = totalPages

        val startIndex = (currentPage - 1) * pageSize
        val endIndex = minOf(startIndex + pageSize, currentSortedList.size)
        val pageItems = currentSortedList.subList(startIndex, endIndex)

        pageItems.forEachIndexed { indexOnPage, item ->
            val globalRank = startIndex + indexOnPage + 1
            val username = item.optString("username")
            val input = item.optInt("input")
            val update = item.optInt("update")

            val card = buatCardUser(username, input, update, globalRank)

            card.alpha = 0f
            card.translationY = 50f
            card.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(300)
                .setStartDelay((indexOnPage * 40).toLong())
                .setInterpolator(OvershootInterpolator(1.1f))
                .start()

            binding.containerRingkasan.addView(card)
        }

        // Update Pagination Controls
        if (currentSortedList.size > pageSize) {
            binding.cardPagination.visibility = View.VISIBLE
            binding.tvPageIndicator.text = "Halaman $currentPage dari $totalPages"

            binding.btnPrevPage.isEnabled = currentPage > 1
            binding.btnPrevPage.alpha = if (currentPage > 1) 1.0f else 0.4f

            binding.btnNextPage.isEnabled = currentPage < totalPages
            binding.btnNextPage.alpha = if (currentPage < totalPages) 1.0f else 0.4f
        } else {
            binding.cardPagination.visibility = View.GONE
        }
    }

    private fun buatCardUser(username: String, input: Int, edit: Int, ranking: Int): View {
        val density = resources.displayMetrics.density

        val card = MaterialCardView(this).apply {
            radius = 16 * density
            cardElevation = 3 * density
            strokeWidth = (1 * density).toInt()
            strokeColor = ContextCompat.getColor(this@RekapanActivity, R.color.divider)
            setCardBackgroundColor(ContextCompat.getColor(this@RekapanActivity, R.color.card_bg))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (12 * density).toInt()
            }
        }

        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * density).toInt(), (14 * density).toInt(), (16 * density).toInt(), (14 * density).toInt())
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        // Ranking Badge Bulat (Gold #1, Silver #2, Bronze #3, Brand #4+)
        val badgeCard = MaterialCardView(this).apply {
            radius = 18 * density
            cardElevation = 0f
            setCardBackgroundColor(warnaBadge(ranking))
            layoutParams = LinearLayout.LayoutParams((36 * density).toInt(), (36 * density).toInt())
        }

        val tvBadge = TextView(this).apply {
            text = "$ranking"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(if (ranking <= 3) Color.WHITE else ContextCompat.getColor(this@RekapanActivity, R.color.brand_primary))
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        badgeCard.addView(tvBadge)
        row.addView(badgeCard)

        // User Info Column
        val infoCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            ).apply {
                marginStart = (14 * density).toInt()
                marginEnd = (8 * density).toInt()
            }
        }

        val tvName = TextView(this).apply {
            text = username
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@RekapanActivity, R.color.text_primary))
        }
        infoCol.addView(tvName)

        // Clean Stat Badges Row (Teks & Badge Bersih)
        val statsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (6 * density).toInt(), 0, 0)
        }

        // Badge Input
        val tvInputBadge = TextView(this).apply {
            text = "INPUT: $input"
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@RekapanActivity, R.color.brand_primary))
            val bgInput = GradientDrawable().apply {
                cornerRadius = 6 * density
                setColor(ContextCompat.getColor(this@RekapanActivity, R.color.brand_accent_light))
            }
            background = bgInput
            setPadding((8 * density).toInt(), (3 * density).toInt(), (8 * density).toInt(), (3 * density).toInt())
        }
        statsRow.addView(tvInputBadge)

        // Badge Edit
        val tvEditBadge = TextView(this).apply {
            text = "EDIT: $edit"
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@RekapanActivity, R.color.warning))
            val bgEdit = GradientDrawable().apply {
                cornerRadius = 6 * density
                setColor(ContextCompat.getColor(this@RekapanActivity, R.color.warning_soft))
            }
            background = bgEdit
            setPadding((8 * density).toInt(), (3 * density).toInt(), (8 * density).toInt(), (3 * density).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = (6 * density).toInt()
            }
        }
        statsRow.addView(tvEditBadge)

        infoCol.addView(statsRow)
        row.addView(infoCol)

        val ivArrow = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams((24 * density).toInt(), (24 * density).toInt())
            setImageResource(android.R.drawable.arrow_down_float)
            setColorFilter(ContextCompat.getColor(this@RekapanActivity, R.color.text_secondary))
        }
        row.addView(ivArrow)

        outer.addView(row)

        val detailContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, (14 * density).toInt(), 0, 0)
        }
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
            1 -> Color.parseColor("#FFB300") // Gold
            2 -> Color.parseColor("#9E9E9E") // Silver
            3 -> Color.parseColor("#CD7F32") // Bronze
            else -> ContextCompat.getColor(this, R.color.brand_accent_light)
        }
    }

    private fun isiDetailUser(container: LinearLayout, username: String) {
        val detailArray = detailArrayGlobal ?: return
        val density = resources.displayMetrics.density
        var found = false

        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * density).toInt()).apply {
                bottomMargin = (8 * density).toInt()
            }
            setBackgroundColor(ContextCompat.getColor(this@RekapanActivity, R.color.divider))
        }
        container.addView(divider)

        for (i in 0 until detailArray.length()) {
            val item = detailArray.getJSONObject(i)
            if (item.optString("username") != username) continue
            found = true

            val siteId = item.optString("site_id")
            val actionType = item.optString("action_type")
            val timestamp = item.optString("timestamp")
            val isInput = actionType.equals("INPUT", ignoreCase = true)

            val itemRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, (8 * density).toInt(), 0, (8 * density).toInt())
            }

            val tag = TextView(this).apply {
                text = if (isInput) "INPUT" else "EDIT"
                textSize = 9.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                val tagBg = GradientDrawable().apply {
                    cornerRadius = 6 * density
                    setColor(ContextCompat.getColor(this@RekapanActivity, if (isInput) R.color.brand_primary else R.color.warning))
                }
                background = tagBg
                setPadding((10 * density).toInt(), (3 * density).toInt(), (10 * density).toInt(), (3 * density).toInt())
            }
            itemRow.addView(tag)

            val textCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = (12 * density).toInt()
                }
            }

            val tvSite = TextView(this).apply {
                text = siteId
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(this@RekapanActivity, R.color.text_primary))
            }
            textCol.addView(tvSite)

            val tvTime = TextView(this).apply {
                text = timestamp
                textSize = 11f
                setTextColor(ContextCompat.getColor(this@RekapanActivity, R.color.text_secondary))
            }
            textCol.addView(tvTime)

            itemRow.addView(textCol)
            container.addView(itemRow)
        }

        if (!found) {
            val tv = TextView(this).apply {
                text = "Tidak ada detail aktivitas."
                textSize = 12f
                setTextColor(ContextCompat.getColor(this@RekapanActivity, R.color.text_secondary))
                setPadding(0, (8 * density).toInt(), 0, (8 * density).toInt())
            }
            container.addView(tv)
        }
    }

    private fun tampilkanPesanError(message: String) {
        binding.containerRingkasan.removeAllViews()
        binding.cardPagination.visibility = View.GONE
        val tv = TextView(this).apply {
            text = "❌ $message"
            gravity = Gravity.CENTER
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@RekapanActivity, R.color.error))
            setPadding(0, 48, 0, 48)
        }
        binding.containerRingkasan.addView(tv)
    }
}
