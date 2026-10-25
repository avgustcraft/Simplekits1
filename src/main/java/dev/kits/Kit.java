package dev.kits;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class Kit {
    public final String name;
    public ItemStack icon;          // null = default chest
    public long cooldown = 86400;   // seconds. 0 = none, -1 = one time only
    public final List<ItemStack> items = new ArrayList<>();

    public Kit(String name) {
        this.name = name;
    }

    public String displayName() {
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }
}
