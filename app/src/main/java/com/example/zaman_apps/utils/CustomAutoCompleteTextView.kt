package com.example.zaman_apps.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import com.google.android.material.R
import com.google.android.material.textfield.MaterialAutoCompleteTextView

/**
 * Custom AutoCompleteTextView yang mempertahankan daftar dropdown tetap terbuka di layar
 * saat soft keyboard ditutup dengan tombol Back HP.
 */
class CustomAutoCompleteTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.autoCompleteTextViewStyle
) : MaterialAutoCompleteTextView(context, attrs, defStyleAttr) {

    private var isHandlingBack = false

    override fun dismissDropDown() {
        if (isHandlingBack) {
            // Blokir penutupan dropdown bawaan Android SDK saat tombol Back keyboard ditekan
            return
        }
        super.dismissDropDown()
    }

    override fun onKeyPreIme(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            if (imm != null && imm.isAcceptingText) {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    return true
                } else if (event.action == KeyEvent.ACTION_UP) {
                    isHandlingBack = true
                    val hidden = imm.hideSoftInputFromWindow(windowToken, 0)

                    Handler(Looper.getMainLooper()).postDelayed({
                        isHandlingBack = false
                        if (!isPopupShowing && adapter != null && adapter.count > 0) {
                            try { showDropDown() } catch (_: Exception) {}
                        }
                    }, 100)

                    if (hidden) {
                        return true
                    } else {
                        isHandlingBack = false
                    }
                }
            }
        }
        return super.onKeyPreIme(keyCode, event)
    }
}
