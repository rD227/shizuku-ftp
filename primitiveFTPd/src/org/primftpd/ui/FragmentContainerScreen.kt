package org.primftpd.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import kotlinx.coroutines.delay


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FragmentContainerScreen(
    title: String,
    fragmentFactory: () -> androidx.fragment.app.Fragment, // 传入 Fragment 的构造方法
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val fragmentManager = (context as? FragmentActivity)?.supportFragmentManager
    var hasNavigatedBack by remember { mutableStateOf(false) }


    var canLoadFragment by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(50)
        canLoadFragment = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (!hasNavigatedBack) {
                                hasNavigatedBack = true
                                onBack()
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        val containerId = remember { android.view.View.generateViewId() }
        val fragmentTag = remember(containerId) { "fragment_$containerId" }

        Box(modifier = Modifier
            .padding(padding)
            .fillMaxSize()) {
            AndroidView<FragmentContainerView>(
                factory = { ctx ->
                    FragmentContainerView(ctx).apply {
                        id = containerId
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (canLoadFragment) {
                DisposableEffect(containerId) {
                    val fragment = fragmentFactory()
                    fragmentManager?.beginTransaction()
                        ?.replace(containerId, fragment, fragmentTag)
                        ?.commit()

                    onDispose {
                        fragmentManager?.findFragmentByTag(fragmentTag)?.let { existingFragment ->
                            fragmentManager.beginTransaction()
                                .remove(existingFragment)
                                .commitAllowingStateLoss()
                        }
                    }
                }
            } else {
                // 加载中占位，避免跳转瞬间的突兀感
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}




