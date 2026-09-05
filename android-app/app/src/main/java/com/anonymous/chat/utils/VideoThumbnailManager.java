package com.anonymous.chat.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.LruCache;
import android.widget.ImageView;

import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;

import com.anonymous.chat.R;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.ref.WeakReference;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VideoThumbnailManager {
    private static final String TAG = "VideoThumbnailManager";
    private static final String THUMB_DIR_NAME = "video_thumbnails";
    private static final int MAX_MEMORY_CACHE_BYTES = (int) (Runtime.getRuntime().maxMemory() / 8); // 1/8th of heap

    private static volatile VideoThumbnailManager instance;

    private final LruCache<String, Bitmap> memoryCache;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Set<String> activeExtractingUrls = new HashSet<>();

    private VideoThumbnailManager() {
        memoryCache = new LruCache<String, Bitmap>(Math.max(1024 * 1024 * 8, MAX_MEMORY_CACHE_BYTES)) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount();
            }
        };
    }

    public static VideoThumbnailManager getInstance() {
        if (instance == null) {
            synchronized (VideoThumbnailManager.class) {
                if (instance == null) {
                    instance = new VideoThumbnailManager();
                }
            }
        }
        return instance;
    }

    public void loadThumbnail(Context context, String videoUrl, ImageView target, int cornerRadiusDp) {
        if (context == null || target == null || videoUrl == null || videoUrl.trim().isEmpty()) {
            return;
        }

        final String cleanUrl = videoUrl.trim();
        target.setTag(R.id.tag_video_thumb_url, cleanUrl);

        // 1. Check in-memory LRU cache (0ms instant display)
        Bitmap cachedBitmap = memoryCache.get(cleanUrl);
        if (cachedBitmap != null && !cachedBitmap.isRecycled()) {
            applyBitmapToView(context, target, cachedBitmap, cornerRadiusDp);
            return;
        }

        // 2. Set default card placeholder while extracting/loading
        target.setImageResource(R.drawable.bg_card_topic);
        target.setScaleType(ImageView.ScaleType.CENTER_CROP);

        final Context appContext = context.getApplicationContext();
        final WeakReference<ImageView> targetRef = new WeakReference<>(target);

        executor.execute(() -> {
            try {
                // 3. Check persistent disk cache (<5ms)
                File diskFile = getDiskCacheFile(appContext, cleanUrl);
                if (diskFile != null && diskFile.exists() && diskFile.length() > 0) {
                    Bitmap diskBitmap = decodeSampledBitmap(diskFile.getAbsolutePath(), 640, 360);
                    if (diskBitmap != null) {
                        memoryCache.put(cleanUrl, diskBitmap);
                        postSuccess(cleanUrl, targetRef, diskBitmap, cornerRadiusDp);
                        return;
                    }
                }

                // 4. Concurrency check: prevent duplicated extractions for the same video URL
                synchronized (activeExtractingUrls) {
                    if (activeExtractingUrls.contains(cleanUrl)) {
                        return;
                    }
                    activeExtractingUrls.add(cleanUrl);
                }

                Bitmap extractedBitmap = null;
                try {
                    // Check if local downloaded video file already exists
                    File localCached = VideoCacheManager.getInstance().getCachedFile(appContext, cleanUrl);
                    if (localCached != null && localCached.exists() && localCached.length() > 0) {
                        extractedBitmap = extractFrameFromFile(localCached);
                    }

                    // If not on disk, extract frame directly over HTTP Range headers (<100ms)
                    if (extractedBitmap == null) {
                        String serverUrl = PreferenceManager.getInstance(appContext).getServerBaseUrl();
                        String fullUrl = ImageUtils.getFullMediaUrl(serverUrl, cleanUrl);
                        extractedBitmap = extractFrameFromNetwork(fullUrl);
                    }

                    if (extractedBitmap != null) {
                        // Downscale frame if excessively large to preserve RAM
                        Bitmap downscaled = downscaleIfNeeded(extractedBitmap, 640);
                        if (downscaled != extractedBitmap) {
                            extractedBitmap.recycle();
                            extractedBitmap = downscaled;
                        }

                        // Save to disk cache for future instant scrolls
                        if (diskFile != null) {
                            saveBitmapToDisk(extractedBitmap, diskFile);
                        }

                        memoryCache.put(cleanUrl, extractedBitmap);
                        final Bitmap finalBitmap = extractedBitmap;
                        postSuccess(cleanUrl, targetRef, finalBitmap, cornerRadiusDp);
                    }
                } finally {
                    synchronized (activeExtractingUrls) {
                        activeExtractingUrls.remove(cleanUrl);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed extracting video thumbnail for " + cleanUrl, e);
            }
        });
    }

    private void postSuccess(String cleanUrl, WeakReference<ImageView> targetRef, Bitmap bitmap, int cornerRadiusDp) {
        mainHandler.post(() -> {
            ImageView view = targetRef.get();
            if (view != null && cleanUrl.equals(view.getTag(R.id.tag_video_thumb_url))) {
                applyBitmapToView(view.getContext(), view, bitmap, cornerRadiusDp);
            }
        });
    }

    private void applyBitmapToView(Context context, ImageView target, Bitmap bitmap, int cornerRadiusDp) {
        if (cornerRadiusDp > 0) {
            int radiusPx = (int) (cornerRadiusDp * context.getResources().getDisplayMetrics().density);
            RoundedBitmapDrawable rbd = RoundedBitmapDrawableFactory.create(context.getResources(), bitmap);
            rbd.setCornerRadius(radiusPx);
            target.setImageDrawable(rbd);
        } else {
            target.setImageBitmap(bitmap);
        }
        target.setScaleType(ImageView.ScaleType.CENTER_CROP);
    }

    private Bitmap extractFrameFromFile(File file) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(file.getAbsolutePath());
            Bitmap frame = retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if (frame == null) {
                frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            }
            if (frame == null) {
                frame = retriever.getFrameAtTime();
            }
            return frame;
        } catch (Exception e) {
            Log.w(TAG, "extractFrameFromFile failed: " + e.getMessage());
            return null;
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {}
        }
    }

    private Bitmap extractFrameFromNetwork(String fullUrl) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("User-Agent", "AnonymousChat-Android/3.6.14");
            headers.put("Accept-Ranges", "bytes");
            retriever.setDataSource(fullUrl, headers);

            Bitmap frame = retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if (frame == null) {
                frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            }
            if (frame == null) {
                frame = retriever.getFrameAtTime();
            }
            return frame;
        } catch (Exception e) {
            Log.w(TAG, "extractFrameFromNetwork failed for " + fullUrl + ": " + e.getMessage());
            return null;
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {}
        }
    }

    private Bitmap downscaleIfNeeded(Bitmap source, int maxDim) {
        if (source == null) return null;
        int w = source.getWidth();
        int h = source.getHeight();
        if (w <= maxDim && h <= maxDim) {
            return source;
        }
        float ratio = Math.min((float) maxDim / w, (float) maxDim / h);
        int targetW = Math.max(1, Math.round(w * ratio));
        int targetH = Math.max(1, Math.round(h * ratio));
        return Bitmap.createScaledBitmap(source, targetW, targetH, true);
    }

    private void saveBitmapToDisk(Bitmap bitmap, File file) {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            File tmp = new File(file.getAbsolutePath() + ".tmp");
            try (FileOutputStream fos = new FileOutputStream(tmp)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos);
                fos.flush();
            }
            if (tmp.exists()) {
                tmp.renameTo(file);
            }
        } catch (Exception e) {
            Log.w(TAG, "saveBitmapToDisk failed", e);
        }
    }

    private Bitmap decodeSampledBitmap(String path, int reqWidth, int reqHeight) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, options);

            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
            options.inJustDecodeBounds = false;
            options.inPreferredConfig = Bitmap.Config.RGB_565; // Saves 50% memory!
            return BitmapFactory.decodeFile(path, options);
        } catch (Exception e) {
            return null;
        }
    }

    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    private File getDiskCacheFile(Context context, String url) {
        try {
            File cacheDir = new File(context.getCacheDir(), THUMB_DIR_NAME);
            if (!cacheDir.exists()) cacheDir.mkdirs();
            String hash = hashString(url);
            return new File(cacheDir, hash + ".jpg");
        } catch (Exception e) {
            return null;
        }
    }

    private String hashString(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(input.getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }

    public void clearCache() {
        memoryCache.evictAll();
    }
}
