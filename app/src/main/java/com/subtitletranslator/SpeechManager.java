package com.subtitletranslator;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;

public class SpeechManager {

    public interface SpeechCallback {
        void onPartialResult(String text);
        void onResult(String text);
        void onStatusChange(String status);
    }

    private static final int SAMPLE_RATE = 16000;
    private static final int BUFFER_SIZE = 4096;

    private final Context context;
    private final String sourceLang;
    private final SpeechCallback callback;
    private final Handler mainHandler;

    private Model model;
    private Recognizer recognizer;
    private AudioRecord audioRecord;
    private Thread recordingThread;
    private volatile boolean active = false;

    public SpeechManager(Context context, String sourceLang, SpeechCallback callback) {
        this.context = context;
        this.sourceLang = sourceLang;
        this.callback = callback;
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void start() {
        active = true;
        mainHandler.post(() -> callback.onStatusChange("⏳ Cargando modelo..."));

        new Thread(() -> {
            try {
                // Buscar modelo en Descargas
                File modelPath = new File(
                    android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_DOWNLOADS),
                    "model");

                if (!modelPath.exists()) {
                    mainHandler.post(() -> callback.onStatusChange(
                        "⚠️ Carpeta 'model' no encontrada en Descargas"));
                    return;
                }

                model = new Model(modelPath.getAbsolutePath());
                recognizer = new Recognizer(model, SAMPLE_RATE);
                startRecording();
                mainHandler.post(() -> callback.onStatusChange("🎙️ Escuchando..."));

} catch (Exception e) {
    mainHandler.post(() -> callback.onStatusChange(
        "⚠️ Error: " + e.getClass().getSimpleName() + " - " + e.getMessage()));
            }
        }).start();
    }

    private void startRecording() {
        int minBuffer = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);

        int bufferSize = Math.max(minBuffer, BUFFER_SIZE);

        audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize);

        audioRecord.startRecording();

        recordingThread = new Thread(() -> {
            short[] buffer = new short[BUFFER_SIZE];
            while (active) {
                int read = audioRecord.read(buffer, 0, buffer.length);
                if (read > 0 && recognizer != null) {
                    try {
                        if (recognizer.acceptWaveForm(buffer, read)) {
                            String result = recognizer.getResult();
                            String text = extractText(result, "text");
                            if (!text.isEmpty()) {
                                mainHandler.post(() -> callback.onResult(text));
                            }
                        } else {
                            String partial = recognizer.getPartialResult();
                            String text = extractText(partial, "partial");
                            if (!text.isEmpty()) {
                                mainHandler.post(() -> callback.onPartialResult(text));
                            }
                        }
                    } catch (Exception e) { }
                }
            }
        });
        recordingThread.start();
    }

    private String extractText(String json, String key) {
        try {
            return new JSONObject(json).optString(key, "").trim();
        } catch (Exception e) {
            return "";
        }
    }

    public void stop() {
        active = false;
        if (audioRecord != null) {
            try {
                audioRecord.stop();
                audioRecord.release();
            } catch (Exception e) {}
            audioRecord = null;
        }
        if (recognizer != null) {
            try { recognizer.close(); } catch (Exception e) {}
            recognizer = null;
        }
        if (model != null) {
            try { model.close(); } catch (Exception e) {}
            model = null;
        }
    }
}
