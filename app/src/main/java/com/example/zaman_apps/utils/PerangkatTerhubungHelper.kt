package com.example.zaman_apps.utils

import android.content.Context
import androidx.appcompat.app.AlertDialog

object PerangkatTerhubungHelper {

    val OPTIONS = arrayOf(
        "RBS",
        "RRU",
        "ONT/GPON/METRO",
        "MN TN/CN",
        "RTN"
    )

    fun showDialog(
        context: Context,
        rectiNum: Int,
        currentValue: String,
        onSelected: (String) -> Unit
    ) {
        val currentSelected = currentValue.split("/").map { it.trim() }
        val checkedItems = BooleanArray(OPTIONS.size) { idx ->
            currentSelected.any { it.equals(OPTIONS[idx], ignoreCase = true) }
        }

        AlertDialog.Builder(context)
            .setTitle("Perangkat Terhubung Recti #$rectiNum")
            .setMultiChoiceItems(OPTIONS, checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setPositiveButton("OK") { _, _ ->
                val selectedList = mutableListOf<String>()
                checkedItems.forEachIndexed { index, checked ->
                    if (checked) selectedList.add(OPTIONS[index])
                }
                val result = selectedList.joinToString("/")
                onSelected(result)
            }
            .setNeutralButton("Reset") { _, _ ->
                onSelected("")
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}
