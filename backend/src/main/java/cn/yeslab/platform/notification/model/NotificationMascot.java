package cn.yeslab.platform.notification.model;

public enum NotificationMascot {
    MELINA("梅琳娜"), NAILONG("奶龙");

    private final String displayName;

    NotificationMascot(String displayName) { this.displayName = displayName; }

    public String getDisplayName() { return displayName; }
}
