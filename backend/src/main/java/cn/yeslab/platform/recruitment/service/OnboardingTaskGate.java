package cn.yeslab.platform.recruitment.service;

import cn.yeslab.platform.identity.model.AccountEntity;

import java.util.UUID;

/**
 * 新手任务门槛：招新模块通过该接口查询与回写新手任务状态，避免与任务模块形成包级循环依赖。
 * 实现位于任务模块，当新手任务功能尚未接入数据时一律返回 {@link State#NOT_ISSUED}，
 * 此时转正按「历史记录」放行。
 */
public interface OnboardingTaskGate {

    enum State {
        /** 该报名记录没有新手任务（功能上线前的历史记录，或尚未接入发放）。 */
        NOT_ISSUED,
        /** 已有新手任务但尚未通过（待完成、待确认或已驳回）。 */
        IN_PROGRESS,
        /** 新手任务已通过，可以转正。 */
        APPROVED
    }

    State state(UUID applicationId);

    /** 转正成功后回写结果：关闭在途新手任务、记录豁免理由与生成的成员档案。 */
    void recordConversion(UUID applicationId, UUID convertedProfileId, AccountEntity operator, String exemptionReason);
}
