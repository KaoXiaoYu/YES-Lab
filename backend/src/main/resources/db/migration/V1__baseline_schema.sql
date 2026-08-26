CREATE TABLE accounts (
    enabled BIT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    id BINARY(16) NOT NULL,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role ENUM ('CORE_STUDENT','MEMBER','TEACHER','VISITOR') NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_accounts_username UNIQUE (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE member_profiles (
    current_rank INTEGER,
    total_points INTEGER NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    account_id BINARY(16) NOT NULL,
    id BINARY(16) NOT NULL,
    grade VARCHAR(30),
    member_code VARCHAR(64) NOT NULL,
    name VARCHAR(80) NOT NULL,
    class_name VARCHAR(100),
    major VARCHAR(100),
    headline VARCHAR(160),
    internal_contact VARCHAR(200),
    avatar_url VARCHAR(500),
    profile_html LONGTEXT NOT NULL,
    status ENUM ('CANDIDATE','EXITED','OFFICIAL','PAUSED','TRIAL') NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_member_profiles_account UNIQUE (account_id),
    CONSTRAINT uk_member_profiles_code UNIQUE (member_code),
    CONSTRAINT fk_member_profiles_account FOREIGN KEY (account_id) REFERENCES accounts (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE member_achievement_records (
    member_id BINARY(16) NOT NULL,
    record_text VARCHAR(500),
    CONSTRAINT fk_member_achievement_records_member FOREIGN KEY (member_id) REFERENCES member_profiles (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE member_project_records (
    member_id BINARY(16) NOT NULL,
    record_text VARCHAR(500),
    CONSTRAINT fk_member_project_records_member FOREIGN KEY (member_id) REFERENCES member_profiles (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE member_skill_tags (
    member_id BINARY(16) NOT NULL,
    tag VARCHAR(80) NOT NULL,
    CONSTRAINT fk_member_skill_tags_member FOREIGN KEY (member_id) REFERENCES member_profiles (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE project_teams (
    end_date DATE,
    externally_visible BIT NOT NULL,
    start_date DATE,
    cover_size_bytes BIGINT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    advisor_profile_id BINARY(16),
    created_by_account_id BINARY(16) NOT NULL,
    id BINARY(16) NOT NULL,
    leader_profile_id BINARY(16) NOT NULL,
    cover_content_type VARCHAR(80),
    cover_stored_name VARCHAR(120),
    team_name VARCHAR(120) NOT NULL,
    project_name VARCHAR(160) NOT NULL,
    document_url VARCHAR(500),
    git_repository_url VARCHAR(500),
    cover_original_name VARCHAR(255),
    description LONGTEXT NOT NULL,
    outcomes LONGTEXT,
    progress_description LONGTEXT,
    status ENUM ('ACTIVE','ARCHIVED','COMPLETED','PAUSED','PLANNING') NOT NULL,
    type ENUM ('COMPETITION','INTERNAL','OPEN_SOURCE','RESEARCH') NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_project_teams_advisor FOREIGN KEY (advisor_profile_id) REFERENCES member_profiles (id),
    CONSTRAINT fk_project_teams_creator FOREIGN KEY (created_by_account_id) REFERENCES accounts (id),
    CONSTRAINT fk_project_teams_leader FOREIGN KEY (leader_profile_id) REFERENCES member_profiles (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE project_required_skill_tags (
    project_id BINARY(16) NOT NULL,
    tag VARCHAR(80) NOT NULL,
    CONSTRAINT fk_project_required_tags_project FOREIGN KEY (project_id) REFERENCES project_teams (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE project_stage_goals (
    project_id BINARY(16) NOT NULL,
    goal VARCHAR(500) NOT NULL,
    CONSTRAINT fk_project_stage_goals_project FOREIGN KEY (project_id) REFERENCES project_teams (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE project_team_administrators (
    member_profile_id BINARY(16) NOT NULL,
    project_id BINARY(16) NOT NULL,
    PRIMARY KEY (member_profile_id, project_id),
    CONSTRAINT fk_project_admin_member FOREIGN KEY (member_profile_id) REFERENCES member_profiles (id),
    CONSTRAINT fk_project_admin_project FOREIGN KEY (project_id) REFERENCES project_teams (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE project_team_members (
    member_profile_id BINARY(16) NOT NULL,
    project_id BINARY(16) NOT NULL,
    PRIMARY KEY (member_profile_id, project_id),
    CONSTRAINT fk_project_member_profile FOREIGN KEY (member_profile_id) REFERENCES member_profiles (id),
    CONSTRAINT fk_project_member_project FOREIGN KEY (project_id) REFERENCES project_teams (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE achievement_news (
    published_date DATE NOT NULL,
    visible BIT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    created_by_account_id BINARY(16) NOT NULL,
    id BINARY(16) NOT NULL,
    source_name VARCHAR(120) NOT NULL,
    title VARCHAR(220) NOT NULL,
    source_url VARCHAR(800) NOT NULL,
    summary LONGTEXT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_achievement_news_creator FOREIGN KEY (created_by_account_id) REFERENCES accounts (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE competitions (
    competition_date DATE,
    display_order INTEGER NOT NULL,
    featured BIT NOT NULL,
    national_date DATE,
    provincial_date DATE,
    certificate_size_bytes BIGINT,
    created_at DATETIME(6) NOT NULL,
    reviewed_at DATETIME(6),
    updated_at DATETIME(6) NOT NULL,
    advisor_profile_id BINARY(16),
    captain_profile_id BINARY(16) NOT NULL,
    id BINARY(16) NOT NULL,
    project_id BINARY(16),
    reviewer_account_id BINARY(16),
    submitted_by_account_id BINARY(16) NOT NULL,
    advisor_name VARCHAR(80),
    captain_name VARCHAR(80) NOT NULL,
    certificate_content_type VARCHAR(80),
    certificate_stored_name VARCHAR(100),
    award_name VARCHAR(160),
    name VARCHAR(180) NOT NULL,
    track VARCHAR(180),
    review_note VARCHAR(500),
    certificate_original_name VARCHAR(255),
    description LONGTEXT NOT NULL,
    level ENUM ('INTERNATIONAL','NATIONAL','OTHER','PROVINCIAL','REGIONAL','SCHOOL') NOT NULL,
    lifecycle ENUM ('FINISHED','ONGOING','PLANNED') NOT NULL,
    verification_status ENUM ('APPROVED','NOT_REQUIRED','PENDING','REJECTED') NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_competitions_certificate UNIQUE (certificate_stored_name),
    CONSTRAINT fk_competitions_advisor FOREIGN KEY (advisor_profile_id) REFERENCES member_profiles (id),
    CONSTRAINT fk_competitions_captain FOREIGN KEY (captain_profile_id) REFERENCES member_profiles (id),
    CONSTRAINT fk_competitions_project FOREIGN KEY (project_id) REFERENCES project_teams (id),
    CONSTRAINT fk_competitions_reviewer FOREIGN KEY (reviewer_account_id) REFERENCES accounts (id),
    CONSTRAINT fk_competitions_submitter FOREIGN KEY (submitted_by_account_id) REFERENCES accounts (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE competition_images (
    display_order INTEGER NOT NULL,
    size_bytes BIGINT NOT NULL,
    competition_id BINARY(16) NOT NULL,
    id BINARY(16) NOT NULL,
    content_type VARCHAR(80) NOT NULL,
    stored_name VARCHAR(100) NOT NULL,
    description VARCHAR(300) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_competition_images_stored_name UNIQUE (stored_name),
    CONSTRAINT fk_competition_images_competition FOREIGN KEY (competition_id) REFERENCES competitions (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE competition_participants (
    captain BIT NOT NULL,
    display_order INTEGER NOT NULL,
    competition_id BINARY(16) NOT NULL,
    id BINARY(16) NOT NULL,
    linked_profile_id BINARY(16),
    display_name VARCHAR(80) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_competition_participants_competition FOREIGN KEY (competition_id) REFERENCES competitions (id),
    CONSTRAINT fk_competition_participants_profile FOREIGN KEY (linked_profile_id) REFERENCES member_profiles (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE homepage_content (
    id BIGINT NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    updated_by VARCHAR(64) NOT NULL,
    content_json LONGTEXT NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE recruitment_applications (
    interview_passed BIT,
    interview_score INTEGER,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    applicant_account_id BINARY(16) NOT NULL,
    converted_member_id BINARY(16),
    id BINARY(16) NOT NULL,
    interviewer_account_id BINARY(16),
    grade VARCHAR(30),
    interviewer_name VARCHAR(80),
    name VARCHAR(80) NOT NULL,
    class_name VARCHAR(100) NOT NULL,
    linked_quiz_id VARCHAR(100),
    major VARCHAR(100) NOT NULL,
    contact VARCHAR(200) NOT NULL,
    experience LONGTEXT,
    interview_evaluation LONGTEXT,
    stage ENUM ('FORMAL_MEMBER','INTERVIEW','PROBATION','REJECTED','SCREENING','SIGNUP','SKILL_TEST') NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_recruitment_applicant UNIQUE (applicant_account_id),
    CONSTRAINT fk_recruitment_applicant FOREIGN KEY (applicant_account_id) REFERENCES accounts (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE recruitment_existing_skills (
    application_id BINARY(16) NOT NULL,
    skill VARCHAR(100),
    CONSTRAINT fk_recruitment_skills_application FOREIGN KEY (application_id) REFERENCES recruitment_applications (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE recruitment_intended_tags (
    application_id BINARY(16) NOT NULL,
    tag VARCHAR(80) NOT NULL,
    CONSTRAINT fk_recruitment_intended_tags_application FOREIGN KEY (application_id) REFERENCES recruitment_applications (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE recruitment_interest_directions (
    application_id BINARY(16) NOT NULL,
    direction VARCHAR(80) NOT NULL,
    CONSTRAINT fk_recruitment_directions_application FOREIGN KEY (application_id) REFERENCES recruitment_applications (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE recruitment_suggested_tags (
    application_id BINARY(16) NOT NULL,
    tag VARCHAR(80),
    CONSTRAINT fk_recruitment_suggested_tags_application FOREIGN KEY (application_id) REFERENCES recruitment_applications (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE recruitment_status_history (
    changed_at DATETIME(6) NOT NULL,
    application_id BINARY(16) NOT NULL,
    id BINARY(16) NOT NULL,
    operator_account_id BINARY(16) NOT NULL,
    operator_username VARCHAR(64) NOT NULL,
    note VARCHAR(500),
    from_stage ENUM ('FORMAL_MEMBER','INTERVIEW','PROBATION','REJECTED','SCREENING','SIGNUP','SKILL_TEST'),
    to_stage ENUM ('FORMAL_MEMBER','INTERVIEW','PROBATION','REJECTED','SCREENING','SIGNUP','SKILL_TEST') NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_recruitment_history_application (application_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
