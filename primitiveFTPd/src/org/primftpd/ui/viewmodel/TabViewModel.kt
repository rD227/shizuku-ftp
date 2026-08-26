package org.primftpd.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.primftpd.ui.data.TabState

class TabViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(TabState(isServerRunning = false))
    val uiState = _uiState.asStateFlow()

    private val _navigationEvent = MutableStateFlow<String?>(null)
    val navigationEvent = _navigationEvent.asStateFlow()

    fun updateServerRunning(running: Boolean) {
        _uiState.update { it.copy(isServerRunning = running) }
    }

    fun onNavigate(destination: String) {
        viewModelScope.launch {
            _navigationEvent.emit(destination)
        }
    }
}