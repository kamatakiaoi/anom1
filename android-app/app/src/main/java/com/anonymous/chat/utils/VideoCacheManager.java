package com.anonymous.chat.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class VideoCacheManager {
    private static final String TAG = "VideoCacheManager";
    private static final long MAX_CACHE_SIZE_BYTES = 150L * 1024L * 1024L; // 150 MB
    private static final long PRUNE_TARGET_BYTES = 90L * 1024L * 1024L;   // 90 MB

    private static VideoCacheManager instance;

    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final OkHttpClient httpClient;

    private final Map<String, List<VideoCallback>> activeDownloads = new HashMap<>();

    public interface VideoCallback {
        void onReady(File file);
        void onProgress(int percent);
        void onError(Exception e);
    }

    private VideoCacheManager() {
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    public static synchronized VideoCacheManager getInstance() {
        if (instance == null) {
            instance = new VideoCacheManager();
        }
        return instance;
    }

    public File getCacheDir(Context context) {
        File dir = new File(context.getCacheDir(), "video_cache");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public String hashUrl(String url) {
        if (url == null) return "unknown";
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(url.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(url.hashCode());
        }
    }

    public File getCachedFile(Context context, String videoUrl) {
        if (context == null || videoUrl == null || videoUrl.trim().isEmpty()) return null;
        File dir = getCacheDir(context);
        String key = hashUrl(videoUrl);
        File file = new File(dir, key + ".mp4");
        if (file.exists() && file.isFile() && file.length() > 0) {
            file.setLastModified(System.currentTimeMillis());
            return file;
        }
        return null;
    }

    public void preload(Context context, String videoUrl) {
        // Disabled heavy background downloading to conserve user bandwidth and prevent thread starvation
    }

    public void getVideoFile(Context context, String videoUrl, VideoCallback callback) {
        if (context == null || videoUrl == null || videoUrl.trim().isEmpty()) {
            if (callback != null) {
                callback.onError(new IllegalArgumentException("Invalid video URL or context"));
            }
            return;
        }

        final Context appCtx = context.getApplicationContext();

        // 1. Check if already cached
        File cached = getCachedFile(appCtx, videoUrl);
        if (cached != null) {
            if (callback != null) {
                mainHandler.post(() -> callback.onReady(cached));
            }
            return;
        }

        // 2. Handle Data URI (Base64)
        if (videoUrl.startsWith("data:video/")) {
            executor.execute(() -> {
                try {
                    File dir = getCacheDir(appCtx);
                    String key = hashUrl(videoUrl);
                    File target = new File(dir, key + ".mp4");
                    if (!target.exists() || target.length() == 0) {
                        int comma = videoUrl.indexOf(",");
                        String base64Data = (comma != -1) ? videoUrl.substring(comma + 1) : videoUrl;
                        byte[] bytes = Base64.decode(base64Data, Base64.DEFAULT);
                        try (FileOutputStream fos = new FileOutputStream(target)) {
                            fos.write(bytes);
                            fos.flush();
                        }
                    }
                    if (callback != null) {
                        mainHandler.post(() -> callback.onReady(target));
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to decode base64 video", e);
                    if (callback != null) {
                        mainHandler.post(() -> callback.onError(e));
                    }
                }
            });
            return;
        }

        // 3. Handle Network Video URL
        String serverUrl = PreferenceManager.getInstance(appCtx).getServerBaseUrl();
        final String fullUrl = ImageUtils.getFullMediaUrl(serverUrl, videoUrl);
        final String key = hashUrl(videoUrl);

        synchronized (activeDownloads) {
            if (activeDownloads.containsKey(key)) {
                if (callback != null) {
                    activeDownloads.get(key).add(callback);
                }
                return;
            }

            List<VideoCallback> list = new ArrayList<>();
            if (callback != null) list.add(callback);
            activeDownloads.put(key, list);
        }

        executor.execute(() -> {
            File dir = getCacheDir(appCtx);
            File tmpFile = new File(dir, key + ".mp4.tmp");
            File targetFile = new File(dir, key + ".mp4");

            try {
                Request request = new Request.Builder()
                        .url(fullUrl)
                        .header("Accept-Encoding", "identity")
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        throw new Exception("HTTP error code: " + response.code());
                    }

                    ResponseBody body = response.body();
                    if (body == null) throw new Exception("Empty response body");

                    long contentLength = body.contentLength();
                    long totalRead = 0;
                    byte[] buffer = new byte[32768];
                    int read;

                    int lastReportedPercent = -1;

                    try (InputStream is = body.byteStream();
                         FileOutputStream fos = new FileOutputStream(tmpFile)) {
                        while ((read = is.read(buffer)) != -1) {
                            fos.write(buffer, 0, read);
                            totalRead += read;

                            if (contentLength > 0) {
                                int percent = (int) ((100L * totalRead) / contentLength);
                                if (percent >= lastReportedPercent + 5) {
                                    lastReportedPercent = percent;
                                    dispatchProgress(key, percent);
                                }
                            }
                        }
                        fos.flush();
                    }

                    if (targetFile.exists()) targetFile.delete();
                    if (!tmpFile.renameTo(targetFile)) {
                        throw new Exception("Failed to rename temp video cache file");
                    }

                    targetFile.setLastModified(System.currentTimeMillis());
                    pruneCacheIfNeeded(appCtx);

                    dispatchReady(key, targetFile);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed downloading video: " + fullUrl, e);
                if (tmpFile.exists()) tmpFile.delete();
                dispatchError(key, e);
            }
        });
    }

    private void dispatchProgress(String key, int percent) {
        mainHandler.post(() -> {
            List<VideoCallback> list;
            synchronized (activeDownloads) {
                list = activeDownloads.get(key);
                if (list != null) list = new ArrayList<>(list);
            }
            if (list != null) {
                for (VideoCallback cb : list) {
                    try { cb.onProgress(percent); } catch (Exception ignored) {}
                }
            }
        });
    }

    private void dispatchReady(String key, File file) {
        mainHandler.post(() -> {
            List<VideoCallback> list;
            synchronized (activeDownloads) {
                list = activeDownloads.remove(key);
            }
            if (list != null) {
                for (VideoCallback cb : list) {
                    try { cb.onReady(file); } catch (Exception ignored) {}
                }
            }
        });
    }

    private void dispatchError(String key, Exception e) {
        mainHandler.post(() -> {
            List<VideoCallback> list;
            synchronized (activeDownloads) {
                list = activeDownloads.remove(key);
            }
            if (list != null) {
                for (VideoCallback cb : list) {
                    try { cb.onError(e); } catch (Exception ignored) {}
                }
            }
        });
    }

    private void pruneCacheIfNeeded(Context context) {
        executor.execute(() -> {
            try {
                File dir = getCacheDir(context);
                File[] files = dir.listFiles();
                if (files == null || files.length == 0) return;

                long totalSize = 0;
                List<File> fileList = new ArrayList<>();
                for (File f : files) {
                    if (f.getName().endsWith(".tmp")) {
                        // Delete stale temp files older than 30 mins
                        if (System.currentTimeMillis() - f.lastModified() > 30 * 60 * 1000) {
                            f.delete();
                        }
                    } else if (f.isFile()) {
                        totalSize += f.length();
                        fileList.add(f);
                    }
                }

                if (totalSize > MAX_CACHE_SIZE_BYTES) {
                    Collections.sort(fileList, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
                    for (File f : fileList) {
                        if (totalSize <= PRUNE_TARGET_BYTES) break;
                        long len = f.length();
                        if (f.delete()) {
                            totalSize -= len;
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Cache pruning error", e);
            }
        });
    }
}
