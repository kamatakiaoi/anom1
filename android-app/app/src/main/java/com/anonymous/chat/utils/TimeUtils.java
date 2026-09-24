package com.anonymous.chat.utils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class TimeUtils {
    private static final TimeZone TZ_UTC = TimeZone.getTimeZone("UTC");
    private static final TimeZone TZ_VN = TimeZone.getTimeZone("GMT+7");

    private static final ThreadLocal<SimpleDateFormat> ISO_FORMAT = ThreadLocal.withInitial(() -> {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        sdf.setTimeZone(TZ_UTC);
        return sdf;
    });

    private static final ThreadLocal<SimpleDateFormat> ISO_FORMAT_NO_MS = ThreadLocal.withInitial(() -> {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        sdf.setTimeZone(TZ_UTC);
        return sdf;
    });

    private static final ThreadLocal<SimpleDateFormat> TIME_FORMAT_VN = ThreadLocal.withInitial(() -> {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        sdf.setTimeZone(TZ_VN);
        return sdf;
    });

    private static final ThreadLocal<SimpleDateFormat> TIME_FORMAT_UTC = ThreadLocal.withInitial(() -> {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        sdf.setTimeZone(TZ_UTC);
        return sdf;
    });

    public static Date parseIsoDate(String isoString) {
        if (isoString == null || isoString.isEmpty()) return new Date();
        try {
            return ISO_FORMAT.get().parse(isoString);
        } catch (ParseException e) {
            try {
                return ISO_FORMAT_NO_MS.get().parse(isoString);
            } catch (ParseException ex) {
                return new Date();
            }
        }
    }

    public static long parseIsoToMillis(String isoString) {
        Date d = parseIsoDate(isoString);
        return d != null ? d.getTime() : 0;
    }

    public static String formatMessageTime(String isoString, String timezonePref) {
        Date date = parseIsoDate(isoString);
        if ("vn".equalsIgnoreCase(timezonePref)) {
            return TIME_FORMAT_VN.get().format(date);
        } else {
            return TIME_FORMAT_UTC.get().format(date);
        }
    }

    public static String formatRelativeTime(String isoString) {
        Date date = parseIsoDate(isoString);
        long now = System.currentTimeMillis();
        long diffSec = (now - date.getTime()) / 1000;
        if (diffSec < 0) diffSec = 0;

        if (diffSec < 60) return diffSec + "s ago";
        if (diffSec < 3600) return (diffSec / 60) + "m ago";
        if (diffSec < 86400) return (diffSec / 3600) + "h ago";
        if (diffSec < 2592000) return (diffSec / 86400) + "d ago";
        return (diffSec / 2592000) + "mo ago";
    }

    public static String formatUptime(String uptime) {
        return uptime != null && !uptime.isEmpty() ? uptime : "—";
    }
}
