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
    private static final int MAX_HISTORY = 50;

    private WindowManager windowManager;
    private View overlayView;
    private TextView tvSubtitle;
    private TextView tvOriginal;
    private TextView tvHistory;
    private ScrollView scrollHistory;
    private View divider;
    private ImageButton btnClose;
    private ImageButton btnMinimize;
    private ImageButton btnHistory;

    private SpeechManager speechManager;
    private OfflineTranslator translator;
    private AudioManager audioManager;

    private String sourceLang = "auto";
    private int fontSize = 18;
    private boolean isPaused = false;
    private boolean historyVisible = false;

    private Handler mainHandler;
    private StringBuilder historyText = new StringBuilder();
    private int historyCount = 0;

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
                startSpeech();
                break;
            case ACTION_STOP:
                stopSpeech();
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

    private void startSpeech() {
        speechManager = new SpeechManager(this, sourceLang,
                new SpeechManager.SpeechCallback() {

                    @Override
                    public void onPartialResult(String text) {
                        mainHandler.post(() -> {
                            if (tvSubtitle != null) tvSubtitle.setText(text);
                            if (tvOriginal != null) tvOriginal.setVisibility(View.VISIBLE);
                        });
                    }

                    @Override
                    public void onResult(String text) {
                        mainHandler.post(() -> {
                            if (tvSubtitle != null) tvSubtitle.setText(text);
                            addToHistory(text);
                        });
                    }

                    @Override
                    public void onStatusChange(String status) {
                        mainHandler.post(() -> {
                            if (tvSubtitle != null && !isPaused) {
                                String current = tvSubtitle.getText().toString();
                                if (current.isEmpty() || current.startsWith("🎙")
                                        || current.startsWith("⚠") || current.startsWith("⏳")
                                        || current.startsWith("🔄") || current.startsWith("⏸")) {
                                    tvSubtitle.setText(status);
                                }
                            }
                        });
                    }
                });
        speechManager.start();
    }

    private void addToHistory(String text) {
        if (text == null || text.trim().isEmpty()) return;
        historyCount++;
        if (historyCount > MAX_HISTORY) {
            // Borrar primera línea
            int idx = historyText.indexOf("\n");
            if (idx >= 0) historyText.delete(0, idx + 1);
            historyCount = MAX_HISTORY;
        }
        if (historyText.length() > 0) historyText.append("\n");
        historyText.append(text);

        if (tvHistory != null) {
            tvHistory.setText(historyText.toString());
            // Auto-scroll al final
            if (scrollHistory != null) {
                scrollHistory.post(() -> scrollHistory.fullScroll(View.FOCUS_DOWN));
            }
        }
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
        scrollHistory = overlayView.findViewById(R.id.scrollHistory);
        divider = overlayView.findViewById(R.id.divider);
        btnClose = overlayView.findViewById(R.id.btnClose);
        btnMinimize = overlayView.findViewById(R.id.btnMinimize);
        btnHistory = overlayView.findViewById(R.id.btnHistory);

        tvSubtitle.setTextSize(fontSize);

        btnClose.setOnClickListener(v -> {
            stopSpeech();
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
                stopSpeech();
            } else {
                tvSubtitle.setText("🎙️ Escuchando...");
                btnMinimize.setImageResource(android.R.drawable.ic_media_pause);
                startSpeech();
            }
        });

        btnHistory.setOnClickListener(v -> {
            historyVisible = !historyVisible;
            scrollHistory.setVisibility(historyVisible ? View.VISIBLE : View.GONE);
            divider.setVisibility(historyVisible ? View.VISIBLE : View.GONE);
            if (historyVisible && scrollHistory != null) {
                scrollHistory.post(() -> scrollHistory.fullScroll(View.FOCUS_DOWN));
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
        removeOverlay();
        if (translator != null) translator.destroy();
    }
}
