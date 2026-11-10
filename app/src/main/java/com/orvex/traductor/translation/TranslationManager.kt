package com.orvex.traductor.translation

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions

class TranslationManager {
    private var translator: Translator? = null
    private var isModelDownloaded = false

    init {
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.SPANISH)
            .build()
        translator = Translation.getClient(options)
    }

    fun downloadModel(onResult: (Boolean, String) -> Unit) {
        val conditions = DownloadConditions.Builder().build()

        translator?.let { client ->
            client.downloadModelIfNeeded(conditions)
                .addOnSuccessListener {
                    isModelDownloaded = true
                    onResult(true, "Modelo de traducción descargado con éxito.")
                }
                .addOnFailureListener { exception ->
                    isModelDownloaded = false
                    onResult(false, "Error al descargar modelo: ${exception.localizedMessage}")
                }
        } ?: onResult(false, "El cliente de traducción no está inicializado.")
    }

    fun translateText(englishText: String, onResult: (String) -> Unit) {
        if (englishText.isBlank()) {
            onResult("")
            return
        }

        translator?.let { client ->
            client.translate(englishText)
                .addOnSuccessListener { translatedText ->
                    onResult(translatedText)
                }
                .addOnFailureListener { exception ->
                    onResult("Error de traducción: ${exception.localizedMessage}")
                }
        } ?: onResult("Traductor no disponible.")
    }

    fun close() {
        translator?.close()
        translator = null
    }
}
