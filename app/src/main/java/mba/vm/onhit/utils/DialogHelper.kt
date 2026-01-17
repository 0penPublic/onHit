package mba.vm.onhit.utils

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import mba.vm.onhit.Constant
import mba.vm.onhit.R
import mba.vm.onhit.core.ConfigManager

object DialogHelper {

    fun createBottomDialog(context: Context, layoutRes: Int): Dialog {
        val dialog = Dialog(context, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar)
        val view = LayoutInflater.from(context).inflate(layoutRes, null)
        dialog.setContentView(view)
        dialog.window?.let { window ->
            window.setGravity(Gravity.BOTTOM)
            window.setBackgroundDrawableResource(android.R.color.transparent)
            val params = window.attributes
            params.width = WindowManager.LayoutParams.MATCH_PARENT
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            window.attributes = params
            window.setWindowAnimations(android.R.style.Animation_InputMethod)
        }
        return dialog
    }

    fun showInputBottomSheet(
        context: Context,
        title: String,
        defaultText: String = "",
        onConfirm: (String) -> Unit
    ) {
        val dialog = createBottomDialog(context, R.layout.bottom_dialog_input)
        val etInput = dialog.findViewById<EditText>(R.id.et_input)
        val btnOk = dialog.findViewById<Button>(R.id.btn_ok)
        val btnCancel = dialog.findViewById<Button>(R.id.btn_cancel)
        
        dialog.findViewById<TextView>(R.id.tv_title).text = title
        etInput.setText(defaultText)
        btnOk.setText(android.R.string.ok)
        btnCancel.setText(android.R.string.cancel)
        
        btnOk.setOnClickListener {
            val text = etInput.text.toString()
            if (text.isNotEmpty()) {
                onConfirm(text)
                dialog.dismiss()
            }
        }
        btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    fun showConfirmBottomSheet(
        context: Context,
        title: String,
        message: String,
        onConfirm: () -> Unit
    ) {
        val dialog = createBottomDialog(context, R.layout.bottom_dialog_input)
        dialog.findViewById<TextView>(R.id.tv_title).text = title
        val etInput = dialog.findViewById<EditText>(R.id.et_input)
        etInput.visibility = View.GONE
        
        val tvMsg = TextView(context).apply {
            text = message
            textSize = 16f
            setPadding(0, 0, 0, 24)
        }
        (etInput.parent as ViewGroup).addView(tvMsg, 1)

        val btnOk = dialog.findViewById<Button>(R.id.btn_ok)
        val btnCancel = dialog.findViewById<Button>(R.id.btn_cancel)
        
        btnOk.setText(android.R.string.cancel)
        btnCancel.setText(android.R.string.ok)
        btnCancel.setTextColor(0xFFFF5252.toInt())

        btnOk.setOnClickListener { dialog.dismiss() }
        btnCancel.setOnClickListener {
            onConfirm()
            dialog.dismiss()
        }
        dialog.show()
    }

    fun showNfcDialog(context: Context, title: String? = null, message: String? = null, onCancel: () -> Unit): Dialog {
        val dialog = createBottomDialog(context, R.layout.bottom_dialog_nfc)
        title?.let { dialog.findViewById<TextView>(R.id.tv_title)?.text = it }
        message?.let { dialog.findViewById<TextView>(R.id.tv_prompt)?.text = it }
        dialog.findViewById<View>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }
        dialog.setOnDismissListener { onCancel() }
        dialog.show()
        return dialog
    }

    fun showSettingsSheet(context: Context, onChangeDir: () -> Unit) {
        val dialog = createBottomDialog(context, R.layout.bottom_sheet_settings)
        
        val btnChangeDir = dialog.findViewById<View>(R.id.btn_change_dir)
        val btnGithub = dialog.findViewById<View>(R.id.btn_github)
        val switchFixedUid = dialog.findViewById<Switch>(R.id.switch_fixed_uid)
        val uidConfigSummary = dialog.findViewById<TextView>(R.id.tv_uid_config_summary)
        val etUidConfig = dialog.findViewById<EditText>(R.id.et_uid_config)

        btnChangeDir.setOnClickListener {
            dialog.dismiss()
            onChangeDir()
        }

        btnGithub.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(Constant.GITHUB_URL))
            context.startActivity(intent)
        }

        val isFixed = ConfigManager.isFixedUid(context)
        switchFixedUid.isChecked = isFixed
        updateUidEditText(uidConfigSummary, etUidConfig, isFixed, context)

        switchFixedUid.setOnCheckedChangeListener { _, isChecked ->
            ConfigManager.setFixedUid(context, isChecked)
            updateUidEditText(uidConfigSummary, etUidConfig, isChecked, context)
        }

        etUidConfig.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val input = s.toString()
                if (ConfigManager.isFixedUid(context)) {
                    val hex = HexUtils.filterHex(input)
                    if (hex != input) {
                        etUidConfig.setText(hex)
                        etUidConfig.setSelection(hex.length)
                    }
                    ConfigManager.setFixedUidValue(context, hex)
                } else {
                    val len = input.toIntOrNull()
                    if (len != null && len in 0..65536) {
                        setNormalTextColor(etUidConfig, context)
                        ConfigManager.setRandomUidLen(context, input)
                    } else {
                        etUidConfig.setTextColor(Color.RED)
                    }
                }
            }
        })

        dialog.show()
    }

    private fun setNormalTextColor(et: EditText, context: Context) {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
        et.setTextColor(typedValue.data)
    }

    private fun updateUidEditText(summary: TextView, et: EditText, isFixed: Boolean, context: Context) {
        if (isFixed) {
            et.hint = context.getString(R.string.settings_hint_fixed_uid)
            summary.text = context.getString(R.string.settings_hint_fixed_uid)
            et.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            et.setText(ConfigManager.getFixedUidValue(context))
        } else {
            et.hint = context.getString(R.string.settings_hint_random_uid_len)
            summary.text = context.getString(R.string.settings_hint_random_uid_len)
            et.inputType = InputType.TYPE_CLASS_NUMBER
            et.setText(ConfigManager.getRandomUidLen(context))
        }
        setNormalTextColor(et, context)
        et.setSelection(et.text.length)
    }
}
