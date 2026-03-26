package com.subtitletranslator;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.*;
import androidx.annotation.Nullable;

public class MainActivity extends Activity {

    private static final int REQUEST_OVERLAY_PERMISSION = 1001;
    private static final int REQUEST_RECORD_AUDIO = 1002;
    private static final int REQUEST_STORAGE = 1003;

    private Button btnToggle;
    private TextView tvStatus;
    private Spinner spinnerSourceLang;
    private SeekBar seekBarFontSize;
    private TextView tvFontSizeLabel;
    private Switch switchAutoDetect;

    private boolean serviceRunning = false;

    private static final String[] LANGUAGES = {
        "auto", "en-US", "fr-FR", "de-DE", "it-IT", "pt-BR",
        "pt-PT", "ja-JP", "ko-KR", "zh-CN", "zh-TW", "ru-RU",
        "ar-SA", "nl-NL", "pl-PL", "sv-SE", "tr-TR", "cs-CZ",
        "da-DK", "fi-FI", "nb-NO", "uk-UA", "id-ID", "hi-IN"
    };
    private static final String[] LANG_NAMES = {
        "Auto-detectar", "Inglés", "Francés", "Alemán", "Italiano", "Portugués (Brasil)",
        "Portugués", "Japonés", "Coreano", "Chino (Simplif.)", "Chino (Trad.)", "Ruso",
        "Árabe", "Holandés", "Polaco", "Sueco", "Turco", "Checo",
        "Danés", "Finlandés", "Noruego", "Ucraniano", "Indonesio", "Hindi"
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnToggle = findViewById(R.id.btnToggle);
        tvStatus = findViewById(R.id.tvStatus);
        spinnerSourceLang = findViewById(R.id.spinnerSourceLang);
        seekBarFontSize = findViewById(R.id.seekBarFontSize);
        tvFontSizeLabel = findViewById(R.id.tvFontSizeLabel);
        switchAutoDetect = findViewById(R.id.switchAutoDetect);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, LANG_NAMES);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSourceLang.setAdapter(adapter);

        seekBarFontSize.setMax(32);
        seekBarFontSize.setProgress(10);
        tvFontSizeLabel.setText("Tamaño texto: 18sp");
        seekBarFontSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int size = 12 + progress;
                tvFontSizeLabel.setText("Tamaño texto: " + size + "sp");
                if (serviceRunning) {
                    Intent i = new Intent(MainActivity.this, OverlayService.class);
                    i.setAction(OverlayService.ACTION_UPDATE_FONT);
                    i.putExtra("fontSize", size);
                    startService(i);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnToggle.setOnClickListener(v -> {
            if (!serviceRunning) {
                checkPermissionsAndStart();
            } else {
                stopOverlayService();
            }
        });

        updateUI();
    }

    private void checkPermissionsAndStart() {
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
            tvStatus.setText("⚠️ Activa el permiso 'Mostrar sobre otras apps' y vuelve aquí");
            return;
        }
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO},
                    REQUEST_RECORD_AUDIO);
            return;
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission("android.permission.READ_MEDIA_AUDIO")
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                    "android.permission.READ_MEDIA_AUDIO",
                    "android.permission.READ_MEDIA_VIDEO",
                    "android.permission.READ_MEDIA_IMAGES"
                }, REQUEST_STORAGE);
                return;
            }
        } else {
            if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                    android.Manifest.permission.READ_EXTERNAL_STORAGE
                }, REQUEST_STORAGE);
                return;
            }
        }
        startOverlayService();
    }

    private void startOverlayService() {
        int selectedPos = spinnerSourceLang.getSelectedItemPosition();
        String langCode = LANGUAGES[selectedPos];
        int fontSize = 12 + seekBarFontSize.getProgress();

        Intent serviceIntent = new Intent(this, OverlayService.class);
        serviceIntent.setAction(OverlayService.ACTION_START);
        serviceIntent.putExtra("sourceLang", langCode);
        serviceIntent.putExtra("fontSize", fontSize);
        startForegroundService(serviceIntent);

        serviceRunning = true;
        updateUI();
        moveTaskToBack(true);
    }

    private void stopOverlayService() {
        Intent serviceIntent = new Intent(this, OverlayService.class);
        serviceIntent.setAction(OverlayService.ACTION_STOP);
        startService(serviceIntent);
        serviceRunning = false;
        updateUI();
    }

    private void updateUI() {
        if (serviceRunning) {
            btnToggle.setText("⏹ Detener subtítulos");
            btnToggle.setBackgroundColor(0xFFE53935);
            tvStatus.setText("🎙️ Escuchando y traduciendo al español...");
        } else {
            btnToggle.setText("▶ Iniciar subtítulos");
            btnToggle.setBackgroundColor(0xFF43A047);
            tvStatus.setText("Listo para iniciar");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            if (Settings.canDrawOverlays(this)) {
                checkPermissionsAndStart();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                startOverlayService();
            } else {
                tvStatus.setText("⚠️ Se necesita permiso de micrófono");
            }
        }
        if (requestCode == REQUEST_STORAGE) {
            startOverlayService();
        }
    }
}
