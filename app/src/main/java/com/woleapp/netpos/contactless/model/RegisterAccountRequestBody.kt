package com.woleapp.netpos.contactless.model

data class RegisterAccountRequestBody(
    val accountNumber: String,
    val businessAddress: String,
    val businessName: String,
    val contactInformation: String,
    val merchantId: String,
    val password: String,
    val phoneNumber: String,
    val terminalId: String,
    val username: String
)