package com.example.zaman_apps

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.example.zaman_apps.databinding.ActivityKelolaUserBinding
import com.example.zaman_apps.utils.SessionHelper
import org.json.JSONArray
import org.json.JSONObject

class KelolaUserActivity : AppCompatActivity() {

    private lateinit var binding: ActivityKelolaUserBinding
    // BARU — samakan dengan deployment aktif yang dipakai LoginActivity
    private val urlSheet = "https://script.google.com/macros/s/AKfycby4n_8DasDqYLL-K7DQMUb_6pyF7FkuDaSWde98TDMlF0BnneJjVVwadqzuRzq-M1KguA/exec"

    private var userList = listOf<JSONObject>()
    private var isLoading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityKelolaUserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.fabTambahUser.setOnClickListener { dialogTambahUser() }

        // Search TextWatcher
        binding.etSearchUser.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterUsers(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // 🔥 SWIPE TO REFRESH
        binding.swipeRefresh.setColorSchemeResources(R.color.brand_primary)
        binding.swipeRefresh.setOnRefreshListener {
            loadUsers(skipLoading = true)
        }

        loadUsers()
    }

    private fun filterUsers(query: String) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) {
            tampilkanUsers(userList)
            return
        }
        val filtered = userList.filter { item ->
            val username = item.optString("username").lowercase()
            val name = item.optString("name").lowercase()
            val role = item.optString("role").lowercase()
            username.contains(q) || name.contains(q) || role.contains(q)
        }
        tampilkanUsers(filtered)
    }

    // ==================== LOADING OVERLAY ====================
    private fun showLoading(message: String = "Memuat data user...") {
        if (!::binding.isInitialized) return
        isLoading = true
        binding.overlayLoading.visibility = View.VISIBLE
        binding.tvLoadingMessage.text = message
        binding.fabTambahUser.isEnabled = false
        binding.fabTambahUser.alpha = 0.5f
    }

    private fun hideLoading() {
        if (!::binding.isInitialized) return
        isLoading = false
        binding.overlayLoading.visibility = View.GONE
        binding.fabTambahUser.isEnabled = true
        binding.fabTambahUser.alpha = 1.0f
    }

    // ==================== LOAD USERS ====================
    private fun loadUsers(skipLoading: Boolean = false) {
        if (!skipLoading) {
            showLoading("Memuat daftar user...")
        }
        val queue = Volley.newRequestQueue(this)
        val request = object : StringRequest(
            Request.Method.POST, urlSheet,
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
                    is java.net.SocketTimeoutException -> "Koneksi timeout, coba lagi"
                    is java.net.UnknownHostException -> "Tidak ada koneksi internet"
                    else -> "Koneksi terputus: ${error.message}"
                }
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        ) {
            override fun getParams(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["action"] = "list_users"
                params["requester_role"] = SessionHelper.getRole(this@KelolaUserActivity)
                return params
            }
        }
        request.retryPolicy = DefaultRetryPolicy(15000, 1, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT)
        queue.add(request)
    }

    // ==================== PARSE & TAMPILKAN ====================
    private fun parseUsers(usersArray: JSONArray): List<JSONObject> {
        val list = mutableListOf<JSONObject>()
        for (i in 0 until usersArray.length()) {
            list.add(usersArray.getJSONObject(i))
        }
        return list
    }

    private fun tampilkanUsers(users: List<JSONObject>) {
        val total = users.size
        binding.tvTotalUsers.text = "Total $total user terdaftar"
        binding.containerUsers.removeAllViews()

        if (users.isEmpty()) {
            val tv = TextView(this)
            tv.text = "📭 Belum ada user terdaftar."
            tv.textSize = 15f
            tv.gravity = Gravity.CENTER
            tv.setPadding(0, 64, 0, 64)
            tv.setTextColor(Color.parseColor("#888888"))
            binding.containerUsers.addView(tv)
            return
        }

        users.forEachIndexed { index, item ->
            val card = buatCardUser(item)

            // 1. Sembunyikan card dan turunkan posisinya SEBELUM dimasukkan ke layout
            card.alpha = 0f
            card.translationY = 150f

            // 2. Masukkan card ke dalam container
            binding.containerUsers.addView(card)

            // 3. Gunakan .post { } agar animasi berjalan TEPAT setelah UI siap digambar
            card.post {
                card.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(500) // Sedikit diperlambat agar mulus
                    .setStartDelay((index * 80).toLong()) // Jarak waktu antar card
                    .setInterpolator(android.view.animation.OvershootInterpolator(1.2f))
                    .start()
            }
        }
    }

    // ==================== CEK DUPLIKAT ====================
    private fun isUsernameExists(username: String, excludeUsername: String? = null): Boolean {
        val normalized = username.trim().lowercase()
        return userList.any { user ->
            val existing = user.optString("username").trim().lowercase()
            if (excludeUsername != null && existing == excludeUsername.trim().lowercase()) {
                false
            } else {
                existing == normalized
            }
        }
    }

    // ==================== CARD USER ====================
    private fun buatCardUser(item: JSONObject): View {
        val username = item.optString("username")
        val name = item.optString("name").ifEmpty { username }
        val role = item.optString("role")
        val canEdit = item.optBoolean("can_edit")

        val card = com.google.android.material.card.MaterialCardView(this)
        card.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 28 } // Ruang antar kartu diperlebar
        card.radius = 28f // Radius melengkung elegan
        card.cardElevation = 6f // Efek melayang (shadow)
        card.strokeWidth = 0 // Bersih tanpa garis tepi

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(36, 32, 24, 32)

        // AVATAR INISIAL (Baru)
        val avatar = TextView(this)
        avatar.text = name.firstOrNull()?.uppercase() ?: "U"
        avatar.setTextColor(Color.WHITE)
        avatar.textSize = 18f
        avatar.setTypeface(null, android.graphics.Typeface.BOLD)
        avatar.gravity = Gravity.CENTER
        avatar.layoutParams = LinearLayout.LayoutParams(100, 100).apply {
            marginEnd = 32
        }
        val bgAvatar = android.graphics.drawable.GradientDrawable()
        bgAvatar.shape = android.graphics.drawable.GradientDrawable.OVAL
        // Warna avatar beda untuk Admin (Merah) dan User (Biru)
        bgAvatar.setColor(if (role == "admin") Color.parseColor("#FF5252") else Color.parseColor("#448AFF"))
        avatar.background = bgAvatar
        row.addView(avatar)

        val infoCol = LinearLayout(this)
        infoCol.orientation = LinearLayout.VERTICAL
        infoCol.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

        val tvName = TextView(this)
        tvName.text = name
        tvName.textSize = 16f
        tvName.setTypeface(null, android.graphics.Typeface.BOLD)
        tvName.setTextColor(Color.parseColor("#1A1A1A"))
        infoCol.addView(tvName)

        val tvUsername = TextView(this)
        tvUsername.text = "@$username"
        tvUsername.textSize = 13f
        tvUsername.setTextColor(Color.parseColor("#777777"))
        tvUsername.setPadding(0, 4, 0, 4)
        infoCol.addView(tvUsername)

        val tvRole = TextView(this)
        val roleLabel = if (role == "admin") "🛡️ Admin" else "👤 User"
        val editLabel = if (canEdit) " • Bisa edit" else " • Tidak bisa edit"
        tvRole.text = roleLabel + editLabel
        tvRole.textSize = 12f
        tvRole.setTextColor(Color.parseColor("#999999"))
        infoCol.addView(tvRole)

        row.addView(infoCol)

        // Wadah Tombol Aksi
        val actionCol = LinearLayout(this)
        actionCol.orientation = LinearLayout.HORIZONTAL
        actionCol.gravity = Gravity.CENTER_VERTICAL

        val btnEdit = ImageButtonSimple("✏️") { dialogEditUser(username, name, role, canEdit) }
        val btnDelete = ImageButtonSimple("🗑️") { konfirmasiHapus(username) }

        actionCol.addView(btnEdit)
        actionCol.addView(btnDelete)

        row.addView(actionCol)
        card.addView(row)
        return card
    }

    private fun ImageButtonSimple(emoji: String, onClick: () -> Unit): TextView {
        val tv = TextView(this)
        tv.text = emoji
        tv.textSize = 18f
        tv.setPadding(24, 12, 24, 12)
        tv.setOnClickListener { onClick() }
        tv.isClickable = true
        tv.isFocusable = true
        val outValue = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true)
        tv.setBackgroundResource(outValue.resourceId)
        return tv
    }

    // ==================== DIALOG TAMBAH USER ====================
    private fun dialogTambahUser() {
        val layout = buatFormDialog(null, null, null)
        AlertDialog.Builder(this)
            .setTitle("Tambah User Baru")
            .setView(layout.root)
            .setPositiveButton("Simpan") { _, _ ->
                val name = layout.etName.text.toString().trim()
                val username = layout.etUsername.text.toString().trim()
                val password = layout.etPassword.text.toString().trim()
                val role = layout.spinnerRole.selectedItem.toString().lowercase()
                val canEdit = true

                if (name.isEmpty() || username.isEmpty() || password.isEmpty()) {
                    Toast.makeText(this, "Nama, Username & password wajib diisi", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (isUsernameExists(username)) {
                    AlertDialog.Builder(this)
                        .setTitle("Username Sudah Ada")
                        .setMessage("User dengan username \"$username\" sudah terdaftar.\nGunakan username lain.")
                        .setPositiveButton("OK", null)
                        .show()
                    return@setPositiveButton
                }

                kirimTambahUser(name, username, password, role, canEdit)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    // ==================== DIALOG EDIT USER ====================
// BARU
    private fun dialogEditUser(username: String, currentName: String, currentRole: String, currentCanEdit: Boolean) {
        val layout = buatFormDialog(username, currentName, currentRole)

        AlertDialog.Builder(this)
            .setTitle("Edit User: $currentName")
            .setView(layout.root)
            .setPositiveButton("Simpan") { _, _ ->
                val newName = layout.etName.text.toString().trim()
                val newUsername = layout.etUsername.text.toString().trim()
                val newPassword = layout.etPassword.text.toString().trim()
                val role = layout.spinnerRole.selectedItem.toString().lowercase()
                val canEdit = role == "admin"

                if (newUsername.isEmpty() || newName.isEmpty()) {
                    Toast.makeText(this, "Nama & Username tidak boleh kosong", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
// BARU (BENAR)
                if (newUsername != username && isUsernameExists(newUsername, excludeUsername = username)) {
                    AlertDialog.Builder(this)
                        .setTitle("Username Sudah Ada")
                        .setMessage("User dengan username \"$newUsername\" sudah terdaftar.\nGunakan username lain.")
                        .setPositiveButton("OK", null)
                        .show()
                    return@setPositiveButton
                }

                if (newUsername != username) {
                    konfirmasiRename(username, newUsername, newName, role, canEdit, newPassword)
                } else {
                    kirimEditUser(username, newName, newPassword, role, canEdit)
                }
                }
            .setNegativeButton("Batal", null)
            .show()
    }

    // ==================== RENAME KONFIRMASI ====================
// BARU
    private fun konfirmasiRename(oldUsername: String, newUsername: String, newName: String, role: String, canEdit: Boolean, newPassword: String) {
        AlertDialog.Builder(this)
            .setTitle("Ganti Username?")
            .setMessage("Username \"$oldUsername\" akan diganti jadi \"$newUsername\".\nSeluruh histori aktivitas (log input/edit) juga akan diperbarui otomatis.\nLanjutkan?")
            .setPositiveButton("Ya, Ganti") { _, _ ->
                kirimRenameUser(oldUsername, newUsername) { berhasil ->
                    if (berhasil) {
                        kirimEditUser(newUsername, newName, newPassword, role, canEdit)
                    }
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    // ==================== KIRIM RENAME ====================
    private fun kirimRenameUser(oldUsername: String, newUsername: String, onDone: (Boolean) -> Unit) {
        showLoading("Mengganti username $oldUsername menjadi $newUsername...")
        val queue = Volley.newRequestQueue(this)
        val request = object : StringRequest(
            Request.Method.POST, urlSheet,
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
            override fun getParams(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["action"] = "rename_user"
                params["requester_role"] = SessionHelper.getRole(this@KelolaUserActivity)
                params["old_username"] = oldUsername
                params["new_username_value"] = newUsername
                return params
            }
        }
        request.retryPolicy = DefaultRetryPolicy(15000, 1, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT)
        queue.add(request)
    }

    // ==================== FORM DIALOG HELPER ====================
// BARU
    private data class FormDialogViews(
        val root: LinearLayout,
        val etName: EditText,
        val etUsername: EditText,
        val etPassword: EditText,
        val spinnerRole: Spinner
    )

    private fun buatFormDialog(username: String?, name: String?, role: String?): FormDialogViews {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(64, 48, 64, 24) // Spacing luar yang lebih lega dan proporsional

        // Fungsi internal untuk mencetak EditText yang seragam dan elegan
        fun createStyledEditText(hintText: String, defaultText: String?, isPassword: Boolean = false): EditText {
            val et = EditText(this)
            et.hint = hintText
            et.setText(defaultText ?: "")
            et.textSize = 15f
            et.setPadding(48, 36, 48, 36) // Ruang lega di dalam kotak input
            et.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 36 } // Jarak antar kotak input

            // Latar belakang kotak input: Rounded & Soft Grey
            val bg = android.graphics.drawable.GradientDrawable()
            bg.setColor(Color.parseColor("#F8F9FA")) // Latar abu-abu sangat terang
            bg.cornerRadius = 24f // Sudut melengkung
            bg.setStroke(3, Color.parseColor("#E9ECEF")) // Garis tepi abu-abu halus
            et.background = bg

            if (isPassword) {
                et.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            return et
        }

        // Pembuatan Input Form
        val etName = createStyledEditText("👤 Nama Lengkap", name)
        root.addView(etName)

        val etUsername = createStyledEditText("🏷️ Username", username)
        root.addView(etUsername)

        val passwordHint = if (username == null) "🔒 Password" else "🔒 Password baru (kosongkan jika tetap)"
        val etPassword = createStyledEditText(passwordHint, null, true)
        root.addView(etPassword)

        // Label untuk Spinner
        val tvRoleLabel = TextView(this)
        tvRoleLabel.text = "Role / Hak Akses"
        tvRoleLabel.textSize = 13f
        tvRoleLabel.setTextColor(Color.parseColor("#777777"))
        tvRoleLabel.setPadding(12, 0, 0, 12)
        root.addView(tvRoleLabel)

        // Wadah Spinner agar desainnya seragam dengan EditText (Rounded)
        val spinnerContainer = LinearLayout(this)
        val bgSpinner = android.graphics.drawable.GradientDrawable()
        bgSpinner.setColor(Color.parseColor("#F8F9FA"))
        bgSpinner.cornerRadius = 24f
        bgSpinner.setStroke(3, Color.parseColor("#E9ECEF"))
        spinnerContainer.background = bgSpinner
        spinnerContainer.setPadding(24, 8, 24, 8)
        spinnerContainer.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )

        val spinnerRole = Spinner(this)
        // Huruf depan kapital agar rapi (sistem lama tetap aman karena menggunakan .lowercase() saat ditarik)
        val roleAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("User", "Admin"))
        spinnerRole.adapter = roleAdapter
        spinnerRole.setSelection(if (role == "admin") 1 else 0)
        spinnerRole.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )

        spinnerContainer.addView(spinnerRole)
        root.addView(spinnerContainer)

        return FormDialogViews(root, etName, etUsername, etPassword, spinnerRole)
    }

    // ==================== HAPUS USER ====================
    private fun konfirmasiHapus(username: String) {
        AlertDialog.Builder(this)
            .setTitle("Hapus User")
            .setMessage("Yakin ingin menghapus \"$username\"? Tindakan ini tidak bisa dibatalkan.")
            .setPositiveButton("Hapus") { _, _ -> kirimHapusUser(username) }
            .setNegativeButton("Batal", null)
            .show()
    }

    // ==================== KIRIM REQUEST (TAMBAH, EDIT, HAPUS) ====================
    private fun kirimTambahUser(name: String, username: String, password: String, role: String, canEdit: Boolean) {
        showLoading("Menambahkan user $username...")
        val queue = Volley.newRequestQueue(this)
        val request = object : StringRequest(
            Request.Method.POST, urlSheet,
            { response ->
                hideLoading()
                handleSimpleResponse(response)
            },
            { error ->
                hideLoading()
                Toast.makeText(this, "Koneksi terputus", Toast.LENGTH_SHORT).show()
            }
        ) {
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
        request.retryPolicy = DefaultRetryPolicy(15000, 1, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT)
        queue.add(request)
    }

    // BARU
    private fun kirimEditUser(username: String, newName: String, newPassword: String, role: String, canEdit: Boolean) {
        showLoading("Menyimpan perubahan user $username...")
        val queue = Volley.newRequestQueue(this)
        val request = object : StringRequest(
            Request.Method.POST, urlSheet,
            { response ->
                hideLoading()
                handleSimpleResponse(response)
            },
            { error ->
                hideLoading()
                Toast.makeText(this, "Koneksi terputus", Toast.LENGTH_SHORT).show()
            }
        ) {
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
        request.retryPolicy = DefaultRetryPolicy(15000, 1, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT)
        queue.add(request)
    }

    private fun kirimHapusUser(username: String) {
        showLoading("Menghapus user $username...")

        // PERBAIKAN 1: Tambahkan HurlStack untuk mengikuti redirect dari Google Apps Script
        val queue = Volley.newRequestQueue(this, object : com.android.volley.toolbox.HurlStack() {
            override fun createConnection(url: java.net.URL): java.net.HttpURLConnection {
                val connection = super.createConnection(url)
                connection.instanceFollowRedirects = true
                return connection
            }
        })

        val request = object : StringRequest(
            Request.Method.POST, urlSheet,
            { response ->
                hideLoading()
                handleSimpleResponse(response)
            },
            { error ->
                hideLoading()
                Toast.makeText(this, "Koneksi terputus", Toast.LENGTH_SHORT).show()
            }
        ) {
            override fun getParams(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["action"] = "delete_user"
                params["requester_role"] = SessionHelper.getRole(this@KelolaUserActivity)
                params["requester_username"] = SessionHelper.getUsername(this@KelolaUserActivity)
                params["target_username"] = username
                return params
            }
        }

        // PERBAIKAN 2: Naikkan timeout menjadi 30 detik (30000 ms) untuk HP fisik
        request.retryPolicy = DefaultRetryPolicy(30000, 1, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT)
        queue.add(request)
    }

    private fun handleSimpleResponse(response: String) {
        try {
            val json = JSONObject(response)
            Toast.makeText(this, json.optString("message"), Toast.LENGTH_SHORT).show()
            if (json.optString("status") == "success") loadUsers()
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal memproses respons", Toast.LENGTH_SHORT).show()
        }
    }
}