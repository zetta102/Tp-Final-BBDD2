-- ============================================================
-- Netflix Media Document - PostgreSQL Schema
-- Based on Netflix MediaDatabase Media Timeline Data Model
-- ============================================================
-- PostgreSQL is used for:
--   - Master data management (documents, tracks, components)
--   - Schema definitions and validation (JSON Schema storage)
--   - Referential integrity and ACID transactions
-- ============================================================

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ============================================================
-- SCHEMA DEFINITIONS
-- ============================================================

-- Document Schemas: Store JSON Schema definitions for validation
CREATE TABLE document_schemas (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    schema_name VARCHAR(255) NOT NULL UNIQUE,
    version VARCHAR(50) NOT NULL,
    json_schema JSONB NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Media Documents: Master record for each media document
CREATE TABLE media_documents (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    external_id VARCHAR(255) UNIQUE,
    schema_id UUID REFERENCES document_schemas(id),
    document_type VARCHAR(100) NOT NULL,
    title VARCHAR(500),
    metadata JSONB DEFAULT '{}',
    status VARCHAR(50) DEFAULT 'active',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Tracks: Groups events by media modality (video, audio, text)
CREATE TABLE tracks (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    document_id UUID NOT NULL REFERENCES media_documents(id) ON DELETE CASCADE,
    track_id VARCHAR(50) NOT NULL,
    track_type VARCHAR(50) NOT NULL, -- video, audio, text, etc.
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(document_id, track_id)
);

-- Components: Sub-groupings within tracks (e.g., left/right audio channels)
CREATE TABLE components (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    track_id UUID NOT NULL REFERENCES tracks(id) ON DELETE CASCADE,
    component_id VARCHAR(50) NOT NULL,
    event_rate_numerator INTEGER NOT NULL DEFAULT 24000,
    event_rate_denominator INTEGER NOT NULL DEFAULT 1001,
    x_size INTEGER, -- spatial resolution width
    y_size INTEGER, -- spatial resolution height
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(track_id, component_id)
);

-- Create indexes for common queries
CREATE INDEX idx_media_documents_type ON media_documents(document_type);
CREATE INDEX idx_media_documents_status ON media_documents(status);
CREATE INDEX idx_media_documents_metadata ON media_documents USING GIN (metadata);
CREATE INDEX idx_tracks_document_id ON tracks(document_id);
CREATE INDEX idx_tracks_type ON tracks(track_type);
CREATE INDEX idx_components_track_id ON components(track_id);

-- ============================================================
-- SEED DATA
-- ============================================================

-- Insert document schemas
INSERT INTO document_schemas (id, schema_name, version, json_schema, description) VALUES
(
    'a1b2c3d4-e5f6-7890-abcd-ef1234567890',
    'video_face_detection',
    '1.0.0',
    '{
        "$schema": "http://json-schema.org/draft-07/schema#",
        "type": "object",
        "properties": {
            "algorithm": {"type": "string"},
            "confidence_threshold": {"type": "number", "minimum": 0, "maximum": 1}
        },
        "required": ["algorithm"]
    }',
    'Schema for face detection analysis results'
),
(
    'b2c3d4e5-f6a7-8901-bcde-f12345678901',
    'subtitle_ttml',
    '1.0.0',
    '{
        "$schema": "http://json-schema.org/draft-07/schema#",
        "type": "object",
        "properties": {
            "language": {"type": "string"},
            "format": {"type": "string", "enum": ["TTML", "WebVTT", "SRT"]}
        },
        "required": ["language"]
    }',
    'Schema for subtitle/timed text documents'
),
(
    'c3d4e5f6-a7b8-9012-cdef-123456789012',
    'vmaf_quality',
    '1.0.0',
    '{
        "$schema": "http://json-schema.org/draft-07/schema#",
        "type": "object",
        "properties": {
            "model_version": {"type": "string"},
            "reference_file": {"type": "string"}
        },
        "required": ["model_version"]
    }',
    'Schema for VMAF video quality scores'
),
(
    'd4e5f6a7-b8c9-0123-defa-234567890123',
    'audio_analysis',
    '1.0.0',
    '{
        "$schema": "http://json-schema.org/draft-07/schema#",
        "type": "object",
        "properties": {
            "channels": {"type": "integer"},
            "sample_rate": {"type": "integer"}
        },
        "required": ["channels"]
    }',
    'Schema for audio analysis including loudness and channel information'
);

-- Insert Media Document: Stranger Things S4E1
INSERT INTO media_documents (id, external_id, schema_id, document_type, title, metadata) VALUES
(
    '11111111-1111-1111-1111-111111111111',
    'NMDB-ST-S4E1-001',
    'a1b2c3d4-e5f6-7890-abcd-ef1234567890',
    'video_face_detection',
    'Stranger Things S4E1 - Face Detection Analysis',
    '{
        "algorithm": "video_face_detection",
        "confidence_threshold": 0.85,
        "series": "Stranger Things",
        "season": 4,
        "episode": 1,
        "duration_seconds": 4620
    }'
);

INSERT INTO media_documents (id, external_id, schema_id, document_type, title, metadata) VALUES
(
    '22222222-2222-2222-2222-222222222222',
    'NMDB-ST-S4E1-002',
    'b2c3d4e5-f6a7-8901-bcde-f12345678901',
    'subtitle_ttml',
    'Stranger Things S4E1 - English Subtitles',
    '{
        "language": "en-US",
        "format": "TTML",
        "series": "Stranger Things",
        "season": 4,
        "episode": 1
    }'
);

INSERT INTO media_documents (id, external_id, schema_id, document_type, title, metadata) VALUES
(
    '33333333-3333-3333-3333-333333333333',
    'NMDB-ST-S4E1-003',
    'c3d4e5f6-a7b8-9012-cdef-123456789012',
    'vmaf_quality',
    'Stranger Things S4E1 - VMAF Quality Analysis',
    '{
        "model_version": "vmaf_v0.6.1",
        "reference_file": "st_s4e1_master.mxf",
        "series": "Stranger Things",
        "season": 4,
        "episode": 1
    }'
);

INSERT INTO media_documents (id, external_id, schema_id, document_type, title, metadata) VALUES
(
    '44444444-4444-4444-4444-444444444444',
    'NMDB-ST-S4E1-004',
    'd4e5f6a7-b8c9-0123-defa-234567890123',
    'audio_analysis',
    'Stranger Things S4E1 - Audio Loudness Analysis',
    '{
        "channels": 2,
        "sample_rate": 48000,
        "codec": "PCM",
        "series": "Stranger Things",
        "season": 4,
        "episode": 1
    }'
);

-- Insert Tracks
-- Video track for face detection
INSERT INTO tracks (id, document_id, track_id, track_type, metadata) VALUES
(
    'aaaa1111-1111-1111-1111-111111111111',
    '11111111-1111-1111-1111-111111111111',
    '0',
    'video',
    '{"codec": "H.265", "resolution": "3840x2160", "frame_rate": "23.976"}'
);

-- Text track for subtitles
INSERT INTO tracks (id, document_id, track_id, track_type, metadata) VALUES
(
    'bbbb2222-2222-2222-2222-222222222222',
    '22222222-2222-2222-2222-222222222222',
    '0',
    'text',
    '{"format": "TTML", "language": "en-US"}'
);

-- Video track for VMAF
INSERT INTO tracks (id, document_id, track_id, track_type, metadata) VALUES
(
    'cccc3333-3333-3333-3333-333333333333',
    '33333333-3333-3333-3333-333333333333',
    '0',
    'video',
    '{"analysis_type": "quality_metric"}'
);

-- Audio track for loudness (stereo with 2 components)
INSERT INTO tracks (id, document_id, track_id, track_type, metadata) VALUES
(
    'dddd4444-4444-4444-4444-444444444444',
    '44444444-4444-4444-4444-444444444444',
    '0',
    'audio',
    '{"type": "stereo audio", "channels": 2}'
);

-- Insert Components
-- Video component for face detection (HD resolution, 23.976 fps)
INSERT INTO components (id, track_id, component_id, event_rate_numerator, event_rate_denominator, x_size, y_size, metadata) VALUES
(
    'eeee1111-1111-1111-1111-111111111111',
    'aaaa1111-1111-1111-1111-111111111111',
    '0',
    24000, 1001, 1920, 1080,
    '{"analysis": "face_detection", "model": "mtcnn_v2"}'
);

-- Text component for subtitles
INSERT INTO components (id, track_id, component_id, event_rate_numerator, event_rate_denominator, x_size, y_size, metadata) VALUES
(
    'ffff2222-2222-2222-2222-222222222222',
    'bbbb2222-2222-2222-2222-222222222222',
    '0',
    1, 1, NULL, NULL,
    '{"encoding": "UTF-8"}'
);

-- Video component for VMAF scores
INSERT INTO components (id, track_id, component_id, event_rate_numerator, event_rate_denominator, x_size, y_size, metadata) VALUES
(
    'aaaa3333-3333-3333-3333-333333333333',
    'cccc3333-3333-3333-3333-333333333333',
    '0',
    24000, 1001, 3840, 2160,
    '{"metric": "VMAF"}'
);

-- Audio components (Left and Right channels)
INSERT INTO components (id, track_id, component_id, event_rate_numerator, event_rate_denominator, x_size, y_size, metadata) VALUES
(
    'bbbb4444-4444-4444-4444-444444444444',
    'dddd4444-4444-4444-4444-444444444444',
    '0',
    48000, 1, NULL, NULL,
    '{"channel": "left"}'
),
(
    'cccc4444-4444-4444-4444-444444444444',
    'dddd4444-4444-4444-4444-444444444444',
    '1',
    48000, 1, NULL, NULL,
    '{"channel": "right"}'
);

-- ============================================================
-- EXAMPLE QUERIES
-- ============================================================

-- Query 1: Get all media documents for a specific series
-- SELECT * FROM media_documents 
-- WHERE metadata->>'series' = 'Stranger Things';

-- Query 2: Get document structure with tracks and components
-- SELECT 
--     md.title,
--     md.document_type,
--     t.track_type,
--     c.component_id,
--     c.x_size,
--     c.y_size,
--     c.event_rate_numerator,
--     c.event_rate_denominator
-- FROM media_documents md
-- JOIN tracks t ON t.document_id = md.id
-- JOIN components c ON c.track_id = t.id
-- WHERE md.external_id = 'NMDB-ST-S4E1-001';

-- Query 3: Find all video tracks across documents
-- SELECT 
--     md.title,
--     t.track_id,
--     t.metadata
-- FROM tracks t
-- JOIN media_documents md ON md.id = t.document_id
-- WHERE t.track_type = 'video';

-- Query 4: Get components with specific frame rate (23.976 fps = 24000/1001)
-- SELECT 
--     md.title,
--     c.component_id,
--     c.event_rate_numerator::float / c.event_rate_denominator as frame_rate
-- FROM components c
-- JOIN tracks t ON t.id = c.track_id
-- JOIN media_documents md ON md.id = t.document_id
-- WHERE c.event_rate_numerator = 24000 AND c.event_rate_denominator = 1001;

-- Query 5: Find stereo audio documents with both channels
-- SELECT 
--     md.title,
--     COUNT(c.id) as channel_count,
--     array_agg(c.metadata->>'channel') as channels
-- FROM media_documents md
-- JOIN tracks t ON t.document_id = md.id
-- JOIN components c ON c.track_id = t.id
-- WHERE t.track_type = 'audio'
-- GROUP BY md.id, md.title
-- HAVING COUNT(c.id) = 2;

-- Query 6: Get schema information for a document type
-- SELECT 
--     ds.schema_name,
--     ds.version,
--     ds.json_schema,
--     COUNT(md.id) as document_count
-- FROM document_schemas ds
-- LEFT JOIN media_documents md ON md.schema_id = ds.id
-- GROUP BY ds.id, ds.schema_name, ds.version, ds.json_schema;
