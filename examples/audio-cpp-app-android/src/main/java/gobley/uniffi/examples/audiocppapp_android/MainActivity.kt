package gobley.uniffi.examples.audiocppapp_android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import gobley.uniffi.examples.audiocppapp.AudioCppApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AudioCppApp()
        }
    }
}