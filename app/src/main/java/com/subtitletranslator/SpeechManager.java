package com.subtitletranslator;

import android.content.Context;
import android.content.Intent;
import android.os.*;
import android.speech.*;
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

    private SpeechRecognizer recognizer;
    private final Handler handler;
    private boolean active = false;
    private int errorCount = 0;
    private static final int MAX_ERRORS = 5;
    private static final int BASE_DELAY_MS = 500;

    public SpeechManager(Context context, String sourceLang, SpeechCallback callback) {
        this.context = context;
        this.sourceLang = sourceLang;
        this.callback = callback;
        this.handler = new Handler(Looper.getMainLooper());
    }

    public void start() {
        active = true;
        errorCount = 0;
        listen();
    }

    public void stop() {
        active = false;
        handler.removeCallbacksAndMessages(null);
        destroyRecognizer();
    }

    private void listen() {
        if (!active) return;

        destroyRecognizer();
        recognizer = SpeechRecognizer.createSpeechRecognizer(context);
        recognizer.setRecognitionListener(new RecognitionListener() {

            @Override
            public void onReadyForSpeech(Bundle params) {
                errorCount = 0;
                callback.onStatusChange("🎙️ Escuchando...");
            }

            @Override
            public void onBeginningOfSpeech() {
                callback.onStatusChange("💬 Procesando...");
            }

            @Override
            public void onPartialResults(Bundle partial) {
                ArrayList<String> list = partial.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION);
                if (list != null && !list.isEmpty() && !list.get(0).isEmpty()) {
                    callback.onPartialResult(list.get(0));
                }
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> list = results.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION);
                if (list != null && !list.isEmpty() && !list.get(0).isEmpty()) {
                    callback.onResult(list.get(0));
                }
                // Reinicio inmediato tras resultado exitoso
                scheduleRestart(300);
            }

            @Override
            public void onError(int error) {
                switch (error) {
                    case SpeechRecognizer.ERROR_NO_MATCH:
                    case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                        // Errores normales, reiniciar rápido
                        scheduleRestart(300);
                        break;
                    case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                        // Esperar más tiempo
                        scheduleRestart(1000);
                        break;
                    case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                        callback.onStatusChange("⚠️ Sin permiso de micrófono");
                        active = false;
                        break;
                    default:
                        errorCount++;
                        if (errorCount >= MAX_ERRORS) {
                            // Pausa larga y resetea contador
                            callback.onStatusChange("🔄 Reiniciando...");
                            errorCount = 0;
                            scheduleRestart(3000);
                        } else {
                            // Espera progresiva
                            int delay = BASE_DELAY_MS * errorCount;
                            scheduleRestart(delay);
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
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        intent.putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L);
        intent.putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L);
        // Timeout extendido para reducir ERROR_SPEECH_TIMEOUT
        intent.putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500L);

        try {
            recognizer.startListening(intent);
        } catch (Exception e) {
            scheduleRestart(1000);
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
    }
