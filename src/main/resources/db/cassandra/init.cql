-- ============================================================
-- Netflix Media Document - Cassandra Schema
-- Based on Netflix MediaDatabase Media Timeline Data Model
-- ============================================================
-- Cassandra is used for:
--   - High-throughput event storage (time-series data)
--   - Efficient temporal range queries
--   - Partitioning by document/component for fast lookups
-- ============================================================

-- Create keyspace for media timeline data
CREATE KEYSPACE IF NOT EXISTS media_timeline 
WITH replication = {
    'class': 'SimpleStrategy', 
    'replication_factor': 1
};

USE media_timeline;

-- ============================================================
-- TABLE DEFINITIONS
-- ============================================================

-- Events by Document and Component
-- Partition: (document_id, component_id) - all events for a component together
-- Clustering: start_time ASC - enables efficient time-range scans
CREATE TABLE IF NOT EXISTS events_by_document (
    document_id UUID,
    component_id TEXT,
    event_id UUID,
    start_time BIGINT,        -- Frame number or time unit
    end_time BIGINT,          -- Frame number or time unit
    event_type TEXT,          -- face_detection, subtitle, vmaf, loudness, etc.
    metadata MAP<TEXT, TEXT>, -- Flexible key-value metadata
    regions LIST<FROZEN<MAP<TEXT, INT>>>, -- List of spatial regions [{xmin, xmax, ymin, ymax}]
    created_at TIMESTAMP,
    PRIMARY KEY ((document_id, component_id), start_time, event_id)
) WITH CLUSTERING ORDER BY (start_time ASC, event_id ASC);

-- Events by Time Range (for cross-document temporal queries)
-- Partition: (event_type, time_bucket) - bucket by hour/day for scalability
-- Enables queries like "find all face detections in the last hour"
CREATE TABLE IF NOT EXISTS events_by_time_range (
    event_type TEXT,
    time_bucket TEXT,         -- e.g., '2024-01-15-14' (YYYY-MM-DD-HH)
    document_id UUID,
    component_id TEXT,
    event_id UUID,
    start_time BIGINT,
    end_time BIGINT,
    metadata MAP<TEXT, TEXT>,
    created_at TIMESTAMP,
    PRIMARY KEY ((event_type, time_bucket), created_at, event_id)
) WITH CLUSTERING ORDER BY (created_at DESC, event_id ASC);

-- Events with spatial regions (for region-based queries)
-- Partition: (document_id) - all spatial events for a document
-- Secondary index on region bounds for filtering
CREATE TABLE IF NOT EXISTS events_with_regions (
    document_id UUID,
    event_id UUID,
    component_id TEXT,
    start_time BIGINT,
    end_time BIGINT,
    region_xmin INT,
    region_xmax INT,
    region_ymin INT,
    region_ymax INT,
    metadata MAP<TEXT, TEXT>,
    PRIMARY KEY (document_id, event_id)
);

-- Create secondary indexes for common query patterns
CREATE INDEX IF NOT EXISTS idx_events_type ON events_by_document (event_type);

-- ============================================================
-- SEED DATA: Stranger Things S4E1
-- ============================================================

-- Face Detection Events (Document 1, Component 0)
-- Outdoor shot: frames 0-71 (3 seconds at 23.976 fps)
INSERT INTO events_by_document (document_id, component_id, event_id, start_time, end_time, event_type, metadata, regions, created_at)
VALUES (
    11111111-1111-1111-1111-111111111111,
    '0',
    11111111-aaaa-1111-1111-111111111111,
    0, 71,
    'face_detection',
    {'shot_environment': 'outdoors', 'face_count': '1', 'confidence': '0.92', 'character': 'Eleven'},
    [{'xmin': 1152, 'xmax': 1536, 'ymin': 108, 'ymax': 648}],
    '2024-01-15 10:30:00'
);

-- Indoor shot: frames 72-143 (3 seconds)
INSERT INTO events_by_document (document_id, component_id, event_id, start_time, end_time, event_type, metadata, regions, created_at)
VALUES (
    11111111-1111-1111-1111-111111111111,
    '0',
    11111111-aaaa-2222-1111-111111111111,
    72, 143,
    'face_detection',
    {'shot_environment': 'indoors', 'face_count': '2', 'confidence': '0.88', 'character': 'Mike,Dustin'},
    [{'xmin': 576, 'xmax': 960, 'ymin': 108, 'ymax': 648}, {'xmin': 1200, 'xmax': 1584, 'ymin': 120, 'ymax': 660}],
    '2024-01-15 10:30:01'
);

-- Another outdoor shot with face
INSERT INTO events_by_document (document_id, component_id, event_id, start_time, end_time, event_type, metadata, regions, created_at)
VALUES (
    11111111-1111-1111-1111-111111111111,
    '0',
    11111111-aaaa-3333-1111-111111111111,
    144, 287,
    'face_detection',
    {'shot_environment': 'outdoors', 'face_count': '1', 'confidence': '0.95', 'character': 'Hopper'},
    [{'xmin': 800, 'xmax': 1120, 'ymin': 200, 'ymax': 700}],
    '2024-01-15 10:30:02'
);

-- Subtitle Events (Document 2, Component 0)
INSERT INTO events_by_document (document_id, component_id, event_id, start_time, end_time, event_type, metadata, regions, created_at)
VALUES (
    22222222-2222-2222-2222-222222222222,
    '0',
    22222222-bbbb-1111-2222-222222222222,
    0, 72,
    'subtitle',
    {'text': 'Previously on Stranger Things...', 'style': 'italic', 'position': 'bottom-center'},
    [],
    '2024-01-15 10:30:00'
);

INSERT INTO events_by_document (document_id, component_id, event_id, start_time, end_time, event_type, metadata, regions, created_at)
VALUES (
    22222222-2222-2222-2222-222222222222,
    '0',
    22222222-bbbb-2222-2222-222222222222,
    96, 168,
    'subtitle',
    {'text': 'Hawkins, Indiana - 1986', 'style': 'normal', 'position': 'bottom-center'},
    [],
    '2024-01-15 10:30:01'
);

INSERT INTO events_by_document (document_id, component_id, event_id, start_time, end_time, event_type, metadata, regions, created_at)
VALUES (
    22222222-2222-2222-2222-222222222222,
    '0',
    22222222-bbbb-3333-2222-222222222222,
    192, 264,
    'subtitle',
    {'text': 'Something is coming...', 'style': 'normal', 'position': 'bottom-center'},
    [],
    '2024-01-15 10:30:02'
);

INSERT INTO events_by_document (document_id, component_id, event_id, start_time, end_time, event_type, metadata, regions, created_at)
VALUES (
    22222222-2222-2222-2222-222222222222,
    '0',
    22222222-bbbb-4444-2222-222222222222,
    288, 360,
    'subtitle',
    {'text': 'I can feel it.', 'style': 'normal', 'position': 'bottom-center'},
    [],
    '2024-01-15 10:30:03'
);

-- VMAF Quality Score Events (Document 3, Component 0)
-- Frame-by-frame quality scores
INSERT INTO events_by_document (document_id, component_id, event_id, start_time, end_time, event_type, metadata, regions, created_at)
VALUES (
    33333333-3333-3333-3333-333333333333,
    '0',
    33333333-cccc-1111-3333-333333333333,
    0, 23,
    'vmaf_score',
    {'vmaf': '94.5', 'motion': '12.3', 'adm': '0.97', 'vif': '0.89'},
    [],
    '2024-01-15 10:30:00'
);

INSERT INTO events_by_document (document_id, component_id, event_id, start_time, end_time, event_type, metadata, regions, created_at)
VALUES (
    33333333-3333-3333-3333-333333333333,
    '0',
    33333333-cccc-2222-3333-333333333333,
    24, 47,
    'vmaf_score',
    {'vmaf': '92.1', 'motion': '45.6', 'adm': '0.95', 'vif': '0.87'},
    [],
    '2024-01-15 10:30:01'
);

INSERT INTO events_by_document (document_id, component_id, event_id, start_time, end_time, event_type, metadata, regions, created_at)
VALUES (
    33333333-3333-3333-3333-333333333333,
    '0',
    33333333-cccc-3333-3333-333333333333,
    48, 71,
    'vmaf_score',
    {'vmaf': '96.8', 'motion': '8.2', 'adm': '0.98', 'vif': '0.92'},
    [],
    '2024-01-15 10:30:02'
);

INSERT INTO events_by_document (document_id, component_id, event_id, start_time, end_time, event_type, metadata, regions, created_at)
VALUES (
    33333333-3333-3333-3333-333333333333,
    '0',
    33333333-cccc-4444-3333-333333333333,
    72, 95,
    'vmaf_score',
    {'vmaf': '88.3', 'motion': '78.9', 'adm': '0.92', 'vif': '0.84'},
    [],
    '2024-01-15 10:30:03'
);

-- Audio Loudness Events - Left Channel (Document 4, Component 0)
INSERT INTO events_by_document (document_id, component_id, event_id, start_time, end_time, event_type, metadata, regions, created_at)
VALUES (
    44444444-4444-4444-4444-444444444444,
    '0',
    44444444-dddd-1111-4444-444444444444,
    0, 48000,
    'loudness',
    {'channel': 'left', 'lufs': '-23.5', 'peak_db': '-3.2', 'dynamic_range': '12.4'},
    [],
    '2024-01-15 10:30:00'
);

INSERT INTO events_by_document (document_id, component_id, event_id, start_time, end_time, event_type, metadata, regions, created_at)
VALUES (
    44444444-4444-4444-4444-444444444444,
    '0',
    44444444-dddd-2222-4444-444444444444,
    48001, 96000,
    'loudness',
    {'channel': 'left', 'lufs': '-18.2', 'peak_db': '-1.5', 'dynamic_range': '15.8'},
    [],
    '2024-01-15 10:30:01'
);

-- Audio Loudness Events - Right Channel (Document 4, Component 1)
INSERT INTO events_by_document (document_id, component_id, event_id, start_time, end_time, event_type, metadata, regions, created_at)
VALUES (
    44444444-4444-4444-4444-444444444444,
    '1',
    44444444-eeee-1111-4444-444444444444,
    0, 48000,
    'loudness',
    {'channel': 'right', 'lufs': '-23.8', 'peak_db': '-3.5', 'dynamic_range': '12.1'},
    [],
    '2024-01-15 10:30:00'
);

INSERT INTO events_by_document (document_id, component_id, event_id, start_time, end_time, event_type, metadata, regions, created_at)
VALUES (
    44444444-4444-4444-4444-444444444444,
    '1',
    44444444-eeee-2222-4444-444444444444,
    48001, 96000,
    'loudness',
    {'channel': 'right', 'lufs': '-18.5', 'peak_db': '-1.8', 'dynamic_range': '15.5'},
    [],
    '2024-01-15 10:30:01'
);

-- Populate events_by_time_range for cross-document queries
INSERT INTO events_by_time_range (event_type, time_bucket, document_id, component_id, event_id, start_time, end_time, metadata, created_at)
VALUES ('face_detection', '2024-01-15-10', 11111111-1111-1111-1111-111111111111, '0', 11111111-aaaa-1111-1111-111111111111, 0, 71, {'character': 'Eleven'}, '2024-01-15 10:30:00');

INSERT INTO events_by_time_range (event_type, time_bucket, document_id, component_id, event_id, start_time, end_time, metadata, created_at)
VALUES ('face_detection', '2024-01-15-10', 11111111-1111-1111-1111-111111111111, '0', 11111111-aaaa-2222-1111-111111111111, 72, 143, {'character': 'Mike,Dustin'}, '2024-01-15 10:30:01');

INSERT INTO events_by_time_range (event_type, time_bucket, document_id, component_id, event_id, start_time, end_time, metadata, created_at)
VALUES ('subtitle', '2024-01-15-10', 22222222-2222-2222-2222-222222222222, '0', 22222222-bbbb-1111-2222-222222222222, 0, 72, {'text': 'Previously on Stranger Things...'}, '2024-01-15 10:30:00');

-- Populate events_with_regions for spatial queries
INSERT INTO events_with_regions (document_id, event_id, component_id, start_time, end_time, region_xmin, region_xmax, region_ymin, region_ymax, metadata)
VALUES (11111111-1111-1111-1111-111111111111, 11111111-aaaa-1111-1111-111111111111, '0', 0, 71, 1152, 1536, 108, 648, {'character': 'Eleven'});

INSERT INTO events_with_regions (document_id, event_id, component_id, start_time, end_time, region_xmin, region_xmax, region_ymin, region_ymax, metadata)
VALUES (11111111-1111-1111-1111-111111111111, 11111111-aaaa-2222-1111-111111111111, '0', 72, 143, 576, 960, 108, 648, {'character': 'Mike'});

INSERT INTO events_with_regions (document_id, event_id, component_id, start_time, end_time, region_xmin, region_xmax, region_ymin, region_ymax, metadata)
VALUES (11111111-1111-1111-1111-111111111111, 11111111-aaaa-3333-1111-111111111111, '0', 144, 287, 800, 1120, 200, 700, {'character': 'Hopper'});

-- ============================================================
-- EXAMPLE QUERIES (run in cqlsh)
-- ============================================================

-- Query 1: Get all events for a specific document and component within a time range
-- SELECT * FROM events_by_document 
-- WHERE document_id = 11111111-1111-1111-1111-111111111111 
--   AND component_id = '0'
--   AND start_time >= 0 AND start_time <= 100;

-- Query 2: Get all face detection events in a specific time bucket
-- SELECT * FROM events_by_time_range 
-- WHERE event_type = 'face_detection' 
--   AND time_bucket = '2024-01-15-10';

-- Query 3: Get events with regions in a specific screen area (e.g., right half of screen)
-- SELECT * FROM events_with_regions 
-- WHERE document_id = 11111111-1111-1111-1111-111111111111
--   AND region_xmin >= 960 ALLOW FILTERING;

-- Query 4: Get all subtitle events for a document
-- SELECT * FROM events_by_document 
-- WHERE document_id = 22222222-2222-2222-2222-222222222222
--   AND component_id = '0';

-- Query 5: Get VMAF scores below threshold (quality issues)
-- SELECT * FROM events_by_document 
-- WHERE document_id = 33333333-3333-3333-3333-333333333333
--   AND component_id = '0'
--   AND event_type = 'vmaf_score' ALLOW FILTERING;

-- Query 6: Compare left and right audio channels
-- SELECT * FROM events_by_document 
-- WHERE document_id = 44444444-4444-4444-4444-444444444444
--   AND component_id IN ('0', '1');
