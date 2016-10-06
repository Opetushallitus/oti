ALTER TABLE registration
    ADD COLUMN language_code TEXT NOT NULL DEFAULT 'fi' REFERENCES language(code);
