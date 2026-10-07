package cn.openlims.platform.task.service;

import cn.openlims.platform.common.error.ApiException;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务正文与子任务清单的公共校验：新手任务大任务与普通任务共用同一套规则。
 */
final class TaskContentSanitizer {

    /** 一篇任务最多 50 项子任务（新手任务与普通任务一致）。 */
    static final int MAX_SUBTASKS = 50;

    /** 子任务说明的编辑器上限；为空表示该子任务只有标题。 */
    static final int MAX_SUBTASK_CONTENT = 5000;

    private static final PolicyFactory TASK_HTML_POLICY = new HtmlPolicyBuilder()
            .allowElements("p", "h2", "h3", "blockquote", "ul", "ol", "li", "strong", "em", "s",
                    "code", "pre", "br", "hr", "a", "img")
            .allowWithoutAttributes("p", "h2", "h3", "blockquote", "ul", "ol", "li", "strong", "em", "s",
                    "code", "pre", "br", "hr")
            .allowUrlProtocols("http", "https")
            .allowAttributes("href").onElements("a")
            .allowAttributes("src", "alt", "title").onElements("img")
            .requireRelNofollowOnLinks()
            .toFactory();

    private TaskContentSanitizer() {
    }

    /** OWASP 白名单清洗富文本正文；纯空白视为无效。 */
    static String cleanContent(String value) {
        String safe = TASK_HTML_POLICY.sanitize(value == null ? "" : value.trim());
        String text = safe.replaceAll("<[^>]+>", "").replace("&nbsp;", "").trim();
        if (text.isEmpty() && !safe.contains("<img")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请输入有效的任务正文");
        }
        return safe;
    }

    /** 子任务去重、去空并限制数量；允许为空（普通任务可以不设子任务）。 */
    static List<String> normalizeSubtasks(List<String> subtasks) {
        Map<String, String> distinct = new LinkedHashMap<>();
        if (subtasks != null) {
            for (String item : subtasks) {
                String title = item == null ? "" : item.trim();
                if (!title.isEmpty()) distinct.putIfAbsent(title, title);
            }
        }
        if (distinct.size() > MAX_SUBTASKS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "子任务不能超过 " + MAX_SUBTASKS + " 项");
        }
        return List.copyOf(distinct.values());
    }

    /** 成员为子任务提交的内容：必填，走与任务正文同一条白名单，上限同子任务说明。 */
    static String cleanSubmissionContent(String value) {
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请填写该子任务的完成内容");
        }
        return cleanSubtaskContent(value);
    }

    /**
     * 子任务说明：允许为空（纯清单项），非空时走与任务正文同一条白名单。
     * 上限按 {@link #MAX_SUBTASK_CONTENT} 校验前端提交的原始长度。
     */
    static String cleanSubtaskContent(String value) {
        if (value == null || value.isBlank()) return null;
        if (value.length() > MAX_SUBTASK_CONTENT) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "子任务说明不能超过 " + MAX_SUBTASK_CONTENT + " 个字符");
        }
        return cleanContent(value);
    }
}
