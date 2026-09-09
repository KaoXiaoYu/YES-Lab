package cn.yeslab.platform.discussion.service;

import cn.yeslab.platform.common.error.ApiException;
import cn.yeslab.platform.discussion.api.DiscussionModels;
import cn.yeslab.platform.discussion.model.DiscussionPostEntity;
import cn.yeslab.platform.discussion.model.DiscussionPostLikeEntity;
import cn.yeslab.platform.discussion.model.DiscussionReplyEntity;
import cn.yeslab.platform.discussion.model.DiscussionReplyLikeEntity;
import cn.yeslab.platform.discussion.repository.DiscussionPostLikeRepository;
import cn.yeslab.platform.discussion.repository.DiscussionPostRepository;
import cn.yeslab.platform.discussion.repository.DiscussionReplyLikeRepository;
import cn.yeslab.platform.discussion.repository.DiscussionReplyRepository;
import cn.yeslab.platform.identity.model.AccountEntity;
import cn.yeslab.platform.identity.model.MemberProfileEntity;
import cn.yeslab.platform.identity.repository.MemberProfileRepository;
import cn.yeslab.platform.identity.service.AuthService;
import cn.yeslab.platform.notification.service.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class DiscussionService {
    private final DiscussionPostRepository posts;
    private final DiscussionReplyRepository replies;
    private final DiscussionPostLikeRepository postLikes;
    private final DiscussionReplyLikeRepository replyLikes;
    private final MemberProfileRepository profiles;
    private final AuthService authService;
    private final NotificationService notificationService;

    public DiscussionService(DiscussionPostRepository posts, DiscussionReplyRepository replies,
                             DiscussionPostLikeRepository postLikes, DiscussionReplyLikeRepository replyLikes,
                             MemberProfileRepository profiles, AuthService authService,
                             NotificationService notificationService) {
        this.posts = posts;
        this.replies = replies;
        this.postLikes = postLikes;
        this.replyLikes = replyLikes;
        this.profiles = profiles;
        this.authService = authService;
        this.notificationService = notificationService;
    }

    @PreAuthorize("hasAnyRole('TEACHER','CORE_STUDENT','MEMBER')")
    @Transactional(readOnly = true)
    public List<DiscussionModels.PostView> list(Authentication authentication) {
        AccountEntity viewer = authService.requireAccount(authentication);
        return posts.findTop50ByOrderByCreatedAtDesc().stream().map(post -> toView(post, viewer)).toList();
    }

    @PreAuthorize("hasAnyRole('TEACHER','CORE_STUDENT','MEMBER')")
    @Transactional
    public DiscussionModels.PostView create(Authentication authentication, DiscussionModels.PostRequest request) {
        AccountEntity author = authService.requireAccount(authentication);
        DiscussionPostEntity post = posts.save(new DiscussionPostEntity(author, request.title().trim(), request.content().trim()));
        return toView(post, author);
    }

    @PreAuthorize("hasAnyRole('TEACHER','CORE_STUDENT','MEMBER')")
    @Transactional
    public DiscussionModels.PostView update(Authentication authentication, UUID postId, DiscussionModels.PostRequest request) {
        AccountEntity operator = authService.requireAccount(authentication);
        DiscussionPostEntity post = requirePost(postId);
        requireOwner(post.getAuthor(), operator, false);
        post.update(request.title().trim(), request.content().trim());
        return toView(posts.save(post), operator);
    }

    @PreAuthorize("hasAnyRole('TEACHER','CORE_STUDENT','MEMBER')")
    @Transactional
    public void delete(Authentication authentication, UUID postId) {
        AccountEntity operator = authService.requireAccount(authentication);
        DiscussionPostEntity post = requirePost(postId);
        requireOwner(post.getAuthor(), operator, true);
        replyLikes.deleteByReplyPostId(postId);
        replies.deleteByPostId(postId);
        postLikes.deleteByPostId(postId);
        posts.delete(post);
    }

    @PreAuthorize("hasAnyRole('TEACHER','CORE_STUDENT','MEMBER')")
    @Transactional
    public DiscussionModels.PostView togglePostLike(Authentication authentication, UUID postId) {
        AccountEntity account = authService.requireAccount(authentication);
        DiscussionPostEntity post = requirePost(postId);
        if (post.getAuthor().getId().equals(account.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "不能给自己的讨论点赞");
        }
        DiscussionPostLikeEntity existing = postLikes.findByPostIdAndAccountId(postId, account.getId()).orElse(null);
        if (existing == null) {
            postLikes.save(new DiscussionPostLikeEntity(account, post));
            notificationService.sendAggregated(post.getAuthor(), "DISCUSSION_LIKE", "你的讨论获得了 {count} 个赞",
                    displayName(account) + " 点赞了《" + post.getTitle() + "》", "/discussions#post-" + postId,
                    "DISCUSSION_POST_LIKE:" + postId);
        } else {
            postLikes.delete(existing);
        }
        return toView(post, account);
    }

    @PreAuthorize("hasAnyRole('TEACHER','CORE_STUDENT','MEMBER')")
    @Transactional
    public DiscussionModels.PostView reply(Authentication authentication, UUID postId, DiscussionModels.ReplyRequest request) {
        AccountEntity author = authService.requireAccount(authentication);
        DiscussionPostEntity post = requirePost(postId);
        DiscussionReplyEntity reply = replies.save(new DiscussionReplyEntity(post, author, request.content().trim()));
        if (!post.getAuthor().getId().equals(author.getId())) {
            notificationService.send(post.getAuthor(), "DISCUSSION_REPLY", "有人回复了你的讨论",
                    displayName(author) + "：" + excerpt(reply.getContent(), 120), "/discussions#post-" + postId);
        }
        return toView(post, author);
    }

    @PreAuthorize("hasAnyRole('TEACHER','CORE_STUDENT','MEMBER')")
    @Transactional
    public DiscussionModels.PostView updateReply(Authentication authentication, UUID replyId,
                                                  DiscussionModels.ReplyRequest request) {
        AccountEntity operator = authService.requireAccount(authentication);
        DiscussionReplyEntity reply = requireReply(replyId);
        requireOwner(reply.getAuthor(), operator, false);
        reply.update(request.content().trim());
        replies.save(reply);
        return toView(reply.getPost(), operator);
    }

    @PreAuthorize("hasAnyRole('TEACHER','CORE_STUDENT','MEMBER')")
    @Transactional
    public DiscussionModels.PostView deleteReply(Authentication authentication, UUID replyId) {
        AccountEntity operator = authService.requireAccount(authentication);
        DiscussionReplyEntity reply = requireReply(replyId);
        requireOwner(reply.getAuthor(), operator, true);
        DiscussionPostEntity post = reply.getPost();
        replyLikes.deleteByReplyId(replyId);
        replies.delete(reply);
        return toView(post, operator);
    }

    @PreAuthorize("hasAnyRole('TEACHER','CORE_STUDENT','MEMBER')")
    @Transactional
    public DiscussionModels.PostView toggleReplyLike(Authentication authentication, UUID replyId) {
        AccountEntity account = authService.requireAccount(authentication);
        DiscussionReplyEntity reply = requireReply(replyId);
        if (reply.getAuthor().getId().equals(account.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "不能给自己的回复点赞");
        }
        DiscussionReplyLikeEntity existing = replyLikes.findByReplyIdAndAccountId(replyId, account.getId()).orElse(null);
        if (existing == null) {
            replyLikes.save(new DiscussionReplyLikeEntity(account, reply));
            notificationService.sendAggregated(reply.getAuthor(), "DISCUSSION_LIKE", "你的回复获得了 {count} 个赞",
                    displayName(account) + " 点赞了你的回复", "/discussions#post-" + reply.getPost().getId(),
                    "DISCUSSION_REPLY_LIKE:" + replyId);
        } else {
            replyLikes.delete(existing);
        }
        return toView(reply.getPost(), account);
    }

    private DiscussionModels.PostView toView(DiscussionPostEntity post, AccountEntity viewer) {
        boolean admin = viewer.getRole().isSystemAdmin();
        List<DiscussionModels.ReplyView> replyViews = replies.findByPostIdOrderByCreatedAtAsc(post.getId()).stream()
                .map(reply -> new DiscussionModels.ReplyView(reply.getId(), toAuthor(reply.getAuthor()), reply.getContent(),
                        replyLikes.countByReplyId(reply.getId()), replyLikes.existsByReplyIdAndAccountId(reply.getId(), viewer.getId()),
                        reply.getAuthor().getId().equals(viewer.getId()), admin || reply.getAuthor().getId().equals(viewer.getId()),
                        reply.getCreatedAt(), reply.getUpdatedAt())).toList();
        return new DiscussionModels.PostView(post.getId(), toAuthor(post.getAuthor()), post.getTitle(), post.getContent(),
                postLikes.countByPostId(post.getId()), postLikes.existsByPostIdAndAccountId(post.getId(), viewer.getId()),
                post.getAuthor().getId().equals(viewer.getId()), admin || post.getAuthor().getId().equals(viewer.getId()),
                post.getCreatedAt(), post.getUpdatedAt(), replyViews);
    }

    private DiscussionModels.AuthorView toAuthor(AccountEntity account) {
        return new DiscussionModels.AuthorView(account.getId(), displayName(account), account.getRole().name());
    }

    private String displayName(AccountEntity account) {
        return profiles.findByAccountId(account.getId()).map(MemberProfileEntity::getName).orElse(account.getUsername());
    }

    private DiscussionPostEntity requirePost(UUID id) {
        return posts.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "讨论不存在"));
    }

    private DiscussionReplyEntity requireReply(UUID id) {
        return replies.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "回复不存在"));
    }

    private static void requireOwner(AccountEntity author, AccountEntity operator, boolean allowAdmin) {
        if (!author.getId().equals(operator.getId()) && !(allowAdmin && operator.getRole().isSystemAdmin())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "只能修改自己的内容");
        }
    }

    private static String excerpt(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }
}
