package cn.yeslab.platform.publicsite.model;

import cn.yeslab.platform.publicsite.cms.api.HomepageModels;

import java.util.List;
import java.util.Map;

public final class PublicShowcase {

    private PublicShowcase() {
    }

    public record Home(
            Profile profile,
            Advisor advisor,
            Statistics statistics,
            List<Project> projects,
            List<Member> members,
            Map<String, List<RankingEntry>> rankings,
            List<Update> updates,
            List<Award> awards,
            List<Sponsor> sponsors,
            List<ExternalLink> externalLinks,
            HomepageModels.HomepageContent homepageContent
    ) {
    }

    public record Profile(
            String name,
            String displayName,
            String fullName,
            String slogan,
            String description,
            List<String> researchDirections
    ) {
    }

    public record Statistics(int activeProjects, int members, int achievements) {
    }

    public record Advisor(
            String initials,
            String name,
            String role,
            String description,
            List<String> tags
    ) {
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
            boolean core,
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

    public record Award(String competition, String category, String level, String prize) {
    }

    public record Sponsor(
            String name,
            String type,
            String description,
            List<String> focus,
            String logoUrl,
            String websiteUrl
    ) {
    }

    public record ExternalLink(String platform, String label, String url, boolean enabled) {
    }
}
