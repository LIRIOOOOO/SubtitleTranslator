package com.subtitletranslator;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.StorageService;
import org.json.JSONObject;
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
        callback.onStatusChange("⏳ Cargando modelo...");

        // Determinar qué modelo descargar según idioma
        String modelName = getModelName(sourceLang);

        StorageService.unpack(context, modelName, "model",
            (model) -> {
                this.model = model;
                try {
                    recognizer = new Recognizer(model, SAMPLE_RATE);
                    startRecording();
                    mainHandler.post(() -> callback.onStatusChange("🎙️ Escuchando..."));
                } catch (IOException e) {
                    mainHandler.post(() -> callback.onStatusChange("⚠️ Error al iniciar reconocedor"));
                }
            },
            (exception) -> {
                mainHandler.post(() -> callback.onStatusChange("⚠️ Error descargando modelo"));
            }
        );
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
                            // Resultado final
                            String result = recognizer.getResult();
                            String text = extractText(result, "text");
                            if (!text.isEmpty()) {
                                mainHandler.post(() -> callback.onResult(text));
                            }
                        } else {
                            // Resultado parcial
                            String partial = recognizer.getPartialResult();
                            String text = extractText(partial, "partial");
                            if (!text.isEmpty()) {
                                mainHandler.post(() -> callback.onPartialResult(text));
                            }
                        }
                    } catch (Exception e) {
                        // Continuar aunque haya error en frame
                    }
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

    private String getModelName(String langCode) {
        if (langCode == null || "auto".equals(langCode) || langCode.startsWith("en")) {
            return "vosk-model-small-en-us-0.15";
        } else if (langCode.startsWith("fr")) {
            return "vosk-model-small-fr-0.22";
        } else if (langCode.startsWith("de")) {
            return "vosk-model-small-de-0.15";
        } else if (langCode.startsWith("es")) {
            return "vosk-model-small-es-0.42";
        } else if (langCode.startsWith("pt")) {
            return "vosk-model-small-pt-0.3";
        } else if (langCode.startsWith("it")) {
            return "vosk-model-small-it-0.22";
        } else if (langCode.startsWith("ru")) {
            return "vosk-model-small-ru-0.22";
        } else if (langCode.startsWith("zh")) {
            return "vosk-model-small-cn-0.22";
        } else if (langCode.startsWith("ja")) {
            return "vosk-model-small-ja-0.22";
        } else if (langCode.startsWith("ko")) {
            return "vosk-model-small-ko-0.22";
        } else {
            // Default inglés
            return "vosk-model-small-en-us-0.15";
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
