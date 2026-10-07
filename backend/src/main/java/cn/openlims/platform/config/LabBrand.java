package cn.openlims.platform.config;

import tools.jackson.databind.ObjectMapper;

/** Shared with Vite through config/branding.json; packaged by Maven resources. */
public final class LabBrand {
    private record Branding(String name, String displayName, String fullName, String description,
                            String logo, String logoOnDark, String favicon, String shareImage, String repositoryUrl) {}

    private static final Branding VALUE = load();
    public static final String NAME = VALUE.name();
    public static final String DISPLAY_NAME = VALUE.displayName();
    public static final String FULL_NAME = VALUE.fullName();
    public static final String DESCRIPTION = VALUE.description();
    public static final String REPOSITORY_URL = VALUE.repositoryUrl();

    private LabBrand() {}

    private static Branding load() {
        try (var input = LabBrand.class.getResourceAsStream("/branding.json")) {
            if (input == null) throw new IllegalStateException("Missing shared branding.json");
            return new ObjectMapper().readValue(input, Branding.class);
        } catch (Exception error) {
            throw new IllegalStateException("Cannot load shared lab branding", error);
        }
    }
}
