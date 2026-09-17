ALTER TABLE recruitment_applications
    ADD COLUMN interview_result_pending BIT NOT NULL DEFAULT 0 AFTER interview_decision_opinion;
