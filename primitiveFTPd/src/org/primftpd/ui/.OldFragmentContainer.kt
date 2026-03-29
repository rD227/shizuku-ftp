package org.primftpd.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import org.primftpd.R

class OldStyFragmentUIContainer : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.xml.preferences, container, false)
    }
}

@Preview
@Composable
fun OldStyFragmentUIPreview() {
    AndroidView(
        factory = { context ->
            LayoutInflater.from(context).inflate(R.xml.preferences, null)
        },
        modifier = Modifier.fillMaxSize()
    )
}
