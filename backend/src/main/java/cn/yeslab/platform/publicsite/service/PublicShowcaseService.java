package cn.yeslab.platform.publicsite.service;

import cn.yeslab.platform.publicsite.model.PublicShowcase;
import cn.yeslab.platform.publicsite.repository.PublicShowcaseRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class PublicShowcaseService {

    private final PublicShowcaseRepository repository;

    public PublicShowcaseService(PublicShowcaseRepository repository) {
        this.repository = repository;
    }

    public PublicShowcase.Home getHome() {
        return repository.getHome();
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
