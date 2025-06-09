package dev.mrduck;

public class QuestProgress {
    public String status;
    public int progress;
    public long lastUpdated;

    public QuestProgress(String status, int progress, long lastUpdated) {
        this.status = status;
        this.progress = progress;
        this.lastUpdated = lastUpdated;
    }
}
