package dev.kits;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class KitManager {

    private final KitsPlugin plugin;
    private final Map<String, Kit> kits = new LinkedHashMap<>();
    private final Map<UUID, Map<String, Long>> claims = new HashMap<>();
    private final File kitsFile;
    private final File claimsFile;

    public KitManager(KitsPlugin plugin) {
        this.plugin = plugin;
        plugin.getDataFolder().mkdirs();
        this.kitsFile = new File(plugin.getDataFolder(), "kits.yml");
        this.claimsFile = new File(plugin.getDataFolder(), "claims.yml");
    }

    // ---------- kit access ----------

    public Collection<Kit> kits() {
        return kits.values();
    }

    public Kit get(String name) {
        return name == null ? null : kits.get(name.toLowerCase(Locale.ROOT));
    }

    public Kit create(String name) {
        Kit kit = new Kit(name.toLowerCase(Locale.ROOT));
        kits.put(kit.name, kit);
        return kit;
    }

    public void delete(String name) {
        kits.remove(name.toLowerCase(Locale.ROOT));
        for (Map<String, Long> m : claims.values()) m.remove(name.toLowerCase(Locale.ROOT));
        saveKits();
        saveClaims();
    }

    // ---------- giving ----------

    /** Gives the kit and records the claim. Leftovers drop at the player's feet. */
    public void give(Player p, Kit kit) {
        PlayerInventory inv = p.getInventory();
        for (ItemStack original : kit.items) {
            ItemStack item = original.clone();

            // auto-equip armor if that slot is empty
            EquipmentSlot slot = item.getType().getEquipmentSlot();
            if (slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST
                    || slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET) {
                ItemStack current = inv.getItem(slot);
                if ((current == null || current.getType().isAir()) && item.getAmount() == 1) {
                    inv.setItem(slot, item);
                    continue;
                }
            }

            Map<Integer, ItemStack> left = inv.addItem(item);
            for (ItemStack rest : left.values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), rest);
            }
        }
        claims.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>())
                .put(kit.name, System.currentTimeMillis());
        saveClaims();
    }

    /** 0 = ready, Long.MAX_VALUE = one-time kit already claimed, otherwise millis left. */
    public long remainingMillis(Player p, Kit kit) {
        if (p.hasPermission("kits.bypass")) return 0;
        Long last = claims.getOrDefault(p.getUniqueId(), Map.of()).get(kit.name);
        if (last == null) return 0;
        if (kit.cooldown < 0) return Long.MAX_VALUE;
        return Math.max(0, last + kit.cooldown * 1000L - System.currentTimeMillis());
    }

    public void resetClaims(UUID id, String kitName) {
        if (kitName == null) {
            claims.remove(id);
        } else {
            Map<String, Long> m = claims.get(id);
            if (m != null) m.remove(kitName.toLowerCase(Locale.ROOT));
        }
        saveClaims();
    }

    public static String formatTime(long millis) {
        long s = millis / 1000;
        long d = s / 86400, h = (s % 86400) / 3600, m = (s % 3600) / 60, sec = s % 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append("d ");
        if (h > 0) sb.append(h).append("h ");
        if (m > 0) sb.append(m).append("m ");
        if (sb.isEmpty() || (d == 0 && h == 0)) sb.append(sec).append("s");
        return sb.toString().trim();
    }

    // ---------- storage ----------

    public void load() {
        kits.clear();
        claims.clear();

        YamlConfiguration y = YamlConfiguration.loadConfiguration(kitsFile);
        ConfigurationSection sec = y.getConfigurationSection("kits");
        if (sec != null) {
            for (String name : sec.getKeys(false)) {
                ConfigurationSection k = sec.getConfigurationSection(name);
                if (k == null) continue;
                Kit kit = new Kit(name);
                kit.cooldown = k.getLong("cooldown", 86400);
                kit.icon = decode(k.getString("icon"));
                for (String s : k.getStringList("items")) {
                    ItemStack item = decode(s);
                    if (item != null) kit.items.add(item);
                }
                kits.put(name, kit);
            }
        }

        YamlConfiguration c = YamlConfiguration.loadConfiguration(claimsFile);
        for (String uuidStr : c.getKeys(false)) {
            ConfigurationSection s = c.getConfigurationSection(uuidStr);
            if (s == null) continue;
            try {
                Map<String, Long> m = new HashMap<>();
                for (String kitName : s.getKeys(false)) m.put(kitName, s.getLong(kitName));
                claims.put(UUID.fromString(uuidStr), m);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public void saveKits() {
        YamlConfiguration y = new YamlConfiguration();
        for (Kit kit : kits.values()) {
            String base = "kits." + kit.name;
            y.set(base + ".cooldown", kit.cooldown);
            if (kit.icon != null) y.set(base + ".icon", encode(kit.icon));
            List<String> list = new ArrayList<>();
            for (ItemStack item : kit.items) list.add(encode(item));
            y.set(base + ".items", list);
        }
        try {
            y.save(kitsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save kits.yml: " + e.getMessage());
        }
    }

    public void saveClaims() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, Map<String, Long>> e : claims.entrySet()) {
            for (Map.Entry<String, Long> k : e.getValue().entrySet()) {
                y.set(e.getKey() + "." + k.getKey(), k.getValue());
            }
        }
        try {
            y.save(claimsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save claims.yml: " + e.getMessage());
        }
    }

    private static String encode(ItemStack item) {
        return Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }

    private static ItemStack decode(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(s));
        } catch (Exception e) {
            return null;
        }
    }

    public static Material defaultIcon() {
        return Material.CHEST;
    }
}
