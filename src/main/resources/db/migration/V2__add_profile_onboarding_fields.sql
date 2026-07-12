ALTER TABLE profile
    ADD COLUMN birth_date DATE,
    ADD COLUMN gender VARCHAR(32),
    ADD COLUMN onboarding_completed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT ck_profile_gender
        CHECK (gender IS NULL OR gender IN ('FEMALE', 'MALE', 'NON_BINARY', 'OTHER', 'PREFER_NOT_TO_SAY'));
