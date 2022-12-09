package com.woleapp.netpos.contactless.util

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ClickableSpan
import android.text.style.StyleSpan
import android.util.Base64
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.woleapp.netpos.contactless.R
import com.woleapp.netpos.contactless.model.* // ktlint-disable no-wildcard-imports
import com.woleapp.netpos.contactless.taponphone.tlv.BerTag
import com.woleapp.netpos.contactless.taponphone.tlv.BerTlvParser
import com.woleapp.netpos.contactless.taponphone.tlv.HexUtil
import com.woleapp.netpos.contactless.ui.dialog.LoadingDialog
import com.woleapp.netpos.contactless.util.AppConstants.STRING_LOADING_DIALOG_TAG
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

object RandomPurposeUtil {
    fun stringToBase64(text: String): String {
        val data: ByteArray = text.toByteArray()
        return Base64.encodeToString(data, Base64.DEFAULT)
    }

    fun base64ToPlainText(base64String: String): String {
        val decodedString = Base64.decode(base64String, Base64.DEFAULT)
        return String(decodedString)
    }

    fun LifecycleOwner.showSnackBar(rootView: View, message: String) {
        Snackbar.make(rootView, message, Snackbar.LENGTH_LONG).show()
    }

    @SuppressLint("ConstantLocale")
    fun getCurrentDateTime() =
        SimpleDateFormat(
            "yyyy-MM-dd hh:mm a",
            Locale.getDefault()
        ).format(System.currentTimeMillis())
            .format(Date())

    @SuppressLint("SimpleDateFormat")
    fun dateStr2Long(dateStr: String, inputDateFormat: String): Long {
        val formattedDateString = dateStr.removeSuffix(dateStr.takeLast(3))
        return try {
            val c = Calendar.getInstance()
            c.time = SimpleDateFormat(inputDateFormat)
                .parse(dateStr)!!
            c.timeInMillis
        } catch (e: ParseException) {
            e.printStackTrace()
            0
        }
    }

    fun generateRandomRrn(length: Int): String {
        val random = Random()
        var digits = ""
        digits += (random.nextInt(9) + 1).toString()
        for (i in 1 until length) {
            digits += (random.nextInt(10) + 0).toString()
        }
        return digits
    }

    @Throws(IndexOutOfBoundsException::class)
    fun customSpannableString(
        text: String,
        startIndex: Int,
        endIndex: Int,
        clickAction: () -> Unit
    ): SpannableString {
        val spannedText = SpannableString(text)
        val styleSpan = StyleSpan(Typeface.BOLD)
        val clickableSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                clickAction()
            }
        }
        if (startIndex < 0) throw IndexOutOfBoundsException("$startIndex must be at least 0")
        if (text.isEmpty()) throw IndexOutOfBoundsException("$text can't be empty")
        if (endIndex > text.length) throw IndexOutOfBoundsException("$endIndex can't be greater than the length of $text")
        spannedText.setSpan(styleSpan, startIndex, endIndex, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
        spannedText.setSpan(
            clickableSpan,
            startIndex,
            endIndex,
            Spanned.SPAN_INCLUSIVE_EXCLUSIVE
        )
        return spannedText
    }

    fun <T> Fragment.observeServerResponse(
        serverResponse: LiveData<Resource<T>>,
        loadingDialog: LoadingDialog,
        fragmentManager: FragmentManager,
        successAction: () -> Unit
    ) {
        serverResponse.observe(this.viewLifecycleOwner) {
            when (it.status) {
                Status.SUCCESS -> {
                    loadingDialog.dismiss()
                    if (
                        it.data is PostQrToServerResponse ||
                        it.data is PostQrToServerVerveResponseModel ||
                        it.data is QrTransactionResponseModel ||
                        it.data is VerveTransactionResponse
                    ) {
                        successAction()
                    } else {
                        showSnackBar(this.requireView(), getString(R.string.an_error_occurred))
                    }
                }
                Status.LOADING -> {
                    loadingDialog.show(
                        fragmentManager,
                        STRING_LOADING_DIALOG_TAG
                    )
                }
                Status.ERROR -> {
                    loadingDialog.dismiss()
                    showSnackBar(this.requireView(), getString(R.string.an_error_occurred))
                }
                Status.TIMEOUT -> {
                    loadingDialog.dismiss()
                    showSnackBar(this.requireView(), getString(R.string.timeOut))
                }
            }
        }
    }

    fun closeSoftKeyboard(context: Context, activity: Activity) {
        activity.currentFocus?.let { view ->
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    // MTIP
    fun setField59(aid: String): String {
        val additionalTagManipulation =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><AdditionalEmvTags><EmvTag><TagId>84</TagId><TagValue>$aid</TagValue></EmvTag></AdditionalEmvTags>"
        val len = additionalTagManipulation.length.toString()
        val lengthOfLength = len.length

        return "216MPOS_DEVICE_TYPE111217AdditionalEmvTags${lengthOfLength}${additionalTagManipulation.length}$additionalTagManipulation"
    }

    fun getWithTag(tag: String, iccData: String): String? {
        return try {
            val tlvList = BerTlvParser().parse(HexUtil.parseHex(iccData))
            tlvList.find(BerTag(tag)).hexValue
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun formatFailedVerveTransRespToExtractIswResponse(transResponse: VerveTransactionResponse): VerveTransactionResponse {
        val iswResponseInStringFormat =
            transResponse.message.split("response:\n").last().removeSuffix("\n\"")
                .replace("\\", "")
        val iswResponse = Gson().fromJson(iswResponseInStringFormat, Response::class.java)
        return transResponse.copy(
            code = iswResponse.errors.first().code,
            message = iswResponse.errors.first().message
        )
    }
}
