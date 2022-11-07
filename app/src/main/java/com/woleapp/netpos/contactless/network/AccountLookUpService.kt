package com.woleapp.netpos.contactless.network

import com.woleapp.netpos.contactless.model.* // ktlint-disable no-wildcard-imports
import io.reactivex.Single
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

interface AccountLookUpService {

    @POST("account-lookup")
    fun findAccount(@Body accountNumber: AccountNumberLookUpRequest): Single<AccountNumberLookUpResponse>

    @POST("confirm-otp")
    fun confirmOTP(@Body confirmOTP: ConfirmOTPRequest): Single<ConfirmOTPResponse>

    @POST("user/register-existing-user")
    fun registerExistingAccount(
        @Body registerExistingAccountRegisterRequest: ExistingAccountRegisterRequest,
        @Query("bank") bank: String,
        @Query("partnerId") partnerId: String
    ): Single<ExistingAccountRegisterResponse>
}
