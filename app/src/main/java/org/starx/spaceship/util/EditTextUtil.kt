package org.starx.spaceship.util

import android.text.InputType
import android.text.TextUtils
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import org.starx.spaceship.R

class EditTextUtil(val et: EditTextPreference) {
    fun setNumberOnly(start: Int = 0, end: Int = 0): EditTextUtil{
        et.apply {
            setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_NUMBER
            }
            setOnPreferenceChangeListener { _, newValue ->
                if (start == end) return@setOnPreferenceChangeListener true
                val value: Int
                try {
                    value = (newValue as String).toInt()
                }catch (_: java.lang.NumberFormatException){
                    Toast.makeText(context, "Invalid number",
                        Toast.LENGTH_SHORT).show()
                    return@setOnPreferenceChangeListener false
                }
                val isValid = (value in start..end)
                if (!isValid) {
                    Toast.makeText(context, "Invalid number range, should be $start..$end",
                        Toast.LENGTH_SHORT).show()
                }
                isValid
            }
        }
        return this
    }
    fun setPasswordWithMask(char: Char='*'): EditTextUtil{
        et.apply {
            isSingleLineTitle = true
            dialogLayoutResource = R.layout.widget_textfield
            setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
        }
        setMask(char)
        return this
    }
    private fun setMask(char: Char): EditTextUtil{
        et.summaryProvider = Preference.SummaryProvider<EditTextPreference> { preference ->
            val text = preference.text
            if (TextUtils.isEmpty(text)) {
                "Not set"
            } else {
                val sb = StringBuilder()
                for (i: Int in 1..text!!.length) {
                    sb.append(char)
                }
                sb
            }
        }
        return this
    }
    fun setSuffix(suffix: String): EditTextUtil{
        et.summaryProvider = Preference.SummaryProvider<EditTextPreference> { preference ->
            val text = preference.text
            if (TextUtils.isEmpty(text)) {
                "Not set"
            } else {
                val sb = StringBuilder()
                sb.append(text)
                sb.append(suffix)
                sb
            }
        }
        return this
    }
}