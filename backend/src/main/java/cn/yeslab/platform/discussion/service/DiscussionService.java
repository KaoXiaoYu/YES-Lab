package cn.yeslab.platform.discussion.service;

import cn.yeslab.platform.common.error.ApiException;
import cn.yeslab.platform.discussion.api.DiscussionModels;
import cn.yeslab.platform.discussion.model.DiscussionContentNumberEntity;
import cn.yeslab.platform.discussion.model.DiscussionContentType;
import cn.yeslab.platform.discussion.model.DiscussionPostEntity;
import cn.yeslab.platform.discussion.model.DiscussionPostLikeEntity;
import cn.yeslab.platform.discussion.model.DiscussionReplyEntity;
import cn.yeslab.platform.discussion.model.DiscussionReplyLikeEntity;
import cn.yeslab.platform.discussion.repository.DiscussionContentNumberRepository;
import cn.yeslab.platform.discussion.repository.DiscussionPostLikeRepository;
import cn.yeslab.platform.discussion.repository.DiscussionPostRepository;
import cn.yeslab.platform.discussion.repository.DiscussionReplyLikeRepository;
import cn.yeslab.platform.discussion.repository.DiscussionReplyRepository;
import cn.yeslab.platform.identity.model.AccountEntity;
import cn.yeslab.platform.identity.model.MemberProfileEntity;
import cn.yeslab.platform.identity.model.MemberStatus;
import cn.yeslab.platform.identity.repository.MemberProfileRepository;
import cn.yeslab.platform.identity.service.AuthService;
import cn.yeslab.platform.notification.service.NotificationService;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class DiscussionService {
    private static final PolicyFactory DISCUSSION_HTML_POLICY = new HtmlPolicyBuilder()
            .allowElements("p", "h2", "h3", "blockquote", "ul", "ol", "li", "strong", "em", "s",
                    "code", "pre", "br", "hr", "a", "img")
            .allowWithoutAttributes("p", "h2", "h3", "blockquote", "ul", "ol", "li", "strong", "em", "s",
                    "code", "pre", "br", "hr")
            .allowUrlProtocols("http", "https")
            .allowAttributes("href").onElements("a")
            .allowAttributes("src", "alt", "title").onElements("img")
            .requireRelNofollowOnLinks()
            .toFactory();
    private final DiscussionPostRepository posts;
    private final DiscussionReplyRepository replies;
    private final DiscussionPostLikeRepository postLikes;
    private final DiscussionReplyLikeRepository replyLikes;
    private final DiscussionContentNumberRepository contentNumbers;
    private final MemberProfileRepository profiles;
    private final AuthService authService;
    private final NotificationService notificationService;

    public DiscussionService(DiscussionPostRepository posts, DiscussionReplyRepository replies,
                             DiscussionPostLikeRepository postLikes, DiscussionReplyLikeRepository replyLikes,
                             DiscussionContentNumberRepository contentNumbers, MemberProfileRepository profiles, AuthService authService,
                             NotificationService notificationService) {
        this.posts = posts;
        this.replies = replies;
        this.postLikes = postLikes;
        this.replyLikes = replyLikes;
        this.contentNumbers = contentNumbers;
        this.profiles = profiles;
        this.authService = authService;
        this.notificationService = notificationService;
    }

    @Transactional
    public List<DiscussionModels.PostView> list(Authentication authentication, DiscussionModels.SortMode sort) {
        AccountEntity viewer = authenticatedAccount(authentication);
        List<DiscussionModels.PostView> views = posts.findAll().stream().map(post -> toView(post, viewer)).toList();
        return views.stream().sorted(postComparator(sort)).toList();
    }

    @Transactional
    public List<DiscussionModels.ContributionView> contributions(UUID profileId) {
        MemberProfileEntity profile = profiles.findById(profileId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "成员资料不存在"));
        if (profile.getStatus() != MemberStatus.OFFICIAL) {
            throw new ApiException(HttpStatus.NOT_FOUND, "该成员主页暂未公开");
        }

        List<DiscussionModels.ContributionView> contributions = new ArrayList<>();
        posts.findByAuthorIdOrderByCreatedAtDesc(profile.getAccount().getId()).forEach(post ->
                contributions.add(new DiscussionModels.ContributionView(
                        numberFor(DiscussionContentType.POST, post.getId(), post.getCreatedAt()), "POST",
                        post.getId(), post.getTitle(), safeForRead(post.getContent()), post.getCreatedAt())));
        replies.findByAuthorIdOrderByCreatedAtDesc(profile.getAccount().getId()).forEach(reply ->
                contributions.add(new DiscussionModels.ContributionView(
                        numberFor(DiscussionContentType.REPLY, reply.getId(), reply.getCreatedAt()), "REPLY",
                        reply.getPost().getId(), reply.getPost().getTitle(), safeForRead(reply.getContent()), reply.getCreatedAt())));
        return contributions.stream()
                .sorted(Comparator.comparing(DiscussionModels.ContributionView::createdAt).reversed()
                        .thenComparing(Comparator.comparingLong(DiscussionModels.ContributionView::contentNumber).reversed()))
                .toList();
    }

    @PreAuthorize("hasAnyRole('TEACHER','CORE_STUDENT','MEMBER')")
    @Transactional
    public DiscussionModels.PostView create(Authentication authentication, DiscussionModels.PostRequest request) {
        AccountEntity author = authService.requireAccount(authentication);
        DiscussionPostEntity post = posts.saveAndFlush(new DiscussionPostEntity(
                author, request.title().trim(), cleanContent(request.content()), Boolean.TRUE.equals(request.announcement())));
        numberFor(DiscussionContentType.POST, post.getId(), post.getCreatedAt());
        if (post.isAnnouncement()) {
            notificationService.broadcastDiscussionAnnouncement("讨论板公告：" + post.getTitle(),
                    displayName(author) + " 发布了公告：" + excerpt(post.getContent(), 180),
                    "/discussions#post-" + post.getId());
        }
        return toView(post, author);
    }

    @PreAuthorize("hasAnyRole('TEACHER','CORE_STUDENT','MEMBER')")
    @Transactional
    public DiscussionModels.PostView update(Authentication authentication, UUID postId, DiscussionModels.PostRequest request) {
        AccountEntity operator = authService.requireAccount(authentication);
        DiscussionPostEntity post = requirePost(postId);
        if (post.isAnnouncement()) {
            throw new ApiException(HttpStatus.CONFLICT, "公告发布后不能修改");
        }
        requireOwner(post.getAuthor(), operator, false);
        post.update(request.title().trim(), cleanContent(request.content()));
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

    @PreAuthorize("hasAnyRole('TEACHER','CORE_STUDENT')")
    @Transactional
    public DiscussionModels.PostView togglePin(Authentication authentication, UUID postId) {
        AccountEntity operator = authService.requireAccount(authentication);
        DiscussionPostEntity post = requirePost(postId);
        post.togglePinned();
        posts.save(post);
        return toView(post, operator);
    }

    @PreAuthorize("hasAnyRole('TEACHER','CORE_STUDENT','MEMBER')")
    @Transactional
    public DiscussionModels.PostView reply(Authentication authentication, UUID postId, DiscussionModels.ReplyRequest request) {
        AccountEntity author = authService.requireAccount(authentication);
        DiscussionPostEntity post = requirePost(postId);
        DiscussionReplyEntity reply = replies.saveAndFlush(new DiscussionReplyEntity(post, author, cleanContent(request.content())));
        numberFor(DiscussionContentType.REPLY, reply.getId(), reply.getCreatedAt());
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
        reply.update(cleanContent(request.content()));
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
        boolean signedIn = viewer != null;
        boolean admin = signedIn && viewer.getRole().isSystemAdmin();
        List<DiscussionModels.ReplyView> replyViews = replies.findByPostIdOrderByCreatedAtAsc(post.getId()).stream()
                .map(reply -> new DiscussionModels.ReplyView(reply.getId(),
                        numberFor(DiscussionContentType.REPLY, reply.getId(), reply.getCreatedAt()),
                        toAuthor(reply.getAuthor()), safeForRead(reply.getContent()),
                        replyLikes.countByReplyId(reply.getId()), signedIn && replyLikes.existsByReplyIdAndAccountId(reply.getId(), viewer.getId()),
                        signedIn && reply.getAuthor().getId().equals(viewer.getId()),
                        admin || signedIn && reply.getAuthor().getId().equals(viewer.getId()),
                        reply.getCreatedAt(), reply.getUpdatedAt())).toList();
        return new DiscussionModels.PostView(post.getId(),
                numberFor(DiscussionContentType.POST, post.getId(), post.getCreatedAt()),
                toAuthor(post.getAuthor()), post.getTitle(), safeForRead(post.getContent()),
                postLikes.countByPostId(post.getId()), signedIn && postLikes.existsByPostIdAndAccountId(post.getId(), viewer.getId()),
                signedIn && !post.isAnnouncement() && post.getAuthor().getId().equals(viewer.getId()),
                admin || signedIn && post.getAuthor().getId().equals(viewer.getId()),
                post.isAnnouncement(), post.isPinned(), admin, post.getPinnedAt(),
                post.getCreatedAt(), post.getUpdatedAt(), replyViews);
    }

    private DiscussionModels.AuthorView toAuthor(AccountEntity account) {
        MemberProfileEntity profile = profiles.findByAccountId(account.getId()).orElse(null);
        return new DiscussionModels.AuthorView(account.getId(), profile == null ? null : profile.getId(),
                profile == null ? account.getUsername() : profile.getName(),
                account.getRole().name(), profile == null ? null : profile.getAvatarUrl());
    }

    private long numberFor(DiscussionContentType type, UUID contentId, java.time.Instant createdAt) {
        return contentNumbers.findByContentId(contentId)
                .orElseGet(() -> contentNumbers.saveAndFlush(new DiscussionContentNumberEntity(type, contentId, createdAt)))
                .getSequenceNumber();
    }

    private static Comparator<DiscussionModels.PostView> postComparator(DiscussionModels.SortMode sort) {
        Comparator<DiscussionModels.PostView> newest = Comparator
                .comparing(DiscussionModels.PostView::createdAt).reversed()
                .thenComparing(Comparator.comparingLong(DiscussionModels.PostView::contentNumber).reversed());
        return switch (sort) {
            case OLDEST -> Comparator.comparing(DiscussionModels.PostView::createdAt)
                    .thenComparingLong(DiscussionModels.PostView::contentNumber);
            case ID_ASC -> Comparator.comparingLong(DiscussionModels.PostView::contentNumber);
            case ID_DESC -> Comparator.comparingLong(DiscussionModels.PostView::contentNumber).reversed();
            case MOST_LIKED -> Comparator.comparingLong(DiscussionModels.PostView::likeCount).reversed()
                    .thenComparing(newest);
            case MOST_REPLIED -> Comparator.comparingInt((DiscussionModels.PostView view) -> view.replies().size()).reversed()
                    .thenComparing(newest);
            case NEWEST -> newest;
        };
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
        String text = safeForRead(value).replaceAll("<[^>]+>", " ")
                .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
                .replaceAll("\\s+", " ").trim();
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    private AccountEntity authenticatedAccount(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) return null;
        return authService.requireAccount(authentication);
    }

    private static String cleanContent(String value) {
        String safe = safeForRead(value == null ? "" : value.trim());
        String text = safe.replaceAll("<[^>]+>", "").replace("&nbsp;", "").trim();
        if (text.isEmpty() && !safe.contains("<img")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请输入有效的讨论内容");
        }
        return safe;
    }

    private static String safeForRead(String value) {
        return DISCUSSION_HTML_POLICY.sanitize(value == null ? "" : value);
    }
}
