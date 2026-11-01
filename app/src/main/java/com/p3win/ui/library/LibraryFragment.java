package com.p3win.ui.library;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.p3win.P3Application;
import com.winlator.R;
import com.p3win.core.launch.GameLauncher;
import com.p3win.core.paths.AppPaths;
import com.p3win.core.prefs.P3Prefs;
import com.p3win.data.dao.GameDao;
import com.p3win.data.entity.Game;
import com.p3win.data.entity.GameProfile;
import com.p3win.ui.shell.P3Fragment;
import com.p3win.ui.widget.GamePosterAdapter;

import java.io.File;
import java.util.List;

/**
 * Библиотека игр.
 *
 * <p>Поиск, избранное, сортировка и сетка постеров. Запуск — долгое нажатие
 * по карточке открывает меню (профиль, избранное, удаление), обычное нажатие
 * запускает игру: в консольном интерфейсе главный сценарий должен быть
 * в один тап.
 */
public class LibraryFragment extends P3Fragment implements GamePosterAdapter.Listener {

    private RecyclerView grid;
    private View emptyView;
    private EditText search;
    private GamePosterAdapter adapter;
    private TextView filterAll;
    private TextView filterFavorites;
    private TextView filterSort;

    private boolean onlyFavorites;
    private GameDao.Sort sort = GameDao.Sort.LAST_PLAYED;
    private String query = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.p3_fragment_library, container, false);

        grid = root.findViewById(R.id.P3LibraryGrid);
        emptyView = root.findViewById(R.id.P3LibraryEmpty);
        search = root.findViewById(R.id.P3LibrarySearch);
        filterAll = root.findViewById(R.id.P3FilterAll);
        filterFavorites = root.findViewById(R.id.P3FilterFavorites);
        filterSort = root.findViewById(R.id.P3FilterSort);

        int columns = Math.max(2, P3Prefs.gridColumns());
        grid.setLayoutManager(new GridLayoutManager(getContext(), columns));
        adapter = new GamePosterAdapter(artworkRoot(), this);
        grid.setAdapter(adapter);

        root.findViewById(R.id.P3LibraryAdd).setOnClickListener(v -> addGame());
        root.findViewById(R.id.P3LibraryEmptyAction).setOnClickListener(v -> addGame());
        filterAll.setOnClickListener(v -> setFavorites(false));
        filterFavorites.setOnClickListener(v -> setFavorites(true));
        filterSort.setOnClickListener(v -> chooseSort());

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                query = s != null ? s.toString().trim() : "";
                load();
            }
        });

        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        reloadDelayed(this::load);
    }

    @Override
    public void scrollToTop() {
        if (grid != null) grid.smoothScrollToPosition(0);
    }

    private void setFavorites(boolean favorites) {
        onlyFavorites = favorites;
        filterAll.setBackgroundResource(favorites
                ? R.drawable.p3_chip_background : R.drawable.p3_chip_background_active);
        filterAll.setTextColor(getResources().getColor(favorites
                ? R.color.p3_text_secondary : R.color.p3_bg));
        filterFavorites.setBackgroundResource(favorites
                ? R.drawable.p3_chip_background_active : R.drawable.p3_chip_background);
        filterFavorites.setTextColor(getResources().getColor(favorites
                ? R.color.p3_bg : R.color.p3_text_secondary));
        load();
    }

    private void chooseSort() {
        final String[] labels = {
                getString(R.string.p3_library_sort_played),
                getString(R.string.p3_library_sort_title),
                getString(R.string.p3_library_sort_playtime),
                getString(R.string.p3_library_sort_added)
        };
        final GameDao.Sort[] values = {
                GameDao.Sort.LAST_PLAYED,
                GameDao.Sort.TITLE,
                GameDao.Sort.PLAYTIME,
                GameDao.Sort.ADDED
        };

        new androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.Theme_P3Win_Dialog)
                .setTitle(R.string.p3_library_sort)
                .setItems(labels, (dialog, which) -> {
                    sort = values[which];
                    filterSort.setText(labels[which]);
                    load();
                })
                .show();
    }

    private void load() {
        final String searchQuery = query;
        final boolean favoritesOnly = onlyFavorites;
        final GameDao.Sort currentSort = sort;

        async(() -> {
            P3Application app = P3Application.get();
            if (app == null || app.repository() == null) return null;
            return app.repository().games().query(searchQuery, 0, favoritesOnly, currentSort);
        }, games -> {
            if (games == null || getView() == null) return;
            adapter.submit(games);
            boolean isEmpty = games.isEmpty();
            emptyView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            grid.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        });
    }

    private void addGame() {
        // Первый шаг — список контейнеров: игра всегда живёт внутри контейнера.
        ContainerPickerDialog.show(requireContext(), container -> {
            if (container == null) return;
            new AddGameDialog(requireContext(), container, this::load).show();
        });
    }

    @Override
    public void onGameClick(Game game) {
        if (getContext() == null) return;
        final android.content.Context context = getContext().getApplicationContext();
        async(() -> {
            P3Application app = P3Application.get();
            if (app == null || app.repository() == null) return null;
            GameProfile profile = game.profileId > 0
                    ? app.repository().profiles().findById(game.profileId) : null;
            String error = GameLauncher.launch(context, game, profile);
            if (error == null) app.repository().games().touch(game.id);
            return error;
        }, error -> {
            if (error != null) showLaunchError(error);
        });
    }

    @Override
    public void onGameLongClick(Game game) {
        GameCardMenuDialog.show(requireContext(), game, this::load);
    }

    private File artworkRoot() {
        if (getContext() == null) return null;
        return AppPaths.of(getContext()).getLibraryDir();
    }
}
