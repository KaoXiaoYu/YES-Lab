package cn.openlims.platform.points.model;

public enum PointCategory {
    COMPETITION("竞赛成果"),
    PROJECT("项目贡献"),
    LAB_CONTRIBUTION("实验室贡献"),
    MEDIA("运营与自媒体贡献");

    private final String label;

    PointCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
