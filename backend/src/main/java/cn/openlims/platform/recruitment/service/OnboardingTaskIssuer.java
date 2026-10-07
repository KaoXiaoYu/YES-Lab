package cn.openlims.platform.recruitment.service;

import cn.openlims.platform.identity.model.AccountEntity;
import cn.openlims.platform.recruitment.model.RecruitmentApplicationEntity;

/**
 * 新手任务发放：报名者进入技能测试阶段时由招新模块触发，实现位于任务模块。
 * 与 {@link OnboardingTaskGate} 同理，通过接口反转依赖，避免招新与任务包相互依赖。
 */
public interface OnboardingTaskIssuer {

    /** 为该报名记录发放新手任务；已存在在途新手任务时不重复发放。 */
    void issueIfAbsent(RecruitmentApplicationEntity application, AccountEntity operator);
}
