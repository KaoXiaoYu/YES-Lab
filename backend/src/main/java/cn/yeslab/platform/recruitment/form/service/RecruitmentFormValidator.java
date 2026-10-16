package cn.yeslab.platform.recruitment.form.service;

import cn.yeslab.platform.common.error.ApiException;
import cn.yeslab.platform.recruitment.form.api.RecruitmentFormModels;
import cn.yeslab.platform.recruitment.form.api.RecruitmentFormModels.*;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class RecruitmentFormValidator {
    private static final Set<BasicBinding> CORE = Set.of(BasicBinding.NAME, BasicBinding.EMAIL, BasicBinding.MAJOR, BasicBinding.CLASS_NAME);

    public void validate(Schema schema) {
        if (schema == null) fail("schema", "请输入报名表配置");
        if (schema.schemaVersion() != 1) fail("schema.schemaVersion", "不支持的表单格式版本");
        text(schema.title(), 160, true, "schema.title");
        text(schema.description(), 3000, false, "schema.description");
        List<RecruitmentFormModels.Module> modules = schema.modules();
        if (modules == null || modules.isEmpty() || modules.size() > RecruitmentFormDefaults.MAX_MODULES) fail("schema.modules", "报名表应包含 1—50 个模块");
        Set<UUID> ids = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        Set<ModuleBinding> bindings = new HashSet<>();
        int basic = 0;
        int technical = 0;
        int requiredUploads = 0;
        for (int i = 0; i < modules.size(); i++) {
            var module = modules.get(i);
            String path = "schema.modules[" + i + "]";
            if (module == null) fail(path, "模块不能为空");
            unique(module.id(), ids, path + ".id");
            order(module.displayOrder(), orders, path + ".displayOrder");
            if (module.type() == null) fail(path + ".type", "请选择模块类型");
            text(module.title(), 160, true, path + ".title");
            text(module.description(), 3000, false, path + ".description");
            if (module.binding() != null && !bindings.add(module.binding())) fail(path + ".binding", "系统字段绑定不能重复");
            validateBinding(module, path);
            if (module.type() != ModuleType.BASIC_INFO && !empty(module.fields())) fail(path + ".fields", "该模块不能包含基础资料字段");
            if (module.type() != ModuleType.CHOICE && (!empty(module.options()) || module.choice() != null)) fail(path + ".choice", "该模块不能包含选择配置");
            if (module.type() != ModuleType.TEXT && module.text() != null) fail(path + ".text", "该模块不能包含文字配置");
            if (module.type() != ModuleType.UPLOAD && module.upload() != null) fail(path + ".upload", "该模块不能包含上传配置");
            switch (module.type()) {
                case BASIC_INFO -> {
                    basic++;
                    if (!module.enabled() || !module.required()) fail(path + ".enabled", "核心基础信息模块不能停用或取消必填");
                    validateBasic(module.fields(), ids, path);
                }
                case CHOICE -> validateChoice(module, ids, path);
                case TEXT -> validateText(module, path);
                case UPLOAD -> {
                    validateUpload(module.upload(), path);
                    if (module.enabled() && module.required()) requiredUploads++;
                }
                case TECHNICAL -> {
                    technical++;
                    if (!module.required()) fail(path + ".required", "技术认知启用时沿用至少回答 3 道规则，停用请使用模块开关");
                }
                case INFO -> {
                    if (module.required()) fail(path + ".required", "说明模块不收集答案，不能设置必填");
                }
            }
        }
        if (basic != 1) fail("schema.modules", "必须且只能保留一个基础信息模块");
        if (technical > 1) fail("schema.modules", "技术认知模块最多一个");
        if (requiredUploads > RecruitmentFormDefaults.MAX_TOTAL_FILES) fail("schema.modules", "必填上传模块超过全表 20 份文件上限，报名者将无法完成表单");
    }

    private void validateBasic(List<BasicField> fields, Set<UUID> ids, String path) {
        if (fields == null || fields.size() > BasicBinding.values().length) fail(path + ".fields", "基础信息字段无效");
        Set<BasicBinding> bindings = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        for (int i = 0; i < fields.size(); i++) {
            var field = fields.get(i);
            String key = path + ".fields[" + i + "]";
            if (field == null) fail(key, "基础信息字段不能为空");
            unique(field.id(), ids, key + ".id");
            order(field.displayOrder(), orders, key + ".displayOrder");
            if (field.binding() == null || !bindings.add(field.binding())) fail(key + ".binding", "基础字段绑定无效或重复");
            text(field.label(), 100, true, key + ".label");
            text(field.description(), 1000, false, key + ".description");
            if (CORE.contains(field.binding()) && (!field.enabled() || !field.required())) fail(key + ".required", "姓名、邮箱、专业与班级必须保留并填写");
        }
        if (!bindings.containsAll(CORE)) fail(path + ".fields", "缺少姓名、邮箱、专业或班级字段");
    }

    private void validateChoice(RecruitmentFormModels.Module module, Set<UUID> ids, String path) {
        if (module.options() == null || module.options().isEmpty() || module.options().size() > RecruitmentFormDefaults.MAX_OPTIONS) fail(path + ".options", "选择模块应包含 1—30 个选项");
        Set<Integer> orders = new HashSet<>();
        Set<String> labels = new HashSet<>();
        for (int i = 0; i < module.options().size(); i++) {
            var option = module.options().get(i);
            String key = path + ".options[" + i + "]";
            if (option == null) fail(key, "选项不能为空");
            unique(option.id(), ids, key + ".id");
            order(option.displayOrder(), orders, key + ".displayOrder");
            text(option.label(), module.binding() == ModuleBinding.INTEREST_DIRECTIONS ? 80 : 100, true, key + ".label");
            if (!labels.add(option.label().trim())) fail(key + ".label", "选项文字不能重复");
        }
        var config = module.choice();
        if (config == null || config.mode() == null) fail(path + ".choice", "请设置单选或多选");
        if (config.minSelections() < 0 || config.maxSelections() < 1 || config.minSelections() > config.maxSelections()
                || config.maxSelections() > module.options().size()) fail(path + ".choice", "选择数量应在有效选项数量之内");
        if (module.required() && config.minSelections() < 1) fail(path + ".choice.minSelections", "必填选择模块至少选择一项");
        if (config.mode() == ChoiceMode.SINGLE && (config.maxSelections() != 1 || config.minSelections() > 1)) fail(path + ".choice", "单选模块最多选择一项");
    }

    private void validateText(RecruitmentFormModels.Module module, String path) {
        var config = module.text();
        if (config == null || config.format() == null) fail(path + ".text", "请选择填写格式");
        int cap = switch (module.binding() == null ? "CUSTOM" : module.binding().name()) {
            case "PORTFOLIO_INTRODUCTION" -> 3000;
            case "EXISTING_SKILLS" -> 100;
            case "INTENDED_TAGS" -> 80;
            case "MEDIA_LINKS" -> 500;
            default -> 5000;
        };
        if (config.minLength() < 0 || config.maxLength() < 1 || config.maxLength() > cap || config.minLength() > config.maxLength()) fail(path + ".text", "填写长度范围无效或超过系统上限");
        boolean list = config.format() == TextFormat.TAG_LIST || config.format() == TextFormat.LINK_LIST;
        if (list && (config.maxEntries() == null || config.maxEntries() < 1 || config.maxEntries() > (config.format() == TextFormat.LINK_LIST ? 8 : 50))) fail(path + ".text.maxEntries", "列表项数量超出上限");
        if (!list && config.maxEntries() != null) fail(path + ".text.maxEntries", "单项文字不能设置列表数量");
    }

    private void validateUpload(UploadSettings config, String path) {
        if (config == null || config.allowedTypes() == null || config.allowedTypes().isEmpty()
                || config.allowedTypes().stream().anyMatch(java.util.Objects::isNull) || config.allowedTypes().size() > 2
                || new HashSet<>(config.allowedTypes()).size() != config.allowedTypes().size()) fail(path + ".upload.allowedTypes", "上传类型只能选择图片、PDF，且不能重复");
        if (config.maxFiles() < 1 || config.maxFiles() > 6) fail(path + ".upload.maxFiles", "单个上传模块最多 6 份文件");
        if (config.maxImageBytes() < 1 || config.maxImageBytes() > RecruitmentFormDefaults.IMAGE_BYTES) fail(path + ".upload.maxImageBytes", "图片上限不能超过 5MB");
        if (config.maxPdfBytes() < 1 || config.maxPdfBytes() > RecruitmentFormDefaults.PDF_BYTES) fail(path + ".upload.maxPdfBytes", "PDF 上限不能超过 10MB");
    }

    private void validateBinding(RecruitmentFormModels.Module module, String path) {
        if (module.binding() == null) return;
        boolean valid = switch (module.binding()) {
            case INTEREST_DIRECTIONS -> module.type() == ModuleType.CHOICE && module.choice() != null && module.choice().mode() == ChoiceMode.MULTIPLE;
            case EXISTING_SKILLS, INTENDED_TAGS -> module.type() == ModuleType.TEXT && module.text() != null && module.text().format() == TextFormat.TAG_LIST;
            case SELF_INTRODUCTION, EXPERIENCE, PORTFOLIO_INTRODUCTION -> module.type() == ModuleType.TEXT && module.text() != null && module.text().format() == TextFormat.MULTI_LINE;
            case MEDIA_LINKS -> module.type() == ModuleType.TEXT && module.text() != null && module.text().format() == TextFormat.LINK_LIST;
            case PORTFOLIO_IMAGES -> module.type() == ModuleType.UPLOAD;
            case TECHNICAL_ANSWERS -> module.type() == ModuleType.TECHNICAL;
        };
        if (!valid) fail(path + ".binding", "系统字段绑定与模块类型不匹配");
    }

    private boolean empty(List<?> values) { return values == null || values.isEmpty(); }
    private void unique(UUID id, Set<UUID> ids, String path) {
        if (id == null || !ids.add(id)) fail(path, "模块、字段与选项 ID 不能为空或重复");
    }
    private void order(int order, Set<Integer> orders, String path) {
        if (order < 0 || order >= 1000 || !orders.add(order)) fail(path, "顺序应为 0—999 内不重复的整数");
    }
    private void text(String value, int maximum, boolean required, String path) {
        if (value == null) { if (required) fail(path, "此项不能为空"); return; }
        if ((required && value.isBlank()) || value.length() > maximum || value.chars().anyMatch(c -> c == 0)) fail(path, "文字为空、过长或含无效字符");
    }
    private void fail(String path, String message) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "请检查报名表配置", Map.of(path, message));
    }
}
