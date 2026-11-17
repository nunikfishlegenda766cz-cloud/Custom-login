package com.example.simplelogin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class SimpleLogin extends JavaPlugin implements Listener, CommandExecutor {

    private final Set<UUID> loggedIn = new HashSet<>();
    private File dataFile;
    private FileConfiguration dataConfig;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("register").setExecutor(this);
        getCommand("login").setExecutor(this);

        createDataFile();
        getLogger().info("SimpleLogin byl úspěšně spuštěn!");
    }

    @Override
    public void onDisable() {
        loggedIn.clear();
    }

    private void createDataFile() {
        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            dataFile.getParentFile().mkdirs();
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void saveData() {
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : encodedhash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        
        if (!dataConfig.contains("users." + uuid.toString())) {
            player.sendMessage(ChatColor.YELLOW + "Zaregistruj se příkazem: /register <heslo> <heslo>");
        } else {
            player.sendMessage(ChatColor.YELLOW + "Přihlas se příkazem: /login <heslo>");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        loggedIn.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!loggedIn.contains(event.getPlayer().getUniqueId())) {
            Location from = event.getFrom();
            Location to = event.getTo();
            if (to != null && (from.getX() != to.getX() || from.getZ() != to.getZ())) {
                event.setTo(from);
            }
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        if (!loggedIn.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "Nejprve se musíš přihlásit nebo zaregistrovat!");
        }
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!loggedIn.contains(event.getPlayer().getUniqueId())) {
            String msg = event.getMessage().toLowerCase();
            if (!msg.startsWith("/login") && !msg.startsWith("/register")) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(ChatColor.RED + "Před přihlášením můžeš použít pouze /login nebo /register!");
            }
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
        String name = command.getName().toLowerCase();

        if (name.equals("register")) {
            if (dataConfig.contains("users." + uuid.toString())) {
                player.sendMessage(ChatColor.RED + "Už jsi zaregistrovaný! Použij /login <heslo>.");
                return true;
            }
            if (args.length < 2) {
                player.sendMessage(ChatColor.RED + "Použití: /register <heslo> <potvrzení_hesla>");
                return true;
            }
            if (!args[0].equals(args[1])) {
                player.sendMessage(ChatColor.RED + "Hesla se neshodují!");
                return true;
            }
            if (args.length > 0 && args[0].length() < 4) {
                player.sendMessage(ChatColor.RED + "Heslo musí mít alespoň 4 znaky!");
                return true;
            }

            dataConfig.set("users." + uuid.toString(), hashPassword(args[0]));
            saveData();
            loggedIn.add(uuid);
            player.sendMessage(ChatColor.GREEN + "Úspěšně ses zaregistroval a přihlásil!");
            return true;
        }

        if (name.equals("login")) {
            if (!dataConfig.contains("users." + uuid.toString())) {
                player.sendMessage(ChatColor.RED + "Nejprve se musíš zaregistrovat pomocí /register <heslo> <heslo>.");
                return true;
            }
            if (loggedIn.contains(uuid)) {
                player.sendMessage(ChatColor.RED + "Už jsi přihlášený!");
                return true;
            }
            if (args.length < 1) {
                player.sendMessage(ChatColor.RED + "Použití: /login <heslo>");
                return true;
            }

            String storedHash = dataConfig.getString("users." + uuid.toString());
            String inputHash = hashPassword(args[0]);

            if (storedHash != null && storedHash.equals(inputHash)) {
                loggedIn.add(uuid);
                player.sendMessage(ChatColor.GREEN + "Úspěšně ses přihlásil!");
            } else {
                player.sendMessage(ChatColor.RED + "Nesprávné heslo!");
            }
            return true;
        }

        return false;
    }
}
