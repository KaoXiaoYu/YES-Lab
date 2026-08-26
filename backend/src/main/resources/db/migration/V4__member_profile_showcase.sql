ALTER TABLE member_profiles
    ADD COLUMN showcase_configured BIT NOT NULL DEFAULT b'0';

CREATE TABLE member_featured_projects (
    display_order INTEGER NOT NULL,
    member_id BINARY(16) NOT NULL,
    project_id BINARY(16) NOT NULL,
    PRIMARY KEY (member_id, display_order),
    CONSTRAINT uk_member_featured_projects UNIQUE (member_id, project_id),
    CONSTRAINT fk_member_featured_projects_member FOREIGN KEY (member_id) REFERENCES member_profiles (id),
    CONSTRAINT fk_member_featured_projects_project FOREIGN KEY (project_id) REFERENCES project_teams (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE member_featured_competitions (
    display_order INTEGER NOT NULL,
    member_id BINARY(16) NOT NULL,
    competition_id BINARY(16) NOT NULL,
    PRIMARY KEY (member_id, display_order),
    CONSTRAINT uk_member_featured_competitions UNIQUE (member_id, competition_id),
    CONSTRAINT fk_member_featured_competitions_member FOREIGN KEY (member_id) REFERENCES member_profiles (id),
    CONSTRAINT fk_member_featured_competitions_competition FOREIGN KEY (competition_id) REFERENCES competitions (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
