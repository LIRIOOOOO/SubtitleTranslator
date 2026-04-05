package com.subtitletranslator;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import com.google.mlkit.nl.translate.*;
import com.google.mlkit.common.model.DownloadConditions;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.util.*;

public class SpeechManager {

    public interface SpeechCallback {
        void onPartialResult(String text);
        void onResult(String text);
        void onStatusChange(String status);
    }

    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL = AudioFormat.CHANNEL_IN_MONO;
    private static final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private static final int SEGMENT_SECONDS = 3;
    private static final int BUFFER_SIZE = SAMPLE_RATE * SEGMENT_SECONDS * 2;

    private final Context context;
    private final String sourceLang;
    private final SpeechCallback callback;
    private final Handler mainHandler;

    private AudioRecord audioRecord;
    private Thread captureThread;
    private volatile boolean active = false;

    private Translator translator;
    private boolean translatorReady = false;

    private String lastSent = "";

    public SpeechManager(Context context, String sourceLang, SpeechCallback callback) {
        this.context = context;
        this.sourceLang = sourceLang;
        this.callback = callback;
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void start() {
        active = true;
        mainHandler.post(() -> callback.onStatusChange("⏳ Preparando traductor..."));
        initTranslator();
    }

    private void initTranslator() {
        TranslatorOptions options = new TranslatorOptions.Builder()
                .setSourceLanguage(toMlKitLang(sourceLang))
                .setTargetLanguage(TranslateLanguage.SPANISH)
                .build();

        translator = Translation.getClient(options);
        DownloadConditions conditions = new DownloadConditions.Builder().build();

        translator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener(unused -> {
                    translatorReady = true;
                    mainHandler.post(() -> callback.onStatusChange("🎙️ Escuchando..."));
                    startCapture();
                })
                .addOnFailureListener(e -> {
                    translatorReady = false;
                    mainHandler.post(() -> callback.onStatusChange("🎙️ Escuchando (sin traducción)..."));
                    startCapture();
                });
    }

    private void startCapture() {
        int minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING);

        audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL,
                ENCODING,
                Math.max(minBuffer, BUFFER_SIZE));

        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            mainHandler.post(() -> callback.onStatusChange("⚠️ Error al iniciar micrófono"));
            return;
        }

        audioRecord.startRecording();

        captureThread = new Thread(() -> {
            byte[] segment = new byte[BUFFER_SIZE];
            while (active) {
                int totalRead = 0;
                while (totalRead < BUFFER_SIZE && active) {
                    int read = audioRecord.read(segment, totalRead, BUFFER_SIZE - totalRead);
                    if (read > 0) totalRead += read;
                }
                if (active && totalRead > 0) {
                    final byte[] audioData = Arrays.copyOf(segment, totalRead);
                    new Thread(() -> recognizeAndTranslate(audioData)).start();
                }
            }
        });
        captureThread.start();
    }

    private void recognizeAndTranslate(byte[] audioData) {
        try {
            String lang = "auto".equals(sourceLang) ? "en-US" : sourceLang;
            String recognized = callGoogleSpeech(audioData, lang);

            if (recognized == null || recognized.isEmpty()) return;
            if (recognized.equals(lastSent)) return;
            lastSent = recognized;

            final String rec = recognized;
            mainHandler.post(() -> callback.onPartialResult(rec));

            if (translatorReady && translator != null) {
                translator.translate(recognized)
                        .addOnSuccessListener(translated -> callback.onResult(translated))
                        .addOnFailureListener(e -> callback.onResult(recognized));
            } else {
                mainHandler.post(() -> callback.onResult(recognized));
            }

        } catch (Exception e) {
            android.util.Log.e("SpeechManager", "Error: " + e.getMessage());
        }
    }

    private String callGoogleSpeech(byte[] pcm, String langCode) {
        try {
            // Codificar audio en base64
            String audioBase64 = android.util.Base64.encodeToString(
                    buildWav(pcm), android.util.Base64.NO_WRAP);

            // Construir JSON request
            JSONObject config = new JSONObject();
            config.put("encoding", "LINEAR16");
            config.put("sampleRateHertz", SAMPLE_RATE);
            config.put("languageCode", langCode);
            config.put("enableAutomaticPunctuation", false);
            config.put("profanityFilter", false);

            JSONObject audio = new JSONObject();
            audio.put("content", audioBase64);

            JSONObject request = new JSONObject();
            request.put("config", config);
            request.put("audio", audio);

            // Google Cloud Speech v1 REST API
            String url = "https://speech.googleapis.com/v1/speech:recognize" +
                    "?key=AIzaSyC6-7Q3-pC9dKMT3vgQiEoOEK6OMr6v3H8";

            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setDoOutput(true);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);

            byte[] body = request.toString().getBytes("UTF-8");
            conn.setRequestProperty("Content-Length", String.valueOf(body.length));
            OutputStream os = conn.getOutputStream();
            os.write(body);
            os.flush();
            os.close();

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                android.util.Log.e("SpeechManager", "HTTP " + responseCode);
                return null;
            }

            BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();

            JSONObject response = new JSONObject(sb.toString());
            JSONArray results = response.optJSONArray("results");
            if (results != null && results.length() > 0) {
                JSONArray alternatives = results.getJSONObject(0)
                        .optJSONArray("alternatives");
                if (alternatives != null && alternatives.length() > 0) {
                    return alternatives.getJSONObject(0).optString("transcript", "");
                }
            }

        } catch (Exception e) {
            android.util.Log.e("SpeechManager", "Speech error: " + e.getMessage());
        }
        return null;
    }

    private byte[] buildWav(byte[] pcm) throws IOException {
        int dataSize = pcm.length;
        int totalSize = dataSize + 44;
        ByteArrayOutputStream out = new ByteArrayOutputStream(totalSize);
        DataOutputStream dos = new DataOutputStream(out);

        dos.writeBytes("RIFF");
        dos.writeInt(Integer.reverseBytes(totalSize - 8));
        dos.writeBytes("WAVE");
        dos.writeBytes("fmt ");
        dos.writeInt(Integer.reverseBytes(16));
        dos.writeShort(Short.reverseBytes((short) 1));
        dos.writeShort(Short.reverseBytes((short) 1));
        dos.writeInt(Integer.reverseBytes(SAMPLE_RATE));
        dos.writeInt(Integer.reverseBytes(SAMPLE_RATE * 2));
        dos.writeShort(Short.reverseBytes((short) 2));
        dos.writeShort(Short.reverseBytes((short) 16));
        dos.writeBytes("data");
        dos.writeInt(Integer.reverseBytes(dataSize));
        dos.write(pcm);
        dos.close();

        return out.toByteArray();
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
        if (audioRecord != null) {
            try {
                audioRecord.stop();
                audioRecord.release();
            } catch (Exception e) {}
            audioRecord = null;
        }
        if (captureThread != null) {
            captureThread.interrupt();
            captureThread = null;
        }
        if (translator != null) {
            translator.close();
            translator = null;
        }
    }
}
