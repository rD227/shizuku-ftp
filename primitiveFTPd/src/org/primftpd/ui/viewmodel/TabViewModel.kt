package org.primftpd.ui.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.primftpd.ui.data.TabState

class TabViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(TabState(isServerRunning = false))

    fun updateServerRunning(running: Boolean) {
        _uiState.update { it.copy(isServerRunning = running) }
    }

}

//看起来Tab传给下一级uiMainScreen的东西并不多，我不是很想把它们搬运到viewmodel来