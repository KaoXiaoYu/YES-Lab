package cn.openlims.platform.identity.model;

/**
 * 成员档案状态。
 *
 * <p>成员管理只提供 {@link #TRIAL}（试用）与 {@link #OFFICIAL}（正式）两个可选项。</p>
 *
 * <p>{@link #CANDIDATE}、{@link #PAUSED}、{@link #EXITED} 已停用：它们没有任何业务逻辑引用，
 * 枚举值与数据库 ENUM 保留仅为存量数据读取兼容，存量数据不做迁移，界面与接口都不会再写入这些值。</p>
 */
public enum MemberStatus {
    /** 已停用，仅兼容历史数据读取。 */
    CANDIDATE,
    TRIAL,
    OFFICIAL,
    /** 已停用，仅兼容历史数据读取。 */
    PAUSED,
    /** 已停用，仅兼容历史数据读取。 */
    EXITED
}
