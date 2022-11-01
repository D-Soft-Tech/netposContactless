package com.woleapp.netpos.contactless.ui.fragments

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import com.danbamitale.epmslib.entities.TransactionType
import com.woleapp.netpos.contactless.BuildConfig
import com.woleapp.netpos.contactless.R
import com.woleapp.netpos.contactless.adapter.ServiceAdapter
import com.woleapp.netpos.contactless.databinding.FragmentTransactionsBinding
import com.woleapp.netpos.contactless.databinding.LayoutPreauthDialogBinding
import com.woleapp.netpos.contactless.databinding.LayoutVerveCardQrAmountDialogBinding
import com.woleapp.netpos.contactless.databinding.QrAmoutDialogBinding
import com.woleapp.netpos.contactless.model.PostQrToServerModel
import com.woleapp.netpos.contactless.model.QrScannedDataModel
import com.woleapp.netpos.contactless.model.Service
import com.woleapp.netpos.contactless.ui.dialog.LoadingDialog
import com.woleapp.netpos.contactless.ui.dialog.QrPasswordPinBlockDialog
import com.woleapp.netpos.contactless.util.AppConstants.PIN_BLOCK_BK
import com.woleapp.netpos.contactless.util.AppConstants.PIN_BLOCK_RK
import com.woleapp.netpos.contactless.util.AppConstants.STRING_PIN_BLOCK_DIALOG_TAG
import com.woleapp.netpos.contactless.util.AppConstants.STRING_QR_READ_RESULT_BUNDLE_KEY
import com.woleapp.netpos.contactless.util.AppConstants.STRING_QR_READ_RESULT_REQUEST_KEY
import com.woleapp.netpos.contactless.util.AppConstants.getGUID
import com.woleapp.netpos.contactless.util.HISTORY_ACTION_PREAUTH
import com.woleapp.netpos.contactless.util.HISTORY_ACTION_REFUND
import com.woleapp.netpos.contactless.util.HISTORY_ACTION_REVERSAL
import com.woleapp.netpos.contactless.util.RandomPurposeUtil.observeServerResponse
import com.woleapp.netpos.contactless.util.RandomPurposeUtil.stringToBase64
import com.woleapp.netpos.contactless.util.Singletons
import com.woleapp.netpos.contactless.viewmodels.ScanQrViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class TransactionsFragment @Inject constructor() : BaseFragment() {

    private lateinit var adapter: ServiceAdapter
    private var _binding: FragmentTransactionsBinding? = null
    private val binding get() = _binding!!
    private val scanQrViewModel by activityViewModels<ScanQrViewModel>()
    private lateinit var qrAmountDialogBinding: QrAmoutDialogBinding
    private lateinit var verveCardQrAmountDialogBinding: LayoutVerveCardQrAmountDialogBinding
    private lateinit var qrAmountDialog: androidx.appcompat.app.AlertDialog
    private lateinit var qrAmountDialogForVerveCard: androidx.appcompat.app.AlertDialog
    private val qrPinBlock: QrPasswordPinBlockDialog = QrPasswordPinBlockDialog()
    private lateinit var requestNarration: String
    private var amountToPayInDouble: Double? = 0.0
    private var qrData: QrScannedDataModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestNarration = Singletons.getCurrentlyLoggedInUser()?.mid?.let {
            "$it:${Singletons.getCurrentlyLoggedInUser()?.terminal_id}:${BuildConfig.STRING_MPGS_TAG}"
        } ?: ""

        requireActivity().supportFragmentManager.setFragmentResultListener(
            STRING_QR_READ_RESULT_REQUEST_KEY,
            requireActivity()
        ) { _, bundle ->
            qrData = bundle.getParcelable<QrScannedDataModel>(STRING_QR_READ_RESULT_BUNDLE_KEY)
            qrData?.let {
                if (it.card_scheme.contains(
                        "verve",
                        true
                    )
                ) showAmountDialogForVerveCard() else showAmountDialog(it)
            }
        }

        requireActivity().supportFragmentManager.setFragmentResultListener(
            PIN_BLOCK_RK,
            requireActivity()
        ) { _, bundle ->
            val pinFromPinBlockDialog = bundle.getString(PIN_BLOCK_BK)
            pinFromPinBlockDialog?.let {
                qrData?.let { it1 -> formatPinAndSendToServer(it, amountToPayInDouble, it1) }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTransactionsBinding.inflate(inflater, container, false)
        adapter = ServiceAdapter {
            val nextFrag: Fragment? = when (it.id) {
                0 -> SalesFragment.newInstance()
                1 -> TransactionHistoryFragment.newInstance(action = HISTORY_ACTION_REFUND)
                8 -> TransactionHistoryFragment.newInstance(action = HISTORY_ACTION_REVERSAL)
                7 -> SalesFragment.newInstance(TransactionType.PURCHASE_WITH_CASH_BACK)
                2 -> {
                    showPreAuthDialog()
                    null
                }
                4 ->
                    FragmentBarCodeScanner()

//                {
// //                     NetPosBarcodeSdk
//                    // QRFragment()
//                    NetPosBarcodeSdk.startScan(requireContext(), resultLauncher)
// //                    addFragmentWithoutRemove(FragmentBarCodeScanner())
//                    null
//                }
                5 -> ReprintFragment()
                6 -> SalesFragment.newInstance(isVend = true)
                else -> SalesFragment.newInstance(TransactionType.CASH_ADVANCE)
            }
            nextFrag?.let { fragment ->
                addFragmentWithoutRemove(fragment)
            }
        }

        qrAmountDialogBinding = QrAmoutDialogBinding.inflate(inflater, null, false)
            .apply {
                executePendingBindings()
                lifecycleOwner = viewLifecycleOwner
            }

        verveCardQrAmountDialogBinding =
            LayoutVerveCardQrAmountDialogBinding.inflate(inflater, null, false)
                .apply {
                    executePendingBindings()
                    lifecycleOwner = viewLifecycleOwner
                }

        qrAmountDialog = androidx.appcompat.app.AlertDialog.Builder(requireContext()).apply {
            setView(qrAmountDialogBinding.root)
        }.create()

        qrAmountDialogForVerveCard =
            androidx.appcompat.app.AlertDialog.Builder(requireContext()).apply {
                setView(verveCardQrAmountDialogBinding.root)
            }.create()

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvTransactions.layoutManager = GridLayoutManager(context, 2)
        binding.rvTransactions.adapter = adapter
        setService()
    }

    private fun setService() {
        val listOfService = ArrayList<Service>()
            .apply {
                add(Service(0, "Purchase", R.drawable.ic_purchase))
                add(Service(7, "Purchase With Cashback", R.drawable.ic_purchase))
                // add(Service(1, "Refund", R.drawable.ic_loop))
                // add(Service(8, "Reversal", R.drawable.ic_loop))
                // add(Service(2, "PRE AUTHORIZATION", R.drawable.ic_pre_auth))
                add(Service(3, "Cash Advance", R.drawable.ic_pay_cash_icon))
                add(Service(4, "Scan QR", R.drawable.ic_qr_code))
                add(Service(5, "Reprint", R.drawable.ic_print))
                // add(Service(6, "VEND", R.drawable.ic_vend))
            }
        adapter.submitList(listOfService)
    }

    private fun showPreAuthDialog() {
        val dialog = AlertDialog.Builder(context)
            .apply {
                setCancelable(false)
            }.create()
        val preAuthDialogBinding =
            LayoutPreauthDialogBinding.inflate(LayoutInflater.from(context), null, false)
                .apply {
                    lifecycleOwner = viewLifecycleOwner
                    executePendingBindings()
                    preAuthNew.setOnClickListener {
                        dialog.dismiss()
                        addFragmentWithoutRemove(SalesFragment.newInstance(TransactionType.PRE_AUTHORIZATION))
                    }
                    preAuthComplete.setOnClickListener {
                        dialog.dismiss()
                        addFragmentWithoutRemove(
                            TransactionHistoryFragment.newInstance(
                                HISTORY_ACTION_PREAUTH
                            )
                        )
                    }
                    cancelButton.setOnClickListener {
                        dialog.dismiss()
                    }
                }
        dialog.setView(preAuthDialogBinding.root)
        dialog.show()
    }

    private fun showAmountDialog(qrData: QrScannedDataModel) {
        qrAmountDialog.show()
        qrAmountDialogBinding.proceed.setOnClickListener {
            val amountDouble = qrAmountDialogBinding.amount.text.toString().toDoubleOrNull()
            if (qrAmountDialogBinding.amount.text.isNullOrEmpty()) qrAmountDialogBinding.amount.error =
                getString(R.string.amount_empty)
            amountDouble?.let {
                qrAmountDialogBinding.amount.text?.clear()
                qrAmountDialog.cancel()
                val qrDataToSendToBackend =
                    PostQrToServerModel(
                        it,
                        qrData.data,
                        merchantId = BuildConfig.STRING_MERCHANT_ID,
                        narration = requestNarration
                    )
                scanQrViewModel.setScannedQrIsVerveCard(false)
                scanQrViewModel.postScannedQrRequestToServer(qrDataToSendToBackend)
                observeServerResponse(
                    scanQrViewModel.sendQrToServerResponse,
                    LoadingDialog(),
                    childFragmentManager
                ) {
                    addFragmentWithoutRemove(
                        CompleteQrPaymentWebViewFragment()
                    )
                }
            }
        }
    }

    private fun showAmountDialogForVerveCard() {
        qrAmountDialogForVerveCard.show()
        verveCardQrAmountDialogBinding.proceed.setOnClickListener {
            if (verveCardQrAmountDialogBinding.amount.text.isNullOrEmpty()) {
                verveCardQrAmountDialogBinding.amount.error = getString(R.string.amount_empty)
            }
            val amountDouble =
                verveCardQrAmountDialogBinding.amount.text.toString().toDoubleOrNull()
            amountToPayInDouble = amountDouble
            verveCardQrAmountDialogBinding.amount.text?.clear()
            qrAmountDialogForVerveCard.cancel()
            qrAmountDialogForVerveCard.dismiss()
            qrPinBlock.show(requireActivity().supportFragmentManager, STRING_PIN_BLOCK_DIALOG_TAG)
        }
    }

    private fun formatPinAndSendToServer(
        pin: String,
        amountDouble: Double?,
        qrData: QrScannedDataModel
    ) {
        val formattedPadding = stringToBase64(pin).removeSuffix('\n'.toString())
        if (pin.length == 4) {
            amountDouble?.let {
                qrAmountDialogForVerveCard.cancel()
                val qrDataToSendToBackend =
                    PostQrToServerModel(
                        it,
                        qrData.data,
                        merchantId = BuildConfig.STRING_MERCHANT_ID,
                        padding = formattedPadding,
                        narration = requestNarration
                    )
                scanQrViewModel.setScannedQrIsVerveCard(true)
                scanQrViewModel.saveTheQrToSharedPrefs(qrDataToSendToBackend.copy(orderId = getGUID()))
                scanQrViewModel.postScannedQrRequestToServer(qrDataToSendToBackend)
                observeServerResponse(
                    scanQrViewModel.sendQrToServerResponseVerve,
                    LoadingDialog(),
                    requireActivity().supportFragmentManager
                ) {
                    addFragmentWithoutRemove(
                        EnterOtpFragment()
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}
