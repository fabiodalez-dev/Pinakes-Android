package com.pinakes.app.data.repository

import com.pinakes.app.data.model.ChangePasswordRequest
import com.pinakes.app.data.model.DeviceItem
import com.pinakes.app.data.model.UpdateProfileRequest
import com.pinakes.app.data.model.UserProfile
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.network.NetworkModule
import com.pinakes.app.data.network.apiCall
import com.pinakes.app.data.network.asUnit

/**
 * Profile management: fetch/update the current user, change password, and list/revoke the
 * authenticated devices (sessions). The `PATCH /me` response may be null or the updated user;
 * [updateProfile] re-fetches when the body is empty so the caller always gets the fresh profile.
 */
class ProfileRepository(private val network: NetworkModule) {

    suspend fun me(): ApiResult<UserProfile> {
        val api = network.api()
        return apiCall { api.me() }
    }

    suspend fun updateProfile(nome: String?, cognome: String?): ApiResult<UserProfile> {
        val api = network.api()
        // PATCH /me returns null/UserProfile. Model it as Unit so apiCall never tries to cast a
        // null body to UserProfile, then re-fetch the canonical profile on success.
        return when (val res = apiCall { api.updateMe(UpdateProfileRequest(nome, cognome)).asUnit() }) {
            is ApiResult.Success -> me()
            is ApiResult.Failure -> res
        }
    }

    suspend fun changePassword(current: String, new: String): ApiResult<Unit> {
        val api = network.api()
        return apiCall { api.changePassword(ChangePasswordRequest(current, new, new)) }
    }

    suspend fun devices(): ApiResult<List<DeviceItem>> {
        val api = network.api()
        return apiCall { api.devices() }
    }

    suspend fun revokeDevice(id: Int): ApiResult<Unit> {
        val api = network.api()
        return apiCall { api.revokeDevice(id) }
    }
}
