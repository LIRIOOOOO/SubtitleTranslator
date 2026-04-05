package com.subtitletranslator;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.media.AudioManager;
import android.media.AudioFocusRequest;
import android.media.AudioAttributes;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.core.app.NotificationCompat;

public class OverlayService extends Service {

    public static final String ACTION_START = "ACTION_START";
    public static final String ACTION_STOP = "ACTION_STOP";
    public static final String ACTION_UPDATE_FONT = "ACTION_UPDATE_FONT";

    private static final String CHANNEL_ID = "subtitle_channel";
    private static final long HISTORY_DURATION_MS = 5000;

    private WindowManager windowManager;
    private View overlayView;
    private TextView tvSubtitle;
    private TextView tvOriginal;
    private TextView tvHistory;
    private ImageButton btnClose;
    private ImageButton btnMinimize;

    private SpeechManager speechManager;
    private OfflineTranslator translator;
    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;

    private String sourceLang = "auto";
    private int fontSize = 18;
    private boolean isPaused = false;

    private Handler mainHandler;
    private Runnable clearHistoryRunnable;

    // Último texto traducido confirmado
    private String lastConfirmedTranslation = "";

    @Override
    public void onCreate() {
        super.onCreate();
        mainHandler = new Handler(Looper.getMainLooper());
        translator = new OfflineTranslator(this);
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
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
                requestAudioFocusDuck();
                startSpeech();
                break;
            case ACTION_STOP:
                stopSpeech();
                abandonAudioFocus();
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

    private void requestAudioFocusDuck() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();
            audioFocusRequest = new AudioFocusRequest.Builder(
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(attrs)
                    .setWillPauseWhenDucked(false)
                    .setOnAudioFocusChangeListener(change -> {})
                    .build();
            audioManager.requestAudioFocus(audioFocusRequest);
        } else {
            audioManager.requestAudioFocus(
                    change -> {},
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
        }
    }

    private void abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (audioFocusRequest != null) {
                audioManager.abandonAudioFocusRequest(audioFocusRequest);
            }
        } else {
            audioManager.abandonAudioFocus(change -> {});
        }
    }

    private void startSpeech() {
        speechManager = new SpeechManager(this, sourceLang,
                new SpeechManager.SpeechCallback() {

                    @Override
                    public void onPartialResult(String text) {
                        // Mostrar traducción parcial en zona principal
                        mainHandler.post(() -> {
                            if (tvSubtitle != null) tvSubtitle.setText(text);
                            if (tvOriginal != null) {
                                tvOriginal.setVisibility(View.VISIBLE);
                            }
                        });
                    }

                    @Override
                    public void onResult(String text) {
                        mainHandler.post(() -> {
                            // Mover traducción actual al historial
                            if (!lastConfirmedTranslation.isEmpty() && tvHistory != null) {
                                tvHistory.setText(lastConfirmedTranslation);
                                tvHistory.setVisibility(View.VISIBLE);

                                // Limpiar historial después de 5 segundos
                                if (clearHistoryRunnable != null) {
                                    mainHandler.removeCallbacks(clearHistoryRunnable);
                                }
                                clearHistoryRunnable = () -> {
                                    if (tvHistory != null) {
                                        tvHistory.setVisibility(View.GONE);
                                        tvHistory.setText("");
                                    }
                                };
                                mainHandler.postDelayed(clearHistoryRunnable, HISTORY_DURATION_MS);
                            }

                            // Mostrar nuevo resultado en zona principal
                            lastConfirmedTranslation = text;
                            if (tvSubtitle != null) tvSubtitle.setText(text);
                        });
                    }

                    @Override
                    public void onStatusChange(String status) {
                        mainHandler.post(() -> {
                            if (tvSubtitle != null && !isPaused) {
                                String current = tvSubtitle.getText().toString();
                                if (current.isEmpty() || current.startsWith("🎙")
                                        || current.startsWith("⚠") || current.startsWith("⏳")
                                        || current.startsWith("🔄")) {
                                    tvSubtitle.setText(status);
                                }
                            }
                        });
                    }
                });
        speechManager.start();
    }

    private void stopSpeech() {
        if (speechManager != null) {
            speechManager.stop();
            speechManager = null;
        }
    }

    private void showOverlay() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_subtitle, null);
        tvSubtitle = overlayView.findViewById(R.id.tvSubtitle);
        tvOriginal = overlayView.findViewById(R.id.tvOriginal);
        tvHistory = overlayView.findViewById(R.id.tvHistory);
        btnClose = overlayView.findViewById(R.id.btnClose);
        btnMinimize = overlayView.findViewById(R.id.btnMinimize);

        tvSubtitle.setTextSize(fontSize);

        btnClose.setOnClickListener(v -> {
            stopSpeech();
            abandonAudioFocus();
            removeOverlay();
            stopForeground(true);
            stopSelf();
        });

        btnMinimize.setOnClickListener(v -> {
            isPaused = !isPaused;
            if (isPaused) {
                tvSubtitle.setText("⏸ Pausado");
                tvOriginal.setVisibility(View.GONE);
                tvHistory.setVisibility(View.GONE);
                btnMinimize.setImageResource(android.R.drawable.ic_media_play);
                stopSpeech();
            } else {
                tvSubtitle.setText("🎙️ Escuchando...");
                btnMinimize.setImageResource(android.R.drawable.ic_media_pause);
                startSpeech();
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
            try { windowManager.removeView(overlayView); } catch (Exception e) {}
            overlayView = null;
        }
        if (clearHistoryRunnable != null) {
            mainHandler.removeCallbacks(clearHistoryRunnable);
        }
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

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Subtítulos en tiempo real",
                    NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopSpeech();
        abandonAudioFocus();
        removeOverlay();
        if (translator != null) translator.destroy();
    }
}
