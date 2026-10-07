package cn.openlims.platform.publicsite.service;

import cn.openlims.platform.points.service.PointService;
import cn.openlims.platform.publicsite.cms.api.HomepageModels;
import cn.openlims.platform.publicsite.cms.service.HomepageContentService;
import cn.openlims.platform.publicsite.model.PublicShowcase;
import cn.openlims.platform.publicsite.repository.PublicShowcaseRepository;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PublicShowcaseService {

    private final PublicShowcaseRepository repository;
    private final PointService points;
    private final HomepageContentService homepageContentService;

    @Autowired
    public PublicShowcaseService(PublicShowcaseRepository repository, HomepageContentService homepageContentService, PointService points) {
        this.repository = repository;
        this.homepageContentService = homepageContentService;
        this.points = points;
    }

    public PublicShowcaseService(PublicShowcaseRepository repository) {
        this.repository = repository;
        this.homepageContentService = null;
        this.points = null;
    }

    public PublicShowcase.Home getHome() {
        PublicShowcase.Home fallback = repository.getHome();
        if (homepageContentService == null) return fallback;
        HomepageModels.HomepageContent content = homepageContentService.publicContent();
        return new PublicShowcase.Home(
                new PublicShowcase.Profile(
                        content.profile().name(), content.profile().displayName(), content.profile().fullName(),
                        content.profile().slogan(), content.profile().description(), content.profile().researchDirections()
                ),
                fallback.advisor(),
                new PublicShowcase.Statistics(
                        fallback.statistics().activeProjects(), fallback.statistics().members(), content.awards().size()
                ),
                fallback.projects(), fallback.members(), points.publicRankings(), points.publicRankingCount(),
                content.updates().stream().map(item -> new PublicShowcase.Update(item.publishedAt(), item.type(), item.title(), item.slug())).toList(),
                content.awards().stream().map(item -> new PublicShowcase.Award(item.competition(), item.category(), item.level(), item.prize())).toList(),
                content.sponsors().stream().map(item -> new PublicShowcase.Sponsor(
                        item.name(), item.type(), item.description(), item.focus(), item.logoUrl(), item.websiteUrl(), item.cooperationDescription()
                )).toList(),
                content.externalLinks().stream().map(item -> new PublicShowcase.ExternalLink(
                        item.platform(), item.label(), item.url(), item.enabled()
                )).toList(),
                content
        );
    }

    public List<PublicShowcase.Project> getProjects() {
        return repository.findProjects();
    }

    public PublicShowcase.Project getProject(String slug) {
        return repository.findProjectBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
    }

    public List<PublicShowcase.Member> getMembers() {
        return repository.findVisibleMembers();
    }

    public PublicShowcase.Member getMember(String slug) {
        return repository.findVisibleMemberBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
    }

    public List<PublicShowcase.RankingEntry> getRanking(String board) {
        Map<String, List<PublicShowcase.RankingEntry>> boards = points == null ? Map.of() : points.publicRankings();
        if (!List.of("总榜", "月榜", "年榜").contains(board)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的榜单");
        }
        return boards.getOrDefault(board, List.of());
    }

    public List<PublicShowcase.Update> getUpdates() {
        return repository.findUpdates();
    }
}
