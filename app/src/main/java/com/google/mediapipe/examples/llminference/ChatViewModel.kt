package com.google.mediapipe.examples.llminference

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.google.mediapipe.examples.llminference.settings.LocalModelFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(
    private val inferenceModel: InferenceModel,
    private val appContext: Context
) : ViewModel() {

    private val _thinkingEnabled = MutableStateFlow(LocalModelFiles.isThinkingEnabled(appContext))
    val thinkingEnabled: StateFlow<Boolean> = _thinkingEnabled.asStateFlow()

    private val _uiState = MutableStateFlow(
        UiState(supportsThinking = _thinkingEnabled.value)
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _textInputEnabled = MutableStateFlow(true)
    val isTextInputEnabled: StateFlow<Boolean> = _textInputEnabled.asStateFlow()

    /** Toggle thinking mode. Saves to prefs, updates native layer, resets chat history. */
    fun setThinkingEnabled(enabled: Boolean) {
        LocalModelFiles.setThinkingEnabled(appContext, enabled)
        _thinkingEnabled.value = enabled
        inferenceModel.updateThinkingMode(enabled)
        _uiState.value = UiState(supportsThinking = enabled)
    }

    fun sendMessage(userMessage: String, userImages: List<Bitmap>) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value.addMessage(userMessage, USER_PREFIX, userImages)
            _uiState.value.createLoadingMessage()
            setInputEnabled(false)
            try {
                val future = inferenceModel.generateResponseAsync(userMessage, userImages) { partialResult, isDone ->
                    if (!isDone && partialResult.isNotEmpty()) {
                        _uiState.value.appendMessage(partialResult)
                    }
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
                val ctx = application.applicationContext
                val inferenceModel = InferenceModel.getInstance(ctx)
                return ChatViewModel(inferenceModel, ctx) as T
            }
        }
    }
}
