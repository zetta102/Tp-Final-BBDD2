// ============================================================
// Netflix Media Document - MongoDB Initialization
// Based on Netflix MediaDatabase Media Timeline Data Model
// ============================================================
// MongoDB is used for:
//   - Full document storage (complete nested structure)
//   - Flexible schema for domain-specific metadata
//   - Spatial/geospatial queries on regions
//   - Aggregation pipelines for complex analytics
// ============================================================

// Switch to media_db database
db = db.getSiblingDB('media_db');

// ============================================================
// COLLECTION SETUP & INDEXES
// ============================================================

// Drop existing collections for clean initialization
db.media_documents.drop();
db.document_schemas.drop();

// Create indexes for media_documents collection
db.media_documents.createIndex({ "externalId": 1 }, { unique: true });
db.media_documents.createIndex({ "documentType": 1 });
db.media_documents.createIndex({ "metadata.series": 1 });
db.media_documents.createIndex({ "metadata.algorithm": 1 });
db.media_documents.createIndex({ "createdAt": -1 });

// Temporal indexes for event queries
db.media_documents.createIndex({ "tracks.components.events.startTime": 1 });
db.media_documents.createIndex({ "tracks.components.events.endTime": 1 });

// Compound index for time-range queries
db.media_documents.createIndex({ 
    "documentType": 1, 
    "tracks.components.events.startTime": 1, 
    "tracks.components.events.endTime": 1 
});

// 2d index for spatial region queries (bounding box queries)
db.media_documents.createIndex({ 
    "tracks.components.events.regions": "2d" 
}, { 
    min: 0, 
    max: 4096,
    sparse: true 
});

// Create indexes for document_schemas collection
db.document_schemas.createIndex({ "schemaName": 1 }, { unique: true });
db.document_schemas.createIndex({ "version": 1 });

print("Indexes created successfully");

// ============================================================
// SEED DATA: Document Schemas
// ============================================================

db.document_schemas.insertMany([
    {
        _id: UUID("a1b2c3d4-e5f6-7890-abcd-ef1234567890"),
        schemaName: "video_face_detection",
        version: "1.0.0",
        jsonSchema: {
            "$schema": "http://json-schema.org/draft-07/schema#",
            type: "object",
            properties: {
                algorithm: { type: "string" },
                confidence_threshold: { type: "number", minimum: 0, maximum: 1 }
            },
            required: ["algorithm"]
        },
        description: "Schema for face detection analysis results",
        createdAt: new Date("2024-01-01T00:00:00Z")
    },
    {
        _id: UUID("b2c3d4e5-f6a7-8901-bcde-f12345678901"),
        schemaName: "subtitle_ttml",
        version: "1.0.0",
        jsonSchema: {
            "$schema": "http://json-schema.org/draft-07/schema#",
            type: "object",
            properties: {
                language: { type: "string" },
                format: { type: "string", enum: ["TTML", "WebVTT", "SRT"] }
            },
            required: ["language"]
        },
        description: "Schema for subtitle/timed text documents",
        createdAt: new Date("2024-01-01T00:00:00Z")
    },
    {
        _id: UUID("c3d4e5f6-a7b8-9012-cdef-123456789012"),
        schemaName: "vmaf_quality",
        version: "1.0.0",
        jsonSchema: {
            "$schema": "http://json-schema.org/draft-07/schema#",
            type: "object",
            properties: {
                model_version: { type: "string" },
                reference_file: { type: "string" }
            },
            required: ["model_version"]
        },
        description: "Schema for VMAF video quality scores",
        createdAt: new Date("2024-01-01T00:00:00Z")
    },
    {
        _id: UUID("d4e5f6a7-b8c9-0123-defa-234567890123"),
        schemaName: "audio_analysis",
        version: "1.0.0",
        jsonSchema: {
            "$schema": "http://json-schema.org/draft-07/schema#",
            type: "object",
            properties: {
                channels: { type: "integer" },
                sample_rate: { type: "integer" }
            },
            required: ["channels"]
        },
        description: "Schema for audio analysis including loudness",
        createdAt: new Date("2024-01-01T00:00:00Z")
    }
]);

print("Document schemas inserted");

// ============================================================
// SEED DATA: Complete Media Documents (Netflix Media Document format)
// ============================================================

// Document 1: Face Detection Analysis (matches article Figure 3 example)
db.media_documents.insertOne({
    _id: UUID("11111111-1111-1111-1111-111111111111"),
    externalId: "NMDB-ST-S4E1-001",
    schemaId: UUID("a1b2c3d4-e5f6-7890-abcd-ef1234567890"),
    documentType: "video_face_detection",
    title: "Stranger Things S4E1 - Face Detection Analysis",
    metadata: {
        algorithm: "video_face_detection",
        confidence_threshold: 0.85,
        series: "Stranger Things",
        season: 4,
        episode: 1,
        duration_seconds: 4620
    },
    tracks: [
        {
            id: "0",
            metadata: { type: "video", codec: "H.265", resolution: "3840x2160" },
            components: [
                {
                    id: "0",
                    eventRateNumerator: 24000,
                    eventRateDenominator: 1001,
                    xSize: 1920,
                    ySize: 1080,
                    metadata: { analysis: "face_detection", model: "mtcnn_v2" },
                    events: [
                        {
                            startTime: 0,
                            endTime: 71,
                            metadata: {
                                shotEnvironment: "outdoors",
                                faceCount: 1,
                                confidence: 0.92,
                                character: "Eleven"
                            },
                            regions: [
                                { xmin: 1152, xmax: 1536, ymin: 108, ymax: 648 }
                            ]
                        },
                        {
                            startTime: 72,
                            endTime: 143,
                            metadata: {
                                shotEnvironment: "indoors",
                                faceCount: 2,
                                confidence: 0.88,
                                characters: ["Mike", "Dustin"]
                            },
                            regions: [
                                { xmin: 576, xmax: 960, ymin: 108, ymax: 648 },
                                { xmin: 1200, xmax: 1584, ymin: 120, ymax: 660 }
                            ]
                        },
                        {
                            startTime: 144,
                            endTime: 287,
                            metadata: {
                                shotEnvironment: "outdoors",
                                faceCount: 1,
                                confidence: 0.95,
                                character: "Hopper"
                            },
                            regions: [
                                { xmin: 800, xmax: 1120, ymin: 200, ymax: 700 }
                            ]
                        },
                        {
                            startTime: 288,
                            endTime: 431,
                            metadata: {
                                shotEnvironment: "indoors",
                                faceCount: 3,
                                confidence: 0.91,
                                characters: ["Joyce", "Murray", "Hopper"]
                            },
                            regions: [
                                { xmin: 200, xmax: 500, ymin: 150, ymax: 600 },
                                { xmin: 700, xmax: 1000, ymin: 140, ymax: 590 },
                                { xmin: 1200, xmax: 1500, ymin: 160, ymax: 610 }
                            ]
                        }
                    ]
                }
            ]
        }
    ],
    createdAt: new Date("2024-01-15T10:30:00Z"),
    updatedAt: new Date("2024-01-15T10:30:00Z")
});

// Document 2: Subtitle Document (matches article Figure 2 example)
db.media_documents.insertOne({
    _id: UUID("22222222-2222-2222-2222-222222222222"),
    externalId: "NMDB-ST-S4E1-002",
    schemaId: UUID("b2c3d4e5-f6a7-8901-bcde-f12345678901"),
    documentType: "subtitle_ttml",
    title: "Stranger Things S4E1 - English Subtitles",
    metadata: {
        language: "en-US",
        format: "TTML",
        series: "Stranger Things",
        season: 4,
        episode: 1
    },
    tracks: [
        {
            id: "0",
            metadata: { type: "text", format: "TTML", language: "en-US" },
            components: [
                {
                    id: "0",
                    eventRateNumerator: 1,
                    eventRateDenominator: 1,
                    metadata: { encoding: "UTF-8" },
                    events: [
                        {
                            startTime: 0,
                            endTime: 3,
                            metadata: {
                                subtitle: "Previously on Stranger Things...",
                                style: "italic",
                                position: "bottom-center"
                            }
                        },
                        {
                            startTime: 4,
                            endTime: 7,
                            metadata: {
                                subtitle: "Hawkins, Indiana - 1986",
                                style: "normal",
                                position: "bottom-center"
                            }
                        },
                        {
                            startTime: 8,
                            endTime: 11,
                            metadata: {
                                subtitle: "Something is coming...",
                                style: "normal",
                                position: "bottom-center"
                            }
                        },
                        {
                            startTime: 12,
                            endTime: 15,
                            metadata: {
                                subtitle: "I can feel it.",
                                style: "normal",
                                position: "bottom-center"
                            }
                        },
                        {
                            startTime: 18,
                            endTime: 22,
                            metadata: {
                                subtitle: "We need to find Eleven.",
                                style: "normal",
                                position: "bottom-center"
                            }
                        },
                        {
                            startTime: 24,
                            endTime: 28,
                            metadata: {
                                subtitle: "She's the only one who can stop this.",
                                style: "normal",
                                position: "bottom-center"
                            }
                        }
                    ]
                }
            ]
        }
    ],
    createdAt: new Date("2024-01-15T10:30:00Z"),
    updatedAt: new Date("2024-01-15T10:30:00Z")
});

// Document 3: VMAF Quality Scores
db.media_documents.insertOne({
    _id: UUID("33333333-3333-3333-3333-333333333333"),
    externalId: "NMDB-ST-S4E1-003",
    schemaId: UUID("c3d4e5f6-a7b8-9012-cdef-123456789012"),
    documentType: "vmaf_quality",
    title: "Stranger Things S4E1 - VMAF Quality Analysis",
    metadata: {
        model_version: "vmaf_v0.6.1",
        reference_file: "st_s4e1_master.mxf",
        series: "Stranger Things",
        season: 4,
        episode: 1,
        average_vmaf: 92.9,
        min_vmaf: 88.3,
        max_vmaf: 96.8
    },
    tracks: [
        {
            id: "0",
            metadata: { type: "video", analysis_type: "quality_metric" },
            components: [
                {
                    id: "0",
                    eventRateNumerator: 24000,
                    eventRateDenominator: 1001,
                    xSize: 3840,
                    ySize: 2160,
                    metadata: { metric: "VMAF" },
                    events: [
                        {
                            startTime: 0,
                            endTime: 23,
                            metadata: { vmaf: 94.5, motion: 12.3, adm: 0.97, vif: 0.89 }
                        },
                        {
                            startTime: 24,
                            endTime: 47,
                            metadata: { vmaf: 92.1, motion: 45.6, adm: 0.95, vif: 0.87 }
                        },
                        {
                            startTime: 48,
                            endTime: 71,
                            metadata: { vmaf: 96.8, motion: 8.2, adm: 0.98, vif: 0.92 }
                        },
                        {
                            startTime: 72,
                            endTime: 95,
                            metadata: { vmaf: 88.3, motion: 78.9, adm: 0.92, vif: 0.84 }
                        },
                        {
                            startTime: 96,
                            endTime: 119,
                            metadata: { vmaf: 91.2, motion: 52.1, adm: 0.94, vif: 0.86 }
                        },
                        {
                            startTime: 120,
                            endTime: 143,
                            metadata: { vmaf: 95.3, motion: 15.7, adm: 0.97, vif: 0.91 }
                        }
                    ]
                }
            ]
        }
    ],
    createdAt: new Date("2024-01-15T10:30:00Z"),
    updatedAt: new Date("2024-01-15T10:30:00Z")
});

// Document 4: Audio Analysis with Stereo Components (matches article Figure 5 example)
db.media_documents.insertOne({
    _id: UUID("44444444-4444-4444-4444-444444444444"),
    externalId: "NMDB-ST-S4E1-004",
    schemaId: UUID("d4e5f6a7-b8c9-0123-defa-234567890123"),
    documentType: "audio_analysis",
    title: "Stranger Things S4E1 - Audio Loudness Analysis",
    metadata: {
        channels: 2,
        sample_rate: 48000,
        codec: "PCM",
        series: "Stranger Things",
        season: 4,
        episode: 1
    },
    tracks: [
        {
            id: "0",
            metadata: { type: "stereo audio", channels: 2 },
            components: [
                {
                    id: "0",
                    eventRateNumerator: 48000,
                    eventRateDenominator: 1,
                    metadata: { channel: "left" },
                    events: [
                        {
                            startTime: 0,
                            endTime: 48000,
                            metadata: { lufs: -23.5, peak_db: -3.2, dynamic_range: 12.4 }
                        },
                        {
                            startTime: 48001,
                            endTime: 96000,
                            metadata: { lufs: -18.2, peak_db: -1.5, dynamic_range: 15.8 }
                        },
                        {
                            startTime: 96001,
                            endTime: 144000,
                            metadata: { lufs: -25.1, peak_db: -5.8, dynamic_range: 10.2 }
                        },
                        {
                            startTime: 144001,
                            endTime: 192000,
                            metadata: { lufs: -14.8, peak_db: -0.5, dynamic_range: 18.5 }
                        }
                    ]
                },
                {
                    id: "1",
                    eventRateNumerator: 48000,
                    eventRateDenominator: 1,
                    metadata: { channel: "right" },
                    events: [
                        {
                            startTime: 0,
                            endTime: 48000,
                            metadata: { lufs: -23.8, peak_db: -3.5, dynamic_range: 12.1 }
                        },
                        {
                            startTime: 48001,
                            endTime: 96000,
                            metadata: { lufs: -18.5, peak_db: -1.8, dynamic_range: 15.5 }
                        },
                        {
                            startTime: 96001,
                            endTime: 144000,
                            metadata: { lufs: -25.4, peak_db: -6.1, dynamic_range: 9.8 }
                        },
                        {
                            startTime: 144001,
                            endTime: 192000,
                            metadata: { lufs: -15.1, peak_db: -0.8, dynamic_range: 18.2 }
                        }
                    ]
                }
            ]
        }
    ],
    createdAt: new Date("2024-01-15T10:30:00Z"),
    updatedAt: new Date("2024-01-15T10:30:00Z")
});

// Document 5: Spanish Subtitles (demonstrates multi-language support)
db.media_documents.insertOne({
    _id: UUID("55555555-5555-5555-5555-555555555555"),
    externalId: "NMDB-ST-S4E1-005",
    schemaId: UUID("b2c3d4e5-f6a7-8901-bcde-f12345678901"),
    documentType: "subtitle_ttml",
    title: "Stranger Things S4E1 - Spanish Subtitles",
    metadata: {
        language: "es-ES",
        format: "TTML",
        series: "Stranger Things",
        season: 4,
        episode: 1
    },
    tracks: [
        {
            id: "0",
            metadata: { type: "text", format: "TTML", language: "es-ES" },
            components: [
                {
                    id: "0",
                    eventRateNumerator: 1,
                    eventRateDenominator: 1,
                    metadata: { encoding: "UTF-8" },
                    events: [
                        {
                            startTime: 0,
                            endTime: 3,
                            metadata: {
                                subtitle: "Anteriormente en Stranger Things...",
                                style: "italic",
                                position: "bottom-center"
                            }
                        },
                        {
                            startTime: 4,
                            endTime: 7,
                            metadata: {
                                subtitle: "Hawkins, Indiana - 1986",
                                style: "normal",
                                position: "bottom-center"
                            }
                        },
                        {
                            startTime: 8,
                            endTime: 11,
                            metadata: {
                                subtitle: "Algo se acerca...",
                                style: "normal",
                                position: "bottom-center"
                            }
                        },
                        {
                            startTime: 12,
                            endTime: 15,
                            metadata: {
                                subtitle: "Puedo sentirlo.",
                                style: "normal",
                                position: "bottom-center"
                            }
                        }
                    ]
                }
            ]
        }
    ],
    createdAt: new Date("2024-01-15T10:35:00Z"),
    updatedAt: new Date("2024-01-15T10:35:00Z")
});

print("Media documents inserted: " + db.media_documents.countDocuments());

// ============================================================
// EXAMPLE QUERIES
// ============================================================

print("\n=== EXAMPLE QUERIES ===\n");

// Query 1: Get all media documents for a specific series
print("Query 1: All documents for Stranger Things series");
printjson(db.media_documents.find(
    { "metadata.series": "Stranger Things" },
    { title: 1, documentType: 1, "metadata.language": 1 }
).toArray());

// Query 2: Temporal query - Get events within a time range
print("\nQuery 2: Face detection events between frames 50-150");
printjson(db.media_documents.aggregate([
    { $match: { documentType: "video_face_detection" } },
    { $unwind: "$tracks" },
    { $unwind: "$tracks.components" },
    { $unwind: "$tracks.components.events" },
    { $match: {
        "tracks.components.events.startTime": { $lte: 150 },
        "tracks.components.events.endTime": { $gte: 50 }
    }},
    { $project: {
        startTime: "$tracks.components.events.startTime",
        endTime: "$tracks.components.events.endTime",
        character: "$tracks.components.events.metadata.character",
        shotEnvironment: "$tracks.components.events.metadata.shotEnvironment"
    }}
]).toArray());

// Query 3: Cross-document subtitle query (all languages)
print("\nQuery 3: Check subtitles across all languages between 0-15 seconds");
printjson(db.media_documents.aggregate([
    { $match: { documentType: "subtitle_ttml" } },
    { $unwind: "$tracks" },
    { $unwind: "$tracks.components" },
    { $unwind: "$tracks.components.events" },
    { $match: {
        "tracks.components.events.startTime": { $lte: 15 },
        "tracks.components.events.endTime": { $gte: 0 }
    }},
    { $project: {
        language: "$metadata.language",
        startTime: "$tracks.components.events.startTime",
        endTime: "$tracks.components.events.endTime",
        subtitle: "$tracks.components.events.metadata.subtitle"
    }},
    { $sort: { language: 1, startTime: 1 } }
]).toArray());

// Query 4: Spatial query - Find faces in right half of screen (x >= 960)
print("\nQuery 4: Face detections in right half of screen (x >= 960)");
printjson(db.media_documents.aggregate([
    { $match: { documentType: "video_face_detection" } },
    { $unwind: "$tracks" },
    { $unwind: "$tracks.components" },
    { $unwind: "$tracks.components.events" },
    { $unwind: "$tracks.components.events.regions" },
    { $match: { "tracks.components.events.regions.xmin": { $gte: 960 } } },
    { $project: {
        startTime: "$tracks.components.events.startTime",
        endTime: "$tracks.components.events.endTime",
        character: "$tracks.components.events.metadata.character",
        region: "$tracks.components.events.regions"
    }}
]).toArray());

// Query 5: VMAF quality issues - find low quality segments (< 90)
print("\nQuery 5: Video segments with VMAF < 90 (quality issues)");
printjson(db.media_documents.aggregate([
    { $match: { documentType: "vmaf_quality" } },
    { $unwind: "$tracks" },
    { $unwind: "$tracks.components" },
    { $unwind: "$tracks.components.events" },
    { $match: { "tracks.components.events.metadata.vmaf": { $lt: 90 } } },
    { $project: {
        title: 1,
        startTime: "$tracks.components.events.startTime",
        endTime: "$tracks.components.events.endTime",
        vmaf: "$tracks.components.events.metadata.vmaf",
        motion: "$tracks.components.events.metadata.motion"
    }}
]).toArray());

// Query 6: Audio analysis - Compare left/right channel loudness
print("\nQuery 6: Stereo channel loudness comparison");
printjson(db.media_documents.aggregate([
    { $match: { documentType: "audio_analysis" } },
    { $unwind: "$tracks" },
    { $unwind: "$tracks.components" },
    { $project: {
        channel: "$tracks.components.metadata.channel",
        avgLufs: { $avg: "$tracks.components.events.metadata.lufs" },
        maxPeak: { $max: "$tracks.components.events.metadata.peak_db" }
    }}
]).toArray());

// Query 7: Spatio-temporal query - Find events in specific region during specific time
print("\nQuery 7: Faces in center of screen (400-1500x) during frames 0-200");
printjson(db.media_documents.aggregate([
    { $match: { documentType: "video_face_detection" } },
    { $unwind: "$tracks" },
    { $unwind: "$tracks.components" },
    { $unwind: "$tracks.components.events" },
    { $match: {
        "tracks.components.events.startTime": { $lte: 200 },
        "tracks.components.events.endTime": { $gte: 0 }
    }},
    { $unwind: "$tracks.components.events.regions" },
    { $match: {
        "tracks.components.events.regions.xmin": { $gte: 400 },
        "tracks.components.events.regions.xmax": { $lte: 1500 }
    }},
    { $project: {
        startTime: "$tracks.components.events.startTime",
        endTime: "$tracks.components.events.endTime",
        character: "$tracks.components.events.metadata.character",
        region: "$tracks.components.events.regions"
    }}
]).toArray());

// Query 8: Document statistics
print("\nQuery 8: Document statistics by type");
printjson(db.media_documents.aggregate([
    { $group: {
        _id: "$documentType",
        count: { $sum: 1 },
        titles: { $push: "$title" }
    }},
    { $sort: { count: -1 } }
]).toArray());

print("\n=== Initialization Complete ===");
print("Total documents: " + db.media_documents.countDocuments());
print("Total schemas: " + db.document_schemas.countDocuments());
