package com.p3win.ui.shell;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.winlator.R;

/**
 * Базовый экран модуля (GPU / Engine / Input / Settings).
 *
 * <p>Все четыре экрана имеют одинаковый каркас: заголовок, кнопка «назад»,
 * прокручиваемое тело. Наследники только наполняют тело и переопределяют
 * обработчики. Это убирает дублирование разметки и гарантирует, что кнопка
 * «назад» ведёт себя одинаково везде.
 */
public abstract class P3ModuleFragment extends P3Fragment {

    protected LinearLayout body;
    private TextView title;
    private ImageView action;

    protected abstract int titleRes();

    protected abstract void buildBody(LinearLayout body);

    /** Вызывается по нажатию на кнопку в правом верхнем углу (если она показана). */
    protected void onAction() {
    }

    protected int actionIcon() {
        return 0;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.p3_fragment_list, container, false);

        body = root.findViewById(R.id.P3ContentBody);
        title = root.findViewById(R.id.P3Title);
        action = root.findViewById(R.id.P3Action);

        title.setText(titleRes());
        root.findViewById(R.id.P3Back).setOnClickListener(v -> {
            if (!P3Navigator.pop(requireActivity())) {
                requireActivity().getSupportFragmentManager().beginTransaction().remove(this).commit();
            }
        });

        int icon = actionIcon();
        if (icon == 0) {
            action.setVisibility(View.GONE);
        } else {
            action.setVisibility(View.VISIBLE);
            action.setImageResource(icon);
            action.setOnClickListener(v -> onAction());
        }

        buildBody(body);
        return root;
    }

    // ------------------------------------------------------------------
    // Помощники построения UI
    // ------------------------------------------------------------------

    protected TextView section(String text) {
        TextView view = new TextView(requireContext());
        view.setText(text.toUpperCase());
        view.setTextSize(10f);
        view.setLetterSpacing(0.14f);
        view.setTextColor(getResources().getColor(R.color.p3_text_tertiary));
        int pad = dp(16);
        view.setPadding(0, pad, 0, dp(6));
        return view;
    }

    protected TextView paragraph(String text) {
        TextView view = new TextView(requireContext());
        view.setText(text);
        view.setTextSize(13f);
        view.setTextColor(getResources().getColor(R.color.p3_text_secondary));
        view.setLineSpacing(dp(3), 1f);
        view.setPadding(0, 0, 0, dp(10));
        return view;
    }

    /**
     * Строка настройки с переключателем.
     *
     * @return созданный Switch (для чтения/установки состояния)
     */
    protected android.widget.Switch toggleRow(LinearLayout parent, String titleText,
                                             String summary, boolean checked,
                                             android.widget.CompoundButton.OnCheckedChangeListener listener) {
        View row = LayoutInflater.from(requireContext())
                .inflate(R.layout.p3_item_setting, parent, false);

        TextView titleView = row.findViewById(R.id.P3SettingTitle);
        TextView summaryView = row.findViewById(R.id.P3SettingSummary);
        android.widget.Switch toggle = row.findViewById(R.id.P3SettingSwitch);

        titleView.setText(titleText);
        summaryView.setText(summary);
        summaryView.setVisibility(summary == null || summary.isEmpty() ? View.GONE : View.VISIBLE);

        toggle.setVisibility(View.VISIBLE);
        toggle.setChecked(checked);
        toggle.setOnCheckedChangeListener(listener);

        // Клик по всей строке тоже переключает — так удобнее на телефоне.
        row.setOnClickListener(v -> toggle.setChecked(!toggle.isChecked()));
        parent.addView(row);
        return toggle;
    }

    /** Строка настройки с текстовым значением справа (открывает выбор). */
    protected View valueRow(LinearLayout parent, String titleText, String value,
                            Runnable onClick) {
        View row = LayoutInflater.from(requireContext())
                .inflate(R.layout.p3_item_setting, parent, false);

        ((TextView) row.findViewById(R.id.P3SettingTitle)).setText(titleText);
        row.findViewById(R.id.P3SettingSummary).setVisibility(View.GONE);

        TextView valueView = row.findViewById(R.id.P3SettingValue);
        valueView.setVisibility(View.VISIBLE);
        valueView.setText(value);

        row.setOnClickListener(v -> {
            if (onClick != null) onClick.run();
        });
        parent.addView(row);
        return row;
    }

    protected View divider(LinearLayout parent) {
        View view = new View(requireContext());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(1)));
        view.setLayoutParams(params);
        view.setBackgroundColor(getResources().getColor(R.color.p3_stroke));
        parent.addView(view);
        return view;
    }

    protected int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
