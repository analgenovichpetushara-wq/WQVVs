package com.p3win.data.entity;

/**
 * Запись о запуске (сессии) игры или контейнера.
 *
 * <p>Источник — {@code SessionTracker}, подписанный на lifecycle игровой
 * Activity. Так статистика собирается без единой правки в ядре.
 */
public class GameSession {

    public long id;

    public long gameId;

    public long containerId;

    /** Исполняемый файл или путь ярлыка. */
    public String executable = "";

    public long startedAt;

    /** 0 — сессия ещё активна. */
    public long endedAt;

    /** Полная длительность, мс. */
    public long durationMs;

    /** Время, когда окно было на экране (без свёрнутых участков), мс. */
    public long activeMs;

    /** Завершилась аварийно (нет onPause / нативный сбой). */
    public boolean crashed;

    /** Код возврата процесса, если удалось получить. */
    public int exitCode;

    /** Снимок профиля на момент запуска — для воспроизведения условий. */
    public String profileSnapshot = "";

    /** Короткая причина сбоя из лога (первые строки stderr). */
    public String failureReason = "";
}
