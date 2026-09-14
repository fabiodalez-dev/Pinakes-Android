package com.pinakes.app.ui.screens.periodicals

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pinakes.app.R
import com.pinakes.app.data.model.StandaloneArticle
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.repository.StandaloneArticlesSource
import com.pinakes.app.ui.common.UiState
import com.pinakes.app.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class StandaloneArticleViewModel @Inject constructor(
    private val source: StandaloneArticlesSource,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val id = savedStateHandle.get<Int>(Routes.ARG_ARTICLE_ID) ?: 0
    private val mutableState = MutableStateFlow<UiState<StandaloneArticle>>(UiState.Loading)
    val state = mutableState.asStateFlow()
    private var generation = 0

    init { refresh() }

    fun refresh() {
        val request = ++generation
        // Do not retain a previously public record/PDF after a 404 or visibility change.
        mutableState.value = UiState.Loading
        viewModelScope.launch {
            val result = source.article(id)
            if (request != generation) return@launch
            mutableState.value = when (result) {
                is ApiResult.Success -> UiState.Success(result.data)
                is ApiResult.Failure -> {
                    val gone = isNotFoundFailure(result) && source.confirmGone()
                    if (request != generation) return@launch
                    periodicalsErrorState(result, periodicalsFailureKind(result, gone),
                        R.string.standalone_articles_error, R.string.standalone_article_not_found)
                }
            }
        }
    }
}
