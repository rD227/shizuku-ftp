package org.primftpd.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.primftpd.R

private const val IGNORED_VERSION = "ignored_update_version"
private const val REMIND_AFTER = "update_remind_after"

internal class UpdatePromptViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("startup_experience", Context.MODE_PRIVATE)
    var updateVersion by mutableStateOf<String?>(null)
        private set
    private var checked = false

    fun checkOnce() {
        if (checked) return
        checked = true
        if (System.currentTimeMillis() < prefs.getLong(REMIND_AFTER, 0L)) return
        viewModelScope.launch {
            val latest = withContext(Dispatchers.IO) { fetchLatestVersionFromGithub() }
            if (latest != null && latest != prefs.getString(IGNORED_VERSION, null) &&
                compareVersions(getVersionName(getApplication()), latest) < 0
            ) updateVersion = latest
        }
    }

    fun dismissUpdate() { updateVersion = null }
}

@Composable
internal fun StartupUpdatePrompt(ready: Boolean, state: UpdatePromptViewModel) {
    LaunchedEffect(ready) { if (ready) state.checkOnce() }
    if (ready) state.updateVersion?.let { UpdateAvailableDialog(it, state::dismissUpdate) }
}

@Composable
internal fun UpdateAvailableDialog(version: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("startup_experience", Context.MODE_PRIVATE) }
    val later = {
        prefs.edit { putLong(REMIND_AFTER, System.currentTimeMillis() + 24 * 60 * 60 * 1000L) }
        onDismiss()
    }
    CompactSettingsDialog(
        onDismissRequest = later,
        title = { Text(stringResource(R.string.update_available_title, version)) },
        text = { Text(stringResource(R.string.update_available_body)) },
        dismissButton = {
            TextButton(onClick = {
                prefs.edit { putString(IGNORED_VERSION, version) }
                onDismiss()
            }) { Text(stringResource(R.string.cancel)) }
            TextButton(onClick = later) { Text(stringResource(R.string.update_later)) }
        },
        confirmButton = {
            TextButton(onClick = {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, "https://github.com/rD227/shizuku-ftp/releases/latest".toUri()))
                    onDismiss()
                } catch (_: android.content.ActivityNotFoundException) {
                    Toast.makeText(context, R.string.update_no_browser, Toast.LENGTH_SHORT).show()
                }
            }) { Text(stringResource(R.string.update_browser)) }
        },
    )
}
