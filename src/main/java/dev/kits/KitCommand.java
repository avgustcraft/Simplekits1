package dev.kits;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class KitCommand implements CommandExecutor, TabCompleter {

    private final KitManager manager;
    private final MenuListener menus;

    public KitCommand(KitManager manager, MenuListener menus) {
        this.manager = manager;
        this.menus = menus;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            KitsPlugin.send(sender, "Players only.", NamedTextColor.RED);
            return true;
        }
        if (args.length == 0) {
            menus.openMenu(p);
            return true;
        }
        Kit kit = manager.get(args[0]);
        if (kit == null) {
            KitsPlugin.send(p, "Unknown kit. Use /kit to see the list.", NamedTextColor.RED);
            return true;
        }
        menus.claim(p, kit);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String start = args[0].toLowerCase(Locale.ROOT);
            for (Kit k : manager.kits()) if (k.name.startsWith(start)) out.add(k.name);
        }
        return out;
    }
}
