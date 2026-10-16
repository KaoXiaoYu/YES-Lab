package cn.yeslab.platform.recruitment.form;

import cn.yeslab.platform.common.error.ApiException;
import cn.yeslab.platform.recruitment.form.api.RecruitmentFormModels.Schema;
import cn.yeslab.platform.recruitment.form.service.RecruitmentFormDefaults;
import cn.yeslab.platform.recruitment.form.service.RecruitmentFormValidator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("unchecked")
class RecruitmentFormValidatorTests {
    private final ObjectMapper mapper = JsonMapper.builder().build();
    private final RecruitmentFormValidator validator = new RecruitmentFormValidator();

    @Test void validDefaultSchemaIncludesTheProtectedFields() { validator.validate(RecruitmentFormDefaults.schema()); }

    @Test void malformedStructureCannotEraseCoreDataOrAliasQuestionIds() {
        invalid(schema -> modules(schema).removeFirst());
        invalid(schema -> modules(schema).getFirst().put("enabled", false));
        invalid(schema -> fields(modules(schema).getFirst()).removeFirst());
        invalid(schema -> fields(modules(schema).getFirst()).getFirst().put("enabled", false));
        invalid(schema -> modules(schema).get(1).put("id", modules(schema).getFirst().get("id")));
        invalid(schema -> modules(schema).get(1).put("displayOrder", 0));
        invalid(schema -> modules(schema).set(1, null));
        invalid(schema -> options(modules(schema).get(1)).getFirst().put("id", null));
    }

    @Test void choiceLimitsAndIdsAreValidatedAgainstActualOptions() {
        invalid(schema -> object(modules(schema).get(1).get("choice")).put("maxSelections", 30));
        invalid(schema -> object(modules(schema).get(1).get("choice")).put("mode", "SINGLE"));
        invalid(schema -> object(modules(schema).get(1).get("choice")).put("minSelections", 0));
        invalid(schema -> options(modules(schema).get(1)).get(1).put("label", options(modules(schema).get(1)).getFirst().get("label")));
        invalid(schema -> options(modules(schema).get(1)).get(1).put("id", options(modules(schema).get(1)).getFirst().get("id")));
        invalid(schema -> options(modules(schema).get(1)).getFirst().put("label", "方".repeat(81)));
    }

    @Test void disabledAndCustomModulesStillCannotExceedSystemLimits() {
        invalid(schema -> {
            modules(schema).get(8).put("enabled", false);
            object(modules(schema).get(8).get("upload")).put("maxFiles", 7);
        });
        invalid(schema -> object(modules(schema).get(8).get("upload")).put("maxImageBytes", 6L * 1024 * 1024));
        invalid(schema -> object(modules(schema).get(8).get("upload")).put("maxPdfBytes", 11L * 1024 * 1024));
        invalid(schema -> object(modules(schema).get(8).get("upload")).put("allowedTypes", new ArrayList<>(java.util.Arrays.asList("IMAGE", null))));
        invalid(schema -> object(modules(schema).get(8).get("upload")).put("allowedTypes", List.of("PDF", "PDF")));
        invalid(schema -> object(modules(schema).get(2).get("text")).put("maxLength", 101));
        invalid(schema -> modules(schema).get(4).put("binding", "MEDIA_LINKS"));
    }

    @Test void repeatedStandaloneModulesUseSeparateIdsAndAcceptInformationBlocks() {
        var schema = defaultMap();
        Map<String, Object> text = mapper.readValue(mapper.writeValueAsString(modules(schema).get(4)), Map.class);
        text.put("id", UUID.randomUUID().toString()); text.put("binding", null); text.put("displayOrder", 10);
        modules(schema).add(text);
        var info = new java.util.LinkedHashMap<String, Object>();
        info.put("id", UUID.randomUUID().toString()); info.put("type", "INFO"); info.put("title", "材料说明");
        info.put("description", "请按顺序填写"); info.put("required", false); info.put("enabled", true); info.put("displayOrder", 11);
        modules(schema).add(info);
        validator.validate(mapper.convertValue(schema, Schema.class));
    }

    @Test void identityFieldBindingsAndModuleCountAreBounded() {
        invalid(schema -> fields(modules(schema).getFirst()).get(1).put("binding", "NAME"));
        invalid(schema -> schema.put("title", " "));
        invalid(schema -> schema.put("schemaVersion", 2));
        invalid(schema -> {
            var source = modules(schema).get(4);
            for (int i = 10; i < 51; i++) {
                var copy = new java.util.LinkedHashMap<>(source);
                copy.put("id", UUID.randomUUID().toString()); copy.put("binding", null); copy.put("displayOrder", i);
                modules(schema).add(copy);
            }
        });
    }

    @Test void requiredUploadsCannotMakeTheFormImpossibleToSubmit() {
        invalid(schema -> {
            var source = modules(schema).get(8);
            for (int i = 10; i < 31; i++) {
                var copy = new java.util.LinkedHashMap<>(source);
                copy.put("id", UUID.randomUUID().toString()); copy.put("binding", null);
                copy.put("displayOrder", i); copy.put("required", true);
                modules(schema).add(copy);
            }
        });
    }

    private void invalid(Consumer<Map<String, Object>> change) {
        var schema = defaultMap(); change.accept(schema);
        assertThatThrownBy(() -> validator.validate(mapper.convertValue(schema, Schema.class)))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.getStatus().value()).isEqualTo(400);
                    assertThat(error.getFields()).isNotEmpty();
                });
    }
    private Map<String, Object> defaultMap() { return mapper.convertValue(RecruitmentFormDefaults.schema(), Map.class); }
    private List<Map<String, Object>> modules(Map<String, Object> schema) { return (List<Map<String, Object>>) schema.get("modules"); }
    private List<Map<String, Object>> fields(Map<String, Object> module) { return (List<Map<String, Object>>) module.get("fields"); }
    private List<Map<String, Object>> options(Map<String, Object> module) { return (List<Map<String, Object>>) module.get("options"); }
    private Map<String, Object> object(Object value) { return (Map<String, Object>) value; }
}
