package com.p3win.core.profile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Каталог профилей контейнеров P3WIN.
 *
 * <h3>Почему именно такие значения</h3>
 * Эталон — realme P3 5G: Snapdragon 6 Gen 4 (1×A720 2.3 ГГц + 3×A720 2.2 ГГц
 * + 4×A520 1.8 ГГц), Adreno 810, 8 ГБ LPDDR4X, экран 2400×1080 120 Гц.
 * <ul>
 *   <li><b>performance</b> — Box64 PERFORMANCE, 1280×720, HUD simple, DXVK 60 fps.
 *       Adreno 810 держит 720p заметно лучше, чем 1080p; масштабирование делает
 *       сам экран, и картинка всё равно резкая.</li>
 *   <li><b>balanced</b> — INTERMEDIATE, 1600×720. Компромисс по умолчанию.</li>
 *   <li><b>compatibility</b> — STABILITY (STRONGMEM=2, SAFEFLAGS=2, X87DOUBLE=1):
 *       медленнее, но переживает кривые UE3/Unity-порты.</li>
 *   <li><b>legacy</b> — STABILITY + ограничение расширений Mesa: игры 2000-х падают
 *       на «слишком новых» расширениях драйвера.</li>
 *   <li><b>custom</b> — пустой профиль для ручной настройки.</li>
 * </ul>
 *
 * <p>Важно про 32 бита: у Snapdragon 6 Gen 4 нет AArch32, поэтому любой 32-битный
 * код идёт через Wine WoW64 → Box64. Профили не пытаются «включить aarch32» —
 * такого пути на этом железе не существует.
 */
public final class ContainerProfiles {

    public static final String PERFORMANCE = "performance";
    public static final String BALANCED = "balanced";
    public static final String COMPATIBILITY = "compatibility";
    public static final String LEGACY = "legacy";
    public static final String CUSTOM = "custom";

    private static final List<ContainerProfile> ALL;

    static {
        ArrayList<ContainerProfile> profiles = new ArrayList<>();

        profiles.add(new ContainerProfile(
                PERFORMANCE,
                "Performance",
                "Максимальный FPS: агрессивный Box64, 720p, HUD включён.",
                "PERFORMANCE",
                "1280x720",
                1,
                2,
                "BOX64_DYNAREC_BIGBLOCK=3 MESA_NO_ERROR=1",
                "version=2.4.1,framerate=60",
                "turnip",
                false));

        profiles.add(new ContainerProfile(
                BALANCED,
                "Balanced",
                "Профиль по умолчанию: стабильно и достаточно быстро.",
                "INTERMEDIATE",
                "1600x720",
                1,
                0,
                "MESA_NO_ERROR=1",
                "version=2.4.1",
                "turnip",
                false));

        profiles.add(new ContainerProfile(
                COMPATIBILITY,
                "Compatibility",
                "Максимум совместимости: медленный, но предсказуемый Box64.",
                "STABILITY",
                "1280x720",
                0,
                0,
                "",
                "version=2.4.1",
                "turnip",
                true));

        profiles.add(new ContainerProfile(
                LEGACY,
                "Legacy",
                "Старые игры (2000-2010): ограничение расширений Mesa.",
                "STABILITY",
                "1024x768",
                0,
                0,
                "MESA_EXTENSION_MAX_YEAR=2003",
                "version=1.10.3",
                "turnip",
                true));

        profiles.add(new ContainerProfile(
                CUSTOM,
                "Custom",
                "Пустой профиль — все параметры выставляются вручную.",
                "INTERMEDIATE",
                "1280x720",
                1,
                0,
                "",
                "",
                "turnip",
                false));

        ALL = Collections.unmodifiableList(profiles);
    }

    private ContainerProfiles() {
    }

    public static List<ContainerProfile> all() {
        return ALL;
    }

    public static ContainerProfile byId(String id) {
        if (id != null) {
            for (ContainerProfile profile : ALL) {
                if (profile.id.equals(id)) return profile;
            }
        }
        return ALL.get(1);
    }

    public static String[] titles() {
        String[] titles = new String[ALL.size()];
        for (int i = 0; i < ALL.size(); i++) titles[i] = ALL.get(i).title;
        return titles;
    }

    public static String[] ids() {
        String[] ids = new String[ALL.size()];
        for (int i = 0; i < ALL.size(); i++) ids[i] = ALL.get(i).id;
        return ids;
    }
}
