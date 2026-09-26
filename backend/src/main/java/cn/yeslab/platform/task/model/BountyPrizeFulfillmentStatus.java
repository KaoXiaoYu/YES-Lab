package cn.yeslab.platform.task.model;

/** 线下奖金履约进度；状态只向前流转。 */
public enum BountyPrizeFulfillmentStatus {
    PENDING,
    ISSUED,
    RECEIVED,
    REVOKED
}
