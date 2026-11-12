package com.example.zaman_apps

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.zaman_apps.databinding.ActivityKelolaDropdownBinding
import com.example.zaman_apps.utils.DropdownOptionsHelper
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class KelolaDropdownActivity : AppCompatActivity() {

    private lateinit var binding: ActivityKelolaDropdownBinding

    private val categoryKeys = listOf(
        DropdownOptionsHelper.KEY_EAS,
        DropdownOptionsHelper.KEY_MERK_GENSET,
        DropdownOptionsHelper.KEY_STATUS_GENSET,
        DropdownOptionsHelper.KEY_TYPE_RECTIFIER,
        DropdownOptionsHelper.KEY_STATUS_RECTIFIER,
        DropdownOptionsHelper.KEY_BACKUP_TIME,
        DropdownOptionsHelper.KEY_JENIS_BATERAI,
        DropdownOptionsHelper.KEY_MERK_BATERAI,
        DropdownOptionsHelper.KEY_JUMLAH_BATTERAI,
        DropdownOptionsHelper.KEY_BALTING,
        DropdownOptionsHelper.KEY_JUMLAH_BANK_NOT_AKTIF,
        DropdownOptionsHelper.KEY_BATERAI_MAPPING
    )

    private var currentCategoryKey = categoryKeys[0]
    private var currentPage = 1
    private val itemsPerPage = 10

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityKelolaDropdownBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        setupCategorySpinner()
        setupListeners()
        loadAndRenderOptions()
    }

    private fun setupCategorySpinner() {
        val categoryNames = categoryKeys.mapIndexed { index, key ->
            "${index + 1}. ${DropdownOptionsHelper.getCategoryName(key)}"
        }

        val adapter = ArrayAdapter(this, R.layout.item_dropdown_site_id, R.id.tvSiteIdItem, categoryNames)
        binding.actvKategoriDropdown.setAdapter(adapter)

        if (categoryNames.isNotEmpty()) {
            binding.actvKategoriDropdown.setText(categoryNames[0], false)
        }

        binding.actvKategoriDropdown.setOnItemClickListener { _, _, position, _ ->
            if (position in categoryKeys.indices) {
                currentCategoryKey = categoryKeys[position]
                currentPage = 1
                loadAndRenderOptions()
            }
        }

        binding.actvKategoriDropdown.setOnClickListener {
            try {
                binding.actvKategoriDropdown.showDropDown()
            } catch (e: Exception) {
                Log.e("KelolaDropdown", "Error showing dropdown: ${e.message}")
            }
        }
    }

    private fun setupListeners() {
        binding.btnTambahOption.setOnClickListener {
            dialogTambahOption()
        }

        binding.btnResetDefault.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Reset Opsi Kategori")
                .setMessage("Yakin ingin mengembalikan opsi kategori ini ke daftar default bawaan?")
                .setPositiveButton("Reset") { _, _ ->
                    DropdownOptionsHelper.resetToDefault(this, currentCategoryKey)
                    currentPage = 1
                    loadAndRenderOptions()
                    Toast.makeText(this, "Berhasil di-reset ke default", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Batal", null)
                .show()
        }

        binding.btnPrevPage.setOnClickListener {
            if (currentPage > 1) {
                currentPage--
                loadAndRenderOptions()
            }
        }

        binding.btnNextPage.setOnClickListener {
            currentPage++
            loadAndRenderOptions()
        }
    }

    private fun loadAndRenderOptions() {
        binding.tvCategoryTitle.text = DropdownOptionsHelper.getCategoryName(currentCategoryKey)
        binding.containerOptionList.removeAllViews()

        if (currentCategoryKey == DropdownOptionsHelper.KEY_BATERAI_MAPPING) {
            renderBateraiMappingList()
            return
        }

        binding.btnTambahOption.visibility = View.VISIBLE
        binding.btnResetDefault.visibility = View.VISIBLE

        val options = DropdownOptionsHelper.getOptions(this, currentCategoryKey)
        val totalItems = options.size
        val totalPages = maxOf(1, Math.ceil(totalItems.toDouble() / itemsPerPage).toInt())

        if (currentPage > totalPages) currentPage = totalPages
        if (currentPage < 1) currentPage = 1

        if (options.isEmpty()) {
            binding.tvOptionCount.text = "0 Opsi Terdaftar"
            binding.cardPagination.visibility = View.GONE
            val tvEmpty = TextView(this)
            tvEmpty.text = "Belum ada opsi pada kategori ini."
            tvEmpty.gravity = Gravity.CENTER
            tvEmpty.setPadding(0, 64, 0, 64)
            tvEmpty.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            binding.containerOptionList.addView(tvEmpty)
            return
        }

        val startIndex = (currentPage - 1) * itemsPerPage
        val endIndex = minOf(startIndex + itemsPerPage, totalItems)
        val pageItems = options.subList(startIndex, endIndex)

        binding.tvOptionCount.text = "Menampilkan ${startIndex + 1} - $endIndex dari $totalItems Opsi"

        if (totalPages > 1) {
            binding.cardPagination.visibility = View.VISIBLE
            binding.tvPageIndicator.text = "Halaman $currentPage dari $totalPages"
            binding.btnPrevPage.isEnabled = (currentPage > 1)
            binding.btnNextPage.isEnabled = (currentPage < totalPages)
        } else {
            binding.cardPagination.visibility = View.GONE
        }

        pageItems.forEachIndexed { relativeIndex, item ->
            val globalIndex = startIndex + relativeIndex
            val card = buatCardOptionItem(globalIndex, item)
            binding.containerOptionList.addView(card)
        }
    }

    private fun renderBateraiMappingList() {
        binding.btnTambahOption.visibility = View.GONE
        binding.btnResetDefault.visibility = View.GONE

        val jenisList = DropdownOptionsHelper.getOptions(this, DropdownOptionsHelper.KEY_JENIS_BATERAI).filter { it.isNotBlank() }
        val totalItems = jenisList.size
        val totalPages = maxOf(1, Math.ceil(totalItems.toDouble() / itemsPerPage).toInt())

        if (currentPage > totalPages) currentPage = totalPages
        if (currentPage < 1) currentPage = 1

        if (jenisList.isEmpty()) {
            binding.tvOptionCount.text = "0 Jenis Baterai"
            binding.cardPagination.visibility = View.GONE
            val tvEmpty = TextView(this)
            tvEmpty.text = "Belum ada jenis baterai terdaftar. Silakan tambahkan Jenis Baterai terlebih dahulu."
            tvEmpty.gravity = Gravity.CENTER
            tvEmpty.setPadding(0, 64, 0, 64)
            tvEmpty.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            binding.containerOptionList.addView(tvEmpty)
            return
        }

        val startIndex = (currentPage - 1) * itemsPerPage
        val endIndex = minOf(startIndex + itemsPerPage, totalItems)
        val pageItems = jenisList.subList(startIndex, endIndex)

        binding.tvOptionCount.text = "Menampilkan ${startIndex + 1} - $endIndex dari $totalItems Jenis Baterai"

        if (totalPages > 1) {
            binding.cardPagination.visibility = View.VISIBLE
            binding.tvPageIndicator.text = "Halaman $currentPage dari $totalPages"
            binding.btnPrevPage.isEnabled = (currentPage > 1)
            binding.btnNextPage.isEnabled = (currentPage < totalPages)
        } else {
            binding.cardPagination.visibility = View.GONE
        }

        pageItems.forEachIndexed { relativeIndex, jenisName ->
            val globalIndex = startIndex + relativeIndex
            val mappedMerks = DropdownOptionsHelper.getMerkMappingForJenis(this, jenisName)
            val mappedJumlahs = DropdownOptionsHelper.getJumlahMappingForJenis(this, jenisName)

            val card = buatCardMappingItem(globalIndex, jenisName, mappedMerks, mappedJumlahs)
            binding.containerOptionList.addView(card)
        }
    }

    private fun buatCardMappingItem(index: Int, jenisName: String, merks: List<String>, jumlahs: List<String>): View {
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
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(24, 20, 20, 20)

        // Title Row
        val titleRow = LinearLayout(this)
        titleRow.orientation = LinearLayout.HORIZONTAL
        titleRow.gravity = Gravity.CENTER_VERTICAL

        val tvIndex = TextView(this)
        tvIndex.text = "#${index + 1}"
        tvIndex.textSize = 12f
        tvIndex.setTypeface(null, Typeface.BOLD)
        tvIndex.setTextColor(ContextCompat.getColor(this, R.color.brand_primary))
        tvIndex.setBackgroundResource(R.drawable.bg_badge)
        tvIndex.setPadding(16, 6, 16, 6)
        titleRow.addView(tvIndex)

        val tvJenis = TextView(this)
        tvJenis.text = jenisName
        tvJenis.textSize = 15f
        tvJenis.setTypeface(null, Typeface.BOLD)
        tvJenis.setTextColor(ContextCompat.getColor(this, R.color.brand_primary))
        val textParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = 16
        }
        tvJenis.layoutParams = textParams
        titleRow.addView(tvJenis)

        val btnAtur = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonStyle)
        btnAtur.text = "Atur Relasi"
        btnAtur.textSize = 12f
        btnAtur.setTypeface(null, Typeface.BOLD)
        btnAtur.setTextColor(ContextCompat.getColor(this, R.color.white))
        btnAtur.backgroundTintList = ContextCompat.getColorStateList(this, R.color.brand_primary)
        btnAtur.cornerRadius = 20
        btnAtur.setOnClickListener {
            dialogAturRelasiBaterai(jenisName)
        }
        titleRow.addView(btnAtur)

        root.addView(titleRow)

        // Divider
        val divider = View(this)
        val divParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply {
            topMargin = 12
            bottomMargin = 12
        }
        divider.layoutParams = divParams
        divider.setBackgroundColor(ContextCompat.getColor(this, R.color.divider))
        root.addView(divider)

        // Mapping Details
        val tvMerkLabel = TextView(this)
        val merkText = if (merks.isEmpty()) "Aturan Default (Semua / Bawaan)" else merks.joinToString(", ")
        tvMerkLabel.text = "🏷️ Merk Terhubung: $merkText"
        tvMerkLabel.textSize = 13f
        tvMerkLabel.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        root.addView(tvMerkLabel)

        val tvJmlLabel = TextView(this)
        val jmlText = if (jumlahs.isEmpty()) "Aturan Default (Semua / Bawaan)" else jumlahs.joinToString(", ")
        tvJmlLabel.text = "🔢 Jumlah Terhubung: $jmlText"
        tvJmlLabel.textSize = 13f
        tvJmlLabel.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        tvJmlLabel.setPadding(0, 4, 0, 0)
        root.addView(tvJmlLabel)

        card.addView(root)
        return card
    }

    private fun dialogAturRelasiBaterai(jenisName: String) {
        val allMerkOptions = DropdownOptionsHelper.getOptions(this, DropdownOptionsHelper.KEY_MERK_BATERAI).filter { it.isNotBlank() }
        val currentMerkSelected = DropdownOptionsHelper.getMerkMappingForJenis(this, jenisName)
        val checkedMerks = BooleanArray(allMerkOptions.size) { idx ->
            currentMerkSelected.contains(allMerkOptions[idx])
        }

        AlertDialog.Builder(this)
            .setTitle("1. Pilih Merk Baterai untuk [$jenisName]")
            .setMultiChoiceItems(allMerkOptions.toTypedArray(), checkedMerks) { _, which, isChecked ->
                checkedMerks[which] = isChecked
            }
            .setPositiveButton("Lanjut ke Jumlah Battery ➔") { _, _ ->
                val selectedMerks = mutableListOf<String>()
                checkedMerks.forEachIndexed { i, checked ->
                    if (checked) selectedMerks.add(allMerkOptions[i])
                }
                dialogAturRelasiJumlahBattery(jenisName, selectedMerks)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun dialogAturRelasiJumlahBattery(jenisName: String, selectedMerks: List<String>) {
        val allJumlahOptions = DropdownOptionsHelper.getOptions(this, DropdownOptionsHelper.KEY_JUMLAH_BATTERAI).filter { it.isNotBlank() }
        val currentJumlahSelected = DropdownOptionsHelper.getJumlahMappingForJenis(this, jenisName)
        val checkedJumlahs = BooleanArray(allJumlahOptions.size) { idx ->
            currentJumlahSelected.contains(allJumlahOptions[idx])
        }

        AlertDialog.Builder(this)
            .setTitle("2. Pilih Jumlah Battery untuk [$jenisName]")
            .setMultiChoiceItems(allJumlahOptions.toTypedArray(), checkedJumlahs) { _, which, isChecked ->
                checkedJumlahs[which] = isChecked
            }
            .setPositiveButton("Simpan Relasi Cloud") { _, _ ->
                val selectedJumlahs = mutableListOf<String>()
                checkedJumlahs.forEachIndexed { i, checked ->
                    if (checked) selectedJumlahs.add(allJumlahOptions[i])
                }

                DropdownOptionsHelper.saveMappingForJenis(this, jenisName, selectedMerks, selectedJumlahs)
                loadAndRenderOptions()
                Toast.makeText(this, "Relasi Baterai untuk [$jenisName] berhasil disimpan ke cloud", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun buatCardOptionItem(index: Int, text: String): View {
        val card = MaterialCardView(this)
        val cardParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 14 }
        card.layoutParams = cardParams
        card.radius = 16f
        card.cardElevation = 3f
        card.strokeWidth = 1
        card.strokeColor = ContextCompat.getColor(this, R.color.divider)
        card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.card_bg))

        val root = LinearLayout(this)
        root.orientation = LinearLayout.HORIZONTAL
        root.gravity = Gravity.CENTER_VERTICAL
        root.setPadding(20, 16, 16, 16)

        // Index Badge
        val tvIndex = TextView(this)
        tvIndex.text = "#${index + 1}"
        tvIndex.textSize = 12f
        tvIndex.setTypeface(null, Typeface.BOLD)
        tvIndex.setTextColor(ContextCompat.getColor(this, R.color.brand_primary))
        tvIndex.setBackgroundResource(R.drawable.bg_badge)
        tvIndex.setPadding(16, 8, 16, 8)
        root.addView(tvIndex)

        // Option Text
        val tvText = TextView(this)
        tvText.text = if (text.isEmpty()) "(Kosong / Blank)" else text
        tvText.textSize = 15f
        tvText.setTypeface(null, Typeface.BOLD)
        tvText.setTextColor(ContextCompat.getColor(this, if (text.isEmpty()) R.color.text_secondary else R.color.text_primary))
        val textParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = 16
        }
        tvText.layoutParams = textParams
        root.addView(tvText)

        // Action Buttons Row
        val actionRow = LinearLayout(this)
        actionRow.orientation = LinearLayout.HORIZONTAL
        actionRow.gravity = Gravity.CENTER_VERTICAL

        // Edit Button (Icon + Text)
        val btnEdit = MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle)
        btnEdit.text = "Edit"
        btnEdit.textSize = 13f
        btnEdit.setTypeface(null, Typeface.BOLD)
        btnEdit.setTextColor(ContextCompat.getColor(this, R.color.brand_primary))
        btnEdit.setIconResource(R.drawable.ic_edit)
        btnEdit.iconTint = ContextCompat.getColorStateList(this, R.color.brand_primary)
        btnEdit.iconSize = (18 * resources.displayMetrics.density).toInt()
        btnEdit.setOnClickListener {
            dialogEditOption(index, text)
        }
        actionRow.addView(btnEdit)

        // Hapus Button (Text)
        val btnDelete = MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle)
        btnDelete.text = "Hapus"
        btnDelete.textSize = 13f
        btnDelete.setTypeface(null, Typeface.BOLD)
        btnDelete.setTextColor(ContextCompat.getColor(this, R.color.error))
        btnDelete.setOnClickListener {
            dialogHapusOption(index, text)
        }
        actionRow.addView(btnDelete)

        root.addView(actionRow)
        card.addView(root)

        // Animasi muncul berurutan
        card.alpha = 0f
        card.translationY = 40f
        card.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(350)
            .setStartDelay(50L * (index % 8))
            .setInterpolator(OvershootInterpolator(1.1f))
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

    private fun createFormInputLayout(hintText: String): Pair<TextInputLayout, TextInputEditText> {
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

        val et = TextInputEditText(til.context)
        et.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        et.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_FILTER
        et.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        et.setTextSize(14f)

        til.addView(et)
        return Pair(til, et)
    }

    private fun dialogTambahOption() {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL

        val header = buatCustomDialogHeader(
            R.drawable.ic_input,
            "Tambah Opsi Dropdown Baru",
            "Kategori: ${DropdownOptionsHelper.getCategoryName(currentCategoryKey)}"
        )
        root.addView(header)

        val formContainer = LinearLayout(this)
        formContainer.orientation = LinearLayout.VERTICAL
        formContainer.setPadding(24, 8, 24, 16)

        val (tilInput, etInput) = createFormInputLayout("Nama Opsi Baru *")
        formContainer.addView(tilInput)
        root.addView(formContainer)

        AlertDialog.Builder(this)
            .setView(root)
            .setPositiveButton("Simpan Opsi") { _, _ ->
                val newText = etInput.text.toString().trim()
                if (newText.isNotEmpty()) {
                    DropdownOptionsHelper.addOption(this, currentCategoryKey, newText)
                    loadAndRenderOptions()
                    Toast.makeText(this, "Opsi berhasil ditambahkan ke cloud", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Nama opsi tidak boleh kosong", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun dialogEditOption(index: Int, oldText: String) {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL

        val header = buatCustomDialogHeader(
            R.drawable.ic_edit,
            "Edit Opsi Dropdown",
            "Kategori: ${DropdownOptionsHelper.getCategoryName(currentCategoryKey)}"
        )
        root.addView(header)

        val formContainer = LinearLayout(this)
        formContainer.orientation = LinearLayout.VERTICAL
        formContainer.setPadding(24, 8, 24, 16)

        val (tilInput, etInput) = createFormInputLayout("Nama Opsi *")
        etInput.setText(oldText)
        formContainer.addView(tilInput)
        root.addView(formContainer)

        AlertDialog.Builder(this)
            .setView(root)
            .setPositiveButton("Simpan Perubahan") { _, _ ->
                val newText = etInput.text.toString().trim()
                DropdownOptionsHelper.updateOption(this, currentCategoryKey, index, newText)
                loadAndRenderOptions()
                Toast.makeText(this, "Opsi berhasil diperbarui ke cloud", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun dialogHapusOption(index: Int, oldText: String) {
        val label = if (oldText.isEmpty()) "(Kosong / Blank)" else "\"$oldText\""

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
        tvTitle.text = "Konfirmasi Hapus Opsi"
        tvTitle.textSize = 16f
        tvTitle.setTypeface(null, Typeface.BOLD)
        tvTitle.setTextColor(ContextCompat.getColor(this, R.color.error))
        textCol.addView(tvTitle)

        val tvSub = TextView(this)
        tvSub.text = "Kategori: ${DropdownOptionsHelper.getCategoryName(currentCategoryKey)}"
        tvSub.textSize = 12f
        tvSub.setTextColor(Color.parseColor("#991B1B"))
        textCol.addView(tvSub)

        headerRow.addView(textCol)
        headerCard.addView(headerRow)
        root.addView(headerCard)

        val tvMsg = TextView(this)
        tvMsg.text = "Yakin ingin menghapus opsi $label dari kategori ini?\n\nPerubahan ini akan tersimpan ke cloud dan berlaku untuk seluruh pengguna."
        tvMsg.textSize = 14f
        tvMsg.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        tvMsg.setLineSpacing(4f, 1f)
        root.addView(tvMsg)

        AlertDialog.Builder(this)
            .setView(root)
            .setPositiveButton("Ya, Hapus Opsi") { _, _ ->
                DropdownOptionsHelper.deleteOption(this, currentCategoryKey, index)
                loadAndRenderOptions()
                Toast.makeText(this, "Opsi berhasil dihapus dari cloud", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}
