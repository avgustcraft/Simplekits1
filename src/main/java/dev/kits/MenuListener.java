package dev.kits;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public final class MenuListener implements Listener {

    // ---- holders identify our inventories ----
    private static final class MainMenu implements InventoryHolder {
        Inventory inv;
        final Map<Integer, String> slots = new HashMap<>();
        @Override public Inventory getInventory() { return inv; }
    }

    private static final class PreviewMenu implements InventoryHolder {
        Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    private static final class EditorMenu implements InventoryHolder {
        Inventory inv;
        final String kit;
        EditorMenu(String kit) { this.kit = kit; }
        @Override public Inventory getInventory() { return inv; }
    }

    private final KitManager manager;

    public MenuListener(KitManager manager) {
        this.manager = manager;
    }

    // ---------- opening ----------

    public void openMenu(Player p) {
        List<Kit> list = new ArrayList<>(manager.kits());
        int size = Math.min(54, Math.max(9, (int) Math.ceil(list.size() / 9.0) * 9));

        MainMenu holder = new MainMenu();
        Inventory inv = Bukkit.createInventory(holder, size,
                Component.text("Kits", NamedTextColor.DARK_GRAY));
        holder.inv = inv;

        for (int i = 0; i < list.size() && i < 54; i++) {
            Kit kit = list.get(i);
            inv.setItem(i, buildIcon(p, kit));
            holder.slots.put(i, kit.name);
        }
        p.openInventory(inv);
    }

    public void openPreview(Player p, Kit kit) {
        PreviewMenu holder = new PreviewMenu();
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text("Preview: " + kit.displayName(), NamedTextColor.DARK_GRAY));
        holder.inv = inv;
        for (int i = 0; i < kit.items.size() && i < 54; i++) {
            inv.setItem(i, kit.items.get(i).clone());
        }
        p.openInventory(inv);
    }

    public void openEditor(Player p, Kit kit) {
        EditorMenu holder = new EditorMenu(kit.name);
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text("Editing: " + kit.displayName(), NamedTextColor.DARK_RED));
        holder.inv = inv;
        for (int i = 0; i < kit.items.size() && i < 54; i++) {
            inv.setItem(i, kit.items.get(i).clone());
        }
        p.openInventory(inv);
        KitsPlugin.send(p, "Put items in the chest, then close it to save.", NamedTextColor.YELLOW);
    }

    // ---------- claiming ----------

    public boolean claim(Player p, Kit kit) {
        if (kit.items.isEmpty()) {
            KitsPlugin.send(p, "That kit is empty.", NamedTextColor.RED);
            return false;
        }
        long rem = manager.remainingMillis(p, kit);
        if (rem == Long.MAX_VALUE) {
            KitsPlugin.send(p, "You can only claim this kit once.", NamedTextColor.RED);
            return false;
        }
        if (rem > 0) {
            KitsPlugin.send(p, "The " + kit.displayName() + " kit is on cooldown: "
                    + KitManager.formatTime(rem), NamedTextColor.RED);
            return false;
        }
        manager.give(p, kit);
        KitsPlugin.send(p, "You claimed the " + kit.displayName() + " kit!", NamedTextColor.GREEN);
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
        return true;
    }

    private ItemStack buildIcon(Player p, Kit kit) {
        ItemStack it = kit.icon != null ? kit.icon.clone() : new ItemStack(KitManager.defaultIcon());
        it.setAmount(1);

        long rem = manager.remainingMillis(p, kit);
        List<Component> lore = new ArrayList<>();
        lore.add(KitsPlugin.line(kit.items.size() + " item stack(s)", NamedTextColor.GRAY));
        lore.add(Component.empty());
        if (rem == 0) {
            lore.add(KitsPlugin.line("Ready to claim!", NamedTextColor.GREEN));
        } else if (rem == Long.MAX_VALUE) {
            lore.add(KitsPlugin.line("Already claimed (one-time kit)", NamedTextColor.RED));
        } else {
            lore.add(KitsPlugin.line("Cooldown: " + KitManager.formatTime(rem), NamedTextColor.RED));
        }
        lore.add(Component.empty());
        lore.add(KitsPlugin.line("Left click: claim", NamedTextColor.YELLOW));
        lore.add(KitsPlugin.line("Right click: preview", NamedTextColor.YELLOW));
        if (p.hasPermission("kits.admin")) {
            lore.add(KitsPlugin.line("Shift + right click: edit (OP)", NamedTextColor.DARK_RED));
        }

        it.editMeta(m -> {
            m.displayName(KitsPlugin.line(kit.displayName(), NamedTextColor.GOLD));
            m.lore(lore);
        });
        return it;
    }

    // ---------- events ----------

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();

        if (holder instanceof PreviewMenu) {
            e.setCancelled(true);
            return;
        }

        if (holder instanceof MainMenu menu) {
            e.setCancelled(true);
            if (e.getClickedInventory() != top) return;
            if (!(e.getWhoClicked() instanceof Player p)) return;

            String name = menu.slots.get(e.getSlot());
            Kit kit = manager.get(name);
            if (kit == null) return;

            ClickType click = e.getClick();
            if (click == ClickType.SHIFT_RIGHT && p.hasPermission("kits.admin")) {
                openEditor(p, kit);
            } else if (click.isRightClick()) {
                openPreview(p, kit);
            } else if (click.isLeftClick()) {
                claim(p, kit);
                top.setItem(e.getSlot(), buildIcon(p, kit)); // refresh cooldown text
            }
            return;
        }

        if (holder instanceof EditorMenu && !e.getWhoClicked().hasPermission("kits.admin")) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        InventoryHolder holder = e.getView().getTopInventory().getHolder();
        if (holder instanceof MainMenu || holder instanceof PreviewMenu) {
            e.setCancelled(true);
        } else if (holder instanceof EditorMenu && !e.getWhoClicked().hasPermission("kits.admin")) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof EditorMenu editor)) return;
        if (!e.getPlayer().hasPermission("kits.admin")) return;

        Kit kit = manager.get(editor.kit);
        if (kit == null) return;

        kit.items.clear();
        for (ItemStack item : e.getInventory().getContents()) {
            if (item != null && !item.getType().isAir()) kit.items.add(item.clone());
        }
        manager.saveKits();
        KitsPlugin.send(e.getPlayer(), "Saved kit " + kit.displayName() + " ("
                + kit.items.size() + " stacks).", NamedTextColor.GREEN);
    }
}
