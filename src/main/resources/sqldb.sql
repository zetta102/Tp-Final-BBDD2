-- PostgreSQL DDL Structural Schema
DROP TABLE IF EXISTS components CASCADE;
DROP TABLE IF EXISTS tracks CASCADE;
DROP TABLE IF EXISTS assets CASCADE;

CREATE TABLE assets
(
    asset_id         UUID PRIMARY KEY,
    title            VARCHAR(255)     NOT NULL,
    release_year     INT              NOT NULL,
    duration_seconds DOUBLE PRECISION NOT NULL
);

CREATE TABLE tracks
(
    track_id   UUID PRIMARY KEY,
    asset_id   UUID        NOT NULL REFERENCES assets (asset_id) ON DELETE CASCADE,
    track_type VARCHAR(50) NOT NULL,
    metadata   JSONB
);

CREATE TABLE components
(
    component_id           UUID PRIMARY KEY,
    track_id               UUID NOT NULL REFERENCES tracks (track_id) ON DELETE CASCADE,
    event_rate_numerator   INT  NOT NULL,
    event_rate_denominator INT  NOT NULL,
    x_size                 INT,
    y_size                 INT,
    metadata               JSONB
);

-- Register Bootstrap Catalog Metadata
INSERT INTO assets (asset_id, title, release_year, duration_seconds)
VALUES ('9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d', 'The Irish Stranger', 2026, 7200.0);

INSERT INTO tracks (track_id, asset_id, track_type, metadata)
VALUES ('0c5f2b8a-3c4a-4d2b-aa5e-8d0768b8e0a2', '9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d', 'video', '{
  "description": "UHD Video Track"
}');