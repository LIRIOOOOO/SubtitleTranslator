package com.subtitletranslator;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.speech.*;
import android.view.*;
import android.widget.*;
import androidx.core.app.NotificationCompat;
import java.util.*;

public class OverlayService extends Service {

    public static final String ACTION_START = "ACTION_START";
    public static final String ACTION_STOP = "ACTION_STOP";
    public static final String ACTION_UPDATE_FONT = "ACTION_UPDATE_FONT";

    private static final String CHANNEL_ID = "subtitle_channel";

    private WindowManager windowManager;
    private View overlayView;
    private TextView tvSubtitle;
    private TextView tvOriginal;
    private ImageButton btnClose;
    private ImageButton btnMinimize;

    private SpeechRecognizer speechRecognizer;
    private String sourceLang = "auto";
    private int fontSize = 18;
    private boolean isListening = false;
    private boolean isPaused = false;

    private Handler mainHandler;
    private OfflineTranslator translator;

    private static final int RESTART_DELAY_MS = 300;
    private Runnable restartRunnable;

    @Override
    public void onCreate() {
        super.onCreate();
        mainHandler = new Handler(Looper.getMainLooper());
        translator = new OfflineTranslator(this);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_STICKY;
        String action = intent.getAction();
        if (action == null) return START_STICKY;

        switch (action) {
            case ACTION_START:
                sourceLang = intent.getStringExtra("sourceLang");
                if (sourceLang == null) sourceLang = "auto";
                fontSize = intent.getIntExtra("fontSize", 18);
                startForegroundWithNotification();
                showOverlay();
                startListening();
                break;
            case ACTION_STOP:
                stopListening();
                removeOverlay();
                stopForeground(true);
                stopSelf();
                break;
            case ACTION_UPDATE_FONT:
                fontSize = intent.getIntExtra("fontSize", 18);
                if (tvSubtitle != null) tvSubtitle.setTextSize(fontSize);
                break;
        }
        return START_STICKY;
    }

    private void startForegroundWithNotification() {
        Intent notifIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                notifIntent, PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("SubTraductor activo")
                .setContentText("Escuchando y traduciendo al español")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();

        startForeground(1, notification);
    }

    private void showOverlay() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_subtitle, null);
        tvSubtitle = overlayView.findViewById(R.id.tvSubtitle);
        tvOriginal = overlayView.findViewById(R.id.tvOriginal);
        btnClose = overlayView.findViewById(R.id.btnClose);
        btnMinimize = overlayView.findViewById(R.id.btnMinimize);

        tvSubtitle.setTextSize(fontSize);

        btnClose.setOnClickListener(v -> {
            stopListening();
            removeOverlay();
            stopForeground(true);
            stopSelf();
        });

        btnMinimize.setOnClickListener(v -> {
            isPaused = !isPaused;
            if (isPaused) {
                tvSubtitle.setText("⏸ Pausado");
                tvOriginal.setVisibility(View.GONE);
                btnMinimize.setImageResource(android.R.drawable.ic_media_play);
                stopListening();
            } else {
                tvSubtitle.setText("🎙️ Escuchando...");
                btnMinimize.setImageResource(android.R.drawable.ic_media_pause);
                startListening();
            }
        });

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.BOTTOM;
        params.y = 60;

        overlayView.setOnTouchListener(new OverlayDragListener(windowManager, params, overlayView));
        windowManager.addView(overlayView, params);
    }

    private void removeOverlay() {
        if (overlayView != null && windowManager != null) {
            try { windowManager.removeView(overlayView); } catch (Exception e) { }
            overlayView = null;
        }
    }

    private void startListening() {
        if (isListening) return;
        isListening = true;

        mainHandler.post(() -> {
            if (speechRecognizer != null) speechRecognizer.destroy();

            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {

                @Override
                public void onResults(Bundle results) {
                    ArrayList<String> matches = results.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        processRecognizedText(matches.get(0));
                    }
                    if (isListening && !isPaused) scheduleRestart();
                }

                @Override
                public void onPartialResults(Bundle partialResults) {
                    ArrayList<String> partial = partialResults.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION);
                    if (partial != null && !partial.isEmpty()) {
                        if (tvOriginal != null) {
                            tvOriginal.setText("🔊 " + partial.get(0));
                            tvOriginal.setVisibility(View.VISIBLE);
                        }
                    }
                }

                @Override
                public void onError(int error) {
                    if (tvSubtitle != null && error != SpeechRecognizer.ERROR_NO_MATCH) {
                        tvSubtitle.setText("⚠️ " + getErrorMsg(error));
                    }
                    if (isListening && !isPaused) scheduleRestart();
                }

                @Override public void onReadyForSpeech(Bundle params) {
                    if (tvSubtitle != null) tvSubtitle.setText("🎙️ Escuchando...");
                }
                @Override public void onBeginningOfSpeech() {
                    if (tvSubtitle != null) tvSubtitle.setText("💬 Procesando...");
                }
                @Override public void onRmsChanged(float rmsdB) {}
                @Override public void onBufferReceived(byte[] buffer) {}
                @Override public void onEndOfSpeech() {}
                @Override public void onEvent(int eventType, Bundle params) {}
            });

            Intent recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            if ("auto".equals(sourceLang)) {
                recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US");
            } else {
                recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, sourceLang);
            }
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000);

            speechRecognizer.startListening(recognizerIntent);
        });
    }

    private void scheduleRestart() {
        if (restartRunnable != null) mainHandler.removeCallbacks(restartRunnable);
        restartRunnable = () -> { isListening = false; startListening(); };
        mainHandler.postDelayed(restartRunnable, RESTART_DELAY_MS);
    }

    private void processRecognizedText(final String text) {
        if (text == null || text.trim().isEmpty()) return;
        new Thread(() -> {
            String translated = translator.translate(text, sourceLang);
            mainHandler.post(() -> {
                if (tvSubtitle != null) tvSubtitle.setText(translated);
                if (tvOriginal != null) {
                    tvOriginal.setText("🔊 " + text);
                    tvOriginal.setVisibility(View.VISIBLE);
                }
            });
        }).start();
    }

    private void stopListening() {
        isListening = false;
        if (restartRunnable != null) mainHandler.removeCallbacks(restartRunnable);
        if (speechRecognizer != null) {
            try { speechRecognizer.stopListening(); speechRecognizer.destroy(); } catch (Exception e) { }
            speechRecognizer = null;
        }
    }

    private String getErrorMsg(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_AUDIO: return "Error de audio";
            case SpeechRecognizer.ERROR_NETWORK: return "Sin red (modo offline)";
            case SpeechRecognizer.ERROR_NO_MATCH: return "No se escuchó nada";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: return "Reconocedor ocupado";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: return "Sin voz detectada";
            default: return "Error " + error;
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Subtítulos en tiempo real", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopListening();
        removeOverlay();
        if (translator != null) translator.destroy();
    }
                }
