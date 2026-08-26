package cn.yeslab.platform.publicsite.service;

import cn.yeslab.platform.publicsite.model.PublicShowcase;
import cn.yeslab.platform.publicsite.cms.api.HomepageModels;
import cn.yeslab.platform.publicsite.cms.service.HomepageContentService;
import cn.yeslab.platform.publicsite.repository.PublicShowcaseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class PublicShowcaseService {

    private final PublicShowcaseRepository repository;
    private final HomepageContentService homepageContentService;

    @Autowired
    public PublicShowcaseService(PublicShowcaseRepository repository, HomepageContentService homepageContentService) {
        this.repository = repository;
        this.homepageContentService = homepageContentService;
    }

    public PublicShowcaseService(PublicShowcaseRepository repository) {
        this.repository = repository;
        this.homepageContentService = null;
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
                fallback.projects(), fallback.members(), fallback.rankings(),
                content.updates().stream().map(item -> new PublicShowcase.Update(item.publishedAt(), item.type(), item.title(), item.slug())).toList(),
                content.awards().stream().map(item -> new PublicShowcase.Award(item.competition(), item.category(), item.level(), item.prize())).toList(),
                content.sponsors().stream().map(item -> new PublicShowcase.Sponsor(
                        item.name(), item.type(), item.description(), item.focus(), item.logoUrl(), item.websiteUrl()
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
        return repository.findRanking(board);
    }

    public List<PublicShowcase.Update> getUpdates() {
        return repository.findUpdates();
    }
}
