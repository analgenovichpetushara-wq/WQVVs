package com.p3win.core.gpu;

import android.content.Context;

import com.p3win.data.entity.GraphicsDriver;
import com.winlator.container.GraphicsDrivers;
import com.winlator.core.DefaultVersion;
import com.winlator.core.GeneralComponents;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Каталог GPU-драйверов.
 *
 * <h3>Откуда берутся версии</h3>
 * Не из выдуманного списка, а из самого ядра — ровно так, как это делает
 * штатный {@code TurnipConfigDialog}:
 * <ul>
 *   <li>{@code GeneralComponents.getBuiltinComponentNames(Type.TURNIP)} —
 *       версии, вшитые в APK ({@code assets/graphics_driver/*.tzst});</li>
 *   <li>{@code getInstalledComponentNames(Type.TURNIP, context)} — версии,
 *       которые пользователь уже поставил вручную из «родного» экрана.</li>
 * </ul>
 * Поэтому список в P3 GPU Manager всегда совпадает с тем, что реально можно
 * выбрать в ядре, и никогда не предлагает несуществующий архив.
 *
 * <h3>Совместимость с Adreno 810</h3>
 * Turnip — драйвер Mesa для Adreno. Сборки под Gen 6–7 (6xx/7xx) на Gen 8
 * (8xx) либо не инициализируются, либо дают артефакты: другое поколение
 * GPU и другой командный поток. Отсюда правило: «поддержка Gen 8 = ветка
 * Mesa 25 и новее». Это эвристика по номеру версии — она консервативна
 * и объяснима пользователю, а не «магический список».
 */
public final class DriverCatalog {

    /** Минимальная ветка Mesa, в которой есть поддержка Adreno Gen 8. */
    private static final int MIN_GEN8_MAJOR = 25;

    private DriverCatalog() {
    }

    /** Полный каталог: Vulkan-драйверы + OpenGL-путь. */
    public static List<GraphicsDriver> list(Context context) {
        List<GraphicsDriver> result = new ArrayList<>();
        if (context == null) return result;

        result.addAll(builtin(context));
        result.addAll(installed(context));
        result.addAll(openGl(context));

        // Свежие версии — выше: пользователю почти всегда нужна последняя.
        Collections.sort(result, (a, b) -> {
            int byDriver = a.driverId.compareTo(b.driverId);
            if (byDriver != 0) return byDriver;
            return compareVersionsDesc(a.version, b.version);
        });
        return result;
    }

    // ------------------------------------------------------------------
    // Turnip
    // ------------------------------------------------------------------

    private static List<GraphicsDriver> builtin(Context context) {
        List<GraphicsDriver> result = new ArrayList<>();
        for (String version : GeneralComponents.getBuiltinComponentNames(
                GeneralComponents.Type.TURNIP)) {
            result.add(turnip(version, true));
        }
        return result;
    }

    private static List<GraphicsDriver> installed(Context context) {
        List<GraphicsDriver> result = new ArrayList<>();
        ArrayList<String> names = GeneralComponents.getInstalledComponentNames(
                GeneralComponents.Type.TURNIP, context);
        for (String name : names) {
            if (name.isEmpty()) continue;
            boolean alreadyKnown = false;
            for (GraphicsDriver existing : result) {
                if (existing.version.equals(name)) alreadyKnown = true;
            }
            if (!alreadyKnown) result.add(turnip(name, false));
        }
        return result;
    }

    private static GraphicsDriver turnip(String version, boolean builtin) {
        GraphicsDriver driver = new GraphicsDriver();
        driver.driverId = GraphicsDrivers.TURNIP;
        driver.version = version;
        driver.displayName = "Turnip " + version;
        driver.vendor = "Mesa / freedreno";
        driver.builtin = builtin;
        driver.supportsGen8 = majorVersion(version) >= MIN_GEN8_MAJOR;
        driver.compatibilityLevel = driver.supportsGen8 ? 1 : 3;
        driver.warning = driver.supportsGen8 ? ""
                : "Mesa " + version + " ориентирована на Adreno Gen 6–7. "
                + "На Adreno 810 возможен отказ Vulkan — используйте ветку "
                + MIN_GEN8_MAJOR + ".x и новее.";
        return driver;
    }

    // ------------------------------------------------------------------
    // OpenGL-путь (аварийный вариант, если Vulkan не стартует)
    // ------------------------------------------------------------------

    private static List<GraphicsDriver> openGl(Context context) {
        List<GraphicsDriver> result = new ArrayList<>();

        result.add(openGl(GraphicsDrivers.GLADIO, DefaultVersion.GLADIO, "Gladio", "Winlator",
                "OpenGL-слой Winlator. Не требует Vulkan-драйвера — аварийный путь, "
                        + "если Turnip не стартует."));
        result.add(openGl(GraphicsDrivers.ZINK, DefaultVersion.ZINK, "Zink", "Mesa",
                "OpenGL поверх Vulkan. Требует рабочий Vulkan."));
        result.add(openGl(GraphicsDrivers.VIRGL, DefaultVersion.VIRGL, "VirGL", "Mesa",
                "Программный OpenGL. Медленный, но самый совместимый."));

        return result;
    }

    private static GraphicsDriver openGl(String id, String version, String name,
                                         String vendor, String note) {
        GraphicsDriver driver = new GraphicsDriver();
        driver.driverId = id;
        driver.version = version;
        driver.displayName = name + " " + version;
        driver.vendor = vendor;
        driver.builtin = true;
        driver.supportsGen8 = true;
        driver.compatibilityLevel = 2;
        driver.warning = note;
        return driver;
    }

    // ------------------------------------------------------------------
    // Совместимость
    // ------------------------------------------------------------------

    /**
     * Оценить драйвер под конкретный GPU.
     *
     * @return строка-предупреждение или пустая строка, если всё хорошо
     */
    public static String compatibilityWarning(GraphicsDriver driver, GpuCapability gpu) {
        if (driver == null || gpu == null) return "";
        if (GraphicsDrivers.isVulkanDriver(driver.driverId)) {
            if (gpu.isGen8() && !driver.supportsGen8) return driver.warning;
            if (!gpu.isGen8() && gpu.generation != GpuCapability.UNKNOWN
                    && driver.supportsGen8 && majorVersion(driver.version) >= 27) {
                // Новые ветки тоже требуют свежего GPU, но это уже не наш случай.
                return "";
            }
        }
        if (GraphicsDrivers.GLADIO.equals(driver.driverId) && gpu.isGen8()) return "";
        return "";
    }

    /** Файл архива драйвера внутри APK/каталога компонентов ядра. */
    public static File archiveFile(Context context, GraphicsDriver driver) {
        if (context == null || driver == null) return null;
        GeneralComponents.Type type = GraphicsDrivers.TURNIP.equals(driver.driverId)
                ? GeneralComponents.Type.TURNIP : GeneralComponents.Type.TURNIP;
        File dir = GeneralComponents.getComponentDir(type, context);
        return new File(dir, "turnip-" + driver.version + ".tzst");
    }

    private static int majorVersion(String version) {
        try {
            int dot = version.indexOf('.');
            return Integer.parseInt(dot > 0 ? version.substring(0, dot) : version);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static int compareVersionsDesc(String a, String b) {
        String[] left = a.split("\\.");
        String[] right = b.split("\\.");
        int length = Math.max(left.length, right.length);
        for (int i = 0; i < length; i++) {
            int l = i < left.length ? parseInt(left[i]) : 0;
            int r = i < right.length ? parseInt(right[i]) : 0;
            if (l != r) return Integer.compare(r, l);
        }
        return 0;
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
