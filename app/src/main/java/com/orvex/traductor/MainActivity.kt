package com.orvex.traductor

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.orvex.traductor.translation.TranslationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL

class MainActivity : ComponentActivity() {

    init {
        System.loadLibrary("whisper_jni")
    }

    private external fun stringFromJNI(): String

    private val translationManager = TranslationManager()
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Manejo del permiso de micrófono
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                MainScreen(
                    jniStatus = stringFromJNI(),
                    onDownloadWhisper = { statusUpdate -> downloadWhisperModel(statusUpdate) },
                    onDownloadTranslation = { statusUpdate ->
                        translationManager.downloadModel { success, msg ->
                            statusUpdate(msg)
                        }
                    },
                    onTranslate = { text, callback ->
                        translationManager.translateText(text, callback)
                    }
                )
            }
        }
    }

    private fun downloadWhisperModel(onStatus: (String) -> Unit) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                onStatus("Descargando modelo Whisper...")
                val modelUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en.bin"
                val file = File(filesDir, "ggml-tiny.en.bin")
                
                if (file.exists()) {
                    withContext(Dispatchers.Main) { onStatus("Modelo Whisper ya existe.") }
                    return@launch
                }

                URL(modelUrl).openStream().use { input ->
                    FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }
                withContext(Dispatchers.Main) { onStatus("Modelo Whisper descargado con éxito.") }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onStatus("Error al descargar Whisper: ${e.message}") }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        translationManager.close()
    }
}

@Composable
fun MainScreen(
    jniStatus: String,
    onDownloadWhisper: (String) -> Unit,
    onDownloadTranslation: (String) -> Unit,
    onTranslate: (String, (String) -> Unit) -> Unit
) {
    var whisperStatus by remember { mutableStateOf("Comprobar estado") }
    var translationStatus by remember { mutableStateOf("Pendiente") }
    var recognizedText by remember { mutableStateOf("Aquí aparecerá el texto reconocido por Whisper.") }
    var translatedText by remember { mutableStateOf("Aquí aparecerá el texto traducido por ML Kit.") }
    var processingState by remember { mutableStateOf("Esperando...") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "TRADUCTOR V0.3 ANDROID", style = MaterialTheme.typography.titleLarge)
        Text(text = "Nativo: $jniStatus", style = MaterialTheme.typography.bodySmall)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(text = "MODELO WHISPER", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { onDownloadWhisper { whisperStatus = it } }) {
                    Text("DESCARGAR WHISPER")
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Estado: $whisperStatus", style = MaterialTheme.typography.bodySmall)
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(text = "MODELO DE TRADUCCIÓN", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { onDownloadTranslation { translationStatus = it } }) {
                    Text("DESCARGAR TRADUCCIÓN")
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Estado: $translationStatus", style = MaterialTheme.typography.bodySmall)
            }
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                processingState = "Prueba de traducción ML Kit..."
                recognizedText = "I don't know what happened."
                onTranslate(recognizedText) { translated ->
                    translatedText = translated
                    processingState = "Traducido correctamente"
                }
            }
        ) {
            Text("PROBAR TRADUCCIÓN (SIMULACIÓN 6S)")
        }

        Text(text = "Estado: $processingState", style = MaterialTheme.typography.bodyMedium)

        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(text = "TEXTO ORIGINAL (INGLÉS)", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = recognizedText, style = MaterialTheme.typography.bodyMedium)
            }
        }

        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(text = "TRADUCCIÓN (ESPAÑOL)", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = translatedText, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
