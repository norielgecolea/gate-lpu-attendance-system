-- Optional student birthdate, used for birthday greetings on the gate kiosk.

ALTER TABLE students
    ADD COLUMN IF NOT EXISTS birthdate DATE;
