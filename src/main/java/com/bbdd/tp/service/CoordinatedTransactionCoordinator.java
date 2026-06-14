package com.bbdd.tp.service;

import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.TransactionOptions;
import com.mongodb.WriteConcern;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.Document;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.UUID;

public class CoordinatedTransactionCoordinator {

    private final DataSource pgDataSource;
    private final MongoClient mongoClient;

    public CoordinatedTransactionCoordinator(DataSource pgDataSource, MongoClient mongoClient) {
        this.pgDataSource = pgDataSource;
        this.mongoClient = mongoClient;
    }

    public void registerComponentAndInitialBucket(UUID componentId, UUID trackId, UUID assetId) throws Exception {
        try (Connection pgConn = pgDataSource.getConnection();
             ClientSession mongoSession = mongoClient.startSession()) {

            pgConn.setAutoCommit(false);

            TransactionOptions txnOptions = TransactionOptions.builder()
                    .readPreference(ReadPreference.primary())
                    .readConcern(ReadConcern.SNAPSHOT)
                    .writeConcern(WriteConcern.MAJORITY)
                    .build();
            mongoSession.startTransaction(txnOptions);

            try {
                String sqlInsert = """
                            INSERT INTO components (component_id, track_id, event_rate_numerator, event_rate_denominator, x_size, y_size, metadata)
                            VALUES (?,?,?,?,?,?,?::jsonb)
                        """;
                try (PreparedStatement stmt = pgConn.prepareStatement(sqlInsert)) {
                    stmt.setObject(1, componentId);
                    stmt.setObject(2, trackId);
                    stmt.setInt(3, 24000);
                    stmt.setInt(4, 1001);
                    stmt.setInt(5, 1920);
                    stmt.setInt(6, 1080);
                    stmt.setString(7, "{\"algorithm\": \"video_face_detection\"}");
                    stmt.executeUpdate();
                }

                MongoCollection<Document> bucketCollection = mongoClient
                        .getDatabase("media_db")
                        .getCollection("timeline_buckets");

                Document bucketDoc = new Document()
                        .append("asset_id", assetId.toString())
                        .append("track_id", trackId.toString())
                        .append("component_id", componentId.toString())
                        .append("bucket_id", 1)
                        .append("bucket_start_time", 0.0)
                        .append("bucket_end_time", 60.0)
                        .append("event_count", 0)
                        .append("events", new ArrayList<>());

                bucketCollection.insertOne(mongoSession, bucketDoc);

                mongoSession.commitTransaction();

                pgConn.commit();
                System.out.println("✅ Dual-write transaction completed successfully across Postgres and MongoDB.");

            } catch (Exception ex) {
                System.err.println("❌ Coordinated transaction failed. Triggering rollbacks... Error: " + ex.getMessage());

                try {
                    pgConn.rollback();
                    System.out.println("↩️ Postgres Transaction successfully rolled back.");
                } catch (SQLException pgEx) {
                    System.err.println("FATAL: Failed to rollback Postgres: " + pgEx.getMessage());
                }

                try {
                    if (mongoSession.hasActiveTransaction()) {
                        mongoSession.abortTransaction();
                        System.out.println("↩️ MongoDB Transaction successfully aborted.");
                    }
                } catch (Exception mongoEx) {
                    System.err.println("FATAL: Failed to abort MongoDB transaction: " + mongoEx.getMessage());
                }

                throw ex;
            }
        }
    }
}