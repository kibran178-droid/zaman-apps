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
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
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
import com.example.zaman_apps.utils.SessionHelper
import com.example.zaman_apps.utils.VolleyHelper
import com.google.android.material.card.MaterialCardView
import org.json.JSONArray
import org.json.JSONObject
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class KelolaUserActivity : AppCompatActivity() {

    private lateinit var binding: ActivityKelolaUserBinding
    private val urlSheet = "https://script.google.com/macros/s/AKfycby4n_8DasDqYLL-K7DQMUb_6pyF7FkuDaSWde98TDMlF0BnneJjVVwadqzuRzq-M1KguA/exec"

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
                    canEdit = obj.optBoolean("can_edit", false)
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
        tvUsername.setPadding(0, 2, 0, 8)
        infoCol.addView(tvUsername)

        val badgeRow = LinearLayout(this)
        badgeRow.orientation = LinearLayout.HORIZONTAL
        badgeRow.gravity = Gravity.CENTER_VERTICAL

        // Role Badge
        val tvRole = TextView(this)
        val isRoleAdmin = user.role.equals("admin", ignoreCase = true)
        tvRole.text = if (isRoleAdmin) "ADMIN" else "USER"
        tvRole.textSize = 11f
        tvRole.setTypeface(null, Typeface.BOLD)
        tvRole.setTextColor(Color.WHITE)
        val bgRole = GradientDrawable()
        bgRole.cornerRadius = 12f
        bgRole.setColor(ContextCompat.getColor(this, if (isRoleAdmin) R.color.brand_primary else R.color.brand_accent_light))
        tvRole.background = bgRole
        tvRole.setPadding(16, 6, 16, 6)
        badgeRow.addView(tvRole)

        // Can Edit Badge
        val tvCanEdit = TextView(this)
        tvCanEdit.text = if (user.canEdit) "✓ BISA EDIT" else "❌ NO EDIT"
        tvCanEdit.textSize = 11f
        tvCanEdit.setTypeface(null, Typeface.BOLD)
        tvCanEdit.setTextColor(ContextCompat.getColor(this, if (user.canEdit) R.color.brand_primary else R.color.text_secondary))
        val bgEdit = GradientDrawable()
        bgEdit.cornerRadius = 12f
        bgEdit.setColor(ContextCompat.getColor(this, if (user.canEdit) R.color.brand_accent_light else R.color.section_bg))
        tvCanEdit.background = bgEdit
        val editParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = 12 }
        tvCanEdit.layoutParams = editParams
        tvCanEdit.setPadding(16, 6, 16, 6)
        badgeRow.addView(tvCanEdit)

        infoCol.addView(badgeRow)
        root.addView(infoCol)

        // Action Edit
        val btnEdit = TextView(this)
        btnEdit.text = "✏️"
        btnEdit.textSize = 18f
        btnEdit.setPadding(16, 12, 16, 12)
        btnEdit.setOnClickListener {
            dialogEditUser(user)
        }
        root.addView(btnEdit)

        // Action Rename Username
        val btnRename = TextView(this)
        btnRename.text = "🏷️"
        btnRename.textSize = 18f
        btnRename.setPadding(16, 12, 16, 12)
        btnRename.setOnClickListener {
            dialogRenameUsername(user.username)
        }
        root.addView(btnRename)

        // Action Delete (hanya jika bukan akun diri sendiri / admin utama)
        val currentUsername = SessionHelper.getUsername(this)
        if (!user.username.equals(currentUsername, ignoreCase = true)) {
            val btnDelete = TextView(this)
            btnDelete.text = "🗑️"
            btnDelete.textSize = 18f
            btnDelete.setPadding(16, 12, 16, 12)
            btnDelete.setOnClickListener {
                konfirmasiHapus(user.username)
            }
            root.addView(btnDelete)
        }

        card.addView(root)
        return card
    }

    // ==================== DIALOG TAMBAH USER ====================
    private fun dialogTambahUser() {
        val views = buatFormDialogViews(isEdit = false)

        AlertDialog.Builder(this)
            .setTitle("Tambah User Baru")
            .setView(views.root)
            .setPositiveButton("Simpan") { _, _ ->
                val name = views.etName.text.toString().trim()
                val username = views.etUsername.text.toString().trim()
                val password = views.etPassword.text.toString().trim()
                val role = views.spinnerRole.selectedItem.toString().lowercase()

                if (username.isEmpty() || password.isEmpty()) {
                    Toast.makeText(this, "Username dan Password wajib diisi", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                kirimTambahUser(name, username, password, role, true)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    // ==================== DIALOG EDIT USER ====================
    private fun dialogEditUser(user: UserItem) {
        val views = buatFormDialogViews(isEdit = true)
        views.etName.setText(user.name)
        views.etUsername.setText(user.username)
        views.etUsername.isEnabled = false
        views.etUsername.alpha = 0.6f

        val roleOptions = arrayOf("User", "Admin")
        val selectedRoleIndex = if (user.role.equals("admin", ignoreCase = true)) 1 else 0
        views.spinnerRole.setSelection(selectedRoleIndex)

        AlertDialog.Builder(this)
            .setTitle("Edit User @${user.username}")
            .setView(views.root)
            .setPositiveButton("Simpan") { _, _ ->
                val name = views.etName.text.toString().trim()
                val password = views.etPassword.text.toString().trim()
                val role = views.spinnerRole.selectedItem.toString().lowercase()

                kirimEditUser(user.username, name, password, role, true)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    // ==================== DIALOG RENAME USERNAME ====================
    private fun dialogRenameUsername(oldUsername: String) {
        val etNewUsername = EditText(this)
        etNewUsername.hint = "Masukkan username baru"
        etNewUsername.setPadding(48, 36, 48, 36)

        val bg = GradientDrawable()
        bg.setColor(Color.parseColor("#F8F9FA"))
        bg.cornerRadius = 24f
        bg.setStroke(3, Color.parseColor("#E9ECEF"))
        etNewUsername.background = bg

        val container = LinearLayout(this)
        container.setPadding(48, 24, 48, 12)
        container.addView(etNewUsername)

        AlertDialog.Builder(this)
            .setTitle("Ganti Username @$oldUsername")
            .setMessage("Ganti username untuk \"$oldUsername\". Histori aktivitas user di Google Sheets juga akan diperbarui secara otomatis.")
            .setView(container)
            .setPositiveButton("Ganti Username") { _, _ ->
                val newUsername = etNewUsername.text.toString().trim()
                if (newUsername.isEmpty()) {
                    Toast.makeText(this, "Username baru tidak boleh kosong", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (newUsername.equals(oldUsername, ignoreCase = true)) {
                    Toast.makeText(this, "Username baru sama dengan yang lama", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                kirimRenameUser(oldUsername, newUsername) { sukses ->
                    if (sukses) {
                        if (oldUsername.equals(SessionHelper.getUsername(this), ignoreCase = true)) {
                            SessionHelper.saveUsername(this, newUsername)
                        }
                    }
                }
            }
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

    // ==================== FORM DIALOG HELPER ====================
    private class FormDialogViews(
        val root: View,
        val etName: EditText,
        val etUsername: EditText,
        val etPassword: EditText,
        val spinnerRole: Spinner
    )

    private fun buatFormDialogViews(isEdit: Boolean): FormDialogViews {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(48, 24, 48, 12)

        fun createStyledEditText(hintText: String, isPassword: Boolean = false): EditText {
            val et = EditText(this)
            et.hint = hintText
            if (isPassword) {
                et.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            et.setPadding(40, 32, 40, 32)
            val bg = GradientDrawable()
            bg.setColor(Color.parseColor("#F8F9FA"))
            bg.cornerRadius = 20f
            bg.setStroke(3, Color.parseColor("#E9ECEF"))
            et.background = bg
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 20 }
            et.layoutParams = params
            return et
        }

        val etName = createStyledEditText("Nama Lengkap")
        root.addView(etName)

        val etUsername = createStyledEditText("Username / UserID")
        root.addView(etUsername)

        val etPassword = createStyledEditText(if (isEdit) "Password Baru (Opsional)" else "Password", isPassword = true)
        root.addView(etPassword)

        val tvRoleLabel = TextView(this)
        tvRoleLabel.text = "Role User"
        tvRoleLabel.textSize = 13f
        tvRoleLabel.setTypeface(null, Typeface.BOLD)
        tvRoleLabel.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        tvRoleLabel.setPadding(0, 8, 0, 8)
        root.addView(tvRoleLabel)

        val spinnerRole = Spinner(this)
        val roleOptions = arrayOf("User", "Admin")
        val adapterRole = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, roleOptions)
        spinnerRole.adapter = adapterRole

        val spinnerBg = GradientDrawable()
        spinnerBg.setColor(Color.parseColor("#F8F9FA"))
        spinnerBg.cornerRadius = 20f
        spinnerBg.setStroke(3, Color.parseColor("#E9ECEF"))

        val spinnerContainer = LinearLayout(this)
        spinnerContainer.background = spinnerBg
        spinnerContainer.setPadding(20, 8, 20, 8)
        val containerParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 20 }
        spinnerContainer.layoutParams = containerParams
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
