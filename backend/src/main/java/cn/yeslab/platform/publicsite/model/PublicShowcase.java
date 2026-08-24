package cn.yeslab.platform.publicsite.model;

import java.util.List;
import java.util.Map;

public final class PublicShowcase {

    private PublicShowcase() {
    }

    public record Home(
            Profile profile,
            Statistics statistics,
            List<Project> projects,
            List<Member> members,
            Map<String, List<RankingEntry>> rankings,
            List<Update> updates,
            List<Sponsor> sponsors,
            List<ExternalLink> externalLinks
    ) {
    }

    public record Profile(
            String name,
            String displayName,
            String slogan,
            String description,
            List<String> researchDirections
    ) {
    }

    public record Statistics(int activeProjects, int members, int achievements) {
    }

    public record Project(
            String slug,
            String number,
            String category,
            String title,
            String summary,
            String status,
            String lead,
            int memberCount,
            List<String> tech,
            String result,
            String repositoryUrl,
            String documentUrl
    ) {
    }

    public record Member(
            String slug,
            String initials,
            String name,
            String gradeAndMajor,
            List<String> tags,
            int points,
            int rank,
            boolean visible
    ) {
    }

    public record RankingEntry(
            int rank,
            String memberSlug,
            String name,
            String initials,
            String primaryTag,
            int points
    ) {
    }

    public record Update(String publishedAt, String type, String title, String slug) {
    }

    public record Sponsor(String name, String type, String description, List<String> focus) {
    }

    public record ExternalLink(String platform, String label, String url, boolean enabled) {
    }
}
