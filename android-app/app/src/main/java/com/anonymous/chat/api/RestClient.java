package com.anonymous.chat.api;

import android.os.Handler;
import android.os.Looper;

import com.anonymous.chat.models.PatchNote;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class RestClient {
    private static final OkHttpClient client = new OkHttpClient();
    private static final Gson gson = new Gson();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface PatchNotesCallback {
        void onSuccess(List<PatchNote> patchNotes);
        void onError(String error);
    }

    public static void fetchPatchNotes(String serverBaseUrl, PatchNotesCallback callback) {
        String url = serverBaseUrl + (serverBaseUrl.endsWith("/") ? "" : "/") + "patch_notes.json?_=" + System.currentTimeMillis();
        Request request = new Request.Builder()
                .url(url)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> {
                    if (callback != null) callback.onError(e.getMessage());
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    mainHandler.post(() -> {
                        if (callback != null) callback.onError("Failed to load patch notes (HTTP " + response.code() + ")");
                    });
                    return;
                }
                try {
                    String json = response.body().string();
                    List<PatchNote> list = gson.fromJson(json, new TypeToken<List<PatchNote>>(){}.getType());
                    if (list == null) list = new ArrayList<>();
                    List<PatchNote> finalList = list;
                    mainHandler.post(() -> {
                        if (callback != null) callback.onSuccess(finalList);
                    });
                } catch (Exception e) {
                    mainHandler.post(() -> {
                        if (callback != null) callback.onError("JSON parsing error: " + e.getMessage());
                    });
                }
            }
        });
    }
}
