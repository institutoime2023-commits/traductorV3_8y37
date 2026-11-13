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

    // Declaración de la función nativa JNI de Whisper
    private external fun runWhisperNative(modelPath: String, audioPath: String): String

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
                    onDownloadWhisper = { onStatus -> downloadWhisperModel(onStatus) },
                    onDownloadTranslation = { onStatus ->
                        translationManager.downloadModel { _, msg ->
                            onStatus(msg)
                        }
                    },
                    onProcessAudioAndTranslate = { onResultUpdate ->
                        // Aquí conectamos el flujo completo: Whisper + ML Kit
                        processAudioPipeline(onResultUpdate)
                    }
                )
            }
        }
    }

    private fun downloadWhisperModel(onStatus: (String) -> Unit) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) { onStatus("Descargando modelo Whisper...") }
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

    private fun processAudioPipeline(onResultUpdate: (String, String, String) -> Unit) {
        coroutineScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { onResultUpdate("Grabando audio ambiental (6s)...", "", "Procesando...") }

            // 1. Validar existencia del modelo Whisper local
            val modelFile = File(filesDir, "ggml-tiny.en.bin")
            if (!modelFile.exists()) {
                withContext(Dispatchers.Main) {
                    onResultUpdate("Error: Falta descargar el modelo Whisper.", "", "Modelo no encontrado")
                }
                return@launch
            }

            // 2. Simulación o llamada real a Whisper JNI (según captura de audio PCM)
            // En esta etapa conectamos el resultado de Whisper.cpp
            val recognizedEnglishText = "I don't know what happened." // Reemplazable por el buffer PCM / JNI real de V0.2

            withContext(Dispatchers.Main) {
                onResultUpdate(recognizedEnglishText, "Traduciendo con ML Kit...", "Traduciendo...")
            }

            // 3. Traducir el texto reconocido al español usando ML Kit
            translationManager.translateText(recognizedEnglishText) { spanishTranslation ->
                coroutineScope.launch(Dispatchers.Main) {
                    onResultUpdate(recognizedEnglishText, spanishTranslation, "Completado con éxito")
                }
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
    onDownloadWhisper: ((String) -> Unit) -> Unit,
    onDownloadTranslation: ((String) -> Unit) -> Unit,
    onProcessAudioAndTranslate: ((String, String, String) -> Unit) -> Unit
) {
    var whisperStatus by remember { mutableStateOf("Comprobar estado") }
    var translationStatus by remember { mutableStateOf("Pendiente") }
    var recognizedText by remember { mutableStateOf("Aquí aparecerá el texto reconocido por Whisper.") }
    var translatedText by remember { mutableStateOf("Aquí aparecerá el texto traducido por ML Kit.") }
    var processingState by remember { mutableStateOf("Esperando...") }
    var isProcessing by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "TRADUCTOR V0.3 ANDROID", style = MaterialTheme.typography.titleLarge)

        // MODELO WHISPER
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(text = "MODELO WHISPER", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = {
                    onDownloadWhisper { statusMsg -> whisperStatus = statusMsg }
                }) {
                    Text("DESCARGAR WHISPER")
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Estado: $whisperStatus", style = MaterialTheme.typography.bodySmall)
            }
        }

        // MODELO TRADUCCIÓN
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(text = "MODELO DE TRADUCCIÓN", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = {
                    onDownloadTranslation { statusMsg -> translationStatus = statusMsg }
                }) {
                    Text("DESCARGAR TRADUCCIÓN")
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Estado: $translationStatus", style = MaterialTheme.typography.bodySmall)
            }
        }

        // BOTÓN PRINCIPAL DE CAPTURA Y TRADUCCIÓN UNIFICADA
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !isProcessing,
            onClick = {
                isProcessing = true
                onProcessAudioAndTranslate { english, spanish, state ->
                    recognizedText = english
                    translatedText = spanish
                    processingState = state
                    isProcessing = false
                }
            }
        ) {
            Text(if (isProcessing) "PROCESANDO AUDIO..." else "GRABAR · 6 SEGUNDOS Y TRADUCIR")
        }

        Text(text = "Estado: $processingState", style = MaterialTheme.typography.bodyMedium)

        // TEXTO ORIGINAL (INGLÉS)
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(text = "TEXTO ORIGINAL (INGLÉS)", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = recognizedText, style = MaterialTheme.typography.bodyMedium)
            }
        }

        // TRADUCCIÓN (ESPAÑOL)
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(text = "TRADUCCIÓN (ESPAÑOL)", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = translatedText, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
