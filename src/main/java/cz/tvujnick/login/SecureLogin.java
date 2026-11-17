package cz.tvujnick.login;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class SecureLogin extends JavaPlugin implements Listener, CommandExecutor {

    private final Set<UUID> loggedInUsers = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("register").setExecutor(this);
        getCommand("login").setExecutor(this);
        getLogger().info("SecureLogin plugin byl úspěšně spuštěn!");
    }

    @Override
    public void onDisable() {
        getLogger().info("SecureLogin byl vypnut.");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        loggedInUsers.remove(uuid);

        if (getConfig().contains("users." + uuid.toString())) {
            player.sendMessage(ChatColor.YELLOW + "🔐 Prosím přihlas se příkazem: /login <heslo>");
        } else {
            player.sendMessage(ChatColor.YELLOW + "📝 Prosím zaregistruj se příkazem: /register <heslo>");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        loggedInUsers.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        checkUnlogged(event.getPlayer(), event);
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        checkUnlogged(event.getPlayer(), event);
    }

    @EventHandler
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage().toLowerCase();
        if (!msg.startsWith("/login") && !msg.startsWith("/register")) {
            checkUnlogged(event.getPlayer(), event);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        checkUnlogged(event.getPlayer(), event);
    }

    private void checkUnlogged(Player player, org.bukkit.event.Cancellable event) {
        UUID uuid = player.getUniqueId();
        boolean isRegistered = getConfig().contains("users." + uuid.toString());
        
        if (!isRegistered || !loggedInUsers.contains(uuid)) {
            event.setCancelled(true);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Tento příkaz mohou používat pouze hráči.");
            return true;
        }

        Player player = (Player) sender;
        UUID uuid = player.getUniqueId();
        String path = "users." + uuid.toString();

        if (command.getName().equalsIgnoreCase("register")) {
            if (getConfig().contains(path)) {
                player.sendMessage(ChatColor.RED + "Už jsi zaregistrovaný! Použij /login.");
                return true;
            }
            if (args.length == 0) {
                player.sendMessage(ChatColor.RED + "Použití: /register <heslo>");
                return true;
            }

            String hashedPassword = hashPassword(args[0]);
            getConfig().set(path, hashedPassword);
            saveConfig();
            
            loggedInUsers.add(uuid);
            player.sendMessage(ChatColor.GREEN + "✅ Úspěšně ses zaregistroval a přihlásil!");
            return true;
        }

        if (command.getName().equalsIgnoreCase("login")) {
            if (!getConfig().contains(path)) {
                player.sendMessage(ChatColor.RED + "Nejsi zaregistrovaný! Použij nejdřív /register.");
                return true;
            }
            if (loggedInUsers.contains(uuid)) {
                player.sendMessage(ChatColor.YELLOW + "⚠️ Už jsi přihlášený!");
                return true;
            }
            if (args.length == 0) {
                player.sendMessage(ChatColor.RED + "Použití: /login <heslo>");
                return true;
            }

            String storedHash = getConfig().getString(path);
            String inputHash = hashPassword(args[0]);

            if (storedHash != null && storedHash.equals(inputHash)) {
                loggedInUsers.add(uuid);
                player.sendMessage(ChatColor.GREEN + "🔓 Úspěšně ses přihlásil!");
            } else {
                player.sendMessage(ChatColor.RED + "❌ Nesprávné heslo!");
            }
            return true;
        }

        return false;
    }

    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * encodedhash.length);
            for (byte b : encodedhash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
