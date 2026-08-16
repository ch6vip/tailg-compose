package com.tailg.plus.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

  /** Snackbar one-shot events (message + severity) — the UI collects them. */
  private val _messages = MutableStateFlow<List<UiMessage>>(emptyList())
  val messages: StateFlow<List<UiMessage>> = _messages.asStateFlow()

  data class UiMessage(val text: String, val isError: Boolean = false)

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
        pushMessage("当前未登录,没有可复制的 Token")
        return@launch
      }
      clipboard.writeClipboardText(token)
      pushMessage("Token 已复制到剪贴板")
    }
  }

  fun pasteFromClipboard() {
    val text = clipboard.readClipboardText()
    if (text == null) {
      pushMessage("剪贴板为空")
    } else {
      _uiState.update { it.copy(tokenText = text) }
      pushMessage("已从剪贴板粘贴")
    }
  }

  fun loginWithToken() {
    val s = _uiState.value
    if (s.busy) return
    val raw = s.tokenText.trim()
    if (raw.isEmpty()) {
      pushMessage("请先粘贴 Token")
      return
    }
    viewModelScope.launch {
      _uiState.update { it.copy(busy = true) }
      try {
        cloud.loginWithToken(raw, phone = s.cloudState.phone, userId = s.cloudState.userId)
        pushMessage("Token 登录成功,车辆已同步")
      } catch (e: Exception) {
        log.operation("Token 登录失败", detail = e.toString(), level = LogLevel.WARNING)
        pushMessage(OfficialCloudRedactor.errorMessage(e), isError = true)
      } finally {
        _uiState.update { it.copy(busy = false) }
      }
    }
  }

  private fun pushMessage(text: String, isError: Boolean = false) {
    _messages.update { it + UiMessage(text = text, isError = isError) }
  }
}
