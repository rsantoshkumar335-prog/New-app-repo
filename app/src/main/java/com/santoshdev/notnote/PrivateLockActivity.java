package com.santoshdev.notnote;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class PrivateLockActivity extends Activity {

    private ImageView btnBackLock;
    private LinearLayout btnUnlockFingerprint;
    private EditText etVaultPin;
    private TextView btnUnlockPin, tvVaultSubtitle;
    private CancellationSignal cancellationSignal;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_private_lock);

        prefs = getSharedPreferences("NotNotePrefs", MODE_PRIVATE);

        btnBackLock = findViewById(R.id.btn_back_lock);
        btnUnlockFingerprint = findViewById(R.id.btn_unlock_fingerprint);
        etVaultPin = findViewById(R.id.et_vault_pin);
        btnUnlockPin = findViewById(R.id.btn_unlock_pin);
        tvVaultSubtitle = findViewById(R.id.tv_vault_subtitle);

        btnBackLock.setOnClickListener(v -> finish());

        String savedPin = prefs.getString("vault_pin", "");
        if (savedPin.isEmpty()) {
            if (tvVaultSubtitle != null) {
                tvVaultSubtitle.setText("Set a 4-digit Vault PIN or use Biometric Unlock");
            }
            if (etVaultPin != null) {
                etVaultPin.setHint("Create 4-digit PIN (or enter 0000)");
            }
            if (btnUnlockPin != null) {
                btnUnlockPin.setText("Set & Open");
            }
        }

        if (btnUnlockPin != null) {
            btnUnlockPin.setOnClickListener(v -> {
                String inputPin = etVaultPin != null ? etVaultPin.getText().toString().trim() : "";
                if (inputPin.length() < 4) {
                    Toast.makeText(this, "Please enter at least 4 digits", Toast.LENGTH_SHORT).show();
                    return;
                }
                String existingPin = prefs.getString("vault_pin", "");
                if (existingPin.isEmpty()) {
                    prefs.edit().putString("vault_pin", inputPin).apply();
                    Toast.makeText(this, "Vault PIN saved!", Toast.LENGTH_SHORT).show();
                    openPrivateVault();
                } else if (existingPin.equals(inputPin)) {
                    openPrivateVault();
                } else {
                    Toast.makeText(this, "Incorrect Vault PIN", Toast.LENGTH_SHORT).show();
                }
            });
        }

        btnUnlockFingerprint.setOnClickListener(v -> showBiometricPrompt());
    }

    private void openPrivateVault() {
        startActivity(new Intent(PrivateLockActivity.this, PrivateNotesActivity.class));
        finish();
    }

    private void showBiometricPrompt() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                BiometricPrompt.Builder builder = new BiometricPrompt.Builder(this)
                        .setTitle("Unlock Private Vault")
                        .setSubtitle("Use your fingerprint to access hidden notes")
                        .setNegativeButton("Use PIN", getMainExecutor(), (dialogInterface, i) -> {
                            if (etVaultPin != null) etVaultPin.requestFocus();
                        });

                BiometricPrompt biometricPrompt = builder.build();
                cancellationSignal = new CancellationSignal();

                biometricPrompt.authenticate(cancellationSignal, getMainExecutor(), new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationError(int errorCode, CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        Toast.makeText(PrivateLockActivity.this, "Use your 4-digit Vault PIN above to unlock", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        openPrivateVault();
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed();
                        Toast.makeText(PrivateLockActivity.this, "Authentication failed. Try again or use PIN.", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                Toast.makeText(this, "Biometrics unavailable — please use the Vault PIN box above.", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Please use the Vault PIN box above to unlock.", Toast.LENGTH_LONG).show();
        }
    }
}
