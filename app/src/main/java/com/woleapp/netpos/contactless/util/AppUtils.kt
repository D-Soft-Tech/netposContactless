package com.woleapp.netpos.contactless.util

import android.app.Activity
import android.content.Context
import android.view.View
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import com.tapadoo.alerter.Alerter
import com.woleapp.netpos.contactless.R
import com.woleapp.netpos.contactless.util.AppConstants.NETPOS_TRANSACTION_FAILED
import com.woleapp.netpos.contactless.util.AppConstants.NETPOS_TRANSACTION_SUCCESS
import pub.devrel.easypermissions.EasyPermissions

data class DialogHelper(
    val dialogType: DialogType,
    val message: String,
    val actionName: String = "Retry",
    val action: (() -> Unit)? = null
)

enum class DialogType(val title: String, val icon: Int) {
    SUCCESS(
        "Success",
        R.drawable.ic_success
    ),
    FAILURE("Failure", R.drawable.ic_error),
    CONFIRMATION("Confirm", R.drawable.ic_warning)
}

private fun checkForPermission(context: Context, perms: String) =
    EasyPermissions.hasPermissions(
        context,
        perms
    )

fun genericPermissionHandler(
    host: LifecycleOwner,
    context: Context,
    perm: String,
    permCode: Int,
    permRationale: String,
    fn: () -> Unit
) {
    if (checkForPermission(context, perm)) {
        fn()
    } else {
        requestForPermission(
            host,
            permCode,
            permRationale,
            perm
        )
    }
}

private fun requestForPermission(
    host: LifecycleOwner,
    requestCode: Int,
    permissionRationale: String,
    permissionToRequest: String
) {
    if (host is Fragment) {
        EasyPermissions.requestPermissions(
            host,
            permissionRationale,
            requestCode,
            permissionToRequest
        )
    } else {
        host as Activity
        EasyPermissions.requestPermissions(
            host,
            permissionRationale,
            requestCode,
            permissionToRequest
        )
    }
}

fun showAlerter(v: View, activity: Activity, alertMessage: String) {
    val type: Int = when {
        alertMessage.contains("Transaction Approved") -> {
            R.color.success
        }
        alertMessage.contains("Transaction Not approved") -> {
            R.color.error_color_2
        }
        else -> {
            R.color.attention
        }
    }
    val icon: Int = when {
        alertMessage.contains(NETPOS_TRANSACTION_SUCCESS) -> R.drawable.success_icon_svg
        alertMessage.contains(NETPOS_TRANSACTION_FAILED) -> R.drawable.failed_icon
        else -> R.drawable.ic_alert
    }
    Alerter.create(activity)
        .setTitle(v.resources.getString(R.string.transaction_response))
        .setText(alertMessage)
        .setIcon(
            AppCompatResources.getDrawable(v.context, icon)!!
        )
        .setBackgroundColorRes(
            type
        )
        .setDuration(3000)
        .show()
}
