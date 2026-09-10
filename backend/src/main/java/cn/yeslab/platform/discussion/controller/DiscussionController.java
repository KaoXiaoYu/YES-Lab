package cn.yeslab.platform.discussion.controller;

import cn.yeslab.platform.common.api.ApiResponse;
import cn.yeslab.platform.discussion.api.DiscussionModels;
import cn.yeslab.platform.discussion.service.DiscussionService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/discussions")
public class DiscussionController {
    private final DiscussionService service;
    public DiscussionController(DiscussionService service) { this.service = service; }

    @GetMapping
    public ApiResponse<List<DiscussionModels.PostView>> list(Authentication authentication,
            @RequestParam(defaultValue = "NEWEST") DiscussionModels.SortMode sort) {
        return ApiResponse.ok(service.list(authentication, sort));
    }

    @GetMapping("/authors/{profileId}")
    public ApiResponse<List<DiscussionModels.ContributionView>> contributions(@PathVariable UUID profileId) {
        return ApiResponse.ok(service.contributions(profileId));
    }

    @PostMapping
    public ApiResponse<DiscussionModels.PostView> create(Authentication authentication,
            @Valid @RequestBody DiscussionModels.PostRequest request) {
        return ApiResponse.ok(service.create(authentication, request));
    }

    @PutMapping("/{postId}")
    public ApiResponse<DiscussionModels.PostView> update(Authentication authentication, @PathVariable UUID postId,
            @Valid @RequestBody DiscussionModels.PostRequest request) {
        return ApiResponse.ok(service.update(authentication, postId, request));
    }

    @DeleteMapping("/{postId}")
    public ApiResponse<Void> delete(Authentication authentication, @PathVariable UUID postId) {
        service.delete(authentication, postId);
        return ApiResponse.ok(null);
    }

    @PatchMapping("/{postId}/like")
    public ApiResponse<DiscussionModels.PostView> like(Authentication authentication, @PathVariable UUID postId) {
        return ApiResponse.ok(service.togglePostLike(authentication, postId));
    }

    @PostMapping("/{postId}/replies")
    public ApiResponse<DiscussionModels.PostView> reply(Authentication authentication, @PathVariable UUID postId,
            @Valid @RequestBody DiscussionModels.ReplyRequest request) {
        return ApiResponse.ok(service.reply(authentication, postId, request));
    }

    @PutMapping("/replies/{replyId}")
    public ApiResponse<DiscussionModels.PostView> updateReply(Authentication authentication, @PathVariable UUID replyId,
            @Valid @RequestBody DiscussionModels.ReplyRequest request) {
        return ApiResponse.ok(service.updateReply(authentication, replyId, request));
    }

    @DeleteMapping("/replies/{replyId}")
    public ApiResponse<DiscussionModels.PostView> deleteReply(Authentication authentication, @PathVariable UUID replyId) {
        return ApiResponse.ok(service.deleteReply(authentication, replyId));
    }

    @PatchMapping("/replies/{replyId}/like")
    public ApiResponse<DiscussionModels.PostView> likeReply(Authentication authentication, @PathVariable UUID replyId) {
        return ApiResponse.ok(service.toggleReplyLike(authentication, replyId));
    }
}
