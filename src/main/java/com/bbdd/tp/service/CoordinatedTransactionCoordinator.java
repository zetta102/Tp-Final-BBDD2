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
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;

/**
 * Service that provisions components using <em>manual, low-level transaction coordination</em>
 * between PostgreSQL (raw JDBC) and MongoDB (native driver {@link ClientSession}).
 *
 * <p>Unlike the Spring-managed strategies in {@link ComponentProvisioningService}, this class
 * bypasses Spring's transaction abstractions entirely and manages both database transactions
 * by hand, providing explicit control over commit order and rollback behavior.</p>
 *
 * <h3>Transaction lifecycle</h3>
 * <ol>
 *   <li>Opens a JDBC {@link Connection} with auto-commit disabled.</li>
 *   <li>Opens a MongoDB {@link ClientSession} and starts a transaction with
 *       {@link ReadConcern#SNAPSHOT} / {@link WriteConcern#MAJORITY}.</li>
 *   <li>Executes SQL INSERT and MongoDB insertOne within the respective sessions.</li>
 *   <li>On success: commits Mongo first, then PostgreSQL.</li>
 *   <li>On failure: explicitly rolls back both (JDBC {@code rollback()} and
 *       Mongo {@code abortTransaction()}).</li>
 * </ol>
 */
@Service
public class CoordinatedTransactionCoordinator {

    private final DataSource pgDataSource;
    private final MongoClient mongoClient;
    private final String mongoDatabaseName;

    /**
     * @param pgDataSource       HikariCP-managed PostgreSQL data source
     * @param mongoClient        MongoDB native driver client (requires replica set for transactions)
     * @param mongoDatabaseFactory Spring-managed factory used to resolve the configured database name
     */
    public CoordinatedTransactionCoordinator(DataSource pgDataSource, MongoClient mongoClient,
                                             MongoDatabaseFactory mongoDatabaseFactory) {
        this.pgDataSource = pgDataSource;
        this.mongoClient = mongoClient;
        this.mongoDatabaseName = mongoDatabaseFactory.getMongoDatabase().getName();
    }

    /**
     * Provisions a component by manually coordinating JDBC and MongoDB transactions.
     *
     * <p>Both connections are managed via try-with-resources. On any exception during
     * the write phase, both transactions are explicitly rolled back before the exception
     * is re-thrown.</p>
     *
     * @param request the component provisioning payload
     * @throws Exception if either database write fails (after both are rolled back)
     */
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
                        .getDatabase(mongoDatabaseName)
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