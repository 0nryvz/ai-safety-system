-- V5 — Add code column to cameras

ALTER TABLE cameras
    ADD COLUMN code VARCHAR(100) NOT NULL;

ALTER TABLE cameras
    ADD CONSTRAINT cameras_code_uk UNIQUE (code);