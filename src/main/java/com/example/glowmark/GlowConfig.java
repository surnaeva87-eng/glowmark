package com.example.glowmark;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Настройки и список отмеченных игроков. Сохраняется в config/glowmark.json. */
public class GlowConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public boolean glow = true;      // подсветка отмеченных
    public boolean alerts = true;    // сообщения о входе/выходе/приближении
    public boolean sound = true;     // звук при оповещении
    public boolean radar = true;     // дистанция в action bar
    public int alertRange = 48;      // радиус "приближения" в блоках

    /** UUID -> последнее известное имя. */
    public Map<String, String> marked = new LinkedHashMap<>();

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("glowmark.json");
    }

    public static GlowConfig load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                GlowConfig c = GSON.fromJson(Files.readString(p), GlowConfig.class);
                if (c != null) {
                    if (c.marked == null) c.marked = new LinkedHashMap<>();
                    return c;
                }
            }
        } catch (Exception ignored) {
        }
        return new GlowConfig();
    }

    public void save() {
        try {
            Files.writeString(path(), GSON.toJson(this));
        } catch (IOException ignored) {
        }
    }

    public boolean isMarked(UUID id) {
        return marked.containsKey(id.toString());
    }

    public void mark(UUID id, String name) {
        marked.put(id.toString(), name);
        save();
    }

    public void unmark(UUID id) {
        marked.remove(id.toString());
        save();
    }

    public String nameOf(UUID id) {
        return marked.getOrDefault(id.toString(), "?");
    }
}
