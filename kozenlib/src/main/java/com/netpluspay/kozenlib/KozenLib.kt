package com.netpluspay.kozenlib

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.preference.PreferenceManager
import com.netpluspay.kozenlib.emv.param.EmvParam
import com.netpluspay.kozenlib.utils.DeviceConfig
import com.pos.sdk.accessory.POIGeneralAPI
import com.pos.sdk.printer.POIPrinterManage

object KozenLib {
    private const val HAS_LOADED_PARAMS = "has_loaded_params"
    private lateinit var printerManager: POIPrinterManage
    private lateinit var context: Context

    @JvmStatic
    fun init(context: Context) {
        this.context = context
        initTerminalConfiguration()
        val poiGeneralAPI = POIGeneralAPI.getDefault()
        poiGeneralAPI.apply {
            Log.e("TAG", "HW: ${getVersion(POIGeneralAPI.VERSION_TYPE_HW)}")
            Log.e("TAG", "AP: ${getVersion(POIGeneralAPI.VERSION_TYPE_AP)}")
            Log.e("TAG", "DSN: ${getVersion(POIGeneralAPI.VERSION_TYPE_DSN)}")
            Log.e("TAG", "SP: ${getVersion(POIGeneralAPI.VERSION_TYPE_SP)}")

        }
        printerManager = POIPrinterManage.getDefault(context)
        if (!PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(HAS_LOADED_PARAMS, false)
        )
            loadEmvParams(context)
    }

    @JvmStatic
    private fun initTerminalConfiguration() {
        Log.e("TAG", "Model ${Build.MODEL}")
        Log.e("TAG", "Manufacturer ${Build.MANUFACTURER}")
        Log.e("TAG", "Brand ${Build.BRAND}")
        if (Build.MODEL == "P3")
            DeviceConfig.InitDevice(DeviceConfig.DEVICE_P3)
        else
            DeviceConfig.InitDevice(DeviceConfig.DEVICE_P4)
//        if (Build.MODEL.endsWith("Z-600")) {
//            DeviceConfig.InitDevice(DeviceConfig.DEVICE_P4)
//        } else {
//            DeviceConfig.InitDevice(DeviceConfig.DEVICE_P3)
//        }
    }

    @JvmStatic
    fun getContext() = context

    @JvmStatic
    fun getPrinterManager(): POIPrinterManage {
        if (::printerManager.isInitialized.not())
            throw NullPointerException(
                "KozenLib has not been initialized, you must call " +
                        "KozenLib.init(context) first, this call needs to be made just once."
            )
        return printerManager
    }

    @JvmStatic
    private fun loadEmvParams(context: Context) {
        EmvParam.loadTerminalParam()
        EmvParam.loadVisaParam()
        EmvParam.loadMasterCardParam()
        //EmvParam.loadAmexParam()
        //EmvParam.loadDiscoverParam()
        //EmvParam.loadQuicsParam()
        EmvParam.loadVisaDrl()
        //EmvParam.loadAemxDrl()
        // EmvParam.loadRuPayService()
        EmvParam.loadAddAids()
        EmvParam.loadAddCapks()
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit().putBoolean(HAS_LOADED_PARAMS, true).apply()
    }
}