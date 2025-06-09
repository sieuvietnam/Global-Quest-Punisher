package dev.mrduck;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

public class QuestScoreboard {
    private final GlobalQuestPunisher plugin;
    private final ScoreboardManager manager;

    public QuestScoreboard(GlobalQuestPunisher plugin) {
        this.plugin = plugin;
        this.manager = Bukkit.getScoreboardManager();
    }

    public void update(Player player) {
        if (manager == null) return;
        
        Scoreboard scoreboard = manager.getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective("globalquest", "dummy","§6§l"+ "Nhiệm vụ hàng tuần");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        Quest currentQuest = plugin.getQuestManager().getCurrentGlobalQuest();
        if (currentQuest == null) {
            objective.getScore("§cKhông có quest").setScore(1);
            player.setScoreboard(scoreboard);
            return;
        }

        int line = 15;

        // Empty line
        objective.getScore(" ").setScore(line--);

        // Quest name (truncated if too long)
        String questName = currentQuest.name();
        if (questName.length() > 30) {
            questName = questName.substring(0, 27) + "...";
        }
        objective.getScore("§e" + questName).setScore(line--);
        
        // Empty line
        objective.getScore("  ").setScore(line--);
        
        // Player progress
        int playerProgress = plugin.getQuestManager().getPlayerProgress(player.getUniqueId());
        boolean completed = plugin.getQuestManager().hasPlayerCompleted(player.getUniqueId());
        
        if (completed) {
            objective.getScore("§a✅ ĐÃ HOÀN THÀNH!").setScore(line--);
        } else {
            objective.getScore("§fTiến độ của bạn:").setScore(line--);
            double percentage = (double) playerProgress / currentQuest.target() * 100;
            objective.getScore("§a" + playerProgress + "§7/" + 
                "§f" + currentQuest.target() + " §7(" + 
                String.format("%.1f", percentage) + "%)").setScore(line--);
        }
        
        // Empty line
        objective.getScore("   ").setScore(line--);
        
        // Time left
        long timeLeft = plugin.getQuestManager().getTimeLeft();
        if (timeLeft > 0) {
            long hoursLeft = timeLeft / (60 * 60 * 1000);
            long daysLeft = hoursLeft / 24;
            
            objective.getScore("§cThời gian còn lại:").setScore(line--);
            if (daysLeft > 0) {
                objective.getScore("§f" + daysLeft + " ngày " + (hoursLeft % 24) + " giờ").setScore(line--);
            } else {
                objective.getScore("§f" + hoursLeft + " giờ").setScore(line--);
            }
        } else {
            objective.getScore("§4HẾT THỜI GIAN!").setScore(line--);
        }
        
        // Empty line
        objective.getScore("    ").setScore(line--);
        
        // Global stats
        int totalCompleted = plugin.getQuestManager().getCompletedCount();
        int totalOnline = plugin.getTotalOnlinePlayers();
        
        objective.getScore("§bNgười đã hoàn thành:").setScore(line--);
        objective.getScore("§f" + totalCompleted + "§7/" + totalOnline).setScore(line--);
        
        // Empty line
        objective.getScore("     ").setScore(line--);
        
        // Top player
        String topPlayer = plugin.getTopPlayerName();
        objective.getScore("§6Người dẫn đầu:").setScore(line--);
        objective.getScore("§f" + topPlayer).setScore(line--);

        player.setScoreboard(scoreboard);
    }
}