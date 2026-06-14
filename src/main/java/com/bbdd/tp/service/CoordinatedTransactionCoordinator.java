package com.bbdd.tp.service;

import com.bbdd.tp.model.ComponentProvisionRequest;
import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.TransactionOptions;
import com.mongodb.WriteConcern;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;

@Service
public class CoordinatedTransactionCoordinator {

    private final DataSource pgDataSource;
    private final MongoClient mongoClient;

    public CoordinatedTransactionCoordinator(DataSource pgDataSource, MongoClient mongoClient) {
        this.pgDataSource = pgDataSource;
        this.mongoClient = mongoClient;
    }

    public void provisionComponentManual(ComponentProvisionRequest request) throws Exception {
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
                    stmt.setObject(1, request.componentId());
                    stmt.setObject(2, request.trackId());
                    stmt.setInt(3, request.eventRateNumerator());
                    stmt.setInt(4, request.eventRateDenominator());

                    if (request.xSize() != null) stmt.setInt(5, request.xSize());
                    else stmt.setNull(5, java.sql.Types.INTEGER);
                    if (request.ySize() != null) stmt.setInt(6, request.ySize());
                    else stmt.setNull(6, java.sql.Types.INTEGER);

                    stmt.setString(7, "{\"algorithm\": \"" + request.algorithmName() + "\"}");
                    stmt.executeUpdate();
                }

                MongoCollection<Document> bucketCollection = mongoClient
                        .getDatabase("media_db")
                        .getCollection("timeline_buckets");

                Document bucketDoc = new Document()
                        .append("asset_id", request.assetId().toString())
                        .append("track_id", request.trackId().toString())
                        .append("component_id", request.componentId().toString())
                        .append("bucket_id", 1)
                        .append("bucket_start_time", 0.0)
                        .append("bucket_end_time", 60.0)
                        .append("event_count", 0)
                        .append("events", new ArrayList<>());

                bucketCollection.insertOne(mongoSession, bucketDoc);

                mongoSession.commitTransaction();
                pgConn.commit();
                System.out.println("✅ Dual-write manual transaction completed successfully.");

            } catch (Exception ex) {
                System.err.println("❌ Coordinated transaction failed. Triggering rollbacks...");

                try {
                    pgConn.rollback();
                } catch (SQLException pgEx) {
                    System.err.println("FATAL: Failed to rollback Postgres: " + pgEx.getMessage());
                }

                try {
                    if (mongoSession.hasActiveTransaction()) {
                        mongoSession.abortTransaction();
                    }
                } catch (Exception mongoEx) {
                    System.err.println("FATAL: Failed to abort MongoDB transaction: " + mongoEx.getMessage());
                }
                throw ex;
            }
        }
    }
}