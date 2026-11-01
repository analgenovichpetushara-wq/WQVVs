package com.p3win.data.entity;

/**
 * Индивидуальная раскладка управления P3WIN.
 *
 * <p>Базовая раскладка хранится в формате ядра {@code .icp} — его читает
 * {@code ControlsProfile}. P3-настройки поверх неё (прозрачность, масштаб,
 * мёртвая зона, привязка к геймпаду) живут здесь и применяются к
 * {@code InputControlsView} после старта сессии.
 */
public class InputLayout {

    public long id;

    public String name = "";

    /** Исходный .icp: имя встроенного профиля или путь к импортированному файлу. */
    public String baseProfile = "";

    /** Ссылка на игру; 0 — глобальная раскладка. */
    public long gameId;

    /** Прозрачность оверлея 0..1. */
    public float opacity = 1.0f;

    /** Масштаб элементов 0.5..2.0. */
    public float scale = 1.0f;

    public boolean hapticFeedback = true;

    /** Мёртвая зона аналоговых стиков 0..1. */
    public float deadZone = 0.15f;

    /** Привязка внешнего геймпада: 0 — любой. */
    public int vendorId;

    public int productId;

    public long createdAt;

    public long updatedAt;
}
