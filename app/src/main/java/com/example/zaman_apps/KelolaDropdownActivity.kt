package com.example.zaman_apps

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.zaman_apps.databinding.ActivityKelolaDropdownBinding
import com.example.zaman_apps.utils.DropdownOptionsHelper
import com.google.android.material.card.MaterialCardView

class KelolaDropdownActivity : AppCompatActivity() {

    private lateinit var binding: ActivityKelolaDropdownBinding

    private val categoryKeys = listOf(
        DropdownOptionsHelper.KEY_EAS,
        DropdownOptionsHelper.KEY_MERK_GENSET,
        DropdownOptionsHelper.KEY_STATUS_GENSET,
        DropdownOptionsHelper.KEY_TYPE_RECTIFIER,
        DropdownOptionsHelper.KEY_JENIS_BATERAI,
        DropdownOptionsHelper.KEY_MERK_BATERAI,
        DropdownOptionsHelper.KEY_JUMLAH_BATTERAI,
        DropdownOptionsHelper.KEY_BALTING
    )

    private var currentCategoryKey = categoryKeys[0]

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

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categoryNames)
        binding.spKategoriDropdown.adapter = adapter

        binding.spKategoriDropdown.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position in categoryKeys.indices) {
                    currentCategoryKey = categoryKeys[position]
                    loadAndRenderOptions()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
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
                    loadAndRenderOptions()
                    Toast.makeText(this, "Berhasil di-reset ke default", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Batal", null)
                .show()
        }
    }

    private fun loadAndRenderOptions() {
        val options = DropdownOptionsHelper.getOptions(this, currentCategoryKey)
        binding.tvCategoryTitle.text = DropdownOptionsHelper.getCategoryName(currentCategoryKey)
        binding.tvOptionCount.text = "${options.size} Opsi"

        binding.containerOptionList.removeAllViews()

        if (options.isEmpty()) {
            val tvEmpty = TextView(this)
            tvEmpty.text = "Belum ada opsi pada kategori ini."
            tvEmpty.gravity = Gravity.CENTER
            tvEmpty.setPadding(0, 64, 0, 64)
            tvEmpty.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            binding.containerOptionList.addView(tvEmpty)
            return
        }

        options.forEachIndexed { index, item ->
            val card = buatCardOptionItem(index, item)
            binding.containerOptionList.addView(card)
        }
    }

    private fun buatCardOptionItem(index: Int, text: String): View {
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
        tvText.textSize = 14f
        tvText.setTypeface(null, Typeface.BOLD)
        tvText.setTextColor(ContextCompat.getColor(this, if (text.isEmpty()) R.color.text_secondary else R.color.text_primary))
        val textParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = 20
        }
        tvText.layoutParams = textParams
        root.addView(tvText)

        // Action Edit
        val btnEdit = TextView(this)
        btnEdit.text = "✏️"
        btnEdit.textSize = 16f
        btnEdit.setPadding(16, 12, 16, 12)
        btnEdit.setOnClickListener {
            dialogEditOption(index, text)
        }
        root.addView(btnEdit)

        // Action Delete
        val btnDelete = TextView(this)
        btnDelete.text = "🗑️"
        btnDelete.textSize = 16f
        btnDelete.setPadding(16, 12, 16, 12)
        btnDelete.setOnClickListener {
            dialogHapusOption(index, text)
        }
        root.addView(btnDelete)

        card.addView(root)
        return card
    }

    private fun dialogTambahOption() {
        val etInput = EditText(this)
        etInput.hint = "Masukkan nama option baru"
        etInput.setPadding(48, 36, 48, 36)

        val bg = GradientDrawable()
        bg.setColor(Color.parseColor("#F8F9FA"))
        bg.cornerRadius = 24f
        bg.setStroke(3, Color.parseColor("#E9ECEF"))
        etInput.background = bg

        val container = LinearLayout(this)
        container.setPadding(48, 24, 48, 12)
        container.addView(etInput)

        AlertDialog.Builder(this)
            .setTitle("Tambah Option Baru")
            .setMessage("Kategori: ${DropdownOptionsHelper.getCategoryName(currentCategoryKey)}")
            .setView(container)
            .setPositiveButton("Simpan") { _, _ ->
                val newText = etInput.text.toString().trim()
                if (newText.isNotEmpty()) {
                    DropdownOptionsHelper.addOption(this, currentCategoryKey, newText)
                    loadAndRenderOptions()
                    Toast.makeText(this, "Option berhasil ditambahkan", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Nama option tidak boleh kosong", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun dialogEditOption(index: Int, oldText: String) {
        val etInput = EditText(this)
        etInput.setText(oldText)
        etInput.setPadding(48, 36, 48, 36)

        val bg = GradientDrawable()
        bg.setColor(Color.parseColor("#F8F9FA"))
        bg.cornerRadius = 24f
        bg.setStroke(3, Color.parseColor("#E9ECEF"))
        etInput.background = bg

        val container = LinearLayout(this)
        container.setPadding(48, 24, 48, 12)
        container.addView(etInput)

        AlertDialog.Builder(this)
            .setTitle("Edit Option")
            .setView(container)
            .setPositiveButton("Simpan") { _, _ ->
                val newText = etInput.text.toString().trim()
                DropdownOptionsHelper.updateOption(this, currentCategoryKey, index, newText)
                loadAndRenderOptions()
                Toast.makeText(this, "Option berhasil diubah", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun dialogHapusOption(index: Int, oldText: String) {
        val label = if (oldText.isEmpty()) "(Kosong / Blank)" else "\"$oldText\""
        AlertDialog.Builder(this)
            .setTitle("Hapus Option")
            .setMessage("Yakin ingin menghapus $label dari kategori ini?")
            .setPositiveButton("Hapus") { _, _ ->
                DropdownOptionsHelper.deleteOption(this, currentCategoryKey, index)
                loadAndRenderOptions()
                Toast.makeText(this, "Option berhasil dihapus", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}
