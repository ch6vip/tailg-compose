package com.tailg.plus.ui.screens

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tailg.plus.R
import com.tailg.plus.data.cloud.OfficialCloudRedactor
import com.tailg.plus.data.cloud.OfficialCloudService
import com.tailg.plus.data.cloud.OfficialCloudState
import com.tailg.plus.log.LogLevel
import com.tailg.plus.log.LogService
import com.tailg.plus.util.ClipboardText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for [CloudTokenScreen].
 *
 * Holds the token input, busy flag and cloud-session state so the screen
 * survives configuration changes (rotation) without losing the pasted token,
 * and keeps the screen composable free of business logic. The clipboard is an
 * Android-only dependency, so it stays injectable for tests.
 *
 * Snackbar messages are emitted as resource IDs ([UiMessage.textRes]) so the
 * ViewModel never touches Android resources; the UI layer resolves them via
 * [androidx.compose.ui.res.stringResource]. Dynamic server-provided error text
 * rides the [UiMessage.text] fallback channel.
 */
@HiltViewModel
class CloudTokenViewModel @Inject constructor(
  private val cloud: OfficialCloudService,
  private val log: LogService,
  private val clipboard: ClipboardText,
) : ViewModel() {

  data class UiState(
    val cloudState: OfficialCloudState = OfficialCloudState.initial(),
    val tokenText: String = "",
    val busy: Boolean = false,
  )

  private val _uiState = MutableStateFlow(UiState())
  val uiState: StateFlow<UiState> = _uiState.asStateFlow()

  /** Snackbar one-shot events (resource id + severity); the UI collects them. */
  private val _messages = MutableStateFlow<List<UiMessage>>(emptyList())
  val messages: StateFlow<List<UiMessage>> = _messages.asStateFlow()

  data class UiMessage(
    @StringRes val textRes: Int,
    val isError: Boolean = false,
    /** Dynamic text (server error etc.) — used instead of [textRes] when set. */
    val text: String? = null,
    /** Format args for [textRes] when it is a format string. */
    val formatArgs: List<Any> = emptyList(),
  )

  init {
    // Mirror the cloud session state into the UI state.
    viewModelScope.launch {
      cloud.stateFlow.collect { cloudState ->
        _uiState.update { it.copy(cloudState = cloudState) }
      }
    }
  }

  fun onTokenTextChange(value: String) {
    _uiState.update { it.copy(tokenText = value) }
  }

  fun consumeMessage() {
    _messages.update { emptyList() }
  }

  /** Seed the field with the current token once, on first access. */
  fun seedTokenIfEmpty() {
    val s = _uiState.value
    if (s.tokenText.isEmpty() && s.cloudState.token.isNotEmpty()) {
      _uiState.update { it.copy(tokenText = s.cloudState.token) }
    }
  }

  fun copyCurrentToken() {
    viewModelScope.launch {
      val token = _uiState.value.cloudState.token.trim()
      if (token.isEmpty()) {
        pushMessageRes(R.string.token_vm_no_session)
        return@launch
      }
      clipboard.writeClipboardText(token)
      pushMessageRes(R.string.token_vm_copied)
    }
  }

  fun pasteFromClipboard() {
    val text = clipboard.readClipboardText()
    if (text == null) {
      pushMessageRes(R.string.token_vm_clipboard_empty)
    } else {
      _uiState.update { it.copy(tokenText = text) }
      pushMessageRes(R.string.token_vm_pasted)
    }
  }

  fun loginWithToken() {
    val s = _uiState.value
    if (s.busy) return
    val raw = s.tokenText.trim()
    if (raw.isEmpty()) {
      pushMessageRes(R.string.token_vm_paste_first)
      return
    }
    viewModelScope.launch {
      _uiState.update { it.copy(busy = true) }
      try {
        cloud.loginWithToken(raw, phone = s.cloudState.phone, userId = s.cloudState.userId)
        pushMessageRes(R.string.token_vm_login_success)
      } catch (e: Exception) {
        log.operation("Token 登录失败", detail = e.toString(), level = LogLevel.WARNING)
        pushMessage(OfficialCloudRedactor.errorMessage(e), isError = true)
      } finally {
        _uiState.update { it.copy(busy = false) }
      }
    }
  }

  private fun pushMessage(text: String, isError: Boolean = false) {
    _messages.update { it + UiMessage(textRes = 0, isError = isError, text = text) }
  }

  private fun pushMessageRes(@StringRes textRes: Int, isError: Boolean = false) {
    _messages.update { it + UiMessage(textRes = textRes, isError = isError) }
  }
}
