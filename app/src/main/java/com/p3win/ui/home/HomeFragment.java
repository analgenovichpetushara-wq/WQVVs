package com.p3win.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.p3win.P3Application;
import com.winlator.R;
import com.p3win.core.launch.GameLauncher;
import com.p3win.core.paths.AppPaths;
import com.p3win.data.entity.Game;
import com.p3win.data.entity.GameProfile;
import com.p3win.data.entity.Milestone;
import com.p3win.ui.shell.P3Fragment;
import com.p3win.ui.shell.P3Navigator;
import com.p3win.ui.shell.SetupFragment;
import com.p3win.ui.shell.P3ShellActivity;
import com.p3win.ui.widget.GameRowAdapter;
import com.p3win.ui.widget.P3ImageLoader;
import com.p3win.util.P3Format;

import java.io.File;
import java.util.List;

/**
 * Главный экран P3WIN.
 *
 * <p>Задача экрана — за один взгляд ответить на три вопроса: «что я играл
 * последним», «сколько у меня всего», «всё ли готово к запуску». Поэтому
 * сверху — герой-блок с продолжением, ниже — недавние, затем статистика.
 */
public class HomeFragment extends P3Fragment implements GameRowAdapter.Listener {

    private View root;
    private View hero;
    private ImageView heroImage;
    private TextView heroTitle;
    private TextView heroSubtitle;
    private TextView heroPlay;
    private View empty;
    private LinearLayout milestones;
    private GameRowAdapter recentAdapter;
    private Game heroGame;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        root = inflater.inflate(R.layout.p3_fragment_home, container, false);

        hero = root.findViewById(R.id.P3HomeHero);
        heroImage = root.findViewById(R.id.P3HomeHeroImage);
        heroTitle = root.findViewById(R.id.P3HomeHeroTitle);
        heroSubtitle = root.findViewById(R.id.P3HomeHeroSubtitle);
        heroPlay = root.findViewById(R.id.P3HomeHeroPlay);
        empty = root.findViewById(R.id.P3HomeEmpty);
        milestones = root.findViewById(R.id.P3HomeMilestones);

        RecyclerView recent = root.findViewById(R.id.P3HomeRecentList);
        recent.setLayoutManager(new LinearLayoutManager(getContext(),
                RecyclerView.HORIZONTAL, false));
        recentAdapter = new GameRowAdapter(artworkRoot(), this);
        recent.setAdapter(recentAdapter);

        root.findViewById(R.id.P3HomeRecentAll).setOnClickListener(v -> openTab(P3ShellActivity.TAB_LIBRARY));
        root.findViewById(R.id.P3HomeSettings).setOnClickListener(v -> openTab(P3ShellActivity.TAB_CONTROL));

        heroPlay.setOnClickListener(v -> playHero());

        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        reloadDelayed(this::load);
    }

    @Override
    public void scrollToTop() {
        if (root instanceof androidx.core.widget.NestedScrollView) {
            ((androidx.core.widget.NestedScrollView) root).smoothScrollTo(0, 0);
        }
    }

    private void load() {
        final android.content.Context context = requireContext().getApplicationContext();
        async(() -> {
            P3Application app = P3Application.get();
            if (app == null || app.repository() == null) return null;
            return new Snapshot(
                    app.repository().games().recentlyPlayed(12),
                    app.repository().games().count(),
                    app.repository().games().totalPlaytime(),
                    app.repository().sessions().recent(200).size(),
                    app.repository().containers().count(),
                    app.repository().milestones().findAll(),
                    new com.p3win.core.integration.CoreContainerLink(context).isSystemReady());
        }, this::render);
    }

    private void render(Snapshot snapshot) {
        if (snapshot == null || getView() == null) return;

        List<Game> recent = snapshot.recent;
        boolean hasGames = snapshot.gamesCount > 0;

        empty.setVisibility(hasGames ? View.GONE : View.VISIBLE);
        ((TextView) root.findViewById(R.id.P3HomeStatus)).setText(
                !snapshot.systemReady ? R.string.p3_home_status_setup
                        : (hasGames ? R.string.p3_home_status_ready : R.string.p3_home_status_setup));

        // Пустое состояние — это состояние «система готова, но игр нет».
        // Если rootfs не установлен, главная вообще не должна выглядеть пустой:
        // Shell открывает мастер настройки (SetupFragment) поверх вкладок.
        // Кнопка здесь ведёт только к созданию контейнера, иначе пользователь
        // попадал бы в экран ядра из двух разных мест с разным текстом.
        View emptyAction = root.findViewById(R.id.P3HomeEmptyAction);
        emptyAction.setOnClickListener(v -> {
            if (!snapshot.systemReady) {
                P3Navigator.push(requireActivity(), new SetupFragment());
            } else {
                openTab(P3ShellActivity.TAB_CONTAINERS);
            }
        });
        ((TextView) emptyAction).setText(!snapshot.systemReady
                ? R.string.p3_home_setup : R.string.p3_containers_create);

        if (hasGames && !recent.isEmpty()) {
            heroGame = recent.get(0);
            hero.setVisibility(View.VISIBLE);
            heroTitle.setText(heroGame.title);
            heroSubtitle.setText(heroGame.lastPlayedAt > 0
                    ? getString(R.string.p3_home_continue) + " · " + P3Format.relative(heroGame.lastPlayedAt)
                    : getString(R.string.p3_library_play));

            File hero = heroImageFile(heroGame);
            if (hero != null) {
                heroImage.setVisibility(View.VISIBLE);
                P3ImageLoader.load(heroImage, hero, 720);
            } else {
                heroImage.setVisibility(View.GONE);
            }
        } else {
            heroGame = null;
            hero.setVisibility(View.GONE);
        }

        recentAdapter.submit(recent);

        ((TextView) root.findViewById(R.id.P3StatGames)).setText(String.valueOf(snapshot.gamesCount));
        ((TextView) root.findViewById(R.id.P3StatPlaytime)).setText(P3Format.playtime(snapshot.playtimeMs));
        ((TextView) root.findViewById(R.id.P3StatSessions)).setText(String.valueOf(snapshot.sessions));

        renderMilestones(snapshot.milestones, snapshot);
    }

    private void renderMilestones(List<Milestone> items, Snapshot snapshot) {
        milestones.removeAllViews();
        if (items == null || items.isEmpty()) {
            milestones.setVisibility(View.GONE);
            return;
        }
        milestones.setVisibility(View.VISIBLE);

        LayoutInflater inflater = LayoutInflater.from(getContext());
        int shown = 0;
        for (Milestone milestone : items) {
            if (shown >= 4) break;
            View row = inflater.inflate(R.layout.p3_item_milestone, milestones, false);
            ((TextView) row.findViewById(R.id.P3MilestoneTitle)).setText(milestone.title);
            ((TextView) row.findViewById(R.id.P3MilestoneDescription)).setText(milestone.description);

            android.widget.ProgressBar bar = row.findViewById(R.id.P3MilestoneBar);
            ImageView check = row.findViewById(R.id.P3MilestoneCheck);
            if (milestone.achieved) {
                bar.setVisibility(View.GONE);
                check.setVisibility(View.VISIBLE);
            } else {
                bar.setVisibility(View.VISIBLE);
                check.setVisibility(View.GONE);
                bar.setProgress(milestone.progress);
            }
            milestones.addView(row);
            shown++;
        }
    }

    private void playHero() {
        if (heroGame == null || getContext() == null) return;
        final Game game = heroGame;
        // Контекст захватываем до ухода в фон: requireContext() в рабочем потоке
        // может бросить исключение, если фрагмент уже откреплён.
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

    private void openTab(int tab) {
        if (getActivity() instanceof P3ShellActivity) {
            ((P3ShellActivity) getActivity()).selectTab(tab, true);
        }
    }

    private File artworkRoot() {
        if (getContext() == null) return null;
        return AppPaths.of(getContext()).getLibraryDir();
    }

    private File heroImageFile(Game game) {
        File root = artworkRoot();
        if (root == null) return null;
        String name = !game.heroFile.isEmpty() ? game.heroFile : game.coverFile;
        if (name.isEmpty()) return null;
        File file = new File(new File(root, String.valueOf(game.id)), name);
        return file.isFile() ? file : null;
    }

    /** Снимок данных главного экрана — собирается в фоне одним заходом. */
    private static final class Snapshot {
        final List<Game> recent;
        final int gamesCount;
        final long playtimeMs;
        final int sessions;
        final int containers;
        final List<Milestone> milestones;
        final boolean systemReady;

        Snapshot(List<Game> recent, int gamesCount, long playtimeMs, int sessions,
                 int containers, List<Milestone> milestones, boolean systemReady) {
            this.recent = recent;
            this.gamesCount = gamesCount;
            this.playtimeMs = playtimeMs;
            this.sessions = sessions;
            this.containers = containers;
            this.milestones = milestones;
            this.systemReady = systemReady;
        }
    }
}
