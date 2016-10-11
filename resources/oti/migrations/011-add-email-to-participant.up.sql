ALTER TABLE participant
    ADD COLUMN email TEXT;

UPDATE participant SET email = 'testi@oph.fi';

ALTER TABLE participant
    ALTER email SET NOT NULL;
