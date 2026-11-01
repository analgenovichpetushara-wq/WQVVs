package com.p3win.ui.widget;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Загрузчик обложек.
 *
 * <p>Зачем свой, а не Glide/Picasso: у нас нет сети — обложки лежат локальными
 * файлами в {@code files/p3win/library/<gameId>/}. Любая библиотека потянула бы
 * лишние зависимости, а весь нужный функционал — это «декодировать файл в фоне
 * и закэшировать по размеру». Плюс кэш не растёт бесконечно.
 */
public final class P3ImageLoader {

    private static final ExecutorService IO = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(24 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };

    private P3ImageLoader() {
    }

    public static void load(ImageView target, File file) {
        load(target, file, 0);
    }

    /**
     * Загрузить изображение.
     *
     * @param targetSizePx желаемая ширина в пикселях (0 — без ресайза)
     */
    public static void load(final ImageView target, final File file, final int targetSizePx) {
        if (target == null) return;
        if (file == null || !file.isFile()) {
            target.setVisibility(android.view.View.GONE);
            return;
        }

        final String key = file.getAbsolutePath() + "#" + targetSizePx;
        Bitmap cached = CACHE.get(key);
        if (cached != null) {
            target.setImageBitmap(cached);
            target.setVisibility(android.view.View.VISIBLE);
            return;
        }

        final String path = file.getAbsolutePath();
        IO.execute(() -> {
            Bitmap bitmap = decode(path, targetSizePx);
            if (bitmap != null) CACHE.put(key, bitmap);
            final Bitmap result = bitmap;
            MAIN.post(() -> {
                if (result == null) {
                    target.setVisibility(android.view.View.GONE);
                    return;
                }
                target.setImageBitmap(result);
                target.setVisibility(android.view.View.VISIBLE);
                target.setAlpha(0f);
                target.animate().alpha(1f).setDuration(140).start();
            });
        });
    }

    private static Bitmap decode(String path, int targetSizePx) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, bounds);

            BitmapFactory.Options options = new BitmapFactory.Options();
            if (targetSizePx > 0 && bounds.outWidth > targetSizePx) {
                int sample = 1;
                while (bounds.outWidth / (sample * 2) >= targetSizePx) sample *= 2;
                options.inSampleSize = sample;
            }
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(path, options);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Сбросить кэш (после массового обновления обложек). */
    public static void clear() {
        CACHE.evictAll();
    }
}
