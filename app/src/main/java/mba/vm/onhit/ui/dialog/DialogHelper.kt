package mba.vm.onhit.ui.dialog

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.core.net.toUri
import androidx.core.view.isVisible
import mba.vm.onhit.Constant
import mba.vm.onhit.R
import mba.vm.onhit.ui.config.ConfigManager
import mba.vm.onhit.utils.HexUtils

object DialogHelper {

    fun showNdefEditorDialog(
        activity: Activity,
        isReadOnly: Boolean = false,
        initialBytes: ByteArray? = null,
        onResult: (ByteArray) -> Unit
    ): NdefEditorDialog {
        val dialog = NdefEditorDialog(activity, isReadOnly, initialBytes, onResult)
        dialog.show()
        return dialog
    }

    fun requestPickFile(activity: Activity) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        activity.startActivityForResult(intent, Constant.REQUEST_SELECT_NDEF_FILE)
    }

    fun requestSaveFile(activity: Activity, filename: String) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_TITLE, "${filename.replace("/", "_")}_payload.bin")
        }
        activity.startActivityForResult(intent, Constant.REQUEST_SAVE_FILE)
    }

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

    fun showShortcutBottomSheet(
        activity: Activity,
        defaultName: String,
        onChangeIcon: () -> Unit,
        onConfirm: (name: String) -> Unit
    ): Dialog {
        val dialog = createBottomDialog(activity, R.layout.bottom_dialog_shortcut)
        val etName = dialog.findViewById<EditText>(R.id.et_shortcut_name)
        val ivIcon = dialog.findViewById<android.widget.ImageView>(R.id.iv_shortcut_icon)
        val btnOk = dialog.findViewById<Button>(R.id.btn_ok)
        val btnCancel = dialog.findViewById<Button>(R.id.btn_cancel)

        etName.setText(defaultName)

        ivIcon.setOnClickListener {
            onChangeIcon()
        }

        btnOk.setOnClickListener {
            val name = etName.text.toString()
            if (name.isNotEmpty()) {
                onConfirm(name)
                dialog.dismiss()
            }
        }
        btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
        return dialog
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

    fun showSettingsSheet(
        context: Context,
        onChangeDir: () -> Unit,
        onChangeBackground: () -> Unit,
        onClearBackground: () -> Unit
    )

    {
        val dialog = createBottomDialog(context, R.layout.bottom_sheet_settings)

        val btnChangeDir = dialog.findViewById<View>(R.id.btn_change_dir)
        val btnRestartNfc = dialog.findViewById<View>(R.id.btn_restart_nfc)
        val btnGithub = dialog.findViewById<View>(R.id.btn_github)
        val btnTelegram = dialog.findViewById<View>(R.id.btn_telegram)
        val spinnerUidMode = dialog.findViewById<Spinner>(R.id.spinner_uid_mode)
        val uidConfigSummary = dialog.findViewById<TextView>(R.id.tv_uid_config_summary)
        val etUidConfig = dialog.findViewById<EditText>(R.id.et_uid_config)

        val btnChangeBackground = dialog.findViewById<View>(R.id.btn_change_background)
        val btnClearBackground = dialog.findViewById<View>(R.id.btn_clear_background)
        val backgroundSummary = dialog.findViewById<TextView>(R.id.tv_background_summary)

        backgroundSummary.text =
            if (ConfigManager.getBackgroundUri(context) == null) {
                context.getString(R.string.settings_background_default)
            } else {
                context.getString(R.string.settings_background_custom)
            }

        btnChangeBackground.setOnClickListener {
            dialog.dismiss()
            onChangeBackground()
        }

        btnClearBackground.setOnClickListener {

            ConfigManager.setBackgroundUri(context, null)

            backgroundSummary.text =
                context.getString(R.string.settings_background_default)

            dialog.dismiss()

            onClearBackground()
        }

        btnChangeDir.setOnClickListener {
            dialog.dismiss()
            onChangeDir()
        }

        btnRestartNfc.setOnClickListener {
            showConfirmBottomSheet(
                context,
                context.getString(R.string.dialog_title_confirm_restart_nfc),
                context.getString(R.string.confirm_restart_nfc_hint)
            ) {
                context.sendBroadcast(Intent(Constant.BROADCAST_RESTART_NFC_SERVICE))
                dialog.dismiss()
            }
        }

        btnGithub.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Constant.GITHUB_URL.toUri())
            context.startActivity(intent)
        }

        btnTelegram.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Constant.TELEGRAM_URL.toUri())
            context.startActivity(intent)
        }

        val uidModes = listOf(
            context.getString(R.string.settings_uid_mode_file),
            context.getString(R.string.settings_uid_mode_len),
            context.getString(R.string.settings_uid_mode_fixed)
        )

        spinnerUidMode.adapter = ArrayAdapter(
            context,
            android.R.layout.simple_spinner_item,
            uidModes
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        val currentMode = ConfigManager.getUidMode(context)
        spinnerUidMode.setSelection(currentMode)
        updateUidEditText(uidConfigSummary, etUidConfig, currentMode, context)

        spinnerUidMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                ConfigManager.setUidMode(context, position)
                updateUidEditText(uidConfigSummary, etUidConfig, position, context)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        etUidConfig.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val input = s.toString()
                val mode = ConfigManager.getUidMode(context)
                if (mode == ConfigManager.UID_MODE_FIXED) {
                    val hex = HexUtils.filterHex(input)
                    if (hex != input) {
                        etUidConfig.setText(hex)
                        etUidConfig.setSelection(hex.length)
                    }
                    ConfigManager.setFixedUidValue(context, hex)
                } else if (mode == ConfigManager.UID_MODE_LEN) {
                    val len = input.toIntOrNull()
                    if (len != null && len in 0..65535) {
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

    private fun updateUidEditText(summary: TextView, et: EditText, mode: Int, context: Context) {
        when (mode) {
            ConfigManager.UID_MODE_FILE -> {
                summary.text = context.getString(R.string.settings_uid_mode_file)
                et.visibility = View.GONE
            }
            ConfigManager.UID_MODE_FIXED -> {
                et.visibility = View.VISIBLE
                et.hint = context.getString(R.string.settings_hint_fixed_uid)
                summary.text = context.getString(R.string.settings_hint_fixed_uid)
                et.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                et.setText(ConfigManager.getFixedUidValue(context))
            }
            ConfigManager.UID_MODE_LEN -> {
                et.visibility = View.VISIBLE
                et.hint = context.getString(R.string.settings_hint_random_uid_len)
                summary.text = context.getString(R.string.settings_hint_random_uid_len)
                et.inputType = InputType.TYPE_CLASS_NUMBER
                et.setText(ConfigManager.getRandomUidLen(context))
            }
        }
        setNormalTextColor(et, context)
        if (et.isVisible) {
            et.setSelection(et.text.length)
        }
    }
}