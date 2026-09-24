package com.example.glowmark;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class MarkScreen extends Screen {
    private static final int COLS = 2;
    private static final int ROWS = 6;
    private static final int PER_PAGE = COLS * ROWS;
    private static final int COL_WIDTH = 170;
    private static final int ROW_HEIGHT = 22;

    private static int page = 0;
    private static String search = "";

    public MarkScreen() {
        super(Component.translatable("screen.glowmark.title"));
    }

    private static String onOff(boolean v) {
        return v ? "§aВКЛ" : "§cВЫКЛ";
    }

    @Override
    protected void init() {
        GlowConfig cfg = GlowMarkClient.CONFIG;

        List<PlayerInfo> players = new ArrayList<>();
        String q = search.toLowerCase(Locale.ROOT);
        for (PlayerInfo i : minecraft.getConnection().getOnlinePlayers()) {
            if (q.isEmpty() || i.getProfile().name().toLowerCase(Locale.ROOT).contains(q)) players.add(i);
        }
        players.sort(Comparator.comparing(i -> i.getProfile().name(), String.CASE_INSENSITIVE_ORDER));

        int pages = Math.max(1, (players.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.max(0, Math.min(page, pages - 1));

        // заголовок и поиск
        int tw = font.width(title);
        addRenderableWidget(new StringWidget(width / 2 - tw / 2, 8, tw, 9, title, font));
        EditBox box = new EditBox(font, width / 2 - 100, 20, 200, 18, Component.literal("Поиск"));
        box.setHint(Component.literal("Поиск по нику..."));
        box.setValue(search);
        box.setResponder(s -> {
            search = s;
            page = 0;
            rebuildWidgets();
        });
        addRenderableWidget(box);
        setInitialFocus(box);

        // список игроков с галочками и пингом
        int startX = width / 2 - (COLS * COL_WIDTH) / 2;
        int startY = 46;
        int from = page * PER_PAGE;
        int to = Math.min(players.size(), from + PER_PAGE);
        for (int i = from; i < to; i++) {
            PlayerInfo info = players.get(i);
            UUID id = info.getProfile().id();
            String name = info.getProfile().name();
            int k = i - from;
            int x = startX + (k % COLS) * COL_WIDTH;
            int y = startY + (k / COLS) * ROW_HEIGHT;
            addRenderableWidget(Checkbox.builder(Component.literal(name + " §7" + info.getLatency() + "мс"), font)
                    .pos(x, y)
                    .selected(cfg.isMarked(id))
                    .onValueChange((cb, value) -> setMarked(id, name, value))
                    .build());
        }

        // переключатели функций
        int bw = 82;
        int tx = width / 2 - (4 * bw + 3 * 4) / 2;
        int ty = height - 52;
        addRenderableWidget(Button.builder(Component.literal("Свечение " + onOff(cfg.glow)), b -> {
            GlowMarkClient.setGlow(minecraft, !cfg.glow);
            rebuildWidgets();
        }).bounds(tx, ty, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Оповещ. " + onOff(cfg.alerts)), b -> {
            cfg.alerts = !cfg.alerts;
            cfg.save();
            rebuildWidgets();
        }).bounds(tx + (bw + 4), ty, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Звук " + onOff(cfg.sound)), b -> {
            cfg.sound = !cfg.sound;
            cfg.save();
            rebuildWidgets();
        }).bounds(tx + 2 * (bw + 4), ty, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Радар " + onOff(cfg.radar)), b -> {
            cfg.radar = !cfg.radar;
            cfg.save();
            rebuildWidgets();
        }).bounds(tx + 3 * (bw + 4), ty, bw, 20).build());

        // низ: страницы, радиус, снять все
        int by = height - 28;
        Button prev = Button.builder(Component.literal("<"), b -> { page--; rebuildWidgets(); })
                .bounds(width / 2 - 150, by, 30, 20).build();
        Button label = Button.builder(Component.literal((page + 1) + "/" + pages), b -> {})
                .bounds(width / 2 - 118, by, 40, 20).build();
        Button next = Button.builder(Component.literal(">"), b -> { page++; rebuildWidgets(); })
                .bounds(width / 2 - 76, by, 30, 20).build();
        prev.active = page > 0;
        label.active = false;
        next.active = page < pages - 1;
        addRenderableWidget(prev);
        addRenderableWidget(label);
        addRenderableWidget(next);

        addRenderableWidget(Button.builder(Component.literal("Радиус: " + cfg.alertRange + "м"), b -> {
            int[] steps = {16, 32, 48, 64, 96, 128};
            int idx = 0;
            for (int i = 0; i < steps.length; i++) if (steps[i] == cfg.alertRange) idx = i;
            cfg.alertRange = steps[(idx + 1) % steps.length];
            cfg.save();
            rebuildWidgets();
        }).bounds(width / 2 - 40, by, 90, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Снять все (" + cfg.marked.size() + ")"), b -> {
            for (String s : new ArrayList<>(cfg.marked.keySet())) setMarked(UUID.fromString(s), "", false);
            rebuildWidgets();
        }).bounds(width / 2 + 54, by, 96, 20).build());
    }

    private void setMarked(UUID id, String name, boolean value) {
        GlowConfig cfg = GlowMarkClient.CONFIG;
        if (value) {
            cfg.mark(id, name);
        } else {
            cfg.unmark(id);
            if (minecraft.level != null) {
                Player p = minecraft.level.getPlayerByUUID(id);
                if (p != null) p.setGlowingTag(false);
            }
        }
    }
}
