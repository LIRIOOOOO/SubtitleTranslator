package com.subtitletranslator;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.*;
import android.speech.*;
import com.google.mlkit.nl.translate.*;
import com.google.mlkit.common.model.DownloadConditions;
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

    private SpeechRecognizer recognizerA;
    private boolean aListening = false;

    private Translator translator;
    private boolean active = false;
    private boolean translatorReady = false;
    private int errorCount = 0;

    private String lastPartialA = "";
    private String lastPartialB = "";
    private String lastResult = "";

    // Receiver para escuchar resultados del proceso B
    private BroadcastReceiver receiverB;

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
        TranslatorOptions options = new TranslatorOptions.Builder()
                .setSourceLanguage(toMlKitLang(sourceLang))
                .setTargetLanguage(TranslateLanguage.SPANISH)
                .build();

        translator = Translation.getClient(options);
        DownloadConditions noRestriction = new DownloadConditions.Builder().build();

        translator.downloadModelIfNeeded(noRestriction)
                .addOnSuccessListener(unused -> {
                    translatorReady = true;
                    callback.onStatusChange("🎙️ Escuchando...");
                    registerReceiverB();
                    startRecognizerB();
                    listenA();
                })
                .addOnFailureListener(e -> {
                    translatorReady = false;
                    callback.onStatusChange("⚠️ Sin modelo, usando texto original");
                    registerReceiverB();
                    startRecognizerB();
                    listenA();
                });
    }

    private void registerReceiverB() {
        receiverB = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (!active) return;
                String text = intent.getStringExtra(RecognizerServiceB.EXTRA_TEXT);
                if (text == null || text.isEmpty()) return;

                String action = intent.getAction();
                if (RecognizerServiceB.BROADCAST_PARTIAL.equals(action)) {
                    if (!text.equals(lastPartialB)) {
                        lastPartialB = text;
                        // Solo mostrar parcial de B si A no está produciendo resultados
                        if (!aListening || lastPartialA.isEmpty()) {
                            translateAndPost(text, true);
                        }
                    }
                } else if (RecognizerServiceB.BROADCAST_RESULT.equals(action)) {
                    if (!text.equals(lastResult)) {
                        lastResult = text;
                        lastPartialB = "";
                        translateAndPost(text, false);
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(RecognizerServiceB.BROADCAST_PARTIAL);
        filter.addAction(RecognizerServiceB.BROADCAST_RESULT);
        context.registerReceiver(receiverB, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    private void startRecognizerB() {
        if (!active) return;
        String lang = "auto".equals(sourceLang) ? "en-US" : sourceLang;
        Intent i = new Intent(context, RecognizerServiceB.class);
        i.setAction(RecognizerServiceB.ACTION_START);
        i.putExtra(RecognizerServiceB.EXTRA_LANG, lang);
        context.startService(i);
    }

    private void stopRecognizerB() {
        Intent i = new Intent(context, RecognizerServiceB.class);
        i.setAction(RecognizerServiceB.ACTION_STOP);
        context.startService(i);
    }

    private void listenA() {
        if (!active) return;

        destroyRecognizerA();
        recognizerA = SpeechRecognizer.createSpeechRecognizer(context);
        recognizerA.setRecognitionListener(new RecognitionListener() {

            @Override
            public void onReadyForSpeech(Bundle params) {
                errorCount = 0;
                lastPartialA = "";
                aListening = true;
            }

            @Override public void onBeginningOfSpeech() {}

            @Override
            public void onPartialResults(Bundle partial) {
                ArrayList<String> list = partial.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION);
                if (list != null && !list.isEmpty()) {
                    String text = list.get(0).trim();
                    if (!text.isEmpty() && !text.equals(lastPartialA)) {
                        lastPartialA = text;
                        translateAndPost(text, true);
                    }
                }
            }

            @Override
            public void onResults(Bundle results) {
                aListening = false;
                lastPartialA = "";
                ArrayList<String> list = results.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION);
                if (list != null && !list.isEmpty()) {
                    String text = list.get(0).trim();
                    if (!text.isEmpty() && !text.equals(lastResult)) {
                        lastResult = text;
                        translateAndPost(text, false);
                    }
                }
                // Reiniciar A con delay mínimo — B cubre el gap
                if (active) handler.postDelayed(() -> listenA(), 150);
            }

            @Override
            public void onError(int error) {
                aListening = false;
                int delay;
                switch (error) {
                    case SpeechRecognizer.ERROR_NO_MATCH:
                    case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                        delay = 150;
                        break;
                    case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                        delay = 600;
                        break;
                    default:
                        errorCount++;
                        delay = errorCount >= 5 ? 3000 : 300 * errorCount;
                        if (errorCount >= 5) errorCount = 0;
                        break;
                }
                if (active) handler.postDelayed(() -> listenA(), delay);
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
        intent.putExtra("android.speech.extra.PREFER_OFFLINE", false);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500L);

        try {
            recognizerA.startListening(intent);
        } catch (Exception e) {
            aListening = false;
            if (active) handler.postDelayed(() -> listenA(), 500);
        }
    }

    private void translateAndPost(String text, boolean isPartial) {
        if (translatorReady && translator != null) {
            translator.translate(text)
                    .addOnSuccessListener(translated -> {
                        if (isPartial) callback.onPartialResult(translated);
                        else callback.onResult(translated);
                    })
                    .addOnFailureListener(e -> postFallback(text, isPartial));
        } else {
            postFallback(text, isPartial);
        }
    }

    private void postFallback(String text, boolean isPartial) {
        if (isPartial) callback.onPartialResult(text);
        else callback.onResult(text);
    }

    private void destroyRecognizerA() {
        if (recognizerA != null) {
            try {
                recognizerA.stopListening();
                recognizerA.destroy();
            } catch (Exception e) {}
            recognizerA = null;
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
        destroyRecognizerA();
        stopRecognizerB();
        if (receiverB != null) {
            try { context.unregisterReceiver(receiverB); } catch (Exception e) {}
            receiverB = null;
        }
        if (translator != null) {
            translator.close();
            translator = null;
        }
    }
}
