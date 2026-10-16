package cn.yeslab.platform.recruitment.form.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class RecruitmentFormModels {
    private RecruitmentFormModels() { }

    public enum ModuleType { BASIC_INFO, CHOICE, TEXT, UPLOAD, TECHNICAL, INFO }
    public enum BasicBinding { NAME, EMAIL, MAJOR, CLASS_NAME, GRADE, PHONE, WECHAT }
    public enum ModuleBinding {
        INTEREST_DIRECTIONS, EXISTING_SKILLS, INTENDED_TAGS, SELF_INTRODUCTION,
        EXPERIENCE, PORTFOLIO_INTRODUCTION, MEDIA_LINKS, PORTFOLIO_IMAGES, TECHNICAL_ANSWERS
    }
    public enum ChoiceMode { SINGLE, MULTIPLE }
    public enum TextFormat { SINGLE_LINE, MULTI_LINE, EMAIL, PHONE, URL, TAG_LIST, LINK_LIST }
    public enum FileType { IMAGE, PDF }

    public record Schema(int schemaVersion, String title, String description, List<Module> modules) { }
    public record Module(
            UUID id, ModuleType type, String title, String description, boolean required,
            boolean enabled, int displayOrder, ModuleBinding binding, List<BasicField> fields,
            List<Option> options, ChoiceSettings choice, TextSettings text, UploadSettings upload
    ) { }
    public record BasicField(
            UUID id, BasicBinding binding, String label, String description,
            boolean required, boolean enabled, int displayOrder
    ) { }
    public record Option(UUID id, String label, int displayOrder) { }
    public record ChoiceSettings(ChoiceMode mode, int minSelections, int maxSelections) { }
    public record TextSettings(TextFormat format, int minLength, int maxLength, Integer maxEntries) { }
    public record UploadSettings(List<FileType> allowedTypes, int maxFiles, long maxImageBytes, long maxPdfBytes) { }
    public record DraftRequest(@NotNull @Min(0) Long expectedRevision, @NotNull Schema schema) { }
    public record PublishRequest(@NotNull @Min(0) Long expectedRevision) { }
    public record PublishedVersion(UUID id, long versionNumber, Schema schema, String publishedBy, Instant publishedAt) { }
    public record AdminView(
            Schema draft, long draftRevision, String updatedBy, Instant updatedAt,
            PublishedVersion published, boolean hasUnpublishedChanges
    ) { }
    public record ApplicantVersion(UUID id, long versionNumber, Schema schema) { }
    public record ApplicantView(boolean legacy, ApplicantVersion form) { }
}
