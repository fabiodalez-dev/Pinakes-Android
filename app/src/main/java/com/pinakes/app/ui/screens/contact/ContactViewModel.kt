package com.pinakes.app.ui.screens.contact

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.repository.MessagesRepository
import com.pinakes.app.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ContactUiState(
    val subject: String = "",
    val body: String = "",
    val sending: Boolean = false,
    val sent: Boolean = false,
    val error: String? = null,
    val errorRes: Int? = null,
) {
    val canSend: Boolean get() = subject.isNotBlank() && body.isNotBlank() && !sending
}

class ContactViewModel(private val messages: MessagesRepository) : ViewModel() {

    private val _state = MutableStateFlow(ContactUiState())
    val state: StateFlow<ContactUiState> = _state.asStateFlow()

    fun onSubject(v: String) = _state.update { it.copy(subject = v, error = null, errorRes = null) }
    fun onBody(v: String) = _state.update { it.copy(body = v, error = null, errorRes = null) }

    fun send() {
        val s = _state.value
        if (!s.canSend) return
        _state.update { it.copy(sending = true, error = null, errorRes = null) }
        viewModelScope.launch {
            when (val res = messages.send(s.subject, s.body)) {
                is ApiResult.Success -> _state.update { it.copy(sending = false, sent = true) }
                is ApiResult.Failure -> _state.update {
                    if (res.message.isNotBlank()) it.copy(sending = false, error = res.message, errorRes = null)
                    else it.copy(sending = false, error = null, errorRes = R.string.contact_send_error)
                }
            }
        }
    }

    class Factory(private val messages: MessagesRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ContactViewModel(messages) as T
    }
}
