package com.anonymous.chat.ui.auth;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.anonymous.chat.api.SocketManager;
import com.anonymous.chat.databinding.ActivityAuthBinding;
import com.anonymous.chat.models.ServerStats;
import com.anonymous.chat.models.UserProfile;
import com.anonymous.chat.ui.main.MainActivity;
import com.anonymous.chat.utils.PreferenceManager;

public class AuthActivity extends AppCompatActivity implements
        SocketManager.ConnectionListener,
        SocketManager.AuthListener,
        SocketManager.ProfileListener {

    private ActivityAuthBinding binding;
    private PreferenceManager prefs;
    private boolean isServerSettingsOpen = false;
    private boolean isRecoverFormOpen = false;
    private boolean isRegistering = false;

    private final Handler timeoutHandler = new Handler(Looper.getMainLooper());
    private Runnable timeoutRunnable;

    private ActivityResultLauncher<String> notifPermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAuthBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        prefs = PreferenceManager.getInstance(this);

        setupNotificationPermission();
        setupServerSettingsUI();
        setupAuthButtons();

        SocketManager.getInstance().addConnectionListener(this);
        SocketManager.getInstance().addAuthListener(this);
        SocketManager.getInstance().addProfileListener(this);

        // Populate saved key if any
        String savedKey = prefs.getAuthKey();
        if (savedKey != null && !savedKey.isEmpty()) {
            binding.etAuthKeyInput.setText(savedKey);
        }

        // Connect socket
        String serverUrl = prefs.getServerBaseUrl();
        if (!SocketManager.getInstance().isConnected()) {
            SocketManager.getInstance().connect(serverUrl);
        } else if (savedKey != null && !savedKey.isEmpty()) {
            setLoading(true, "Authenticating saved key...");
            SocketManager.getInstance().authKey(savedKey);
        }
    }

    private void setupNotificationPermission() {
        notifPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    // Notification permission status
                }
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

    private void setupServerSettingsUI() {
        binding.tvServerConfigLabel.setText("Server: " + prefs.getServerHost() + ":" + prefs.getServerPort());
        binding.etAuthServerHost.setText(prefs.getServerHost());
        binding.etAuthServerPort.setText(String.valueOf(prefs.getServerPort()));

        binding.btnToggleServerSettings.setOnClickListener(v -> {
            isServerSettingsOpen = !isServerSettingsOpen;
            binding.panelServerConfig.setVisibility(isServerSettingsOpen ? View.VISIBLE : View.GONE);
        });

        binding.btnSaveServerConfig.setOnClickListener(v -> {
            String host = binding.etAuthServerHost.getText().toString().trim();
            String portStr = binding.etAuthServerPort.getText().toString().trim();
            if (host.isEmpty()) {
                Toast.makeText(this, "Please enter server host", Toast.LENGTH_SHORT).show();
                return;
            }
            int port = PreferenceManager.DEFAULT_SERVER_PORT;
            try {
                if (!portStr.isEmpty()) port = Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid port", Toast.LENGTH_SHORT).show();
                return;
            }

            prefs.setServerHost(host);
            prefs.setServerPort(port);
            binding.tvServerConfigLabel.setText("Server: " + host + ":" + port);
            binding.panelServerConfig.setVisibility(View.GONE);
            isServerSettingsOpen = false;

            Toast.makeText(this, "Reconnecting...", Toast.LENGTH_SHORT).show();
            setLoading(true, "Connecting to " + host + ":" + port + "...");
            SocketManager.getInstance().forceReconnect(prefs.getServerBaseUrl());
        });
    }

    private void setupAuthButtons() {
        binding.btnAuthLogin.setOnClickListener(v -> {
            String key = binding.etAuthKeyInput.getText().toString().trim();
            if (key.isEmpty()) {
                showError("Please enter your private key");
                return;
            }
            hideError();
            isRegistering = false;
            setLoading(true, "Logging in...");
            prefs.setAuthKey(key);
            SocketManager.getInstance().authKey(key);
        });

        binding.etAuthKeyInput.setOnEditorActionListener((v, actionId, event) -> {
            binding.btnAuthLogin.performClick();
            return true;
        });

        binding.btnAuthRegister.setOnClickListener(v -> {
            String key = binding.etAuthKeyInput.getText().toString().trim();
            if (key.length() < 4) {
                showError("Key must be at least 4 characters");
                return;
            }
            hideError();
            isRegistering = true;
            setLoading(true, "Registering key...");
            SocketManager.getInstance().createKey(key);
        });

        binding.btnToggleRecover.setOnClickListener(v -> {
            isRecoverFormOpen = !isRecoverFormOpen;
            binding.panelRecoverForm.setVisibility(isRecoverFormOpen ? View.VISIBLE : View.GONE);
            binding.btnToggleRecover.setText(isRecoverFormOpen ? "Back to Login" : "Forgot your key? Recover it");
        });

        binding.btnAuthRecover.setOnClickListener(v -> {
            String recoveryKey = binding.etRecoveryKeyInput.getText().toString().trim();
            if (recoveryKey.isEmpty()) {
                showError("Please enter your recovery key");
                return;
            }
            hideError();
            isRegistering = false;
            setLoading(true, "Recovering key...");
            SocketManager.getInstance().recoverKey(recoveryKey);
        });

        binding.etRecoveryKeyInput.setOnEditorActionListener((v, actionId, event) -> {
            binding.btnAuthRecover.performClick();
            return true;
        });

        binding.tvRecoveryKeyDisplay.setOnClickListener(v -> {
            String recKey = binding.tvRecoveryKeyDisplay.getText().toString();
            if (!recKey.isEmpty()) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Recovery Key", recKey);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(this, "Recovery key copied to clipboard!", Toast.LENGTH_SHORT).show();
            }
        });

        binding.btnAuthContinue.setOnClickListener(v -> {
            isRegistering = false;
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
            finish();
        });
    }

    private void setLoading(boolean loading, String message) {
        if (isFinishing() || isDestroyed()) return;

        binding.panelAuthLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading && message != null) {
            binding.tvAuthLoadingText.setText(message);
        }

        binding.btnAuthLogin.setEnabled(!loading);
        binding.btnAuthLogin.setAlpha(loading ? 0.6f : 1.0f);

        binding.btnAuthRegister.setEnabled(!loading);
        binding.btnAuthRegister.setAlpha(loading ? 0.6f : 1.0f);

        binding.btnAuthRecover.setEnabled(!loading);
        binding.btnAuthRecover.setAlpha(loading ? 0.6f : 1.0f);

        binding.etAuthKeyInput.setEnabled(!loading);
        binding.etRecoveryKeyInput.setEnabled(!loading);

        if (loading) {
            timeoutHandler.removeCallbacks(timeoutRunnable);
            timeoutRunnable = () -> {
                setLoading(false, null);
                isRegistering = false;
                showError("Connection timed out. Server may be offline or unreachable. Please check your network or server settings.");
            };
            timeoutHandler.postDelayed(timeoutRunnable, 12000);
        } else {
            timeoutHandler.removeCallbacks(timeoutRunnable);
        }
    }

    private void showError(String message) {
        binding.tvAuthError.setVisibility(View.VISIBLE);
        binding.tvAuthError.setText(message);
    }

    private void hideError() {
        binding.tvAuthError.setVisibility(View.GONE);
    }

    // Socket Connection Listener
    @Override
    public void onConnected() {
        hideError();
        if (binding.panelAuthLoading.getVisibility() == View.VISIBLE) {
            binding.tvAuthLoadingText.setText("Connected. Authenticating...");
        }
    }

    @Override
    public void onDisconnected() {
        setLoading(false, null);
        showError("Disconnected from server");
    }

    @Override
    public void onConnectionError(String error) {
        setLoading(false, null);
        showError("Connection error: " + (error != null ? error : "Could not reach server"));
    }

    @Override public void onPingUpdated(long latencyMs) {}
    @Override public void onStatsUpdated(ServerStats stats) {}

    // Socket Auth Listener
    @Override
    public void onAuthError(String message) {
        setLoading(false, null);
        isRegistering = false;
        showError(message != null ? message : "Authentication failed");
    }

    @Override
    public void onKeyCreated(String recoveryKey) {
        setLoading(false, null);
        isRegistering = true;
        prefs.setRecoveryKey(recoveryKey);

        // Hide inputs and auth buttons to showcase recovery key (matching index.html)
        binding.etAuthKeyInput.setVisibility(View.GONE);
        binding.btnAuthLogin.setVisibility(View.GONE);
        binding.panelAuthDivider.setVisibility(View.GONE);
        binding.btnAuthRegister.setVisibility(View.GONE);
        binding.btnToggleRecover.setVisibility(View.GONE);
        binding.panelRecoverForm.setVisibility(View.GONE);

        binding.tvRecoveryKeyDisplay.setText(recoveryKey);
        binding.panelRecoveryDisplay.setVisibility(View.VISIBLE);
        Toast.makeText(this, "Registration successful!", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onKeyRecovered(String key) {
        setLoading(false, null);
        prefs.setAuthKey(key);
        binding.etAuthKeyInput.setText(key);
        Toast.makeText(this, "Key recovered! Logging in...", Toast.LENGTH_SHORT).show();
        setLoading(true, "Logging in...");
        SocketManager.getInstance().authKey(key);
    }

    @Override
    public void onProfileLoaded(UserProfile profile) {
        setLoading(false, null);
        // If currently in registration flow, hold on until user copies recovery key and taps Continue
        if (isRegistering || binding.panelRecoveryDisplay.getVisibility() == View.VISIBLE) {
            return;
        }

        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    @Override public void onNameChanged(String newName) {}
    @Override public void onAvatarChanged(String newAvatarUrl) {}

    @Override
    protected void onDestroy() {
        super.onDestroy();
        timeoutHandler.removeCallbacks(timeoutRunnable);
        SocketManager.getInstance().removeConnectionListener(this);
        SocketManager.getInstance().removeAuthListener(this);
        SocketManager.getInstance().removeProfileListener(this);
    }
}
