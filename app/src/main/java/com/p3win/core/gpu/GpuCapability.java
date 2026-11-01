package com.p3win.core.gpu;

import android.content.Context;

import com.p3win.core.log.P3Log;
import com.winlator.core.GPUHelper;

/**
 * Что умеет GPU этого устройства.
 *
 * <p>Определяется один раз при старте GPU-менеджера и кэшируется: запросы
 * к GL/Vulkan контексту стоят десятки миллисекунд, а решение «какой драйвер
 * предложить» нужно мгновенно при отрисовке списка.
 *
 * <h3>Почему это вообще нужно</h3>
 * Turnip — это драйвер Mesa для Adreno. Сборки под Gen 6/7 (Adreno 6xx/7xx)
 * на Adreno 810 работают плохо или не запускаются: другой набор GPU-регистров
 * и другое поколение командного потока. Поэтому менеджер обязан знать
 * модель GPU и не предлагать заведомо несовместимый драйвер как «рекомендуемый».
 */
public final class GpuCapability {

    /** Адрено поколения 8 (8xx) — целевое железо проекта. */
    public static final int GEN8 = 8;
    public static final int GEN7 = 7;
    public static final int GEN6 = 6;
    public static final int UNKNOWN = 0;

    /** Модель Adreno, например 810. 0 — не определена. */
    public final int adrenoModel;

    /** Поколение (6/7/8) либо {@link #UNKNOWN}. */
    public final int generation;

    public final boolean vulkanAvailable;
    public final int vulkanApiVersion;
    public final String glRenderer;
    public final String glVendor;

    private GpuCapability(int adrenoModel, boolean vulkanAvailable, int vulkanApiVersion,
                          String glRenderer, String glVendor) {
        this.adrenoModel = adrenoModel;
        this.generation = generationOf(adrenoModel);
        this.vulkanAvailable = vulkanAvailable;
        this.vulkanApiVersion = vulkanApiVersion;
        this.glRenderer = glRenderer != null ? glRenderer : "";
        this.glVendor = glVendor != null ? glVendor : "";
    }

    /** Собрать сведения о GPU. Блокирующий вызов — только из фонового потока. */
    public static GpuCapability detect(Context context) {
        int model = 0;
        boolean vulkan = false;
        int apiVersion = 0;
        String renderer = "";
        String vendor = "";

        try {
            model = GPUHelper.getAdrenoModelId(context);
        } catch (Throwable t) {
            P3Log.warn("GPU", "Не удалось определить модель Adreno: " + t.getMessage());
        }
        try {
            renderer = GPUHelper.glGetRenderer(context);
            vendor = GPUHelper.glGetVendor(context);
            vulkan = true;
        } catch (Throwable t) {
            P3Log.warn("GPU", "GL-контекст недоступен: " + t.getMessage());
        }
        try {
            apiVersion = GPUHelper.vkGetApiVersion();
            vulkan = apiVersion > 0;
        } catch (Throwable t) {
            // Нативный слой может быть ещё не загружен — это нормально на старте.
            vulkan = false;
        }

        return new GpuCapability(model, vulkan, apiVersion, renderer, vendor);
    }

    public static int generationOf(int model) {
        if (model >= 800) return GEN8;
        if (model >= 700) return GEN7;
        if (model >= 600) return GEN6;
        return UNKNOWN;
    }

    public boolean isGen8() {
        return generation == GEN8;
    }

    /** Человекочитаемое описание: {@code Adreno 810 (Gen 8)}. */
    public String describe() {
        if (adrenoModel <= 0) return "GPU не определён";
        return "Adreno " + adrenoModel + " (Gen " + generation + ")";
    }

    /** Версия Vulkan в формате {@code 1.3.274} или пустая строка. */
    public String vulkanVersionText() {
        if (vulkanApiVersion <= 0) return "";
        int major = GPUHelper.vkVersionMajor(vulkanApiVersion);
        int minor = GPUHelper.vkVersionMinor(vulkanApiVersion);
        int patch = GPUHelper.vkVersionPatch(vulkanApiVersion);
        return major + "." + minor + "." + patch;
    }
}
