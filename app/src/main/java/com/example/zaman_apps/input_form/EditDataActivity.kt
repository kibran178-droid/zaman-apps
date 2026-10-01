package com.example.zaman_apps.input_form

import android.Manifest
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.RotateAnimation
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.swiperefreshlayout.widget.CircularProgressDrawable
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.bumptech.glide.Glide
import com.example.zaman_apps.R
import com.example.zaman_apps.databinding.ActivityEditDataBinding
import com.example.zaman_apps.utils.DropdownOptionsHelper
import com.example.zaman_apps.utils.ImageCaptureHelper
import com.example.zaman_apps.utils.ImageHelper
import com.example.zaman_apps.utils.NotificationHelper
import com.example.zaman_apps.utils.SessionHelper
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import org.json.JSONArray
import org.json.JSONObject
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class EditDataActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditDataBinding
    private lateinit var notificationHelper: NotificationHelper
    private val urlSheet = "https://script.google.com/macros/s/AKfycby4n_8DasDqYLL-K7DQMUb_6pyF7FkuDaSWde98TDMlF0BnneJjVVwadqzuRzq-M1KguA/exec"

    private var selectedSiteId: String? = null
    private var siteIdsList = mutableListOf<String>()
    private var isLoading = false
    private var retryCount = 0
    private val maxRetries = 2
    private var isUpdating = false
    private var currentSiteId: String? = null
    private var isRefreshing = false
    private val dataCache = mutableMapOf<String, JSONObject>()

    // State Foto Site (sampai 10 foto)
    private val photoItemsEdit = mutableListOf<String>()
    private val base64CacheEdit = mutableMapOf<Int, String>()
    private var pendingPhotoSlotEdit = -1
    private var cameraUriEdit: Uri? = null
    private var currentPopupEditDialog: Dialog? = null

    private val pickPhotoEditLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            if (pendingPhotoSlotEdit in 0 until photoItemsEdit.size) {
                photoItemsEdit[pendingPhotoSlotEdit] = it.toString()
                prosesCompressBase64(it, pendingPhotoSlotEdit)
            } else if (photoItemsEdit.size < 10) {
                photoItemsEdit.add(it.toString())
                prosesCompressBase64(it, photoItemsEdit.size - 1)
            }
            updatePhotoCountBadge()
            showPopupLihatFotoEdit()
        }
    }

    private val addMultiplePhotosEditLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val spaceLeft = 10 - photoItemsEdit.size
            if (spaceLeft > 0) {
                val toAdd = uris.take(spaceLeft)
                toAdd.forEach { uri ->
                    photoItemsEdit.add(uri.toString())
                    prosesCompressBase64(uri, photoItemsEdit.size - 1)
                }
                updatePhotoCountBadge()
                showPopupLihatFotoEdit()
                showSnackbar("Berhasil menambahkan ${toAdd.size} foto")
            } else {
                showSnackbar("Maksimal 10 foto sudah tercapai", isError = true)
            }
        }
    }

    private val takePhotoEditLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraUriEdit != null) {
            val uri = cameraUriEdit!!
            if (pendingPhotoSlotEdit in 0 until photoItemsEdit.size) {
                photoItemsEdit[pendingPhotoSlotEdit] = uri.toString()
                prosesCompressBase64(uri, pendingPhotoSlotEdit)
            } else if (photoItemsEdit.size < 10) {
                photoItemsEdit.add(uri.toString())
                prosesCompressBase64(uri, photoItemsEdit.size - 1)
            }
            updatePhotoCountBadge()
            showPopupLihatFotoEdit()
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            bukaKameraEdit()
        } else {
            showSnackbar("Izin kamera ditolak", isError = true)
        }
    }

    companion object {
        private const val TAG = "EditDataActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initBinding()
        setupToolbar()
        notificationHelper = NotificationHelper(this)
        notificationHelper.createNotificationChannel()
        requestNotificationPermission()

        checkNetwork()
        loadSiteIdList()
        setupListeners()
        setupCollapsibleSections()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }

    private fun initBinding() {
        try {
            binding = ActivityEditDataBinding.inflate(layoutInflater)
            setContentView(binding.root)
        } catch (e: Exception) {
            Log.e(TAG, "ERROR inflating layout: ${e.message}", e)
            setContentView(R.layout.activity_edit_data)
        }
    }

    private fun setupToolbar() {
        try {
            setSupportActionBar(binding.toolbar)
            supportActionBar?.apply {
                title = "Edit Data Site"
                setDisplayHomeAsUpEnabled(true)
                setDisplayShowHomeEnabled(true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting toolbar: ${e.message}", e)
        }
    }

    private fun checkNetwork() {
        if (!isNetworkAvailable()) {
            showSnackbar("Tidak ada koneksi internet", isError = true)
        }
    }

    private fun setupCollapsibleSections() {
        val sections = listOf(
            Triple(R.id.headerInformasiSite, R.id.contentInformasiSite, R.id.icToggleInformasiSite),
            Triple(R.id.headerStatusPerangkat, R.id.contentStatusPerangkat, R.id.icToggleStatusPerangkat),
            Triple(R.id.headerTeganganArus, R.id.contentTeganganArus, R.id.icToggleTeganganArus),
            Triple(R.id.headerGenset, R.id.contentGenset, R.id.icToggleGenset),
            Triple(R.id.headerRecti1, R.id.contentRecti1, R.id.icToggleRecti1),
            Triple(R.id.headerRecti2, R.id.contentRecti2, R.id.icToggleRecti2),
            Triple(R.id.headerRecti3, R.id.contentRecti3, R.id.icToggleRecti3),
            Triple(R.id.headerRecti4, R.id.contentRecti4, R.id.icToggleRecti4),
            Triple(R.id.headerRecti5, R.id.contentRecti5, R.id.icToggleRecti5),
            Triple(R.id.headerRecti6, R.id.contentRecti6, R.id.icToggleRecti6),
            Triple(R.id.headerRecti7, R.id.contentRecti7, R.id.icToggleRecti7)
        )

        sections.forEach { (headerId, contentId, iconId) ->
            val header = findViewById<View>(headerId)
            val content = findViewById<View>(contentId)
            val icon = findViewById<ImageView>(iconId)
            if (header != null && content != null && icon != null) {
                toggleSection(header, content, icon)
            }
        }
    }

    private fun toggleSection(headerView: View, contentView: View, iconView: ImageView) {
        headerView.setOnClickListener {
            if (contentView.visibility == View.VISIBLE) {
                contentView.animate()
                    .alpha(0f)
                    .setDuration(200)
                    .withEndAction {
                        contentView.visibility = View.GONE
                    }
                    .start()
                iconView.animate().rotation(0f).setDuration(200).start()
            } else {
                contentView.visibility = View.VISIBLE
                contentView.alpha = 0f
                contentView.animate()
                    .alpha(1f)
                    .setDuration(250)
                    .start()
                iconView.animate().rotation(180f).setDuration(250).start()
            }
        }
    }

    private fun setupCascadingBateraiListeners(
        spJenis: Spinner,
        spMerk: Spinner,
        spJml: Spinner
    ) {
        spJenis.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val jenis = parent?.getItemAtPosition(position)?.toString() ?: ""
                val currentMerk = spMerk.selectedItem?.toString() ?: ""
                val currentJml = spJml.selectedItem?.toString() ?: ""

                val merkOpts = DropdownOptionsHelper.getMerkBateraiFiltered(jenis)
                val jmlOpts = DropdownOptionsHelper.getJumlahBatteryFiltered(jenis)

                spMerk.adapter = ArrayAdapter(this@EditDataActivity, android.R.layout.simple_spinner_dropdown_item, merkOpts)
                val merkIdx = merkOpts.indexOfFirst { it.equals(currentMerk, ignoreCase = true) }
                if (merkIdx >= 0) spMerk.setSelection(merkIdx)

                spJml.adapter = ArrayAdapter(this@EditDataActivity, android.R.layout.simple_spinner_dropdown_item, jmlOpts)
                val jmlIdx = jmlOpts.indexOfFirst { it.equals(currentJml, ignoreCase = true) }
                if (jmlIdx >= 0) spJml.setSelection(jmlIdx)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupListeners() {
        binding.actvSiteIdList.setOnItemClickListener { _, _, position, _ ->
            try {
                val selected = binding.actvSiteIdList.adapter?.getItem(position).toString()
                if (selected.isNotEmpty()) {
                    selectedSiteId = selected
                    loadDetailSite(selected)
                    binding.actvSiteIdList.clearFocus()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error on item click: ${e.message}", e)
            }
        }

        binding.actvSiteIdList.setOnFocusChangeListener { _, hasFocus ->
            try {
                if (!hasFocus) {
                    val text = binding.actvSiteIdList.text.toString().trim()
                    if (text.isNotEmpty() && !isLoading) {
                        if (siteIdsList.contains(text)) {
                            selectedSiteId = text
                            loadDetailSite(text)
                        } else {
                            showSnackbar("Site ID '$text' tidak ditemukan", isError = true)
                            binding.layoutFormEdit.visibility = View.GONE
                            binding.btnUpdate.isEnabled = false
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error on focus change: ${e.message}", e)
            }
        }

        binding.actvSiteIdList.setOnClickListener {
            try {
                if (siteIdsList.isNotEmpty()) {
                    binding.actvSiteIdList.showDropDown()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error showing dropdown: ${e.message}", e)
            }
        }

        binding.btnUpdate.setOnClickListener { konfirmasiUpdate() }

        binding.btnRefresh.setOnClickListener {
            if (isUpdating) {
                showSnackbar("Tunggu proses selesai", isError = true)
                return@setOnClickListener
            }
            if (selectedSiteId != null) {
                refreshDetailData(selectedSiteId!!)
            } else {
                showSnackbar("Pilih Site ID terlebih dahulu", isError = true)
            }
        }

        binding.btnKelolaFotoEdit.setOnClickListener {
            showPopupLihatFotoEdit()
        }

        binding.switchStatusBatteryNotAktif1.setOnCheckedChangeListener { _, isChecked -> binding.layoutJumlahBankNotAktif1.visibility = if (isChecked) View.VISIBLE else View.GONE }
        binding.switchStatusBatteryNotAktif2.setOnCheckedChangeListener { _, isChecked -> binding.layoutJumlahBankNotAktif2.visibility = if (isChecked) View.VISIBLE else View.GONE }
        binding.switchStatusBatteryNotAktif3.setOnCheckedChangeListener { _, isChecked -> binding.layoutJumlahBankNotAktif3.visibility = if (isChecked) View.VISIBLE else View.GONE }
        binding.switchStatusBatteryNotAktif4.setOnCheckedChangeListener { _, isChecked -> binding.layoutJumlahBankNotAktif4.visibility = if (isChecked) View.VISIBLE else View.GONE }
        binding.switchStatusBatteryNotAktif5.setOnCheckedChangeListener { _, isChecked -> binding.layoutJumlahBankNotAktif5.visibility = if (isChecked) View.VISIBLE else View.GONE }
        binding.switchStatusBatteryNotAktif6.setOnCheckedChangeListener { _, isChecked -> binding.layoutJumlahBankNotAktif6.visibility = if (isChecked) View.VISIBLE else View.GONE }
        binding.switchStatusBatteryNotAktif7.setOnCheckedChangeListener { _, isChecked -> binding.layoutJumlahBankNotAktif7.visibility = if (isChecked) View.VISIBLE else View.GONE }

        setupCascadingBateraiListeners(binding.spJenisBaterai1, binding.spMerkBaterai1, binding.spJumlahBattery1)
        setupCascadingBateraiListeners(binding.spJenisBaterai2, binding.spMerkBaterai2, binding.spJumlahBattery2)
        setupCascadingBateraiListeners(binding.spJenisBaterai3, binding.spMerkBaterai3, binding.spJumlahBattery3)
        setupCascadingBateraiListeners(binding.spJenisBaterai4, binding.spMerkBaterai4, binding.spJumlahBattery4)
        setupCascadingBateraiListeners(binding.spJenisBaterai5, binding.spMerkBaterai5, binding.spJumlahBattery5)
        setupCascadingBateraiListeners(binding.spJenisBaterai6, binding.spMerkBaterai6, binding.spJumlahBattery6)
        setupCascadingBateraiListeners(binding.spJenisBaterai7, binding.spMerkBaterai7, binding.spJumlahBattery7)
    }

    private fun updatePhotoCountBadge() {
        val count = photoItemsEdit.size
        binding.tvCountFotoEdit.text = "$count / 10"
    }

    private fun bukaKameraEdit() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            val uri = ImageCaptureHelper.buatUriFotoBaru(this)
            cameraUriEdit = uri
            takePhotoEditLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun prosesCompressBase64(uri: Uri, slotIndex: Int) {
        Thread {
            try {
                val b64 = ImageHelper.uriToCompressedBase64(this, uri)
                if (b64 != null) {
                    base64CacheEdit[slotIndex] = b64
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error compressing photo $slotIndex", e)
            }
        }.start()
    }

    private fun showPopupLihatFotoEdit() {
        currentPopupEditDialog?.dismiss()

        val dialog = Dialog(this, R.style.DialogEditTheme)
        currentPopupEditDialog = dialog

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(32, 32, 32, 32)
        root.setBackgroundColor(ContextCompat.getColor(this, R.color.section_bg))

        val tvTitle = TextView(this)
        tvTitle.text = "Dokumentasi Foto Site (${photoItemsEdit.size} / 10 Foto)"
        tvTitle.textSize = 18f
        tvTitle.setTypeface(null, Typeface.BOLD)
        tvTitle.setTextColor(ContextCompat.getColor(this, R.color.brand_primary))
        tvTitle.setPadding(0, 0, 0, 16)
        root.addView(tvTitle)

        val btnAdd = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonStyle)
        btnAdd.text = "Tambah Foto Dokumentasi Baru"
        btnAdd.textSize = 14f
        btnAdd.setTypeface(null, Typeface.BOLD)
        btnAdd.setTextColor(ContextCompat.getColor(this, R.color.white))
        btnAdd.backgroundTintList = ContextCompat.getColorStateList(this, R.color.brand_primary)
        btnAdd.cornerRadius = (16 * resources.displayMetrics.density).toInt()
        val btnAddParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, (50 * resources.displayMetrics.density).toInt()
        ).apply { bottomMargin = 16 }
        btnAdd.layoutParams = btnAddParams
        btnAdd.setOnClickListener {
            if (photoItemsEdit.size >= 10) {
                showSnackbar("Maksimal 10 foto dokumentasi sudah tercapai", isError = true)
                return@setOnClickListener
            }
            pendingPhotoSlotEdit = -1
            val opsi = arrayOf("📷 Ambil dari Kamera", "🖼️ Pilih dari Galeri")
            AlertDialog.Builder(this)
                .setTitle("Tambah Foto Dokumentasi")
                .setItems(opsi) { _, optIdx ->
                    when (optIdx) {
                        0 -> bukaKameraEdit()
                        1 -> addMultiplePhotosEditLauncher.launch("image/*")
                    }
                }
                .show()
        }
        root.addView(btnAdd)

        val scrollView = NestedScrollView(this)
        val scrollParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        )
        scrollView.layoutParams = scrollParams

        val listContainer = LinearLayout(this)
        listContainer.orientation = LinearLayout.VERTICAL

        if (photoItemsEdit.isEmpty()) {
            val tvEmpty = TextView(this)
            tvEmpty.text = "Belum ada foto terpilih untuk site ini."
            tvEmpty.setPadding(0, 48, 0, 48)
            tvEmpty.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            listContainer.addView(tvEmpty)
        } else {
            photoItemsEdit.forEachIndexed { index, itemStr ->
                val card = MaterialCardView(this)
                card.radius = 16f
                card.cardElevation = 4f
                val cardParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 20 }
                card.layoutParams = cardParams

                val cardContent = LinearLayout(this)
                cardContent.orientation = LinearLayout.VERTICAL
                cardContent.setPadding(20, 20, 20, 20)

                val tvLabel = TextView(this)
                tvLabel.text = "Foto Dokumentasi #${index + 1}"
                tvLabel.textSize = 14f
                tvLabel.setTypeface(null, Typeface.BOLD)
                tvLabel.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
                tvLabel.setPadding(0, 0, 0, 12)
                cardContent.addView(tvLabel)

                val iv = ImageView(this)
                val imgParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 450
                )
                iv.layoutParams = imgParams
                iv.scaleType = ImageView.ScaleType.CENTER_CROP

                val progressSpinner = CircularProgressDrawable(this).apply {
                    strokeWidth = 6f
                    centerRadius = 36f
                    setColorSchemeColors(ContextCompat.getColor(this@EditDataActivity, R.color.brand_primary))
                    start()
                }

                if (itemStr.startsWith("http")) {
                    val fileIdMatch = Regex("/d/([a-zA-Z0-9_-]+)").find(itemStr)
                    val directUrl = if (fileIdMatch != null) {
                        "https://drive.google.com/uc?export=view&id=${fileIdMatch.groupValues[1]}"
                    } else {
                        itemStr
                    }
                    Glide.with(this)
                        .load(directUrl)
                        .placeholder(progressSpinner)
                        .error(android.R.drawable.ic_menu_report_image)
                        .into(iv)
                } else {
                    try {
                        val uri = Uri.parse(itemStr)
                        val thumb = ImageHelper.decodeThumbnailFromUri(this, uri, 500, 500)
                        if (thumb != null) {
                            iv.setImageBitmap(thumb)
                        } else {
                            iv.setImageURI(uri)
                        }
                    } catch (_: Exception) {
                        iv.setImageResource(android.R.drawable.ic_menu_gallery)
                    }
                }
                cardContent.addView(iv)

                val rowBtn = LinearLayout(this)
                rowBtn.orientation = LinearLayout.HORIZONTAL
                rowBtn.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 16 }

                val btnGanti = MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle)
                btnGanti.text = "✏️ Ganti Foto"
                btnGanti.textSize = 12f
                btnGanti.setTextColor(ContextCompat.getColor(this, R.color.brand_primary))
                btnGanti.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                btnGanti.setOnClickListener {
                    pendingPhotoSlotEdit = index
                    val opsi = arrayOf("📷 Ambil dari Kamera", "🖼️ Pilih dari Galeri")
                    AlertDialog.Builder(this)
                        .setTitle("Ganti Foto #${index + 1}")
                        .setItems(opsi) { _, optIdx ->
                            when (optIdx) {
                                0 -> bukaKameraEdit()
                                1 -> pickPhotoEditLauncher.launch("image/*")
                            }
                        }
                        .show()
                }
                rowBtn.addView(btnGanti)

                val btnHapus = MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle)
                btnHapus.text = "🗑️ Hapus"
                btnHapus.textSize = 12f
                btnHapus.setTextColor(ContextCompat.getColor(this, R.color.error))
                btnHapus.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                btnHapus.setOnClickListener {
                    AlertDialog.Builder(this)
                        .setTitle("Hapus Foto #${index + 1}?")
                        .setMessage("Yakin ingin menghapus foto dokumentasi ke-${index + 1} ini?")
                        .setPositiveButton("Hapus") { _, _ ->
                            if (index in 0 until photoItemsEdit.size) {
                                photoItemsEdit.removeAt(index)
                                base64CacheEdit.remove(index)
                                updatePhotoCountBadge()
                                showPopupLihatFotoEdit()
                                showSnackbar("Foto #${index + 1} berhasil dihapus")
                            }
                        }
                        .setNegativeButton("Batal", null)
                        .show()
                }
                rowBtn.addView(btnHapus)

                cardContent.addView(rowBtn)
                card.addView(cardContent)
                listContainer.addView(card)
            }
        }

        scrollView.addView(listContainer)
        root.addView(scrollView)

        val btnClose = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonStyle)
        btnClose.text = "Tutup"
        btnClose.textSize = 14f
        btnClose.setTypeface(null, Typeface.BOLD)
        btnClose.setTextColor(ContextCompat.getColor(this, R.color.white))
        btnClose.backgroundTintList = ContextCompat.getColorStateList(this, R.color.brand_primary)
        btnClose.cornerRadius = (16 * resources.displayMetrics.density).toInt()
        btnClose.setOnClickListener { dialog.dismiss() }
        val btnParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, (50 * resources.displayMetrics.density).toInt()
        ).apply { topMargin = 16 }
        btnClose.layoutParams = btnParams
        root.addView(btnClose)

        dialog.setContentView(root)
        dialog.setCancelable(true)
        dialog.show()

        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92).toInt(),
            (resources.displayMetrics.heightPixels * 0.85).toInt()
        )
    }

    private fun isNetworkAvailable(): Boolean {
        return try {
            val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            val networkInfo = connectivityManager.activeNetworkInfo
            networkInfo != null && networkInfo.isConnected
        } catch (e: Exception) {
            Log.e(TAG, "Error checking network: ${e.message}", e)
            true
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        if (isUpdating) {
            showSnackbar("Mohon tunggu proses selesai", isError = true)
            return false
        }
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onBackPressed() {
        if (isUpdating) {
            showSnackbar("Mohon tunggu proses selesai", isError = true)
            return
        }
        showBackConfirmation()
    }

    private fun showBackConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Konfirmasi")
            .setMessage("Apakah Anda yakin ingin keluar? Perubahan yang belum disimpan akan hilang.")
            .setPositiveButton("Ya") { _, _ -> finish() }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showLoadingOverlay(message: String = "Memuat data...") {
        try {
            isUpdating = true

            supportActionBar?.setDisplayHomeAsUpEnabled(false)
            supportActionBar?.setDisplayShowHomeEnabled(false)
            binding.toolbar.setNavigationOnClickListener(null)

            binding.btnRefresh.isEnabled = false
            binding.btnRefresh.alpha = 0.5f
            binding.btnRefresh.isClickable = false
            binding.actvSiteIdList.isEnabled = false
            binding.actvSiteIdList.alpha = 0.5f
            binding.btnUpdate.isEnabled = false
            binding.btnUpdate.alpha = 0.5f

            binding.overlayLoading.visibility = View.VISIBLE
            val textView = binding.overlayLoading.findViewById<TextView>(R.id.tvLoadingMessage)
            textView?.text = message

            binding.root.findViewById<NestedScrollView>(R.id.nestedScrollView)?.isEnabled = false

        } catch (e: Exception) {
            Log.e(TAG, "Error showing overlay: ${e.message}", e)
        }
    }

    private fun hideLoadingOverlay() {
        try {
            isUpdating = false
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.setDisplayShowHomeEnabled(true)
            binding.toolbar.setNavigationOnClickListener {
                if (!isUpdating) showBackConfirmation()
            }
            binding.btnRefresh.isEnabled = true
            binding.btnRefresh.alpha = 1.0f
            binding.btnRefresh.isClickable = true
            binding.actvSiteIdList.isEnabled = true
            binding.actvSiteIdList.alpha = 1.0f
            binding.overlayLoading.visibility = View.GONE
            binding.btnUpdate.isEnabled = true
            binding.btnUpdate.alpha = 1.0f
            binding.root.findViewById<NestedScrollView>(R.id.nestedScrollView)?.isEnabled = true
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding overlay: ${e.message}", e)
        }
    }

    private fun startRefreshAnimation() {
        try {
            val rotateAnim = RotateAnimation(
                0f, 360f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f
            ).apply {
                duration = 800
                repeatCount = Animation.INFINITE
                interpolator = LinearInterpolator()
            }
            binding.btnRefresh.startAnimation(rotateAnim)
            isRefreshing = true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting refresh animation: ${e.message}")
        }
    }

    private fun stopRefreshAnimation() {
        try {
            binding.btnRefresh.clearAnimation()
            isRefreshing = false
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping refresh animation: ${e.message}")
        }
    }

    private fun loadSiteIdList() {
        if (isLoading) return
        isLoading = true
        retryCount = 0
        binding.progressLoadIds.visibility = View.VISIBLE
        binding.actvSiteIdList.isEnabled = false

        val queue = Volley.newRequestQueue(this)
        val url = "$urlSheet?action=list_ids"

        val request = StringRequest(
            Request.Method.GET, url,
            { response ->
                isLoading = false
                binding.progressLoadIds.visibility = View.GONE
                binding.actvSiteIdList.isEnabled = true
                try {
                    val json = JSONObject(response)
                    if (json.optString("status") == "success") {
                        val idsArray = json.optJSONArray("ids")
                        siteIdsList.clear()
                        if (idsArray != null) {
                            for (i in 0 until idsArray.length()) {
                                siteIdsList.add(idsArray.getString(i))
                            }
                        }
                        val adapter = ArrayAdapter(
                            this@EditDataActivity,
                            android.R.layout.simple_dropdown_item_1line,
                            siteIdsList
                        )
                        binding.actvSiteIdList.setAdapter(adapter)
                        binding.actvSiteIdList.threshold = 1
                        if (siteIdsList.isNotEmpty()) {
                            showSnackbar("${siteIdsList.size} Site ID ditemukan")
                            Handler(Looper.getMainLooper()).postDelayed({
                                try { binding.actvSiteIdList.showDropDown() }
                                catch (e: Exception) { Log.e(TAG, "Error showing dropdown: ${e.message}", e) }
                            }, 500)
                        } else {
                            showSnackbar("Tidak ada data Site ID", isError = true)
                        }
                    } else {
                        val msg = json.optString("message", "Gagal memuat daftar Site ID")
                        showSnackbar(msg, isError = true)
                        if (retryCount < maxRetries) retryLoadSiteIds()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing list IDs: ${e.message}", e)
                    showSnackbar("Gagal membaca daftar Site ID: ${e.message}", isError = true)
                    if (retryCount < maxRetries) retryLoadSiteIds()
                }
            },
            { error ->
                isLoading = false
                binding.progressLoadIds.visibility = View.GONE
                binding.actvSiteIdList.isEnabled = true
                val errorMsg = when (error.cause) {
                    is SocketTimeoutException -> "Koneksi timeout, coba lagi"
                    is UnknownHostException -> "Tidak ada koneksi internet"
                    else -> error.message ?: "Koneksi terputus"
                }
                Log.e(TAG, "Error loading IDs: $errorMsg", error)
                showSnackbar("Gagal memuat daftar: $errorMsg", isError = true)
                if (retryCount < maxRetries) retryLoadSiteIds()
            }
        )

        request.retryPolicy = DefaultRetryPolicy(60000, 1, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT)
        queue.add(request)
    }

    private fun retryLoadSiteIds() {
        retryCount++
        showSnackbar("Mencoba ulang... (${retryCount}/$maxRetries)")
        Handler(Looper.getMainLooper()).postDelayed({ loadSiteIdList() }, 2000)
    }

    private fun loadDetailSite(siteId: String) {
        if (dataCache.containsKey(siteId)) {
            val cachedData = dataCache[siteId]
            if (cachedData != null) {
                isiFormDariData(cachedData)
                binding.layoutFormEdit.visibility = View.VISIBLE
                binding.btnUpdate.isEnabled = true
                showSnackbar("📦 Data Site $siteId dimuat dari cache")
                return
            } else {
                dataCache.remove(siteId)
            }
        }

        currentSiteId = siteId
        selectedSiteId = siteId
        if (isLoading) return
        isLoading = true

        binding.actvSiteIdList.setText(siteId)
        binding.progressLoadIds.visibility = View.VISIBLE
        binding.layoutFormEdit.visibility = View.GONE
        binding.btnUpdate.isEnabled = false

        val queue = Volley.newRequestQueue(this)
        val url = "$urlSheet?action=get_detail&site_id=$siteId"

        val request = StringRequest(
            Request.Method.GET, url,
            { response ->
                isLoading = false
                binding.progressLoadIds.visibility = View.GONE
                try {
                    val json = JSONObject(response)
                    if (json.optString("status") == "success") {
                        val data = json.getJSONObject("data")
                        dataCache[siteId] = data
                        isiFormDariData(data)
                        binding.layoutFormEdit.visibility = View.VISIBLE
                        binding.btnUpdate.isEnabled = true
                        showSnackbar("✅ Data Site $siteId berhasil dimuat")
                    } else {
                        val msg = json.optString("message", "Data tidak ditemukan")
                        showSnackbar("❌ $msg", isError = true)
                        binding.layoutFormEdit.visibility = View.VISIBLE
                        binding.btnUpdate.isEnabled = false
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing detail: ${e.message}", e)
                    showSnackbar("❌ Gagal membaca detail data: ${e.message}", isError = true)
                    binding.layoutFormEdit.visibility = View.VISIBLE
                    binding.btnUpdate.isEnabled = false
                }
            },
            { error ->
                isLoading = false
                binding.progressLoadIds.visibility = View.GONE
                val errorMsg = when (error.cause) {
                    is SocketTimeoutException -> "Koneksi timeout, coba lagi"
                    is UnknownHostException -> "Tidak ada koneksi internet"
                    else -> error.message ?: "Koneksi terputus"
                }
                Log.e(TAG, "Error loading detail: $errorMsg", error)
                Snackbar.make(binding.root, "❌ Gagal memuat: $errorMsg", Snackbar.LENGTH_INDEFINITE)
                    .setAction("🔄 Refresh") { refreshDetailData(siteId) }
                    .show()
                binding.layoutFormEdit.visibility = View.VISIBLE
                binding.btnUpdate.isEnabled = false
            }
        )

        request.retryPolicy = DefaultRetryPolicy(15000, 2, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT)
        queue.add(request)
    }

    private fun refreshDetailData(siteId: String) {
        dataCache.remove(siteId)
        showLoadingOverlay("Memuat ulang data Site $siteId...")
        startRefreshAnimation()

        val queue = Volley.newRequestQueue(this)
        val url = "$urlSheet?action=get_detail&site_id=$siteId"

        val request = StringRequest(
            Request.Method.GET, url,
            { response ->
                hideLoadingOverlay()
                stopRefreshAnimation()
                try {
                    val json = JSONObject(response)
                    if (json.optString("status") == "success") {
                        val data = json.getJSONObject("data")
                        dataCache[siteId] = data
                        isiFormDariData(data)
                        binding.layoutFormEdit.visibility = View.VISIBLE
                        binding.btnUpdate.isEnabled = true
                        showSnackbar("✅ Data Site $siteId berhasil dimuat ulang")
                    } else {
                        val msg = json.optString("message", "Data tidak ditemukan")
                        showSnackbar("❌ Gagal memuat ulang: $msg", isError = true)
                        val cachedData = dataCache[siteId]
                        if (cachedData != null) {
                            isiFormDariData(cachedData)
                            binding.layoutFormEdit.visibility = View.VISIBLE
                            binding.btnUpdate.isEnabled = true
                            showSnackbar("⚠️ Menampilkan data cache", isError = true)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing refresh response: ${e.message}", e)
                    showSnackbar("❌ Gagal membaca data: ${e.message}", isError = true)
                    val cachedData = dataCache[siteId]
                    if (cachedData != null) {
                        isiFormDariData(cachedData)
                        binding.layoutFormEdit.visibility = View.VISIBLE
                        binding.btnUpdate.isEnabled = true
                    }
                }
            },
            { error ->
                hideLoadingOverlay()
                stopRefreshAnimation()
                val errorMsg = when (error.cause) {
                    is SocketTimeoutException -> "Koneksi timeout, coba lagi"
                    is UnknownHostException -> "Tidak ada koneksi internet"
                    else -> error.message ?: "Koneksi terputus"
                }
                Log.e(TAG, "Refresh error: $errorMsg", error)
                showRefreshErrorDialog(siteId, errorMsg)
            }
        )

        request.retryPolicy = DefaultRetryPolicy(15000, 2, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT)
        queue.add(request)
    }

    private fun showRefreshErrorDialog(siteId: String, errorMsg: String) {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Gagal Refresh Data")
            .setMessage("""
                $errorMsg
                
                💡 Tips:
                1. Periksa koneksi internet
                2. Coba refresh lagi
                3. Data sebelumnya tetap tersimpan
                
                Site ID: $siteId
            """.trimIndent())
            .setPositiveButton("🔄 Refresh Lagi") { _, _ -> refreshDetailData(siteId) }
            .setNegativeButton("✖️ Tutup") { _, _ ->
                val cachedData = dataCache[siteId]
                if (cachedData != null) {
                    isiFormDariData(cachedData)
                    binding.layoutFormEdit.visibility = View.VISIBLE
                    binding.btnUpdate.isEnabled = true
                    showSnackbar("⚠️ Menampilkan data cache", isError = true)
                } else {
                    binding.layoutFormEdit.visibility = View.GONE
                    binding.btnUpdate.isEnabled = false
                }
            }
            .setNeutralButton("📊 Cek Sheet") { _, _ ->
                val url = "https://docs.google.com/spreadsheets/d/1gYNO4_vKJxHaXeJRnBicinZFJiz5tPIH0sWqT10w96I/edit?gid=0#gid=0"
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
            .show()
    }

    // ==================== FILL FORM ====================
    private fun isiFormDariData(data: JSONObject) {
        try {
            Log.d(TAG, "=== FILLING FORM ===")
            binding.actvSiteIdList.setText(data.optString("site_id"))
            binding.etSiteName.setText(data.optString("site_name", ""))
            binding.etIdPelanggan.setText(parseNumberFromSheet(data.optString("id_pelanggan", "")))
            setSpinnerValueSafe(binding.spMcbKwh, data.optString("mcb_kwh", ""))
            binding.etDaya.setText(parseNumberFromSheet(data.optString("daya", "")))

            photoItemsEdit.clear()
            base64CacheEdit.clear()

            val rawPhotoUrlsStr = data.optString("photo_urls", "")
            if (rawPhotoUrlsStr.isNotBlank()) {
                try {
                    val jsonArray = JSONArray(rawPhotoUrlsStr)
                    for (i in 0 until jsonArray.length()) {
                        val url = jsonArray.optString(i, "")
                        if (url.isNotBlank()) {
                            photoItemsEdit.add(url)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Gagal parse photo_urls JSON string: ${e.message}")
                }
            }

            if (photoItemsEdit.isEmpty()) {
                for (i in 1..10) {
                    val url = data.optString("image_url_$i", "")
                    if (url.isNotBlank()) {
                        photoItemsEdit.add(url)
                    }
                }
            }

            updatePhotoCountBadge()

            setSpinnerValue(binding.spMainsFail, data.optString("mains_fail", ""))
            setSpinnerValue(binding.spRectyFail, data.optString("recty_fail", ""))
            setSpinnerValue(binding.spStolen, data.optString("stolen", ""))
            setSpinnerValue(binding.spLowBatt, data.optString("low_batt", ""))
            setSpinnerValue(binding.spEngRun, data.optString("eng_run", ""))

            binding.etRV.setText(parseNumberFromSheet(data.optString("r_v", "")))
            binding.etSV.setText(parseNumberFromSheet(data.optString("s_v", "")))
            binding.etTV.setText(parseNumberFromSheet(data.optString("t_v", "")))
            binding.etRA.setText(parseNumberFromSheet(data.optString("r_a", "")))
            binding.etSA.setText(parseNumberFromSheet(data.optString("s_a", "")))
            binding.etTA.setText(parseNumberFromSheet(data.optString("t_a", "")))

            setSpinnerValueSafe(binding.spMerkGenset1, data.optString("merk_genset_1", ""))
            binding.etCapacityGenset1.setText(parseNumberFromSheet(data.optString("capacity_genset_1", "")))
            setSpinnerValueSafe(binding.spStatusGenset1, data.optString("status_genset_1", ""))
            binding.etRemarkGenset1.setText(data.optString("remark_genset_1", ""))

            setSpinnerValueSafe(binding.spMerkGenset2, data.optString("merk_genset_2", ""))
            binding.etCapacityGenset2.setText(parseNumberFromSheet(data.optString("capacity_genset_2", "")))
            setSpinnerValueSafe(binding.spStatusGenset2, data.optString("status_genset_2", ""))
            binding.etRemarkGenset2.setText(data.optString("remark_genset_2", ""))

            fun fillRectiFields(idx: Int, spType: Spinner, spMod: Spinner, spModRusak: Spinner, spJenisBat: Spinner, spMerkBat: Spinner, spJmlBat: Spinner, spBalting: Spinner, spArester: Spinner, swNotAktif: SwitchMaterial, layoutNotAktif: View, spNotAktif: Spinner, etMcb: TextInputEditText, etVolt: TextInputEditText, etLoad: TextInputEditText, etPerangkat: TextInputEditText, etRemark: TextInputEditText? = null) {
                setSpinnerValueSafe(spType, data.optString("type_rectifier_$idx", ""))
                setSpinnerValueSafe(spMod, data.optString("jumlah_modul_$idx", ""))
                setSpinnerValueSafe(spModRusak, data.optString("jumlah_modul_rusak_$idx", ""))
                setSpinnerValueSafe(spJenisBat, data.optString("jenis_baterai_$idx", ""))
                setSpinnerValueSafe(spMerkBat, data.optString("merk_baterai_$idx", ""))
                setSpinnerValueSafe(spJmlBat, data.optString(if (idx == 1) "jumlah_battery_p" else "jumlah_battery_p_$idx", ""))
                setSpinnerValueSafe(spBalting, data.optString("battery_recti_$idx", ""))
                setSpinnerValueSafe(spArester, data.optString("kondisi_arester_$idx", ""))

                val optionsBankNotAktif = DropdownOptionsHelper.getOptions(this@EditDataActivity, DropdownOptionsHelper.KEY_JUMLAH_BANK_NOT_AKTIF)
                spNotAktif.adapter = ArrayAdapter(this@EditDataActivity, android.R.layout.simple_spinner_dropdown_item, optionsBankNotAktif)

                val jmlNotAktif = parseNumberFromSheet(data.optString("jumlah_bank_not_aktif_$idx", ""))
                val isNotAktif = jmlNotAktif.isNotBlank() && jmlNotAktif != "0"
                swNotAktif.isChecked = isNotAktif
                layoutNotAktif.visibility = if (isNotAktif) View.VISIBLE else View.GONE
                setSpinnerValueSafe(spNotAktif, jmlNotAktif)

                etMcb.setText(parseNumberFromSheet(data.optString("mcb_battery_recti_$idx", "")))
                etVolt.setText(parseNumberFromSheet(data.optString("bus_voltage_$idx", "")))
                etLoad.setText(parseNumberFromSheet(data.optString("load_a_$idx", "")))
                etPerangkat.setText(data.optString("perangkat_terhubung_$idx", ""))
                if (etRemark != null) etRemark.setText(data.optString("remark_$idx", ""))
            }

            fillRectiFields(1, binding.spTypeRectifier1, binding.spJumlahModul1, binding.spJumlahModulRusak1, binding.spJenisBaterai1, binding.spMerkBaterai1, binding.spJumlahBattery1, binding.spBatteryRecti1, binding.spKondisiArester1, binding.switchStatusBatteryNotAktif1, binding.layoutJumlahBankNotAktif1, binding.spJumlahBankNotAktif1, binding.etMcbBatteryRecti1, binding.etBusVoltage1, binding.etLoadA1, binding.etPerangkatTerhubung1, binding.etRemark1)
            fillRectiFields(2, binding.spTypeRectifier2, binding.spJumlahModul2, binding.spJumlahModulRusak2, binding.spJenisBaterai2, binding.spMerkBaterai2, binding.spJumlahBattery2, binding.spBatteryRecti2, binding.spKondisiArester2, binding.switchStatusBatteryNotAktif2, binding.layoutJumlahBankNotAktif2, binding.spJumlahBankNotAktif2, binding.etMcbBatteryRecti2, binding.etBusVoltage2, binding.etLoadA2, binding.etPerangkatTerhubung2, binding.etRemark2)
            fillRectiFields(3, binding.spTypeRectifier3, binding.spJumlahModul3, binding.spJumlahModulRusak3, binding.spJenisBaterai3, binding.spMerkBaterai3, binding.spJumlahBattery3, binding.spBatteryRecti3, binding.spKondisiArester3, binding.switchStatusBatteryNotAktif3, binding.layoutJumlahBankNotAktif3, binding.spJumlahBankNotAktif3, binding.etMcbBatteryRecti3, binding.etBusVoltage3, binding.etLoadA3, binding.etPerangkatTerhubung3)
            fillRectiFields(4, binding.spTypeRectifier4, binding.spJumlahModul4, binding.spJumlahModulRusak4, binding.spJenisBaterai4, binding.spMerkBaterai4, binding.spJumlahBattery4, binding.spBatteryRecti4, binding.spKondisiArester4, binding.switchStatusBatteryNotAktif4, binding.layoutJumlahBankNotAktif4, binding.spJumlahBankNotAktif4, binding.etMcbBatteryRecti4, binding.etBusVoltage4, binding.etLoadA4, binding.etPerangkatTerhubung4)
            fillRectiFields(5, binding.spTypeRectifier5, binding.spJumlahModul5, binding.spJumlahModulRusak5, binding.spJenisBaterai5, binding.spMerkBaterai5, binding.spJumlahBattery5, binding.spBatteryRecti5, binding.spKondisiArester5, binding.switchStatusBatteryNotAktif5, binding.layoutJumlahBankNotAktif5, binding.spJumlahBankNotAktif5, binding.etMcbBatteryRecti5, binding.etBusVoltage5, binding.etLoadA5, binding.etPerangkatTerhubung5)
            fillRectiFields(6, binding.spTypeRectifier6, binding.spJumlahModul6, binding.spJumlahModulRusak6, binding.spJenisBaterai6, binding.spMerkBaterai6, binding.spJumlahBattery6, binding.spBatteryRecti6, binding.spKondisiArester6, binding.switchStatusBatteryNotAktif6, binding.layoutJumlahBankNotAktif6, binding.spJumlahBankNotAktif6, binding.etMcbBatteryRecti6, binding.etBusVoltage6, binding.etLoadA6, binding.etPerangkatTerhubung6)
            fillRectiFields(7, binding.spTypeRectifier7, binding.spJumlahModul7, binding.spJumlahModulRusak7, binding.spJenisBaterai7, binding.spMerkBaterai7, binding.spJumlahBattery7, binding.spBatteryRecti7, binding.spKondisiArester7, binding.switchStatusBatteryNotAktif7, binding.layoutJumlahBankNotAktif7, binding.spJumlahBankNotAktif7, binding.etMcbBatteryRecti7, binding.etBusVoltage7, binding.etLoadA7, binding.etPerangkatTerhubung7)

        } catch (e: Exception) {
            Log.e(TAG, "Error filling form: ${e.message}", e)
        }
    }

    private fun parseNumberFromSheet(value: String): String {
        if (value.isEmpty()) return ""
        val cleaned = value.replace(",", ".").replace("[^0-9.]".toRegex(), "")
        return if (cleaned.endsWith(".0")) cleaned.dropLast(2) else cleaned
    }

    private fun setSpinnerValue(spinner: Spinner, value: String) {
        if (value.isEmpty()) return
        val adapter = spinner.adapter ?: return
        for (i in 0 until adapter.count) {
            val item = adapter.getItem(i).toString()
            if (item.equals(value, ignoreCase = true)) {
                spinner.setSelection(i)
                return
            }
        }
    }

    private fun setSpinnerValueSafe(spinner: Spinner, value: String, debugTag: String = "") {
        if (value.isEmpty()) return
        val adapter = spinner.adapter ?: return
        for (i in 0 until adapter.count) {
            val item = adapter.getItem(i).toString()
            if (item.equals(value, ignoreCase = true)) {
                spinner.setSelection(i)
                return
            }
        }
    }

    private fun validasiRecti(rectiNum: Int, voltStr: String, loadStr: String): Boolean {
        if (voltStr.isNotBlank()) {
            val volt = voltStr.replace(",", ".").toDoubleOrNull()
            if (volt == null || volt < 40.0 || volt > 60.0) {
                AlertDialog.Builder(this)
                    .setTitle("⚠️ Bus Voltage Tidak Valid")
                    .setMessage("Bus Voltage pada Recti #$rectiNum harus di antara 40 - 60 V.\n\nNilai diinput: $voltStr V")
                    .setPositiveButton("Mengerti", null)
                    .show()
                showSnackbar("Bus Voltage Recti #$rectiNum harus antara 40 - 60 V", isError = true)
                return false
            }
        }
        if (loadStr.isNotBlank()) {
            val load = loadStr.replace(",", ".").toDoubleOrNull()
            if (load == null || load < 0.0 || load > 200.0) {
                AlertDialog.Builder(this)
                    .setTitle("⚠️ Load (A) Tidak Valid")
                    .setMessage("Load (A) pada Recti #$rectiNum harus di antara 0 - 200 A.\n\nNilai diinput: $loadStr A")
                    .setPositiveButton("Mengerti", null)
                    .show()
                showSnackbar("Load Recti #$rectiNum harus antara 0 - 200 A", isError = true)
                return false
            }
        }
        return true
    }

    // ==================== UPDATE DATA ====================
    private fun konfirmasiUpdate() {
        val siteId = binding.actvSiteIdList.text.toString().trim()
        if (siteId.isEmpty()) {
            showSnackbar("Site ID tidak boleh kosong", isError = true)
            return
        }

        if (!validasiRecti(1, binding.etBusVoltage1.text.toString().trim(), binding.etLoadA1.text.toString().trim())) return
        if (!validasiRecti(2, binding.etBusVoltage2.text.toString().trim(), binding.etLoadA2.text.toString().trim())) return
        if (!validasiRecti(3, binding.etBusVoltage3.text.toString().trim(), binding.etLoadA3.text.toString().trim())) return
        if (!validasiRecti(4, binding.etBusVoltage4.text.toString().trim(), binding.etLoadA4.text.toString().trim())) return
        if (!validasiRecti(5, binding.etBusVoltage5.text.toString().trim(), binding.etLoadA5.text.toString().trim())) return
        if (!validasiRecti(6, binding.etBusVoltage6.text.toString().trim(), binding.etLoadA6.text.toString().trim())) return
        if (!validasiRecti(7, binding.etBusVoltage7.text.toString().trim(), binding.etLoadA7.text.toString().trim())) return

        AlertDialog.Builder(this)
            .setTitle("Konfirmasi Update")
            .setMessage("Yakin ingin memperbarui data untuk Site ID: $siteId?")
            .setPositiveButton("Ya, Update") { _, _ -> kirimUpdateData(siteId) }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun kirimUpdateData(siteId: String) {
        showLoadingOverlay("Mengupdate data Site $siteId...")

        val queue = Volley.newRequestQueue(this)

        val request = object : StringRequest(
            Method.POST, urlSheet,
            { response ->
                hideLoadingOverlay()
                try {
                    val json = JSONObject(response)
                    if (json.optString("status") == "success") {
                        dataCache.remove(siteId)
                        notificationHelper.showNotification(
                            "✅ Data Berhasil Diupdate",
                            "Data untuk Site $siteId berhasil diperbarui di server.",
                            true
                        )
                        showSnackbar("✅ Data berhasil diperbarui di server!")
                        Handler(Looper.getMainLooper()).postDelayed({ finish() }, 1500)
                    } else {
                        showSnackbar("❌ Gagal: ${json.optString("message")}", isError = true)
                    }
                } catch (e: Exception) {
                    showSnackbar("❌ Gagal membaca respons server", isError = true)
                }
            },
            { error ->
                hideLoadingOverlay()
                val errorMsg = when (error.cause) {
                    is SocketTimeoutException -> "Koneksi timeout, coba lagi"
                    is UnknownHostException -> "Tidak ada koneksi internet"
                    else -> error.message ?: "Koneksi terputus"
                }
                showSnackbar("❌ Gagal update: $errorMsg", isError = true)
            }
        ) {
            override fun getParams(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["action"] = "update"
                params["username"] = SessionHelper.getUsername(this@EditDataActivity)
                params["site_id"] = siteId
                params["site_name"] = binding.etSiteName.text.toString().trim()
                params["id_pelanggan"] = binding.etIdPelanggan.text.toString().trim()
                params["mcb_kwh"] = binding.spMcbKwh.selectedItem?.toString() ?: ""
                params["daya"] = binding.etDaya.text.toString().trim()

                for (i in 0 until photoItemsEdit.size) {
                    val b64 = base64CacheEdit[i]
                    if (b64 != null) {
                        params["image_base64_${i + 1}"] = b64
                    }
                }

                params["mains_fail"] = binding.spMainsFail.selectedItem?.toString() ?: ""
                params["recty_fail"] = binding.spRectyFail.selectedItem?.toString() ?: ""
                params["stolen"] = binding.spStolen.selectedItem?.toString() ?: ""
                params["low_batt"] = binding.spLowBatt.selectedItem?.toString() ?: ""
                params["eng_run"] = binding.spEngRun.selectedItem?.toString() ?: ""

                params["r_v"] = binding.etRV.text.toString().trim()
                params["s_v"] = binding.etSV.text.toString().trim()
                params["t_v"] = binding.etTV.text.toString().trim()
                params["r_a"] = binding.etRA.text.toString().trim()
                params["s_a"] = binding.etSA.text.toString().trim()
                params["t_a"] = binding.etTA.text.toString().trim()

                params["merk_genset_1"] = binding.spMerkGenset1.selectedItem?.toString() ?: ""
                params["capacity_genset_1"] = binding.etCapacityGenset1.text.toString().trim()
                params["status_genset_1"] = binding.spStatusGenset1.selectedItem?.toString() ?: ""
                params["remark_genset_1"] = binding.etRemarkGenset1.text.toString().trim()

                params["merk_genset_2"] = binding.spMerkGenset2.selectedItem?.toString() ?: ""
                params["capacity_genset_2"] = binding.etCapacityGenset2.text.toString().trim()
                params["status_genset_2"] = binding.spStatusGenset2.selectedItem?.toString() ?: ""
                params["remark_genset_2"] = binding.etRemarkGenset2.text.toString().trim()

                fun putRectiParams(idx: Int, spType: Spinner, spMod: Spinner, spModRusak: Spinner, spJenisBat: Spinner, spMerkBat: Spinner, spJmlBat: Spinner, spBalting: Spinner, spArester: Spinner, swNotAktif: SwitchMaterial, spNotAktif: Spinner, etMcb: TextInputEditText, etVolt: TextInputEditText, etLoad: TextInputEditText, etPerangkat: TextInputEditText, etRemark: TextInputEditText? = null) {
                    params["type_rectifier_$idx"] = spType.selectedItem?.toString() ?: ""
                    params["jumlah_modul_$idx"] = spMod.selectedItem?.toString() ?: ""
                    params["jumlah_modul_rusak_$idx"] = spModRusak.selectedItem?.toString() ?: ""
                    params["jenis_baterai_$idx"] = spJenisBat.selectedItem?.toString() ?: ""
                    params["merk_baterai_$idx"] = spMerkBat.selectedItem?.toString() ?: ""
                    params[if (idx == 1) "jumlah_battery_p" else "jumlah_battery_p_$idx"] = spJmlBat.selectedItem?.toString() ?: ""
                    params["battery_recti_$idx"] = spBalting.selectedItem?.toString() ?: ""
                    params["jumlah_bank_not_aktif_$idx"] = if (swNotAktif.isChecked) spNotAktif.selectedItem?.toString() ?: "" else ""
                    params["mcb_battery_recti_$idx"] = etMcb.text.toString().trim()
                    params["kondisi_arester_$idx"] = spArester.selectedItem?.toString() ?: ""
                    params["bus_voltage_$idx"] = etVolt.text.toString().trim()
                    params["load_a_$idx"] = etLoad.text.toString().trim()
                    params["perangkat_terhubung_$idx"] = etPerangkat.text.toString().trim()
                    if (etRemark != null) params["remark_$idx"] = etRemark.text.toString().trim()
                }

                putRectiParams(1, binding.spTypeRectifier1, binding.spJumlahModul1, binding.spJumlahModulRusak1, binding.spJenisBaterai1, binding.spMerkBaterai1, binding.spJumlahBattery1, binding.spBatteryRecti1, binding.spKondisiArester1, binding.switchStatusBatteryNotAktif1, binding.spJumlahBankNotAktif1, binding.etMcbBatteryRecti1, binding.etBusVoltage1, binding.etLoadA1, binding.etPerangkatTerhubung1, binding.etRemark1)
                putRectiParams(2, binding.spTypeRectifier2, binding.spJumlahModul2, binding.spJumlahModulRusak2, binding.spJenisBaterai2, binding.spMerkBaterai2, binding.spJumlahBattery2, binding.spBatteryRecti2, binding.spKondisiArester2, binding.switchStatusBatteryNotAktif2, binding.spJumlahBankNotAktif2, binding.etMcbBatteryRecti2, binding.etBusVoltage2, binding.etLoadA2, binding.etPerangkatTerhubung2, binding.etRemark2)
                putRectiParams(3, binding.spTypeRectifier3, binding.spJumlahModul3, binding.spJumlahModulRusak3, binding.spJenisBaterai3, binding.spMerkBaterai3, binding.spJumlahBattery3, binding.spBatteryRecti3, binding.spKondisiArester3, binding.switchStatusBatteryNotAktif3, binding.spJumlahBankNotAktif3, binding.etMcbBatteryRecti3, binding.etBusVoltage3, binding.etLoadA3, binding.etPerangkatTerhubung3)
                putRectiParams(4, binding.spTypeRectifier4, binding.spJumlahModul4, binding.spJumlahModulRusak4, binding.spJenisBaterai4, binding.spMerkBaterai4, binding.spJumlahBattery4, binding.spBatteryRecti4, binding.spKondisiArester4, binding.switchStatusBatteryNotAktif4, binding.spJumlahBankNotAktif4, binding.etMcbBatteryRecti4, binding.etBusVoltage4, binding.etLoadA4, binding.etPerangkatTerhubung4)
                putRectiParams(5, binding.spTypeRectifier5, binding.spJumlahModul5, binding.spJumlahModulRusak5, binding.spJenisBaterai5, binding.spMerkBaterai5, binding.spJumlahBattery5, binding.spBatteryRecti5, binding.spKondisiArester5, binding.switchStatusBatteryNotAktif5, binding.spJumlahBankNotAktif5, binding.etMcbBatteryRecti5, binding.etBusVoltage5, binding.etLoadA5, binding.etPerangkatTerhubung5)
                putRectiParams(6, binding.spTypeRectifier6, binding.spJumlahModul6, binding.spJumlahModulRusak6, binding.spJenisBaterai6, binding.spMerkBaterai6, binding.spJumlahBattery6, binding.spBatteryRecti6, binding.spKondisiArester6, binding.switchStatusBatteryNotAktif6, binding.spJumlahBankNotAktif6, binding.etMcbBatteryRecti6, binding.etBusVoltage6, binding.etLoadA6, binding.etPerangkatTerhubung6)
                putRectiParams(7, binding.spTypeRectifier7, binding.spJumlahModul7, binding.spJumlahModulRusak7, binding.spJenisBaterai7, binding.spMerkBaterai7, binding.spJumlahBattery7, binding.spBatteryRecti7, binding.spKondisiArester7, binding.switchStatusBatteryNotAktif7, binding.spJumlahBankNotAktif7, binding.etMcbBatteryRecti7, binding.etBusVoltage7, binding.etLoadA7, binding.etPerangkatTerhubung7)

                return params
            }
        }

        request.retryPolicy = DefaultRetryPolicy(60000, 0, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT)
        queue.add(request)
    }

    private fun showSnackbar(message: String, isError: Boolean = false) {
        val snackbar = Snackbar.make(binding.main, message, Snackbar.LENGTH_LONG)
        snackbar.anchorView = binding.btnUpdate
        if (isError) {
            snackbar.setBackgroundTint(ContextCompat.getColor(this, R.color.error))
            snackbar.setTextColor(ContextCompat.getColor(this, R.color.white))
        } else {
            snackbar.setBackgroundTint(Color.parseColor("#1F2937"))
            snackbar.setTextColor(ContextCompat.getColor(this, R.color.white))
        }
        snackbar.show()
    }
}
