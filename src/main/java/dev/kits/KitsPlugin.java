package dev.kits;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class KitsPlugin extends JavaPlugin {

    private KitManager manager;

    @Override
    public void onEnable() {
        manager = new KitManager(this);
        manager.load();

        MenuListener menus = new MenuListener(manager);
        getServer().getPluginManager().registerEvents(menus, this);

        KitCommand kit = new KitCommand(manager, menus);
        PluginCommand kitCmd = getCommand("kit");
        if (kitCmd != null) {
            kitCmd.setExecutor(kit);
            kitCmd.setTabCompleter(kit);
        }

        KitAdminCommand admin = new KitAdminCommand(manager, menus);
        PluginCommand adminCmd = getCommand("kitadmin");
        if (adminCmd != null) {
            adminCmd.setExecutor(admin);
            adminCmd.setTabCompleter(admin);
        }

        getLogger().info("Loaded " + manager.kits().size() + " kit(s).");
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.saveKits();
            manager.saveClaims();
        }
    }

    public static void send(CommandSender to, String text, NamedTextColor color) {
        to.sendMessage(Component.text("[Kits] ", NamedTextColor.GOLD)
                .append(Component.text(text, color)));
    }

    public static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
}
