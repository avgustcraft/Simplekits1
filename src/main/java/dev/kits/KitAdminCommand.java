package dev.kits;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class KitAdminCommand implements CommandExecutor, TabCompleter {

    private static final Pattern VALID_NAME = Pattern.compile("[a-z0-9_-]{1,24}");
    private static final List<String> SUBS =
            List.of("create", "edit", "delete", "icon", "cooldown", "list", "reset", "reload");

    private final KitManager manager;
    private final MenuListener menus;

    public KitAdminCommand(KitManager manager, MenuListener menus) {
        this.manager = manager;
        this.menus = menus;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("kits.admin")) {
            KitsPlugin.send(sender, "Only OPs can edit kits.", NamedTextColor.RED);
            return true;
        }
        if (args.length == 0) {
            help(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> {
                if (manager.kits().isEmpty()) {
                    KitsPlugin.send(sender, "No kits yet.", NamedTextColor.YELLOW);
                } else {
                    for (Kit k : manager.kits()) {
                        KitsPlugin.send(sender, k.name + " - " + k.items.size()
                                + " stacks, cooldown " + (k.cooldown < 0 ? "one-time" : k.cooldown + "s"),
                                NamedTextColor.YELLOW);
                    }
                }
            }
            case "reload" -> {
                manager.load();
                KitsPlugin.send(sender, "Reloaded " + manager.kits().size() + " kit(s).", NamedTextColor.GREEN);
            }
            case "reset" -> {
                if (args.length < 2) {
                    KitsPlugin.send(sender, "Usage: /kitadmin reset <player> [kit]", NamedTextColor.RED);
                    return true;
                }
                OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[1]);
                if (target == null) {
                    KitsPlugin.send(sender, "Player not found.", NamedTextColor.RED);
                    return true;
                }
                manager.resetClaims(target.getUniqueId(), args.length > 2 ? args[2] : null);
                KitsPlugin.send(sender, "Cooldowns reset for " + args[1] + ".", NamedTextColor.GREEN);
            }
            case "create", "edit", "delete", "icon", "cooldown" -> {
                if (args.length < 2) {
                    KitsPlugin.send(sender, "Usage: /kitadmin " + sub + " <kit>", NamedTextColor.RED);
                    return true;
                }
                handleKitSub(sender, sub, args);
            }
            default -> help(sender);
        }
        return true;
    }

    private void handleKitSub(CommandSender sender, String sub, String[] args) {
        String name = args[1].toLowerCase(Locale.ROOT);
        Kit kit = manager.get(name);

        if (sub.equals("create")) {
            if (!(sender instanceof Player p)) {
                KitsPlugin.send(sender, "Players only.", NamedTextColor.RED);
                return;
            }
            if (!VALID_NAME.matcher(name).matches()) {
                KitsPlugin.send(p, "Name must be 1-24 chars: a-z, 0-9, _ or -", NamedTextColor.RED);
                return;
            }
            if (kit != null) {
                KitsPlugin.send(p, "That kit exists. Use /kitadmin edit " + name, NamedTextColor.RED);
                return;
            }
            kit = manager.create(name);
            for (ItemStack item : p.getInventory().getContents()) {
                if (item != null && !item.getType().isAir()) kit.items.add(item.clone());
            }
            if (!kit.items.isEmpty()) {
                ItemStack icon = new ItemStack(kit.items.get(0).getType());
                kit.icon = icon;
            }
            manager.saveKits();
            KitsPlugin.send(p, "Created kit '" + name + "' from your inventory ("
                    + kit.items.size() + " stacks). Set the icon with /kitadmin icon " + name,
                    NamedTextColor.GREEN);
            return;
        }

        if (kit == null) {
            KitsPlugin.send(sender, "Unknown kit '" + name + "'.", NamedTextColor.RED);
            return;
        }

        switch (sub) {
            case "edit" -> {
                if (sender instanceof Player p) menus.openEditor(p, kit);
                else KitsPlugin.send(sender, "Players only.", NamedTextColor.RED);
            }
            case "delete" -> {
                manager.delete(name);
                KitsPlugin.send(sender, "Deleted kit '" + name + "'.", NamedTextColor.GREEN);
            }
            case "icon" -> {
                if (!(sender instanceof Player p)) {
                    KitsPlugin.send(sender, "Players only.", NamedTextColor.RED);
                    return;
                }
                ItemStack hand = p.getInventory().getItemInMainHand();
                if (hand.getType().isAir()) {
                    KitsPlugin.send(p, "Hold an item to use as the icon.", NamedTextColor.RED);
                    return;
                }
                kit.icon = new ItemStack(hand.getType());
                manager.saveKits();
                KitsPlugin.send(p, "Icon updated.", NamedTextColor.GREEN);
            }
            case "cooldown" -> {
                if (args.length < 3) {
                    KitsPlugin.send(sender, "Usage: /kitadmin cooldown <kit> <seconds>  (0 = none, -1 = one-time)",
                            NamedTextColor.RED);
                    return;
                }
                try {
                    kit.cooldown = Long.parseLong(args[2]);
                    manager.saveKits();
                    KitsPlugin.send(sender, "Cooldown set to " + kit.cooldown + "s.", NamedTextColor.GREEN);
                } catch (NumberFormatException ex) {
                    KitsPlugin.send(sender, "Invalid number.", NamedTextColor.RED);
                }
            }
            default -> { }
        }
    }

    private void help(CommandSender s) {
        KitsPlugin.send(s, "/kitadmin create <kit>  - make kit from your inventory", NamedTextColor.YELLOW);
        KitsPlugin.send(s, "/kitadmin edit <kit>    - edit contents in a chest GUI", NamedTextColor.YELLOW);
        KitsPlugin.send(s, "/kitadmin icon <kit>    - use held item as icon", NamedTextColor.YELLOW);
        KitsPlugin.send(s, "/kitadmin cooldown <kit> <sec> - 0 none, -1 one-time", NamedTextColor.YELLOW);
        KitsPlugin.send(s, "/kitadmin delete <kit>", NamedTextColor.YELLOW);
        KitsPlugin.send(s, "/kitadmin reset <player> [kit]", NamedTextColor.YELLOW);
        KitsPlugin.send(s, "/kitadmin list | reload", NamedTextColor.YELLOW);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("kits.admin")) return out;
        if (args.length == 1) {
            for (String s : SUBS) if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (List.of("edit", "delete", "icon", "cooldown").contains(sub)) {
                for (Kit k : manager.kits()) if (k.name.startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(k.name);
            } else if (sub.equals("reset")) {
                for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("reset")) {
            for (Kit k : manager.kits()) out.add(k.name);
        }
        return out;
    }
}
