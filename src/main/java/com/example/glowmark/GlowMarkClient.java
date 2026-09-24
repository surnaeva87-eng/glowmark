package com.example.glowmark;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class GlowMarkClient implements ClientModInitializer {
    public static GlowConfig CONFIG;

    private static KeyMapping openKey;
    private static KeyMapping toggleKey;

    private int tick;
    private ClientLevel lastLevel;
    private boolean primed;
    private Set<UUID> lastOnline = new HashSet<>();
    private Set<UUID> lastNear = new HashSet<>();

    @Override
    public void onInitializeClient() {
        CONFIG = GlowConfig.load();

        openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.glowmark.open", InputConstants.Type.KEYBOARD, InputConstants.KEY_Y, KeyMapping.Category.MISC));
        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.glowmark.toggle", InputConstants.Type.KEYBOARD, InputConstants.KEY_H, KeyMapping.Category.MISC));

        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    private void onTick(Minecraft mc) {
        while (openKey.consumeClick()) {
            if (mc.player != null && mc.gui.screen() == null) mc.gui.setScreen(new MarkScreen());
        }
        while (toggleKey.consumeClick()) {
            setGlow(mc, !CONFIG.glow);
            if (mc.player != null) {
                mc.gui.hud.setOverlayMessage(Component.literal(
                        "[GlowMark] Подсветка: " + (CONFIG.glow ? "ВКЛ" : "ВЫКЛ")), false);
            }
        }

        if (mc.level == null || mc.player == null || mc.getConnection() == null) {
            lastLevel = null;
            return;
        }
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            lastOnline = new HashSet<>();
            lastNear = new HashSet<>();
            primed = false;
        }

        // Подсветка: сервер сбрасывает флаг при обновлении метаданных, поэтому ставим каждый тик.
        if (CONFIG.glow && !CONFIG.marked.isEmpty()) {
            for (Player p : mc.level.players()) {
                if (p != mc.player && CONFIG.isMarked(p.getUUID())) p.setGlowingTag(true);
            }
        }

        if (++tick % 10 != 0) return;
        scanOnline(mc);
        scanNear(mc);
        radar(mc);
    }

    /** Вход/выход отмеченных игроков с сервера. */
    private void scanOnline(Minecraft mc) {
        Map<UUID, String> online = new HashMap<>();
        for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
            online.put(info.getProfile().id(), info.getProfile().name());
        }
        if (primed) {
            for (Map.Entry<UUID, String> e : online.entrySet()) {
                if (CONFIG.isMarked(e.getKey())) {
                    // обновляем имя, если игрок сменил ник
                    if (!e.getValue().equals(CONFIG.nameOf(e.getKey()))) CONFIG.mark(e.getKey(), e.getValue());
                    if (!lastOnline.contains(e.getKey())) alert(mc, "§a" + e.getValue() + " зашёл на сервер");
                }
            }
            for (UUID id : lastOnline) {
                if (!online.containsKey(id) && CONFIG.isMarked(id)) alert(mc, "§c" + CONFIG.nameOf(id) + " вышел с сервера");
            }
        }
        lastOnline = new HashSet<>(online.keySet());
        primed = true;
    }

    /** Отмеченный игрок вошёл в радиус alertRange. */
    private void scanNear(Minecraft mc) {
        Set<UUID> near = new HashSet<>();
        for (Player p : mc.level.players()) {
            if (p == mc.player || !CONFIG.isMarked(p.getUUID())) continue;
            if (mc.player.distanceTo(p) <= CONFIG.alertRange) near.add(p.getUUID());
        }
        for (UUID id : near) {
            if (!lastNear.contains(id)) {
                alert(mc, "§e" + CONFIG.nameOf(id) + " рядом (в пределах " + CONFIG.alertRange + " блоков)");
            }
        }
        lastNear = near;
    }

    /** Строка в action bar: ближайшие отмеченные игроки и дистанция. */
    private void radar(Minecraft mc) {
        if (!CONFIG.radar || mc.gui.screen() != null || CONFIG.marked.isEmpty()) return;
        List<Player> list = new ArrayList<>();
        for (Player p : mc.level.players()) {
            if (p != mc.player && CONFIG.isMarked(p.getUUID())) list.add(p);
        }
        if (list.isEmpty()) return;
        list.sort((a, b) -> Float.compare(mc.player.distanceTo(a), mc.player.distanceTo(b)));
        StringBuilder sb = new StringBuilder("§6★ ");
        int n = Math.min(3, list.size());
        for (int i = 0; i < n; i++) {
            Player p = list.get(i);
            if (i > 0) sb.append("§7 | §6");
            sb.append(p.getName().getString()).append(" §f").append(Math.round(mc.player.distanceTo(p))).append("м");
        }
        mc.gui.hud.setOverlayMessage(Component.literal(sb.toString()), false);
    }

    private void alert(Minecraft mc, String text) {
        if (!CONFIG.alerts) return;
        mc.player.sendSystemMessage(Component.literal("§8[§6GlowMark§8] §r" + text));
        if (CONFIG.sound) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F));
        }
    }

    /** Включить/выключить подсветку; при выключении сразу снимаем флаг с отмеченных. */
    public static void setGlow(Minecraft mc, boolean on) {
        CONFIG.glow = on;
        CONFIG.save();
        if (!on && mc.level != null) {
            for (Player p : mc.level.players()) {
                if (CONFIG.isMarked(p.getUUID())) p.setGlowingTag(false);
            }
        }
    }
}
