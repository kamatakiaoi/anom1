package com.anonymous.chat.ui.auth;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
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
            SocketManager.getInstance().connect(prefs.getServerBaseUrl());
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
            prefs.setAuthKey(key);
            SocketManager.getInstance().authKey(key);
        });

        binding.btnAuthRegister.setOnClickListener(v -> {
            String key = binding.etAuthKeyInput.getText().toString().trim();
            if (key.length() < 4) {
                showError("Key must be at least 4 characters");
                return;
            }
            hideError();
            prefs.setAuthKey(key);
            SocketManager.getInstance().createKey(key);
        });

        binding.btnToggleRecover.setOnClickListener(v -> {
            isRecoverFormOpen = !isRecoverFormOpen;
            binding.panelRecoverForm.setVisibility(isRecoverFormOpen ? View.VISIBLE : View.GONE);
        });

        binding.btnAuthRecover.setOnClickListener(v -> {
            String recoveryKey = binding.etRecoveryKeyInput.getText().toString().trim();
            if (recoveryKey.isEmpty()) {
                showError("Please enter your recovery key");
                return;
            }
            hideError();
            SocketManager.getInstance().recoverKey(recoveryKey);
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
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
            finish();
        });
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
        String savedKey = prefs.getAuthKey();
        if (savedKey != null && !savedKey.isEmpty()) {
            SocketManager.getInstance().authKey(savedKey);
        }
    }

    @Override
    public void onDisconnected() {
        showError("Disconnected from server");
    }

    @Override
    public void onConnectionError(String error) {
        showError("Connection error: " + (error != null ? error : "Could not reach server"));
    }

    @Override public void onPingUpdated(long latencyMs) {}
    @Override public void onStatsUpdated(ServerStats stats) {}

    // Socket Auth Listener
    @Override
    public void onAuthError(String message) {
        showError(message != null ? message : "Authentication failed");
    }

    @Override
    public void onKeyCreated(String recoveryKey) {
        prefs.setRecoveryKey(recoveryKey);
        binding.tvRecoveryKeyDisplay.setText(recoveryKey);
        binding.panelRecoveryDisplay.setVisibility(View.VISIBLE);
        Toast.makeText(this, "Registration successful!", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onKeyRecovered(String key) {
        prefs.setAuthKey(key);
        binding.etAuthKeyInput.setText(key);
        Toast.makeText(this, "Key recovered! Logging in...", Toast.LENGTH_SHORT).show();
        SocketManager.getInstance().authKey(key);
    }

    @Override
    public void onProfileLoaded(UserProfile profile) {
        // Successfully authenticated!
        if (binding.panelRecoveryDisplay.getVisibility() != View.VISIBLE) {
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
            finish();
        }
    }

    @Override public void onNameChanged(String newName) {}
    @Override public void onAvatarChanged(String newAvatarUrl) {}

    @Override
    protected void onDestroy() {
        super.onDestroy();
        SocketManager.getInstance().removeConnectionListener(this);
        SocketManager.getInstance().removeAuthListener(this);
        SocketManager.getInstance().removeProfileListener(this);
    }
}
