package cn.openlims.platform.task.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * 子任务定义，属于大任务本身，对该任务的全部对象共享；每个子任务是一个可以独立打开的条目。
 *
 * <p>按已确认需求：子任务有标题与可选富文本正文，单层；不单独指派负责人、不设置截止日期、
 * 不单独计分，也没有独立提交与独立审核——只有「已勾选 / 未勾选」。</p>
 */
@Entity
@Table(name = "task_subtasks")
public class TaskSubtaskEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private TaskEntity task;

    @Column(nullable = false, length = 200)
    private String title;

    /** 子任务说明（富文本，可为空）：为空时前端不渲染正文块，只显示标题与勾选。 */
    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String contentHtml;

    @Column(nullable = false)
    private int displayOrder;

    protected TaskSubtaskEntity() {
    }

    public TaskSubtaskEntity(TaskEntity task, String title, String contentHtml, int displayOrder) {
        this.task = task;
        this.title = title;
        this.contentHtml = contentHtml;
        this.displayOrder = displayOrder;
    }

    public UUID getId() { return id; }
    public TaskEntity getTask() { return task; }
    public String getTitle() { return title; }
    public String getContentHtml() { return contentHtml; }
    public int getDisplayOrder() { return displayOrder; }

    public boolean hasContent() {
        return contentHtml != null && !contentHtml.isBlank();
    }

    void rename(String title) {
        this.title = title;
    }

    void updateContent(String contentHtml) {
        this.contentHtml = contentHtml;
    }

    void reorder(int displayOrder) {
        this.displayOrder = displayOrder;
    }
}
