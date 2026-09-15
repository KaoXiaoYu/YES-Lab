package cn.yeslab.platform.points.model;

public enum PointSubcategory {
    COMPETITION_AWARD(PointCategory.COMPETITION, "竞赛获奖", null, AllocationPolicy.PER_MEMBER),
    PROJECT_TASK(PointCategory.PROJECT, "项目任务", null, AllocationPolicy.SHARED_TOTAL),
    LAB_ACTIVITY(PointCategory.LAB_CONTRIBUTION, "实验室贡献", 100, AllocationPolicy.SHARED_TOTAL),
    MEDIA_CONTENT(PointCategory.MEDIA, "内容制作", null, AllocationPolicy.SHARED_TOTAL),
    MEDIA_OPERATION(PointCategory.MEDIA, "运营执行", 200, AllocationPolicy.SHARED_TOTAL),
    MEDIA_REACH(PointCategory.MEDIA, "传播效果", 200, AllocationPolicy.SHARED_TOTAL);

    private final PointCategory category;
    private final String label;
    private final Integer monthlyCap;
    private final AllocationPolicy allocationPolicy;

    PointSubcategory(
            PointCategory category,
            String label,
            Integer monthlyCap,
            AllocationPolicy allocationPolicy
    ) {
        this.category = category;
        this.label = label;
        this.monthlyCap = monthlyCap;
        this.allocationPolicy = allocationPolicy;
    }

    public PointCategory category() {
        return category;
    }

    public String label() {
        return label;
    }

    public Integer monthlyCap() {
        return monthlyCap;
    }

    public AllocationPolicy allocationPolicy() {
        return allocationPolicy;
    }

    public enum AllocationPolicy {
        PER_MEMBER,
        SHARED_TOTAL
    }
}
