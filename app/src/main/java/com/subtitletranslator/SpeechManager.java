package com.subtitletranslator;

import android.content.Context;
import android.content.Intent;
import android.os.*;
import android.speech.*;
import android.util.Log;
import com.google.mlkit.nl.translate.*;
import java.util.*;

public class SpeechManager {

    public interface SpeechCallback {
        void onPartialResult(String text);
        void onResult(String text);
        void onStatusChange(String status);
    }

    private final Context context;
    private final String sourceLang;
    private final SpeechCallback callback;
    private final Handler handler;

    private SpeechRecognizer recognizer;
    private Translator translator;
    private boolean active = false;
    private boolean translatorReady = false;
    private int errorCount = 0;
    private static final int MAX_ERRORS = 5;

    public SpeechManager(Context context, String sourceLang, SpeechCallback callback) {
        this.context = context;
        this.sourceLang = sourceLang;
        this.callback = callback;
        this.handler = new Handler(Looper.getMainLooper());
    }

    public void start() {
        active = true;
        callback.onStatusChange("⏳ Preparando traductor...");
        initTranslator();
    }

    private void initTranslator() {
        String mlkitLang = toMlKitLang(sourceLang);

        TranslatorOptions options = new TranslatorOptions.Builder()
                .setSourceLanguage(mlkitLang)
                .setTargetLanguage(TranslateLanguage.SPANISH)
                .build();

        translator = Translation.getClient(options);

        DownloadConditions conditions = new DownloadConditions.Builder()
                .requireWifi()
                .build();

        // Intentar sin restricción de WiFi primero
        DownloadConditions noRestriction = new DownloadConditions.Builder().build();

        translator.downloadModelIfNeeded(noRestriction)
                .addOnSuccessListener(unused -> {
                    translatorReady = true;
                    callback.onStatusChange("🎙️ Escuchando...");
                    listen();
                })
                .addOnFailureListener(e -> {
                    // Si falla la descarga, usar traducción básica offline
                    translatorReady = false;
                    callback.onStatusChange("⚠️ Sin modelo ML Kit, usando traducción básica");
                    listen();
                });
    }

    private void listen() {
        if (!active) return;

        destroyRecognizer();
        recognizer = SpeechRecognizer.createSpeechRecognizer(context);
        recognizer.setRecognitionListener(new RecognitionListener() {

            @Override
            public void onReadyForSpeech(Bundle params) {
                errorCount = 0;
            }

            @Override
            public void onBeginningOfSpeech() {}

            @Override
            public void onPartialResults(Bundle partial) {
                ArrayList<String> list = partial.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION);
                if (list != null && !list.isEmpty() && !list.get(0).isEmpty()) {
                    String text = list.get(0);
                    translateAndPost(text, true);
                }
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> list = results.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION);
                if (list != null && !list.isEmpty() && !list.get(0).isEmpty()) {
                    translateAndPost(list.get(0), false);
                }
                scheduleRestart(300);
            }

            @Override
            public void onError(int error) {
                switch (error) {
                    case SpeechRecognizer.ERROR_NO_MATCH:
                    case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                        scheduleRestart(300);
                        break;
                    case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                        scheduleRestart(1000);
                        break;
                    default:
                        errorCount++;
                        if (errorCount >= MAX_ERRORS) {
                            errorCount = 0;
                            scheduleRestart(3000);
                        } else {
                            scheduleRestart(500 * errorCount);
                        }
                        break;
                }
            }

            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        String lang = "auto".equals(sourceLang) ? "en-US" : sourceLang;
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L);

        try {
            recognizer.startListening(intent);
        } catch (Exception e) {
            scheduleRestart(1000);
        }
    }

    private void translateAndPost(String text, boolean isPartial) {
        if (translatorReady && translator != null) {
            translator.translate(text)
                    .addOnSuccessListener(translated -> {
                        if (isPartial) {
                            callback.onPartialResult(translated);
                        } else {
                            callback.onResult(translated);
                        }
                        callback.onPartialResult(text); // mostrar original
                    })
                    .addOnFailureListener(e -> {
                        // Fallback al diccionario offline
                        postFallback(text, isPartial);
                    });
        } else {
            postFallback(text, isPartial);
        }
    }

    private void postFallback(String text, boolean isPartial) {
        if (isPartial) {
            callback.onPartialResult(text);
        } else {
            callback.onResult(text);
        }
    }

    private void scheduleRestart(int delayMs) {
        if (!active) return;
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(this::listen, delayMs);
    }

    private void destroyRecognizer() {
        if (recognizer != null) {
            try {
                recognizer.stopListening();
                recognizer.destroy();
            } catch (Exception e) {}
            recognizer = null;
        }
    }

    private String toMlKitLang(String langCode) {
        if (langCode == null || "auto".equals(langCode) || langCode.startsWith("en"))
            return TranslateLanguage.ENGLISH;
        if (langCode.startsWith("fr")) return TranslateLanguage.FRENCH;
        if (langCode.startsWith("de")) return TranslateLanguage.GERMAN;
        if (langCode.startsWith("it")) return TranslateLanguage.ITALIAN;
        if (langCode.startsWith("pt")) return TranslateLanguage.PORTUGUESE;
        if (langCode.startsWith("ja")) return TranslateLanguage.JAPANESE;
        if (langCode.startsWith("ko")) return TranslateLanguage.KOREAN;
        if (langCode.startsWith("zh")) return TranslateLanguage.CHINESE;
        if (langCode.startsWith("ru")) return TranslateLanguage.RUSSIAN;
        if (langCode.startsWith("ar")) return TranslateLanguage.ARABIC;
        if (langCode.startsWith("nl")) return TranslateLanguage.DUTCH;
        if (langCode.startsWith("pl")) return TranslateLanguage.POLISH;
        if (langCode.startsWith("tr")) return TranslateLanguage.TURKISH;
        if (langCode.startsWith("hi")) return TranslateLanguage.HINDI;
        if (langCode.startsWith("id")) return TranslateLanguage.INDONESIAN;
        return TranslateLanguage.ENGLISH;
    }

    public void stop() {
        active = false;
        handler.removeCallbacksAndMessages(null);
        destroyRecognizer();
        if (translator != null) {
            translator.close();
            translator = null;
        }
    }
}
