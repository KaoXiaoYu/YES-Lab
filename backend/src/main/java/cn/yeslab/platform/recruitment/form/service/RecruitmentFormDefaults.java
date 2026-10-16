package cn.yeslab.platform.recruitment.form.service;

import cn.yeslab.platform.recruitment.form.api.RecruitmentFormModels;
import cn.yeslab.platform.recruitment.form.api.RecruitmentFormModels.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class RecruitmentFormDefaults {
    public static final long IMAGE_BYTES = 5L * 1024 * 1024;
    public static final long PDF_BYTES = 10L * 1024 * 1024;
    public static final int MAX_MODULES = 50;
    public static final int MAX_OPTIONS = 30;
    public static final int MAX_TOTAL_FILES = 20;
    public static final long MAX_TOTAL_FILE_BYTES = 50L * 1024 * 1024;
    public static final int MAX_SCHEMA_BYTES = 256 * 1024;
    private RecruitmentFormDefaults() { }

    public static Schema schema() {
        List<BasicField> fields = List.of(
                field(BasicBinding.NAME, "姓名", true, 0), field(BasicBinding.EMAIL, "邮箱", true, 1),
                field(BasicBinding.PHONE, "手机号码", false, 2), field(BasicBinding.WECHAT, "微信号", false, 3),
                field(BasicBinding.MAJOR, "专业", true, 4), field(BasicBinding.CLASS_NAME, "班级", true, 5),
                field(BasicBinding.GRADE, "年级", false, 6));
        List<RecruitmentFormModels.Module> modules = new ArrayList<>();
        modules.add(new RecruitmentFormModels.Module(id("basic"), ModuleType.BASIC_INFO, "基本信息", "用于联系与后续成员建档。", true, true,
                0, null, fields, List.of(), null, null, null));
        String[] directions = {"无人机", "机器人", "视觉", "嵌入式", "硬件", "新媒体", "算法", "深度学习"};
        List<Option> options = new ArrayList<>();
        for (int i = 0; i < directions.length; i++) options.add(new Option(id("direction-" + i), directions[i], i));
        modules.add(new RecruitmentFormModels.Module(id("directions"), ModuleType.CHOICE, "兴趣方向", "请选择至少一个方向。", true, true,
                1, ModuleBinding.INTEREST_DIRECTIONS, List.of(), options, new ChoiceSettings(ChoiceMode.MULTIPLE, 1, 8), null, null));
        modules.add(text("skills", "现有技能", false, 2, ModuleBinding.EXISTING_SKILLS, TextFormat.TAG_LIST, 100, 50));
        modules.add(text("intended", "意向标签", true, 3, ModuleBinding.INTENDED_TAGS, TextFormat.TAG_LIST, 80, 50));
        modules.add(text("introduction", "自我介绍", false, 4, ModuleBinding.SELF_INTRODUCTION, TextFormat.MULTI_LINE, 5000, null));
        modules.add(text("experience", "项目或竞赛经历", false, 5, ModuleBinding.EXPERIENCE, TextFormat.MULTI_LINE, 5000, null));
        modules.add(text("portfolio", "个人展示介绍", false, 6, ModuleBinding.PORTFOLIO_INTRODUCTION, TextFormat.MULTI_LINE, 3000, null));
        modules.add(text("links", "个人链接", false, 7, ModuleBinding.MEDIA_LINKS, TextFormat.LINK_LIST, 500, 8));
        modules.add(new RecruitmentFormModels.Module(id("images"), ModuleType.UPLOAD, "作品图片", "支持 JPG、PNG、WebP，每张最多 5MB。", false, true,
                8, ModuleBinding.PORTFOLIO_IMAGES, List.of(), List.of(), null, null,
                new UploadSettings(List.of(FileType.IMAGE), 6, IMAGE_BYTES, PDF_BYTES)));
        modules.add(new RecruitmentFormModels.Module(id("technical"), ModuleType.TECHNICAL, "技术认知", "分配 5 道题，请至少回答 3 道。", true, true,
                9, ModuleBinding.TECHNICAL_ANSWERS, List.of(), List.of(), null, null, null));
        return new Schema(1, "招新报名表", "初筛前可修改；进入初筛后报名内容锁定。", List.copyOf(modules));
    }

    private static BasicField field(BasicBinding binding, String label, boolean required, int order) {
        return new BasicField(id("field-" + binding), binding, label, "", required, true, order);
    }
    private static RecruitmentFormModels.Module text(String key, String title, boolean required, int order,
            ModuleBinding binding, TextFormat format, int maxLength, Integer entries) {
        return new RecruitmentFormModels.Module(id(key), ModuleType.TEXT, title, "", required, true, order,
                binding, List.of(), List.of(), null, new TextSettings(format, required ? 1 : 0, maxLength, entries), null);
    }
    private static UUID id(String key) {
        return UUID.nameUUIDFromBytes(("yeslab:recruitment-form:default:" + key).getBytes(StandardCharsets.UTF_8));
    }
}
