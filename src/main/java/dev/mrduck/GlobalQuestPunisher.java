package dev.mrduck;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class GlobalQuestPunisher extends JavaPlugin {
    private QuestManager questManager;
    private QuestScoreboard scoreboard;
    
    public QuestScoreboard getScoreboard() { return scoreboard; }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        createDataFolder();

        questManager = new QuestManager(this);
        getServer().getPluginManager().registerEvents(questManager, this);
        this.scoreboard = new QuestScoreboard(this);

        questManager.startQuestTimer();
        getLogger().info("GlobalQuestPunisher Enabled Successfully.");
    }

    @Override
    public void onDisable() {
        if (questManager != null) {
            questManager.shutdown(); // Use the proper shutdown method
        }
        getLogger().info("GlobalQuestPunisher Disabled Successfully.");
    }

    private void createDataFolder() {
        // Create the main data folder
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        
        // Create the global quest data file
        File dataFile = new File(getDataFolder(), "GlobalQuestData.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
                getLogger().info("Created new GlobalQuestData.yml file");
            } catch (Exception ex) {
                getLogger().warning("Could not create GlobalQuestData.yml file: " + ex.getMessage());
            }
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("quest") || cmd.getName().equalsIgnoreCase("globalquest")) {
            
            // Console commands
            if (!(sender instanceof Player)) {
                if (args.length >= 1) {
                    switch (args[0].toLowerCase()) {
                        case "new", "generate" -> {
                            questManager.generateNewGlobalQuest();
                            sender.sendMessage("§aGenerated new global quest!");
                            return true;
                        }
                        case "status", "info" -> {
                            Quest current = questManager.getCurrentGlobalQuest();
                            if (current != null) {
                                sender.sendMessage("Current Quest: " + current.name());
                                sender.sendMessage("Progress: " + questManager.getCompletedCount() + " players completed");
                                sender.sendMessage("Time left: " + (questManager.getTimeLeft() / (60 * 60 * 1000)) + " hours");
                            } else {
                                sender.sendMessage("No active quest");
                            }
                            return true;
                        }
                    }
                }
                sender.sendMessage("Console commands: /quest new, /quest status");
                return true;
            }

            Player player = (Player) sender;
            
            // Handle different command arguments
            if (args.length == 0) {
                // Show quest info
                questManager.showQuestInfo(player);
                return true;
            }
            
            switch (args[0].toLowerCase()) {
                case "top", "leaderboard" -> {
                    showTopPlayers(player);
                    return true;
                }
                case "status", "info" -> {
                    questManager.showQuestInfo(player);
                    return true;
                }
                case "new", "generate" -> {
                    if (!player.hasPermission("globalquest.admin")) {
                        player.sendMessage("§cBạn không có quyền sử dụng lệnh này!");
                        return true;
                    }
                    questManager.generateNewGlobalQuest();
                    player.sendMessage("§aĐã tạo quest toàn cầu mới!");
                    return true;
                }
                case "complete" -> {
                    if (!player.hasPermission("globalquest.admin")) {
                        player.sendMessage("§cBạn không có quyền sử dụng lệnh này!");
                        return true;
                    }
                    if (args.length >= 2) {
                        Player target = Bukkit.getPlayer(args[1]);
                        if (target != null) {
                            questManager.forceCompleteQuest(target);
                            player.sendMessage("§aĐã force complete quest cho " + target.getName());
                        } else {
                            player.sendMessage("§cKhông tìm thấy người chơi: " + args[1]);
                        }
                    } else {
                        questManager.forceCompleteQuest(player);
                        player.sendMessage("§aĐã force complete quest cho bạn!");
                    }
                    return true;
                }
                case "addprogress", "add" -> {
                    if (!player.hasPermission("globalquest.admin")) {
                        player.sendMessage("§cBạn không có quyền sử dụng lệnh này!");
                        return true;
                    }
                    if (args.length >= 2) {
                        try {
                            int amount = Integer.parseInt(args[1]);
                            Player target = args.length >= 3 ? Bukkit.getPlayer(args[2]) : player;
                            if (target != null) {
                                questManager.addProgress(target, amount);
                                player.sendMessage("§aĐã thêm " + amount + " progress cho " + target.getName());
                            } else {
                                player.sendMessage("§cKhông tìm thấy người chơi: " + args[2]);
                            }
                        } catch (NumberFormatException e) {
                            player.sendMessage("§cSố không hợp lệ: " + args[1]);
                        }
                    } else {
                        player.sendMessage("§cSử dụng: /quest addprogress <số> [player]");
                    }
                    return true;
                }
                case "reset" -> {
                    if (!player.hasPermission("globalquest.admin")) {
                        player.sendMessage("§cBạn không có quyền sử dụng lệnh này!");
                        return true;
                    }
                    if (args.length >= 2) {
                        Player target = Bukkit.getPlayer(args[1]);
                        if (target != null) {
                            questManager.resetPlayerProgress(target);
                            player.sendMessage("§aĐã reset progress cho " + target.getName());
                        } else {
                            player.sendMessage("§cKhông tìm thấy người chơi: " + args[1]);
                        }
                    } else {
                        questManager.resetPlayerProgress(player);
                        player.sendMessage("§aĐã reset progress cho bạn!");
                    }
                    return true;
                }
                case "help" -> {
                    showHelp(player);
                    return true;
                }
                default -> {
                    player.sendMessage("§cLệnh không hợp lệ! Sử dụng /quest help để xem danh sách lệnh.");
                    return true;
                }
            }
        }

        return false;
    }

    private void showTopPlayers(Player player) {
        Quest currentQuest = questManager.getCurrentGlobalQuest();
        if (currentQuest == null) {
            player.sendMessage("§cKhông có quest nào đang hoạt động!");
            return;
        }

        // Get all online players and their progress
        List<Map.Entry<String, Integer>> playerProgress = new ArrayList<>();
        
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            int progress = questManager.getPlayerProgress(onlinePlayer.getUniqueId());
            if (progress > 0 || questManager.hasPlayerCompleted(onlinePlayer.getUniqueId())) {
                playerProgress.add(new AbstractMap.SimpleEntry<>(onlinePlayer.getName(), progress));
            }
        }
        
        // Sort by progress (descending)
        playerProgress.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        
        player.sendMessage("§6§l=== TOP NGƯỜI CHƠI ===");
        player.sendMessage("§eQuest: §f" + currentQuest.name());
        player.sendMessage("§7━━━━━━━━━━━━━━━━━━━━");
        
        if (playerProgress.isEmpty()) {
            player.sendMessage("§7Chưa có người chơi nào có tiến độ");
        } else {
            int rank = 1;
            for (int i = 0; i < Math.min(10, playerProgress.size()); i++) {
                Map.Entry<String, Integer> entry = playerProgress.get(i);
                String playerName = entry.getKey();
                int progress = entry.getValue();
                
                String status = questManager.hasPlayerCompleted(Bukkit.getPlayer(playerName).getUniqueId()) ? 
                    " §a✅" : "";
                
                double percentage = (double) progress / currentQuest.target() * 100;
                
                player.sendMessage(String.format("§e%d. §f%s§7: §a%d§7/§f%d §7(%.1f%%)%s", 
                    rank, playerName, progress, currentQuest.target(), percentage, status));
                rank++;
            }
        }
        
        player.sendMessage("§7━━━━━━━━━━━━━━━━━━━━");
        
        // Show player's own progress
        int playerProgress_own = questManager.getPlayerProgress(player.getUniqueId());
        boolean completed = questManager.hasPlayerCompleted(player.getUniqueId());
        
        if (completed) {
            player.sendMessage("§aTiến độ của bạn: §l✅ ĐÃ HOÀN THÀNH!");
        } else {
            double percentage = (double) playerProgress_own / currentQuest.target() * 100;
            player.sendMessage(String.format("§eTiến độ của bạn: §a%d§7/§f%d §7(%.1f%%)", 
                playerProgress_own, currentQuest.target(), percentage));
        }
    }

    private void showHelp(Player player) {
        player.sendMessage("§6§l=== LỆNH QUEST ===");
        player.sendMessage("§e/quest §7- Xem thông tin quest hiện tại");
        player.sendMessage("§e/quest status §7- Xem trạng thái quest");
        player.sendMessage("§e/quest top §7- Xem bảng xếp hạng");
        player.sendMessage("§e/quest help §7- Hiển thị trợ giúp này");
        
        if (player.hasPermission("globalquest.admin")) {
            player.sendMessage("§c§l=== LỆNH ADMIN ===");
            player.sendMessage("§c/quest new §7- Tạo quest mới");
            player.sendMessage("§c/quest complete [player] §7- Force complete quest");
            player.sendMessage("§c/quest addprogress <số> [player] §7- Thêm progress");
            player.sendMessage("§c/quest reset [player] §7- Reset progress");
        }
    }

    public String getTopPlayerName() {
        Quest currentQuest = questManager.getCurrentGlobalQuest();
        if (currentQuest == null) return "Chưa có";
        
        Player topPlayer = null;
        int maxProgress = 0;
        
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            int progress = questManager.getPlayerProgress(onlinePlayer.getUniqueId());
            if (progress > maxProgress) {
                maxProgress = progress;
                topPlayer = onlinePlayer;
            }
        }
        
        return topPlayer != null ? topPlayer.getName() : "Chưa có";
    }

    public QuestManager getQuestManager() {
        return questManager;
    }

    // Utility methods for other classes
    public int getTotalOnlinePlayers() {
        return Bukkit.getOnlinePlayers().size();
    }
    
    public int getCompletedPlayersCount() {
        return questManager.getCompletedCount();
    }
}