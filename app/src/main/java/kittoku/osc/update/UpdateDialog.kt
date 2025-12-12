package kittoku.osc.update

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.Window
import android.widget.CheckBox
import android.widget.TextView
import androidx.preference.PreferenceManager
import com.google.android.material.button.MaterialButton
import kittoku.osc.R

/**
 * Custom styled update dialog with opt-out option
 * Features:
 * - Shows version info and release notes
 * - "Update Now" primary action
 * - "Later" secondary action
 * - "Don't check automatically again" opt-out checkbox
 */
class UpdateDialog(
    private val context: Context,
    private val result: UpdateCheckResult,
    private val onUpdateClick: () -> Unit,
    private val onDismiss: () -> Unit
) {
    private var dialog: Dialog? = null
    
    fun show() {
        dialog = Dialog(context).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            
            val view = LayoutInflater.from(context).inflate(R.layout.dialog_update, null)
            setContentView(view)
            
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window?.setLayout(
                (context.resources.displayMetrics.widthPixels * 0.9).toInt(),
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
            
            // Set version info
            view.findViewById<TextView>(R.id.tv_version_info).text = 
                "New version v${result.latestVersion} is available"
            
            view.findViewById<TextView>(R.id.tv_current_version).text = 
                "Current version: v${result.currentVersion}"
            
            // Set release notes
            view.findViewById<TextView>(R.id.tv_release_notes).text = 
                result.releaseNotes?.take(500) ?: "No release notes available"
            
            // Update Now button
            view.findViewById<MaterialButton>(R.id.btn_update_now).setOnClickListener {
                handleOptOutIfChecked(view)
                dismiss()
                onUpdateClick()
            }
            
            // Later button
            view.findViewById<MaterialButton>(R.id.btn_later).setOnClickListener {
                handleOptOutIfChecked(view)
                dismiss()
                onDismiss()
            }
            
            setCancelable(true)
            setOnCancelListener {
                handleOptOutIfChecked(view)
                onDismiss()
            }
            
            show()
        }
    }
    
    /**
     * If the opt-out checkbox is checked, disable auto-check in preferences
     */
    private fun handleOptOutIfChecked(view: android.view.View) {
        val checkbox = view.findViewById<CheckBox>(R.id.cb_disable_auto_check)
        if (checkbox.isChecked) {
            PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putBoolean("AUTO_CHECK_UPDATES", false)
                .apply()
        }
    }
    
    fun dismiss() {
        dialog?.dismiss()
        dialog = null
    }
}
