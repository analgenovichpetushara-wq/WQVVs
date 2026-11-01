package com.p3win.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Форматирование значений для интерфейса P3WIN. */
public final class P3Format {

    private P3Format() {
    }

    /** {@code 1 ч 24 мин} или {@code 12 мин}. */
    public static String playtime(long millis) {
        if (millis <= 0) return "0 мин";
        long totalMinutes = millis / 60_000L;
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        if (hours > 0) return hours + " ч " + minutes + " мин";
        if (totalMinutes > 0) return totalMinutes + " мин";
        return (millis / 1000L) + " с";
    }

    /** {@code 12.4 ГБ} — для размера контейнеров. */
    public static String size(long bytes) {
        if (bytes <= 0) return "—";
        double gb = bytes / (1024.0 * 1024.0 * 1024.0);
        if (gb >= 1.0) return String.format(Locale.US, "%.1f ГБ", gb);
        double mb = bytes / (1024.0 * 1024.0);
        if (mb >= 1.0) return String.format(Locale.US, "%.0f МБ", mb);
        return String.format(Locale.US, "%.0f КБ", bytes / 1024.0);
    }

    /** Дата в коротком виде: {@code 14 мая}. */
    public static String shortDate(long millis) {
        if (millis <= 0) return "—";
        return new SimpleDateFormat("d MMM", new Locale("ru")).format(new Date(millis));
    }

    /** Дата со временем: {@code 14.05, 21:30}. */
    public static String dateTime(long millis) {
        if (millis <= 0) return "—";
        return new SimpleDateFormat("dd.MM, HH:mm", Locale.US).format(new Date(millis));
    }

    /** Относительное время: «только что», «2 дня назад». */
    public static String relative(long millis) {
        if (millis <= 0) return "никогда";
        long delta = System.currentTimeMillis() - millis;
        if (delta < 60_000L) return "только что";
        if (delta < 3_600_000L) return (delta / 60_000L) + " мин назад";
        if (delta < 86_400_000L) return (delta / 3_600_000L) + " ч назад";
        long days = delta / 86_400_000L;
        if (days == 1) return "вчера";
        if (days < 30) return days + " дн назад";
        return shortDate(millis);
    }

    /** MPI-подобная оценка FPS-строки для HUD. */
    public static String fps(float fps) {
        return String.format(Locale.US, "%.0f", fps);
    }

    /** Температура с округлением. */
    public static String temperature(float celsius) {
        if (celsius <= 0) return "—";
        return String.format(Locale.US, "%.0f°", celsius);
    }
}
