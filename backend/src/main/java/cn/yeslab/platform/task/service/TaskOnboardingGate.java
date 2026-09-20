package cn.yeslab.platform.task.service;

import cn.yeslab.platform.identity.model.AccountEntity;
import cn.yeslab.platform.recruitment.service.OnboardingTaskGate;
import cn.yeslab.platform.task.model.TaskAssignmentEntity;
import cn.yeslab.platform.task.model.TaskAssignmentStatus;
import cn.yeslab.platform.task.model.TaskType;
import cn.yeslab.platform.task.repository.TaskAssignmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * {@link OnboardingTaskGate} 的任务模块实现。
 * 只读取报名记录上最新的新手任务对象，不做任何自动判定。
 */
@Service
public class TaskOnboardingGate implements OnboardingTaskGate {

    private final TaskAssignmentRepository assignments;

    public TaskOnboardingGate(TaskAssignmentRepository assignments) {
        this.assignments = assignments;
    }

    @Override
    @Transactional(readOnly = true)
    public State state(UUID applicationId) {
        return latest(applicationId)
                .map(assignment -> assignment.getStatus() == TaskAssignmentStatus.APPROVED
                        ? State.APPROVED
                        : State.IN_PROGRESS)
                .orElse(State.NOT_ISSUED);
    }

    @Override
    @Transactional
    public void recordConversion(
            UUID applicationId,
            UUID convertedProfileId,
            AccountEntity operator,
            String exemptionReason
    ) {
        latest(applicationId).ifPresent(assignment -> {
            if (exemptionReason != null) {
                assignment.recordExemption(exemptionReason);
            }
            // 新手任务审核路径会先落「已通过」，此处不覆盖其审核人与意见。
            if (assignment.getStatus() != TaskAssignmentStatus.APPROVED) {
                assignment.approve(operator, exemptionReason != null ? "管理员豁免并转正" : "转为正式成员");
            }
            assignment.recordConversion(convertedProfileId);
            assignments.save(assignment);
        });
    }

    private java.util.Optional<TaskAssignmentEntity> latest(UUID applicationId) {
        return assignments.findFirstByRecruitmentApplication_IdAndTask_TaskTypeOrderByCreatedAtDesc(
                applicationId, TaskType.ONBOARDING);
    }
}
