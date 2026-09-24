package com.anonymous.chat.utils;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Random;

public class PreferenceManager {
    private static final String PREF_NAME = "anonymous_chat_prefs";
    private static final String KEY_SERVER_HOST = "server_host";
    private static final String KEY_SERVER_PORT = "server_port";
    private static final String KEY_AUTH_KEY = "auth_key";
    private static final String KEY_RECOVERY_KEY = "recovery_key";
    private static final String KEY_TIMEZONE = "pref_timezone"; // "vn" or "utc"
    private static final String KEY_SOUND_ENABLED = "pref_sound_enabled";
    private static final String KEY_DEVICE_MAC = "chat_device_mac";

    public static final String DEFAULT_SERVER_HOST = "snow.pikamc.vn";
    public static final int DEFAULT_SERVER_PORT = 25222;

    private static PreferenceManager instance;
    private final SharedPreferences prefs;

    private PreferenceManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized PreferenceManager getInstance(Context context) {
        if (instance == null) {
            instance = new PreferenceManager(context);
        }
        return instance;
    }

    public String getDeviceMac() {
        String mac = prefs.getString(KEY_DEVICE_MAC, null);
        if (mac == null || mac.isEmpty()) {
            Random rand = new Random();
            StringBuilder sb = new StringBuilder("MAC");
            for (int i = 0; i < 6; i++) {
                sb.append(i == 0 ? "-" : ":");
                sb.append(String.format("%02X", rand.nextInt(256)));
            }
            mac = sb.toString();
            prefs.edit().putString(KEY_DEVICE_MAC, mac).apply();
        }
        return mac;
    }

    public String getServerHost() {
        return prefs.getString(KEY_SERVER_HOST, DEFAULT_SERVER_HOST);
    }

    public void setServerHost(String host) {
        prefs.edit().putString(KEY_SERVER_HOST, host).apply();
    }

    public int getServerPort() {
        return prefs.getInt(KEY_SERVER_PORT, DEFAULT_SERVER_PORT);
    }

    public void setServerPort(int port) {
        prefs.edit().putInt(KEY_SERVER_PORT, port).apply();
    }

    public String getAuthKey() {
        return prefs.getString(KEY_AUTH_KEY, null);
    }

    public void setAuthKey(String authKey) {
        prefs.edit().putString(KEY_AUTH_KEY, authKey).apply();
    }

    public String getRecoveryKey() {
        return prefs.getString(KEY_RECOVERY_KEY, null);
    }

    public void setRecoveryKey(String recoveryKey) {
        prefs.edit().putString(KEY_RECOVERY_KEY, recoveryKey).apply();
    }

    public String getServerBaseUrl() {
        String host = getServerHost().trim();
        int port = getServerPort();
        if (host.isEmpty()) host = DEFAULT_SERVER_HOST;
        if (port <= 0) port = DEFAULT_SERVER_PORT;

        if (!host.startsWith("http://") && !host.startsWith("https://")) {
            host = "http://" + host;
        }

        // If port is already present in host string (e.g. http://domain:25222), don't append
        String afterScheme = host.substring(host.indexOf("://") + 3);
        if (!afterScheme.contains(":")) {
            return host + ":" + port;
        }
        return host;
    }

    public String getTimezone() {
        return prefs.getString(KEY_TIMEZONE, "vn");
    }

    public void setTimezone(String tz) {
        prefs.edit().putString(KEY_TIMEZONE, tz).apply();
    }

    public boolean isSoundEnabled() {
        return prefs.getBoolean(KEY_SOUND_ENABLED, true);
    }

    public void setSoundEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SOUND_ENABLED, enabled).apply();
    }

    private static final String KEY_MY_UID = "my_profile_uid";
    private static final String KEY_MY_NAME = "my_profile_name";
    private static final String KEY_MY_COLOR = "my_profile_color";
    private static final String KEY_MY_AVATAR = "my_profile_avatar";
    private static final String KEY_MY_ID = "my_profile_id";

    public void saveMyProfile(com.anonymous.chat.models.UserProfile profile) {
        if (profile == null) return;
        SharedPreferences.Editor ed = prefs.edit();
        if (profile.getUid() != null) ed.putString(KEY_MY_UID, profile.getUid());
        if (profile.getName() != null) ed.putString(KEY_MY_NAME, profile.getName());
        if (profile.getColor() != null) ed.putString(KEY_MY_COLOR, profile.getColor());
        if (profile.getAvatar() != null) ed.putString(KEY_MY_AVATAR, profile.getAvatar());
        if (profile.getId() != null) ed.putString(KEY_MY_ID, profile.getId());
        ed.apply();
    }

    public String getMyUid() { return prefs.getString(KEY_MY_UID, null); }
    public String getMyName() { return prefs.getString(KEY_MY_NAME, null); }
    public String getMyColor() { return prefs.getString(KEY_MY_COLOR, null); }
    public String getMyAvatar() { return prefs.getString(KEY_MY_AVATAR, null); }
    public String getMyId() { return prefs.getString(KEY_MY_ID, null); }
}
