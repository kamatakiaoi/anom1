package com.anonymous.chat.ui.connect;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.anonymous.chat.R;
import com.anonymous.chat.api.SocketManager;
import com.anonymous.chat.databinding.ActivityServerConnectBinding;
import com.anonymous.chat.models.ServerStats;
import com.anonymous.chat.ui.main.MainActivity;
import com.anonymous.chat.utils.PreferenceManager;

public class ServerConnectActivity extends AppCompatActivity implements SocketManager.ConnectionListener {

    private ActivityServerConnectBinding binding;
    private PreferenceManager prefs;
    private boolean isConnecting = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityServerConnectBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        prefs = PreferenceManager.getInstance(this);

        binding.etServerHost.setText(prefs.getServerHost());
        binding.etServerPort.setText(String.valueOf(prefs.getServerPort()));

        binding.btnConnectServer.setOnClickListener(v -> handleConnect());

        SocketManager.getInstance().addConnectionListener(this);

        // Auto connect on launch with saved configuration
        handleConnect();
    }

    private void handleConnect() {
        String host = binding.etServerHost.getText().toString().trim();
        String portStr = binding.etServerPort.getText().toString().trim();

        if (host.isEmpty()) {
            Toast.makeText(this, "Please enter server IP or Host", Toast.LENGTH_SHORT).show();
            return;
        }

        int port = 25643;
        if (!portStr.isEmpty()) {
            try {
                port = Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid port number", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        prefs.setServerHost(host);
        prefs.setServerPort(port);

        isConnecting = true;
        binding.btnConnectServer.setEnabled(false);
        binding.tvStatusText.setText("Connecting...");
        binding.dotStatus.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#EAB308")));

        String serverUrl = prefs.getServerBaseUrl();
        SocketManager.getInstance().connect(serverUrl);
    }

    @Override
    public void onConnected() {
        isConnecting = false;
        binding.btnConnectServer.setEnabled(true);
        binding.tvStatusText.setText("Connected");
        binding.dotStatus.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#22C55E")));

        // Transition to Main Lobby Activity
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    @Override
    public void onDisconnected() {
        isConnecting = false;
        binding.btnConnectServer.setEnabled(true);
        binding.tvStatusText.setText("Disconnected");
        binding.dotStatus.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#EF4444")));
        binding.tvPingText.setText("— ms");
    }

    @Override
    public void onConnectionError(String error) {
        isConnecting = false;
        binding.btnConnectServer.setEnabled(true);
        binding.tvStatusText.setText("Error: " + (error != null ? error : "Failed to connect"));
        binding.dotStatus.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#EF4444")));
    }

    @Override
    public void onPingUpdated(long latencyMs) {
        binding.tvPingText.setText(latencyMs + " ms");
    }

    @Override
    public void onStatsUpdated(ServerStats stats) {
        // Updated server stats
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        SocketManager.getInstance().removeConnectionListener(this);
    }
}
