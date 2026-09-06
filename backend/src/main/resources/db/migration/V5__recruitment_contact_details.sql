-- Preserve the original contact field; only classify recognizable historical values.
ALTER TABLE recruitment_applications
    ADD COLUMN email VARCHAR(190) NULL,
    ADD COLUMN phone VARCHAR(30) NULL,
    ADD COLUMN wechat VARCHAR(80) NULL,
    ADD COLUMN self_introduction LONGTEXT NULL;

UPDATE recruitment_applications SET email = LOWER(TRIM(contact))
WHERE contact LIKE '%@%.%' AND CHAR_LENGTH(TRIM(contact)) <= 190;
UPDATE recruitment_applications SET phone = TRIM(contact)
WHERE TRIM(contact) REGEXP '^[+]?[0-9][0-9 -]{6,24}$' AND CHAR_LENGTH(TRIM(contact)) <= 30;
