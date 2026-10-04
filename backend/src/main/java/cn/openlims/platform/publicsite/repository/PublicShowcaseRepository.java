package cn.openlims.platform.publicsite.repository;

import cn.openlims.platform.publicsite.model.PublicShowcase;

import java.util.List;
import java.util.Optional;

public interface PublicShowcaseRepository {

    PublicShowcase.Home getHome();

    List<PublicShowcase.Project> findProjects();

    Optional<PublicShowcase.Project> findProjectBySlug(String slug);

    List<PublicShowcase.Member> findVisibleMembers();

    Optional<PublicShowcase.Member> findVisibleMemberBySlug(String slug);

    List<PublicShowcase.RankingEntry> findRanking(String board);

    List<PublicShowcase.Update> findUpdates();
}
