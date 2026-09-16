ALTER TABLE recruitment_applications
    ADD COLUMN interview_decision ENUM ('PASSED','REJECTED','WAITLIST') NULL AFTER interview_passed,
    ADD COLUMN interview_decision_interviewer_names VARCHAR(1000) NULL AFTER interview_decision,
    ADD COLUMN interview_decision_opinion LONGTEXT NULL AFTER interview_decision_interviewer_names;

UPDATE recruitment_applications
SET interview_decision = CASE
    WHEN interview_passed = 1 THEN 'PASSED'
    WHEN interview_passed = 0 THEN 'REJECTED'
    ELSE NULL
END;
