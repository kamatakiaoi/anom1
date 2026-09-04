package com.anonymous.chat.utils;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class ImageUtils {

    public static String getFileName(Context context, Uri uri) {
        String result = null;
        if (uri.getScheme() != null && uri.getScheme().equals("content")) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index >= 0) {
                        result = cursor.getString(index);
                    }
                }
            } catch (Exception ignored) {}
        }
        if (result == null) {
            result = uri.getPath();
            if (result != null) {
                int cut = result.lastIndexOf('/');
                if (cut != -1) result = result.substring(cut + 1);
            }
        }
        return result != null ? result : "file";
    }

    public static String uriToBase64(Context context, Uri uri, int maxDimension, int quality) {
        try {
            // First pass: decode bounds only for instant fast memory-efficient downsampling
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            try (InputStream is = context.getContentResolver().openInputStream(uri)) {
                if (is == null) return null;
                BitmapFactory.decodeStream(is, null, options);
            }

            int origWidth = options.outWidth;
            int origHeight = options.outHeight;
            if (origWidth <= 0 || origHeight <= 0) return null;

            int inSampleSize = 1;
            while ((origWidth / inSampleSize) > (maxDimension * 1.5) || (origHeight / inSampleSize) > (maxDimension * 1.5)) {
                inSampleSize *= 2;
            }

            options.inJustDecodeBounds = false;
            options.inSampleSize = inSampleSize;
            options.inPreferredConfig = Bitmap.Config.RGB_565; // Highly memory & speed optimized

            Bitmap bitmap;
            try (InputStream is = context.getContentResolver().openInputStream(uri)) {
                if (is == null) return null;
                bitmap = BitmapFactory.decodeStream(is, null, options);
            }
            if (bitmap == null) return null;

            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            if (width > maxDimension || height > maxDimension) {
                float ratio = Math.min((float) maxDimension / width, (float) maxDimension / height);
                int newWidth = Math.max(1, Math.round(ratio * width));
                int newHeight = Math.max(1, Math.round(ratio * height));
                Bitmap scaled = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
                if (scaled != bitmap) {
                    bitmap.recycle();
                    bitmap = scaled;
                }
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream(65536);
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);
            bitmap.recycle();

            byte[] bytes = baos.toByteArray();
            return "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String videoUriToBase64(Context context, Uri uri) {
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is == null) return null;

            ByteArrayOutputStream baos = new ByteArrayOutputStream(131072);
            byte[] buffer = new byte[65536];
            int read;
            int total = 0;
            // Max 50MB limit matching server.js
            while ((read = is.read(buffer)) != -1) {
                total += read;
                if (total > 50 * 1024 * 1024) {
                    return null;
                }
                baos.write(buffer, 0, read);
            }
            byte[] bytes = baos.toByteArray();
            String mime = context.getContentResolver().getType(uri);
            if (mime == null || mime.isEmpty()) mime = "video/mp4";
            return "data:" + mime + ";base64," + Base64.encodeToString(bytes, Base64.NO_WRAP);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String audioUriToBase64(Context context, Uri uri) {
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is == null) return null;

            ByteArrayOutputStream baos = new ByteArrayOutputStream(131072);
            byte[] buffer = new byte[65536];
            int read;
            int total = 0;
            // Max 50MB limit matching server.js
            while ((read = is.read(buffer)) != -1) {
                total += read;
                if (total > 50 * 1024 * 1024) {
                    return null;
                }
                baos.write(buffer, 0, read);
            }
            byte[] bytes = baos.toByteArray();
            String mime = context.getContentResolver().getType(uri);
            if (mime == null || mime.isEmpty()) mime = "audio/mpeg";
            return "data:" + mime + ";base64," + Base64.encodeToString(bytes, Base64.NO_WRAP);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String getFullMediaUrl(String serverBaseUrl, String mediaPath) {
        if (mediaPath == null || mediaPath.trim().isEmpty()) return "";
        String clean = mediaPath.trim().replaceAll("^[\"'\\[\\]]+|[\"'\\[\\],]+$", "").trim();
        if (clean.isEmpty()) return "";

        if (clean.startsWith("http://") || clean.startsWith("https://") || clean.startsWith("data:") || clean.startsWith("file://")) {
            return clean;
        }

        if (serverBaseUrl == null || serverBaseUrl.trim().isEmpty()) {
            serverBaseUrl = "http://" + PreferenceManager.DEFAULT_SERVER_HOST + ":" + PreferenceManager.DEFAULT_SERVER_PORT;
        }
        String cleanBase = serverBaseUrl.replaceAll("/+$", "");

        if (clean.startsWith("/uploads/")) {
            return cleanBase + clean;
        } else if (clean.startsWith("uploads/")) {
            return cleanBase + "/" + clean;
        } else if (clean.startsWith("/")) {
            return cleanBase + "/uploads" + clean;
        } else {
            return cleanBase + "/uploads/" + clean;
        }
    }

    public static void loadImage(Context context, String mediaUrl, android.widget.ImageView target) {
        loadImage(context, mediaUrl, target, 0);
    }

    public static void loadImage(Context context, String mediaUrl, android.widget.ImageView target, int cornerRadiusDp) {
        if (context == null || target == null || mediaUrl == null || mediaUrl.isEmpty()) return;
        try {
            if (mediaUrl.startsWith("data:image/")) {
                int comma = mediaUrl.indexOf(",");
                if (comma != -1) {
                    byte[] decoded = Base64.decode(mediaUrl.substring(comma + 1), Base64.DEFAULT);
                    com.bumptech.glide.RequestBuilder<android.graphics.drawable.Drawable> rb = com.bumptech.glide.Glide.with(context)
                            .load(decoded)
                            .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE);
                    if (cornerRadiusDp > 0) {
                        int radiusPx = (int) (cornerRadiusDp * context.getResources().getDisplayMetrics().density);
                        rb = rb.transform(new com.bumptech.glide.load.resource.bitmap.RoundedCorners(radiusPx));
                    }
                    rb.into(target);
                    return;
                }
            }

            String serverUrl = PreferenceManager.getInstance(context).getServerBaseUrl();
            String full = getFullMediaUrl(serverUrl, mediaUrl);
            com.bumptech.glide.RequestBuilder<android.graphics.drawable.Drawable> rb = com.bumptech.glide.Glide.with(context)
                    .load(full)
                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL);
            if (cornerRadiusDp > 0) {
                int radiusPx = (int) (cornerRadiusDp * context.getResources().getDisplayMetrics().density);
                rb = rb.transform(new com.bumptech.glide.load.resource.bitmap.RoundedCorners(radiusPx));
            }
            rb.into(target);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void loadAvatar(Context context, String avatarUrl, android.widget.ImageView target) {
        if (context == null || target == null) return;
        if (avatarUrl == null || avatarUrl.isEmpty()) return;
        try {
            if (avatarUrl.startsWith("data:image/")) {
                int comma = avatarUrl.indexOf(",");
                if (comma != -1) {
                    byte[] decoded = Base64.decode(avatarUrl.substring(comma + 1), Base64.DEFAULT);
                    com.bumptech.glide.Glide.with(context)
                            .load(decoded)
                            .circleCrop()
                            .into(target);
                    return;
                }
            }
            String serverUrl = PreferenceManager.getInstance(context).getServerBaseUrl();
            String full = getFullMediaUrl(serverUrl, avatarUrl);
            com.bumptech.glide.Glide.with(context)
                    .load(full)
                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                    .circleCrop()
                    .into(target);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static java.io.File saveBase64ToCacheFile(Context context, String dataUri, String prefix, String suffix) {
        if (context == null || dataUri == null) return null;
        try {
            int comma = dataUri.indexOf(",");
            String base64Data = comma != -1 ? dataUri.substring(comma + 1) : dataUri;
            byte[] bytes = Base64.decode(base64Data, Base64.DEFAULT);
            java.io.File file = java.io.File.createTempFile(prefix, suffix, context.getCacheDir());
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(file)) {
                fos.write(bytes);
                fos.flush();
            }
            return file;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
