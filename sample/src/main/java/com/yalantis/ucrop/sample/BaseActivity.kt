package com.yalantis.ucrop.sample

import android.content.DialogInterface
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity

abstract class BaseActivity : AppCompatActivity() {

    companion object {
        const val REQUEST_STORAGE_READ_ACCESS_PERMISSION = 101
        const val REQUEST_STORAGE_WRITE_ACCESS_PERMISSION = 102
    }

    private var alertDialog: AlertDialog? = null

    /**
     * Hide alert dialog if any.
     */
    override fun onStop() {
        super.onStop()
        alertDialog?.let {
            if (it.isShowing) {
                alertDialog?.dismiss()
            }
        }
    }

    /**
     * Requests given permission.
     * If the permission has been denied previously, a Dialog will prompt the user to grant the
     * permission, otherwise it is requested directly.
     */
    fun requestPermission(permission: String, rationale: String, requestCode: Int) {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
            showAlertDialog(getString(R.string.permission_title_rationale), rationale,
                    DialogInterface.OnClickListener { _, _ ->
                        ActivityCompat.requestPermissions(this@BaseActivity,
                                arrayOf(permission), requestCode)
                    }, getString(R.string.label_ok), null, getString(R.string.label_cancel))
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(permission), requestCode)
        }
    }

    /**
     * This method shows dialog with given title & message.
     * Also there is an option to pass onClickListener for positive & negative button.
     *
     * @param title                         - dialog title
     * @param message                       - dialog message
     * @param onPositiveButtonClickListener - listener for positive button
     * @param positiveText                  - positive button text
     * @param onNegativeButtonClickListener - listener for negative button
     * @param negativeText                  - negative button text
     */
    private fun showAlertDialog(title: String?, message: String?,
                                onPositiveButtonClickListener: DialogInterface.OnClickListener?,
                                positiveText: String,
                                onNegativeButtonClickListener: DialogInterface.OnClickListener?,
                                negativeText: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(title)
        builder.setMessage(message)
        builder.setPositiveButton(positiveText, onPositiveButtonClickListener)
        builder.setNegativeButton(negativeText, onNegativeButtonClickListener)
        alertDialog = builder.show()
    }

}