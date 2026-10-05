package com.example.zaman_apps

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
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

        setupSearch()
        setupListeners()

        loadUsers()
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

    private fun filterUsers(query: String) {
        val q = query.trim().lowercase()
        filteredList = if (q.isEmpty()) {
            userList
        } else {
            userList.filter {
                it.username.lowercase().contains(q) || it.name.lowercase().contains(q)
            }
        }
        renderUsers()
    }

    private fun renderUsers() {
        binding.containerUsers.removeAllViews()
        binding.tvTotalUsers.text = "${filteredList.size} User"

        if (filteredList.isEmpty()) {
            val tvEmpty = TextView(this)
            tvEmpty.text = if (userList.isEmpty()) "Belum ada user terdaftar." else "Tidak ada user yang cocok."
            tvEmpty.gravity = Gravity.CENTER
            tvEmpty.setPadding(0, 64, 0, 64)
            tvEmpty.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            binding.containerUsers.addView(tvEmpty)
            return
        }

        filteredList.forEach { user ->
            val card = buatCardUser(user)
            binding.containerUsers.addView(card)
        }
    }

    private fun buatCardUser(user: UserItem): View {
        val card = MaterialCardView(this)
        val cardParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 16 }
        card.layoutParams = cardParams
        card.radius = 16f
        card.cardElevation = 2f
        card.strokeWidth = 1
        card.strokeColor = ContextCompat.getColor(this, R.color.divider)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.HORIZONTAL
        root.gravity = Gravity.CENTER_VERTICAL
        root.setPadding(24, 20, 20, 20)

        // Column Text Info
        val infoCol = LinearLayout(this)
        infoCol.orientation = LinearLayout.VERTICAL
        infoCol.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

        val tvName = TextView(this)
        tvName.text = user.name
        tvName.textSize = 15f
        tvName.setTypeface(null, Typeface.BOLD)
        tvName.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        infoCol.addView(tvName)

        val tvUsername = TextView(this)
        tvUsername.text = "@${user.username}"
        tvUsername.textSize = 13f
        tvUsername.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        tvUsername.setPadding(0, 2, 0, 6)
        infoCol.addView(tvUsername)

        val badgeRow = LinearLayout(this)
        badgeRow.orientation = LinearLayout.HORIZONTAL
        badgeRow.gravity = Gravity.CENTER_VERTICAL

        // Role Badge (ADMIN / USER)
        val tvRole = TextView(this)
        val isRoleAdmin = user.role.equals("admin", ignoreCase = true)
        tvRole.text = if (isRoleAdmin) "ADMIN" else "TEKNISI"
        tvRole.textSize = 10f
        tvRole.setTypeface(null, Typeface.BOLD)
        tvRole.setTextColor(Color.WHITE)
        val bgRole = GradientDrawable()
        bgRole.cornerRadius = 10f
        bgRole.setColor(ContextCompat.getColor(this, if (isRoleAdmin) R.color.brand_primary else R.color.brand_accent_light))
        tvRole.background = bgRole
        tvRole.setPadding(14, 4, 14, 4)
        badgeRow.addView(tvRole)

        infoCol.addView(badgeRow)
        root.addView(infoCol)

        // Action Buttons Row (Edit & Hapus)
        val actionRow = LinearLayout(this)
        actionRow.orientation = LinearLayout.HORIZONTAL
        actionRow.gravity = Gravity.CENTER_VERTICAL

        // Edit Button (Text)
        val btnEdit = MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle)
        btnEdit.text = "Edit"
        btnEdit.textSize = 13f
        btnEdit.setTypeface(null, Typeface.BOLD)
        btnEdit.setTextColor(ContextCompat.getColor(this, R.color.brand_primary))
        btnEdit.setOnClickListener {
            dialogEditUser(user)
        }
        actionRow.addView(btnEdit)

        // Hapus Button (Text - hanya jika bukan akun diri sendiri)
        val currentUsername = SessionHelper.getUsername(this)
        if (!user.username.equals(currentUsername, ignoreCase = true)) {
            val btnDelete = MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle)
            btnDelete.text = "Hapus"
            btnDelete.textSize = 13f
            btnDelete.setTypeface(null, Typeface.BOLD)
            btnDelete.setTextColor(ContextCompat.getColor(this, R.color.error))
            btnDelete.setOnClickListener {
                konfirmasiHapus(user)
            }
            actionRow.addView(btnDelete)
        }

        root.addView(actionRow)
        card.addView(root)
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
                    // Jika username diubah, kirim rename dulu lalu update
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
