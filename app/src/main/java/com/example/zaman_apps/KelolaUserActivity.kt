package com.example.zaman_apps

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.example.zaman_apps.databinding.ActivityKelolaUserBinding
import com.example.zaman_apps.utils.ServerConfig
import com.example.zaman_apps.utils.SessionHelper
import com.example.zaman_apps.utils.VolleyHelper
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.json.JSONArray
import org.json.JSONObject
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class KelolaUserActivity : AppCompatActivity() {

    private lateinit var binding: ActivityKelolaUserBinding
    private val urlSheet = ServerConfig.SCRIPT_URL

    private var userList = listOf<UserItem>()
    private var filteredList = listOf<UserItem>()

    // State Filter & Pagination
    private var selectedFilter = "ALL" // ALL, ADMIN, TEKNISI
    private var currentPage = 1
    private val pageSize = 10

    data class UserItem(
        val username: String,
        val name: String,
        val role: String,
        val canEdit: Boolean
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityKelolaUserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        setupFilterChips()
        setupSearch()
        setupListeners()

        loadUsers()
    }

    // ==================== FILTER CHIPS BINDING & STYLING ====================
    private fun setupFilterChips() {
        updateFilterChipsUI()

        binding.chipFilterSemua.setOnClickListener {
            if (selectedFilter != "ALL") {
                selectedFilter = "ALL"
                updateFilterChipsUI()
                filterUsers(binding.etSearchUser.text?.toString() ?: "")
            }
        }

        binding.chipFilterAdmin.setOnClickListener {
            if (selectedFilter != "ADMIN") {
                selectedFilter = "ADMIN"
                updateFilterChipsUI()
                filterUsers(binding.etSearchUser.text?.toString() ?: "")
            }
        }

        binding.chipFilterTeknisi.setOnClickListener {
            if (selectedFilter != "TEKNISI") {
                selectedFilter = "TEKNISI"
                updateFilterChipsUI()
                filterUsers(binding.etSearchUser.text?.toString() ?: "")
            }
        }
    }

    private fun updateFilterChipsUI() {
        styleChip(binding.chipFilterSemua, selectedFilter == "ALL")
        styleChip(binding.chipFilterAdmin, selectedFilter == "ADMIN")
        styleChip(binding.chipFilterTeknisi, selectedFilter == "TEKNISI")
    }

    private fun styleChip(button: MaterialButton, isActive: Boolean) {
        if (isActive) {
            button.setBackgroundColor(ContextCompat.getColor(this, R.color.brand_primary))
            button.setTextColor(ContextCompat.getColor(this, R.color.white))
            button.strokeWidth = 0
        } else {
            button.setBackgroundColor(ContextCompat.getColor(this, R.color.white))
            button.setTextColor(ContextCompat.getColor(this, R.color.brand_primary))
            button.setStrokeColor(ContextCompat.getColorStateList(this, R.color.brand_primary))
            button.strokeWidth = (1 * resources.displayMetrics.density).toInt()
        }
    }

    // ==================== HEADER STAT COUNTERS ====================
    private fun updateStatCounters() {
        val total = userList.size
        val admin = userList.count { it.role.equals("admin", ignoreCase = true) }
        val teknisi = userList.count { !it.role.equals("admin", ignoreCase = true) }

        binding.tvStatTotalCount.text = total.toString()
        binding.tvStatAdminCount.text = admin.toString()
        binding.tvStatTeknisiCount.text = teknisi.toString()
    }

    private fun setupSearch() {
        binding.etSearchUser.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterUsers(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupListeners() {
        binding.swipeRefresh.setOnRefreshListener {
            loadUsers(skipLoading = true)
        }

        binding.fabTambahUser.setOnClickListener {
            dialogTambahUser()
        }

        // Pagination controls
        binding.btnPrevPageUser.setOnClickListener {
            if (currentPage > 1) {
                currentPage--
                renderUsers()
            }
        }

        binding.btnNextPageUser.setOnClickListener {
            val totalPages = Math.ceil(filteredList.size.toDouble() / pageSize).toInt()
            if (currentPage < totalPages) {
                currentPage++
                renderUsers()
            }
        }
    }

    private fun showLoading(msg: String) {
        binding.overlayLoading.visibility = View.VISIBLE
        binding.overlayLoading.findViewById<TextView>(R.id.tvLoadingMessage)?.text = msg
        binding.fabTambahUser.isEnabled = false
        binding.fabTambahUser.alpha = 0.5f
    }

    private fun hideLoading() {
        binding.overlayLoading.visibility = View.GONE
        binding.fabTambahUser.isEnabled = true
        binding.fabTambahUser.alpha = 1.0f
    }

    // ==================== LOAD USERS ====================
    private fun loadUsers(skipLoading: Boolean = false) {
        if (!skipLoading) {
            showLoading("Memuat daftar user...")
        }
        val queue = VolleyHelper.getRequestQueue(this)
        val request = object : StringRequest(
            Method.POST, urlSheet,
            { response ->
                if (!skipLoading) hideLoading() else binding.swipeRefresh.isRefreshing = false
                try {
                    val json = JSONObject(response)
                    if (json.optString("status") == "success") {
                        userList = json.optJSONArray("users")?.let { parseUsers(it) } ?: emptyList()
                        updateStatCounters()
                        filterUsers(binding.etSearchUser.text?.toString() ?: "")
                    } else {
                        Toast.makeText(this, json.optString("message"), Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Gagal membaca data user: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            },
            { error ->
                if (!skipLoading) hideLoading() else binding.swipeRefresh.isRefreshing = false
                val msg = when (error.cause) {
                    is SocketTimeoutException -> "Koneksi timeout, coba lagi"
                    is UnknownHostException -> "Tidak ada koneksi internet"
                    else -> "Koneksi terputus: ${error.message}"
                }
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        ) {
            override fun getHeaders(): MutableMap<String, String> {
                val headers = HashMap<String, String>()
                headers["Connection"] = "close"
                return headers
            }

            override fun getBodyContentType(): String {
                return "application/x-www-form-urlencoded; charset=utf-8"
            }

            override fun getParamsEncoding(): String {
                return "UTF-8"
            }

            override fun getParams(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["action"] = "list_users"
                params["requester_role"] = SessionHelper.getRole(this@KelolaUserActivity)
                return params
            }
        }
        VolleyHelper.apply60sTimeout(request)
        queue.add(request)
    }

    private fun parseUsers(array: JSONArray): List<UserItem> {
        val list = mutableListOf<UserItem>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                UserItem(
                    username = obj.optString("username"),
                    name = obj.optString("name", obj.optString("username")),
                    role = obj.optString("role", "user"),
                    canEdit = obj.optBoolean("can_edit", true)
                )
            )
        }
        return list
    }

    // ==================== FILTERING LOGIC ====================
    private fun filterUsers(query: String) {
        val q = query.trim().lowercase()
        filteredList = userList.filter { user ->
            val matchesRole = when (selectedFilter) {
                "ADMIN" -> user.role.equals("admin", ignoreCase = true)
                "TEKNISI" -> !user.role.equals("admin", ignoreCase = true)
                else -> true
            }

            val matchesQuery = q.isEmpty() ||
                    user.username.lowercase().contains(q) ||
                    user.name.lowercase().contains(q)

            matchesRole && matchesQuery
        }

        currentPage = 1
        renderUsers()
    }

    // ==================== RENDER USERS (LIST PER BARIS) & PAGINATION ====================
    private fun renderUsers() {
        binding.containerUsersGrid.removeAllViews()
        binding.tvTotalUsers.text = "${filteredList.size} User"

        if (filteredList.isEmpty()) {
            binding.cardPaginationUser.visibility = View.GONE
            val tvEmpty = TextView(this).apply {
                text = if (userList.isEmpty()) "Belum ada user terdaftar." else "Tidak ada user yang cocok."
                gravity = Gravity.CENTER
                setPadding(0, 64, 0, 64)
                setTextColor(ContextCompat.getColor(this@KelolaUserActivity, R.color.text_secondary))
                textSize = 14f
            }
            binding.containerUsersGrid.addView(tvEmpty)
            return
        }

        val totalPages = Math.ceil(filteredList.size.toDouble() / pageSize).toInt().coerceAtLeast(1)
        if (currentPage > totalPages) currentPage = totalPages

        val startIndex = (currentPage - 1) * pageSize
        val endIndex = minOf(startIndex + pageSize, filteredList.size)
        val pageItems = filteredList.subList(startIndex, endIndex)

        // Render List Items per Baris (1 item per row)
        pageItems.forEachIndexed { index, user ->
            val card = buatCardUserList(index, user)
            binding.containerUsersGrid.addView(card)
        }

        // Update Pagination Controls UI
        if (filteredList.size > pageSize) {
            binding.cardPaginationUser.visibility = View.VISIBLE
            binding.tvPageIndicatorUser.text = "Halaman $currentPage dari $totalPages"

            binding.btnPrevPageUser.isEnabled = currentPage > 1
            binding.btnPrevPageUser.alpha = if (currentPage > 1) 1.0f else 0.4f

            binding.btnNextPageUser.isEnabled = currentPage < totalPages
            binding.btnNextPageUser.alpha = if (currentPage < totalPages) 1.0f else 0.4f
        } else {
            binding.cardPaginationUser.visibility = View.GONE
        }
    }

    // ==================== EXECUTIVE DIGITAL PASS USER CARD (PER BARIS) ====================
    private fun buatCardUserList(index: Int, user: UserItem): View {
        val density = resources.displayMetrics.density
        val isRoleAdmin = user.role.equals("admin", ignoreCase = true)

        // Outer Card
        val card = MaterialCardView(this).apply {
            radius = 16 * density
            cardElevation = 3 * density
            strokeWidth = (1 * density).toInt()
            strokeColor = ContextCompat.getColor(this@KelolaUserActivity, R.color.divider)
            setCardBackgroundColor(ContextCompat.getColor(this@KelolaUserActivity, R.color.card_bg))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (12 * density).toInt()
            }
        }

        // Main Card Row Container
        val rootRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = (80 * density).toInt()
        }

        // 1. Left Vertical Accent Strip (Indikator warna role khas ZAMAN)
        val leftAccentStrip = View(this).apply {
            val stripColor = ContextCompat.getColor(
                this@KelolaUserActivity,
                if (isRoleAdmin) R.color.brand_primary else R.color.brand_primary_light
            )
            val bgStrip = GradientDrawable().apply {
                setColor(stripColor)
                cornerRadii = floatArrayOf(
                    16 * density, 16 * density, // top-left
                    0f, 0f,                     // top-right
                    0f, 0f,                     // bottom-right
                    16 * density, 16 * density  // bottom-left
                )
            }
            background = bgStrip
            layoutParams = LinearLayout.LayoutParams(
                (6 * density).toInt(), LinearLayout.LayoutParams.MATCH_PARENT
            )
        }
        rootRow.addView(leftAccentStrip)

        // 2. Content Inner Row (Avatar + User Info + Action Buttons)
        val innerContent = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((14 * density).toInt(), (14 * density).toInt(), (14 * density).toInt(), (14 * density).toInt())
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
        }

        // Avatar Inisial Bulat
        val avatarCard = MaterialCardView(this).apply {
            radius = 22 * density
            cardElevation = 0f
            setCardBackgroundColor(ContextCompat.getColor(this@KelolaUserActivity, R.color.brand_accent_light))
            layoutParams = LinearLayout.LayoutParams((44 * density).toInt(), (44 * density).toInt())
        }

        val initialLetter = user.name.trim().take(1).uppercase().ifEmpty { "U" }
        val tvAvatar = TextView(this).apply {
            text = initialLetter
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@KelolaUserActivity, R.color.brand_primary))
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        avatarCard.addView(tvAvatar)
        innerContent.addView(avatarCard)

        // User Info Column (Nama, @username, Role Badge)
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
            text = user.name
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@KelolaUserActivity, R.color.text_primary))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        infoCol.addView(tvName)

        val tvUsername = TextView(this).apply {
            text = "@${user.username}"
            textSize = 12f
            setTextColor(ContextCompat.getColor(this@KelolaUserActivity, R.color.text_secondary))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, (2 * density).toInt(), 0, (6 * density).toInt())
        }
        infoCol.addView(tvUsername)

        // Role Badge Tag (Teks bersih TANPA ICON)
        val tvRoleBadge = TextView(this).apply {
            text = if (isRoleAdmin) "ADMIN" else "TEKNISI"
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            if (isRoleAdmin) {
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    cornerRadius = 6 * density
                    setColor(ContextCompat.getColor(this@KelolaUserActivity, R.color.brand_primary))
                }
            } else {
                setTextColor(ContextCompat.getColor(this@KelolaUserActivity, R.color.brand_primary))
                background = GradientDrawable().apply {
                    cornerRadius = 6 * density
                    setColor(ContextCompat.getColor(this@KelolaUserActivity, R.color.brand_accent_light))
                    setStroke((1 * density).toInt(), ContextCompat.getColor(this@KelolaUserActivity, R.color.brand_primary))
                }
            }
            setPadding((10 * density).toInt(), (3 * density).toInt(), (10 * density).toInt(), (3 * density).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        infoCol.addView(tvRoleBadge)
        innerContent.addView(infoCol)

        // Action Buttons Row (Edit & Hapus) - Compact & Berdempet
        val actionCol = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val btnEdit = MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
            text = "Edit"
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@KelolaUserActivity, R.color.brand_primary))
            val editBg = GradientDrawable().apply {
                cornerRadius = 8 * density
                setColor(ContextCompat.getColor(this@KelolaUserActivity, R.color.brand_accent_light))
            }
            background = editBg
            insetTop = 0
            insetBottom = 0
            minWidth = 0
            minHeight = 0
            minimumWidth = 0
            minimumHeight = 0
            setPadding((8 * density).toInt(), 0, (8 * density).toInt(), 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, (32 * density).toInt()
            ).apply {
                marginEnd = (4 * density).toInt()
            }
            setOnClickListener {
                dialogEditUser(user)
            }
        }
        actionCol.addView(btnEdit)

        val currentUsername = SessionHelper.getUsername(this)
        if (!user.username.equals(currentUsername, ignoreCase = true)) {
            val btnDelete = MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
                text = "Hapus"
                textSize = 11f
                setTypeface(null, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(this@KelolaUserActivity, R.color.error))
                val deleteBg = GradientDrawable().apply {
                    cornerRadius = 8 * density
                    setColor(Color.parseColor("#FEE2E2"))
                }
                background = deleteBg
                insetTop = 0
                insetBottom = 0
                minWidth = 0
                minHeight = 0
                minimumWidth = 0
                minimumHeight = 0
                setPadding((8 * density).toInt(), 0, (8 * density).toInt(), 0)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, (32 * density).toInt()
                )
                setOnClickListener {
                    konfirmasiHapus(user)
                }
            }
            actionCol.addView(btnDelete)
        }

        innerContent.addView(actionCol)
        rootRow.addView(innerContent)
        card.addView(rootRow)

        // Smooth Entrance Animation (Slide up & Alpha fade)
        card.translationY = 40f * density
        card.alpha = 0f
        card.animate()
            .translationY(0f)
            .alpha(1.0f)
            .setDuration(280)
            .setStartDelay(index * 45L)
            .setInterpolator(DecelerateInterpolator())
            .start()

        return card
    }

    private fun buatCustomDialogHeader(iconRes: Int, title: String, subtitle: String): View {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(24, 20, 24, 12)

        val headerRow = LinearLayout(this)
        headerRow.orientation = LinearLayout.HORIZONTAL
        headerRow.gravity = Gravity.CENTER_VERTICAL

        val cardIcon = MaterialCardView(this)
        cardIcon.radius = 12f
        cardIcon.cardElevation = 0f
        cardIcon.setCardBackgroundColor(ContextCompat.getColor(this, R.color.brand_accent_light))
        val iconParams = LinearLayout.LayoutParams(42, 42)
        cardIcon.layoutParams = iconParams

        val iv = ImageView(this)
        iv.setImageResource(iconRes)
        iv.setColorFilter(ContextCompat.getColor(this, R.color.brand_primary))
        iv.setPadding(10, 10, 10, 10)
        cardIcon.addView(iv)
        headerRow.addView(cardIcon)

        val textCol = LinearLayout(this)
        textCol.orientation = LinearLayout.VERTICAL
        textCol.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = 14
        }

        val tvTitle = TextView(this)
        tvTitle.text = title
        tvTitle.textSize = 16f
        tvTitle.setTypeface(null, Typeface.BOLD)
        tvTitle.setTextColor(ContextCompat.getColor(this, R.color.brand_primary))
        textCol.addView(tvTitle)

        val tvSub = TextView(this)
        tvSub.text = subtitle
        tvSub.textSize = 12f
        tvSub.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        tvSub.setPadding(0, 2, 0, 0)
        textCol.addView(tvSub)

        headerRow.addView(textCol)
        root.addView(headerRow)

        val divider = View(this)
        val divParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2).apply {
            topMargin = 14
        }
        divider.layoutParams = divParams
        divider.setBackgroundColor(ContextCompat.getColor(this, R.color.divider))
        root.addView(divider)

        return root
    }

    private fun createFormInputLayout(hintText: String, isPassword: Boolean = false): Pair<TextInputLayout, TextInputEditText> {
        val til = TextInputLayout(this, null, com.google.android.material.R.attr.textInputStyle)
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 12 }
        til.layoutParams = params
        til.hint = hintText
        til.boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
        til.setBoxCornerRadii(12f, 12f, 12f, 12f)
        til.setBoxStrokeColorStateList(ContextCompat.getColorStateList(this, R.color.brand_primary)!!)
        til.setBoxBackgroundColorResource(R.color.white)

        if (isPassword) {
            til.endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
        }

        val et = TextInputEditText(til.context)
        et.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        if (isPassword) {
            et.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        } else {
            et.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_FILTER
        }
        et.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        et.setTextSize(14f)

        til.addView(et)
        return Pair(til, et)
    }

    // ==================== DIALOG TAMBAH USER ====================
    private fun dialogTambahUser() {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL

        val header = buatCustomDialogHeader(
            android.R.drawable.ic_input_add,
            "Tambah Akun Teknisi Baru",
            "Buat akun baru untuk teknisi agar dapat login ke aplikasi"
        )
        root.addView(header)

        val formContainer = LinearLayout(this)
        formContainer.orientation = LinearLayout.VERTICAL
        formContainer.setPadding(24, 8, 24, 16)

        val (tilName, etName) = createFormInputLayout("Nama Lengkap Teknisi *")
        val (tilUsername, etUsername) = createFormInputLayout("Username / UserID Login *")
        val (tilPassword, etPassword) = createFormInputLayout("Password Akun *", isPassword = true)

        formContainer.addView(tilName)
        formContainer.addView(tilUsername)
        formContainer.addView(tilPassword)

        val tvRoleLabel = TextView(this)
        tvRoleLabel.text = "Hak Akses / Role Akun"
        tvRoleLabel.textSize = 12f
        tvRoleLabel.setTypeface(null, Typeface.BOLD)
        tvRoleLabel.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        tvRoleLabel.setPadding(4, 14, 0, 6)
        formContainer.addView(tvRoleLabel)

        val spinnerRole = Spinner(this)
        val roleOptions = arrayOf("User (Teknisi)", "Admin (Akses Penuh)")
        val adapterRole = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, roleOptions)
        spinnerRole.adapter = adapterRole

        val spinnerBg = GradientDrawable()
        spinnerBg.setColor(Color.WHITE)
        spinnerBg.cornerRadius = 12f
        spinnerBg.setStroke(2, ContextCompat.getColor(this, R.color.brand_primary))

        val spinnerContainer = LinearLayout(this)
        spinnerContainer.background = spinnerBg
        spinnerContainer.setPadding(16, 6, 16, 6)
        val containerParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        spinnerContainer.layoutParams = containerParams
        spinnerContainer.addView(spinnerRole)
        formContainer.addView(spinnerContainer)

        root.addView(formContainer)

        AlertDialog.Builder(this)
            .setView(root)
            .setPositiveButton("Simpan Akun") { _, _ ->
                val name = etName.text.toString().trim()
                val username = etUsername.text.toString().trim()
                val password = etPassword.text.toString().trim()
                val roleSelected = if (spinnerRole.selectedItemPosition == 1) "admin" else "user"

                if (username.isEmpty() || password.isEmpty()) {
                    Toast.makeText(this, "Username dan Password wajib diisi!", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                kirimTambahUser(name, username, password, roleSelected, true)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    // ==================== DIALOG EDIT USER ====================
    private fun dialogEditUser(user: UserItem) {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL

        val header = buatCustomDialogHeader(
            R.drawable.ic_person,
            "Edit Akun Teknisi",
            "Ubah nama, username, password, atau hak akses @${user.username}"
        )
        root.addView(header)

        val formContainer = LinearLayout(this)
        formContainer.orientation = LinearLayout.VERTICAL
        formContainer.setPadding(24, 8, 24, 16)

        val (tilName, etName) = createFormInputLayout("Nama Lengkap Teknisi *")
        etName.setText(user.name)

        val (tilUsername, etUsername) = createFormInputLayout("Username / UserID Login *")
        etUsername.setText(user.username)

        val (tilPassword, etPassword) = createFormInputLayout("Password Baru (kosongkan jika tidak diubah)", isPassword = true)

        formContainer.addView(tilName)
        formContainer.addView(tilUsername)
        formContainer.addView(tilPassword)

        val tvRoleLabel = TextView(this)
        tvRoleLabel.text = "Hak Akses / Role Akun"
        tvRoleLabel.textSize = 12f
        tvRoleLabel.setTypeface(null, Typeface.BOLD)
        tvRoleLabel.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        tvRoleLabel.setPadding(4, 14, 0, 6)
        formContainer.addView(tvRoleLabel)

        val spinnerRole = Spinner(this)
        val roleOptions = arrayOf("User (Teknisi)", "Admin (Akses Penuh)")
        val adapterRole = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, roleOptions)
        spinnerRole.adapter = adapterRole
        val selectedRoleIndex = if (user.role.equals("admin", ignoreCase = true)) 1 else 0
        spinnerRole.setSelection(selectedRoleIndex)

        val spinnerBg = GradientDrawable()
        spinnerBg.setColor(Color.WHITE)
        spinnerBg.cornerRadius = 12f
        spinnerBg.setStroke(2, ContextCompat.getColor(this, R.color.brand_primary))

        val spinnerContainer = LinearLayout(this)
        spinnerContainer.background = spinnerBg
        spinnerContainer.setPadding(16, 6, 16, 6)
        val containerParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        spinnerContainer.layoutParams = containerParams
        spinnerContainer.addView(spinnerRole)
        formContainer.addView(spinnerContainer)

        root.addView(formContainer)

        AlertDialog.Builder(this)
            .setView(root)
            .setPositiveButton("Simpan Perubahan") { _, _ ->
                val newName = etName.text.toString().trim()
                val newUsername = etUsername.text.toString().trim()
                val newPassword = etPassword.text.toString().trim()
                val roleSelected = if (spinnerRole.selectedItemPosition == 1) "admin" else "user"

                if (newUsername.isEmpty()) {
                    Toast.makeText(this, "Username tidak boleh kosong!", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (!newUsername.equals(user.username, ignoreCase = true)) {
                    kirimRenameUser(user.username, newUsername) { sukses ->
                        if (sukses) {
                            if (user.username.equals(SessionHelper.getUsername(this), ignoreCase = true)) {
                                SessionHelper.saveUsername(this, newUsername)
                            }
                            kirimEditUser(newUsername, newName, newPassword, roleSelected, true)
                        }
                    }
                } else {
                    kirimEditUser(user.username, newName, newPassword, roleSelected, true)
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    // ==================== DIALOG HAPUS USER ====================
    private fun konfirmasiHapus(user: UserItem) {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(24, 20, 24, 16)

        val headerCard = MaterialCardView(this)
        headerCard.radius = 14f
        headerCard.cardElevation = 0f
        headerCard.setCardBackgroundColor(Color.parseColor("#FEE2E2"))
        val cardParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 16 }
        headerCard.layoutParams = cardParams

        val headerRow = LinearLayout(this)
        headerRow.orientation = LinearLayout.HORIZONTAL
        headerRow.gravity = Gravity.CENTER_VERTICAL
        headerRow.setPadding(18, 14, 18, 14)

        val ivWarn = ImageView(this)
        ivWarn.setImageResource(android.R.drawable.ic_dialog_alert)
        ivWarn.setColorFilter(ContextCompat.getColor(this, R.color.error))
        val ivParams = LinearLayout.LayoutParams(36, 36)
        ivWarn.layoutParams = ivParams
        headerRow.addView(ivWarn)

        val textCol = LinearLayout(this)
        textCol.orientation = LinearLayout.VERTICAL
        textCol.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = 14
        }

        val tvTitle = TextView(this)
        tvTitle.text = "Konfirmasi Hapus Akun"
        tvTitle.textSize = 16f
        tvTitle.setTypeface(null, Typeface.BOLD)
        tvTitle.setTextColor(ContextCompat.getColor(this, R.color.error))
        textCol.addView(tvTitle)

        val tvSub = TextView(this)
        tvSub.text = "Tindakan ini tidak dapat dibatalkan"
        tvSub.textSize = 12f
        tvSub.setTextColor(Color.parseColor("#991B1B"))
        textCol.addView(tvSub)

        headerRow.addView(textCol)
        headerCard.addView(headerRow)
        root.addView(headerCard)

        val tvMsg = TextView(this)
        tvMsg.text = "Apakah Anda yakin ingin menghapus akun teknisi @${user.username} (${user.name})?\n\nPengguna ini tidak akan dapat login lagi ke aplikasi setelah dihapus."
        tvMsg.textSize = 14f
        tvMsg.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        tvMsg.setLineSpacing(4f, 1f)
        root.addView(tvMsg)

        AlertDialog.Builder(this)
            .setView(root)
            .setPositiveButton("Ya, Hapus Akun") { _, _ -> kirimHapusUser(user.username) }
            .setNegativeButton("Batal", null)
            .show()
    }

    // ==================== KIRIM RENAME ====================
    private fun kirimRenameUser(oldUsername: String, newUsername: String, onDone: (Boolean) -> Unit) {
        showLoading("Mengganti username $oldUsername menjadi $newUsername...")
        val queue = VolleyHelper.getRequestQueue(this)
        val request = object : StringRequest(
            Method.POST, urlSheet,
            { response ->
                hideLoading()
                try {
                    val json = JSONObject(response)
                    Toast.makeText(this, json.optString("message"), Toast.LENGTH_LONG).show()
                    val sukses = json.optString("status") == "success"
                    onDone(sukses)
                    if (sukses) loadUsers()
                } catch (e: Exception) {
                    Toast.makeText(this, "Gagal memproses respons rename", Toast.LENGTH_SHORT).show()
                    onDone(false)
                }
            },
            { error ->
                hideLoading()
                Toast.makeText(this, "Koneksi terputus", Toast.LENGTH_SHORT).show()
                onDone(false)
            }
        ) {
            override fun getHeaders(): MutableMap<String, String> {
                val headers = HashMap<String, String>()
                headers["Connection"] = "close"
                return headers
            }

            override fun getBodyContentType(): String {
                return "application/x-www-form-urlencoded; charset=utf-8"
            }

            override fun getParamsEncoding(): String {
                return "UTF-8"
            }

            override fun getParams(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["action"] = "rename_user"
                params["requester_role"] = SessionHelper.getRole(this@KelolaUserActivity)
                params["old_username"] = oldUsername
                params["new_username_value"] = newUsername
                return params
            }
        }
        VolleyHelper.apply60sTimeout(request)
        queue.add(request)
    }

    // ==================== KIRIM REQUEST (TAMBAH, EDIT, HAPUS) ====================
    private fun kirimTambahUser(name: String, username: String, password: String, role: String, canEdit: Boolean) {
        showLoading("Menambahkan user $username...")
        val queue = VolleyHelper.getRequestQueue(this)
        val request = object : StringRequest(
            Method.POST, urlSheet,
            { response ->
                hideLoading()
                handleSimpleResponse(response)
            },
            { error ->
                hideLoading()
                Toast.makeText(this, "Koneksi terputus", Toast.LENGTH_SHORT).show()
            }
        ) {
            override fun getHeaders(): MutableMap<String, String> {
                val headers = HashMap<String, String>()
                headers["Connection"] = "close"
                return headers
            }

            override fun getBodyContentType(): String {
                return "application/x-www-form-urlencoded; charset=utf-8"
            }

            override fun getParamsEncoding(): String {
                return "UTF-8"
            }

            override fun getParams(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["action"] = "add_user"
                params["requester_role"] = SessionHelper.getRole(this@KelolaUserActivity)
                params["new_name"] = name
                params["new_username"] = username
                params["new_password"] = password
                params["new_role"] = role
                params["can_edit"] = if (canEdit) "1" else "0"
                return params
            }
        }
        VolleyHelper.apply60sTimeout(request)
        queue.add(request)
    }

    private fun kirimEditUser(username: String, newName: String, newPassword: String, role: String, canEdit: Boolean) {
        showLoading("Menyimpan perubahan user $username...")
        val queue = VolleyHelper.getRequestQueue(this)
        val request = object : StringRequest(
            Method.POST, urlSheet,
            { response ->
                hideLoading()
                handleSimpleResponse(response)
            },
            { error ->
                hideLoading()
                Toast.makeText(this, "Koneksi terputus", Toast.LENGTH_SHORT).show()
            }
        ) {
            override fun getHeaders(): MutableMap<String, String> {
                val headers = HashMap<String, String>()
                headers["Connection"] = "close"
                return headers
            }

            override fun getBodyContentType(): String {
                return "application/x-www-form-urlencoded; charset=utf-8"
            }

            override fun getParamsEncoding(): String {
                return "UTF-8"
            }

            override fun getParams(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["action"] = "edit_user"
                params["requester_role"] = SessionHelper.getRole(this@KelolaUserActivity)
                params["target_username"] = username
                params["new_name"] = newName
                if (newPassword.isNotEmpty()) params["new_password"] = newPassword
                params["new_role"] = role
                params["can_edit"] = if (canEdit) "1" else "0"
                return params
            }
        }
        VolleyHelper.apply60sTimeout(request)
        queue.add(request)
    }

    private fun kirimHapusUser(username: String) {
        showLoading("Menghapus user $username...")

        val queue = VolleyHelper.getRequestQueue(this)

        val request = object : StringRequest(
            Method.POST, urlSheet,
            { response ->
                hideLoading()
                handleSimpleResponse(response)
            },
            { error ->
                hideLoading()
                Toast.makeText(this, "Koneksi terputus", Toast.LENGTH_SHORT).show()
            }
        ) {
            override fun getHeaders(): MutableMap<String, String> {
                val headers = HashMap<String, String>()
                headers["Connection"] = "close"
                return headers
            }

            override fun getBodyContentType(): String {
                return "application/x-www-form-urlencoded; charset=utf-8"
            }

            override fun getParamsEncoding(): String {
                return "UTF-8"
            }

            override fun getParams(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["action"] = "delete_user"
                params["requester_role"] = SessionHelper.getRole(this@KelolaUserActivity)
                params["target_username"] = username
                return params
            }
        }
        VolleyHelper.apply60sTimeout(request)
        queue.add(request)
    }

    private fun handleSimpleResponse(response: String) {
        try {
            val json = JSONObject(response)
            val msg = json.optString("message", "Proses selesai")
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            if (json.optString("status") == "success") {
                loadUsers()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal membaca respons server", Toast.LENGTH_SHORT).show()
        }
    }
}
