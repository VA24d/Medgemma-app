package com.google.mediapipe.examples.llminference

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(
    private val inferenceModel: InferenceModel
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _textInputEnabled = MutableStateFlow(true)
    val isTextInputEnabled: StateFlow<Boolean> = _textInputEnabled.asStateFlow()

    fun sendMessage(userMessage: String, userImages: List<Bitmap>) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value.addMessage(userMessage, USER_PREFIX, userImages)
            _uiState.value.createLoadingMessage()
            setInputEnabled(false)
            try {
                val partialResults = mutableListOf<String>()
                val future = inferenceModel.generateResponseAsync(userMessage, userImages) { partialResult, isDone ->
                    partialResults.add(partialResult)
                    _uiState.value.appendMessage(partialResults.joinToString(""))
                }
                future.get()
                setInputEnabled(true)
            } catch (e: Exception) {
                _uiState.value.addMessage(e.localizedMessage ?: "Unknown Error", MODEL_PREFIX)
                setInputEnabled(true)
            }
        }
    }

    private fun setInputEnabled(isEnabled: Boolean) {
        _textInputEnabled.value = isEnabled
    }

    companion object {
        fun getFactory() = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val application = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                val inferenceModel = InferenceModel.getInstance(application.applicationContext)
                return ChatViewModel(inferenceModel) as T
            }
        }
    }
}
