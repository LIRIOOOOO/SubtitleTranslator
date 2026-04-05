package com.subtitletranslator;

import android.app.Service;
import android.content.Intent;
import android.os.*;
import android.speech.*;
import java.util.*;

public class RecognizerServiceB extends Service {

    public static final String ACTION_START = "ACTION_START_B";
    public static final String ACTION_STOP = "ACTION_STOP_B";
    public static final String BROADCAST_PARTIAL = "com.subtitletranslator.PARTIAL_B";
    public static final String BROADCAST_RESULT = "com.subtitletranslator.RESULT_B";
    public static final String EXTRA_TEXT = "text";
    public static final String EXTRA_LANG = "lang";

    private SpeechRecognizer recognizer;
    private Handler handler;
    private boolean active = false;
    private String sourceLang = "en-US";
    private String lastPartial = "";
    private String lastResult = "";

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_STICKY;
        String action = intent.getAction();
        if (action == null) return START_STICKY;

        switch (action) {
            case ACTION_START:
                sourceLang = intent.getStringExtra(EXTRA_LANG);
                if (sourceLang == null) sourceLang = "en-US";
                active = true;
                // Arrancar con delay para no competir con reconocedor A
                handler.postDelayed(this::listen, 400);
                break;
            case ACTION_STOP:
                active = false;
                destroyRecognizer();
                stopSelf();
                break;
        }
        return START_STICKY;
    }

    private void listen() {
        if (!active) return;
        destroyRecognizer();

        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionListener() {

            @Override
            public void onReadyForSpeech(Bundle params) {
                lastPartial = "";
            }

            @Override
            public void onPartialResults(Bundle partial) {
                ArrayList<String> list = partial.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION);
                if (list != null && !list.isEmpty()) {
                    String text = list.get(0).trim();
                    if (!text.isEmpty() && !text.equals(lastPartial)) {
                        lastPartial = text;
                        broadcast(BROADCAST_PARTIAL, text);
                    }
                }
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> list = results.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION);
                if (list != null && !list.isEmpty()) {
                    String text = list.get(0).trim();
                    if (!text.isEmpty() && !text.equals(lastResult)) {
                        lastResult = text;
                        lastPartial = "";
                        broadcast(BROADCAST_RESULT, text);
                    }
                }
                if (active) handler.postDelayed(RecognizerServiceB.this::listen, 200);
            }

            @Override
            public void onError(int error) {
                int delay;
                switch (error) {
                    case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                        delay = 600;
                        break;
                    case SpeechRecognizer.ERROR_NO_MATCH:
                    case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                        delay = 200;
                        break;
                    default:
                        delay = 400;
                        break;
                }
                if (active) handler.postDelayed(RecognizerServiceB.this::listen, delay);
            }

            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, sourceLang);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        intent.putExtra("android.speech.extra.PREFER_OFFLINE", false);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L);

        try {
            recognizer.startListening(intent);
        } catch (Exception e) {
            if (active) handler.postDelayed(this::listen, 500);
        }
    }

    private void broadcast(String action, String text) {
        Intent i = new Intent(action);
        i.putExtra(EXTRA_TEXT, text);
        sendBroadcast(i);
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

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        active = false;
        destroyRecognizer();
    }
}
