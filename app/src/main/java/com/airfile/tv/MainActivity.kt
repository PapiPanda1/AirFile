package com.airfile.tv

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.airfile.tv.server.UploadService
import com.airfile.tv.storage.FileSink
import com.airfile.tv.ui.AirFileTheme
import com.airfile.tv.ui.QrScreen

class MainActivity : ComponentActivity() {

    private val sink by lazy { FileSink(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AirFileState.location.value = sink.describe()
        ContextCompat.startForegroundService(this, Intent(this, UploadService::class.java))

        setContent {
            AirFileTheme {
                val picker = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocumentTree(),
                ) { uri ->
                    if (uri != null) {
                        contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                        )
                        sink.setPickedFolder(uri)
                        AirFileState.location.value = sink.describe()
                    }
                }
                QrScreen(
                    onChooseFolder = {
                        try {
                            picker.launch(null)
                        } catch (_: ActivityNotFoundException) {
                            Toast.makeText(
                                this,
                                "This TV has no folder picker. Files will go to the default location.",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The SD card may have been inserted or removed while we were away.
        AirFileState.location.value = sink.describe()
    }

    override fun onDestroy() {
        if (isFinishing) stopService(Intent(this, UploadService::class.java))
        super.onDestroy()
    }
}
