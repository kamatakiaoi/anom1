package com.anonymous.chat.utils;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

public class ColorHelper {

    public static GradientDrawable getAvatarGradient(String colorString) {
        int color1;
        int color2;

        try {
            if (colorString != null && colorString.contains(",")) {
                String[] parts = colorString.split(",");
                color1 = Color.parseColor(parts[0].trim());
                color2 = Color.parseColor(parts[1].trim());
            } else if (colorString != null && colorString.startsWith("#")) {
                color1 = Color.parseColor(colorString.trim());
                float[] hsv = new float[3];
                Color.colorToHSV(color1, hsv);
                hsv[0] = (hsv[0] + 35) % 360;
                hsv[2] = Math.max(0.2f, hsv[2] * 0.7f);
                color2 = Color.HSVToColor(hsv);
            } else {
                color1 = Color.parseColor("#3B82F6");
                color2 = Color.parseColor("#1D4ED8");
            }
        } catch (Exception e) {
            color1 = Color.parseColor("#3B82F6");
            color2 = Color.parseColor("#1D4ED8");
        }

        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{color1, color2}
        );
        gd.setShape(GradientDrawable.OVAL);
        return gd;
    }

    public static int getBubbleDrawable(boolean isMe, String streakPosition) {
        if (isMe) {
            switch (streakPosition) {
                case "first": return com.anonymous.chat.R.drawable.bg_bubble_me_first;
                case "mid": return com.anonymous.chat.R.drawable.bg_bubble_me_mid;
                case "last": return com.anonymous.chat.R.drawable.bg_bubble_me_last;
                default: return com.anonymous.chat.R.drawable.bg_bubble_me_only;
            }
        } else {
            switch (streakPosition) {
                case "first": return com.anonymous.chat.R.drawable.bg_bubble_other_first;
                case "mid": return com.anonymous.chat.R.drawable.bg_bubble_other_mid;
                case "last": return com.anonymous.chat.R.drawable.bg_bubble_other_last;
                default: return com.anonymous.chat.R.drawable.bg_bubble_other_only;
            }
        }
    }
}
