package edu.sustech.cs307;

import edu.sustech.cs307.exception.DBException;
import edu.sustech.cs307.logicalOperator.LogicalOperator;
import edu.sustech.cs307.meta.MetaManager;
import edu.sustech.cs307.optimizer.LogicalPlanner;
import edu.sustech.cs307.optimizer.PhysicalPlanner;
import edu.sustech.cs307.physicalOperator.PhysicalOperator;
import edu.sustech.cs307.storage.BufferPool;
import edu.sustech.cs307.storage.DiskManager;
import edu.sustech.cs307.storage.replacer.ClockReplacer;
import edu.sustech.cs307.storage.replacer.PageReplacer;
import edu.sustech.cs307.system.DBManager;
import edu.sustech.cs307.system.RecordManager;
import edu.sustech.cs307.system.TransactionManager;
import edu.sustech.cs307.tuple.Tuple;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.IntFunction;

public class TransactionTestRunner {

    private static Path tempDir;

    public static void main(String[] args) throws Exception {
        tempDir = Files.createTempDirectory("cs307_txn_");
        System.out.println("Test directory: " + tempDir);
        int passed = 0;
        int failed = 0;

        String[] tests = {
            "testRollbackToSavepointThenCommit",
            "testRollbackRestoresStateBeforeBegin",
            "testReleaseSavepointRemovesRollbackTarget",
            "testSavepointRequiresTransaction",
            "testBeginInsideTransactionShouldFail",
            "testRollbackToSavepointRequiresTransaction",
            "testReleaseSavepointRequiresTransaction",
            "testRollbackToSavepointKeepsTargetActive",
            "testDuplicateSavepointNamesFollowStackSemantics",
            "testCommitAndRollbackOutsideTransactionAreNoOps",
        };

        for (String test : tests) {
            try {
                if (runTest(test)) passed++;
                else failed++;
            } catch (AssertionError e) {
                System.err.println("  FAILED: " + e.getMessage());
                failed++;
            } catch (Exception e) {
                System.err.println("  ERROR: " + e.getMessage());
                e.printStackTrace();
                failed++;
            }
        }

        System.out.println("\n=== Transaction Results: " + passed + " passed, " + failed + " failed ===");
        if (failed > 0) {
            System.exit(1);
        }
    }

    static boolean runTest(String name) throws Exception {
        System.out.println("\n--- " + name + " ---");
        switch (name) {
            case "testRollbackToSavepointThenCommit":
                return testRollbackToSavepointThenCommit();
            case "testRollbackRestoresStateBeforeBegin":
                return testRollbackRestoresStateBeforeBegin();
            case "testReleaseSavepointRemovesRollbackTarget":
                return testReleaseSavepointRemovesRollbackTarget();
            case "testSavepointRequiresTransaction":
                return testSavepointRequiresTransaction();
            case "testBeginInsideTransactionShouldFail":
                return testBeginInsideTransactionShouldFail();
            case "testRollbackToSavepointRequiresTransaction":
                return testRollbackToSavepointRequiresTransaction();
            case "testReleaseSavepointRequiresTransaction":
                return testReleaseSavepointRequiresTransaction();
            case "testRollbackToSavepointKeepsTargetActive":
                return testRollbackToSavepointKeepsTargetActive();
            case "testDuplicateSavepointNamesFollowStackSemantics":
                return testDuplicateSavepointNamesFollowStackSemantics();
            case "testCommitAndRollbackOutsideTransactionAreNoOps":
                return testCommitAndRollbackOutsideTransactionAreNoOps();
            default:
                return false;
        }
    }

    static boolean testRollbackToSavepointThenCommit() throws Exception {
        DBManager db = buildDbManager();
        execute(db, "CREATE TABLE users (id int)");
        execute(db, "BEGIN");
        execute(db, "INSERT INTO users (id) VALUES (1)");
        execute(db, "SAVEPOINT alice_added");
        execute(db, "INSERT INTO users (id) VALUES (2)");
        execute(db, "SAVEPOINT bob_added");
        execute(db, "INSERT INTO users (id) VALUES (3)");
        execute(db, "ROLLBACK TO SAVEPOINT bob_added");
        execute(db, "COMMIT");

        List<Long> ids = selectIds(db, "SELECT * FROM users");
        System.out.println("  ids=" + ids);
        assert ids.size() == 2 : "Expected 2 ids after rollback, got " + ids.size() + ": " + ids;
        assert ids.contains(1L) && ids.contains(2L) && !ids.contains(3L) :
                "Expected [1, 2] (without 3), got " + ids;
        System.out.println("  PASSED");
        return true;
    }

    static boolean testRollbackRestoresStateBeforeBegin() throws Exception {
        DBManager db = buildDbManager();
        execute(db, "CREATE TABLE users (id int)");
        execute(db, "BEGIN");
        execute(db, "INSERT INTO users (id) VALUES (10)");
        execute(db, "INSERT INTO users (id) VALUES (20)");
        execute(db, "ROLLBACK");

        List<Long> ids = selectIds(db, "SELECT * FROM users");
        System.out.println("  ids=" + ids);
        assert ids.isEmpty() : "Expected empty after ROLLBACK, got " + ids;
        System.out.println("  PASSED");
        return true;
    }

    static boolean testReleaseSavepointRemovesRollbackTarget() throws Exception {
        DBManager db = buildDbManager();
        execute(db, "CREATE TABLE users (id int)");
        execute(db, "BEGIN");
        execute(db, "INSERT INTO users (id) VALUES (1)");
        execute(db, "SAVEPOINT keep_rows");
        execute(db, "INSERT INTO users (id) VALUES (2)");
        execute(db, "RELEASE SAVEPOINT keep_rows");

        boolean gotError = false;
        try {
            execute(db, "ROLLBACK TO SAVEPOINT keep_rows");
        } catch (DBException e) {
            if (e.getMessage().contains("SAVEPOINT_DOES_NOT_EXIST")) {
                gotError = true;
                System.out.println("  Correctly got SAVEPOINT_DOES_NOT_EXIST");
            }
        }
        assert gotError : "Should throw SAVEPOINT_DOES_NOT_EXIST";

        execute(db, "COMMIT");
        List<Long> ids = selectIds(db, "SELECT * FROM users");
        System.out.println("  ids=" + ids);
        assert ids.size() == 2 : "Expected [1, 2], got " + ids;
        assert ids.contains(1L) && ids.contains(2L) : "Expected [1, 2], got " + ids;
        System.out.println("  PASSED");
        return true;
    }

    static boolean testSavepointRequiresTransaction() throws Exception {
        DBManager db = buildDbManager();
        execute(db, "CREATE TABLE users (id int)");

        boolean gotError = false;
        try {
            execute(db, "SAVEPOINT outside_tx");
        } catch (DBException e) {
            if (e.getMessage().contains("TRANSACTION_REQUIRED")) {
                gotError = true;
            }
        }
        assert gotError : "Should throw TRANSACTION_REQUIRED";
        System.out.println("  PASSED");
        return true;
    }

    static boolean testBeginInsideTransactionShouldFail() throws Exception {
        DBManager db = buildDbManager();
        execute(db, "BEGIN");

        boolean gotError = false;
        try {
            execute(db, "BEGIN");
        } catch (DBException e) {
            if (e.getMessage().contains("TRANSACTION_ALREADY_ACTIVE")) {
                gotError = true;
            }
        }
        assert gotError : "Should throw TRANSACTION_ALREADY_ACTIVE";
        System.out.println("  PASSED");
        return true;
    }

    static boolean testRollbackToSavepointRequiresTransaction() throws Exception {
        DBManager db = buildDbManager();
        execute(db, "CREATE TABLE users (id int)");

        boolean gotError = false;
        try {
            execute(db, "ROLLBACK TO SAVEPOINT no_tx");
        } catch (DBException e) {
            if (e.getMessage().contains("TRANSACTION_REQUIRED")) {
                gotError = true;
            }
        }
        assert gotError : "Should throw TRANSACTION_REQUIRED";
        System.out.println("  PASSED");
        return true;
    }

    static boolean testReleaseSavepointRequiresTransaction() throws Exception {
        DBManager db = buildDbManager();
        execute(db, "CREATE TABLE users (id int)");

        boolean gotError = false;
        try {
            execute(db, "RELEASE SAVEPOINT no_tx");
        } catch (DBException e) {
            if (e.getMessage().contains("TRANSACTION_REQUIRED")) {
                gotError = true;
            }
        }
        assert gotError : "Should throw TRANSACTION_REQUIRED";
        System.out.println("  PASSED");
        return true;
    }

    static boolean testRollbackToSavepointKeepsTargetActive() throws Exception {
        DBManager db = buildDbManager();
        execute(db, "CREATE TABLE users (id int)");
        execute(db, "BEGIN");
        execute(db, "INSERT INTO users (id) VALUES (1)");
        execute(db, "SAVEPOINT keep_point");
        execute(db, "INSERT INTO users (id) VALUES (2)");
        execute(db, "ROLLBACK TO SAVEPOINT keep_point");
        execute(db, "INSERT INTO users (id) VALUES (3)");
        execute(db, "ROLLBACK TO SAVEPOINT keep_point");
        execute(db, "INSERT INTO users (id) VALUES (4)");
        execute(db, "COMMIT");

        List<Long> ids = selectIds(db, "SELECT * FROM users");
        System.out.println("  ids=" + ids);
        assert ids.size() == 2 : "Expected [1, 4], got " + ids;
        assert ids.contains(1L) && ids.contains(4L) : "Expected [1, 4], got " + ids;
        System.out.println("  PASSED");
        return true;
    }

    static boolean testDuplicateSavepointNamesFollowStackSemantics() throws Exception {
        DBManager db = buildDbManager();
        execute(db, "CREATE TABLE users (id int)");
        execute(db, "BEGIN");
        execute(db, "INSERT INTO users (id) VALUES (1)");
        execute(db, "SAVEPOINT same_name");
        execute(db, "INSERT INTO users (id) VALUES (2)");
        execute(db, "SAVEPOINT same_name");
        execute(db, "INSERT INTO users (id) VALUES (3)");

        execute(db, "ROLLBACK TO SAVEPOINT same_name");
        List<Long> ids = selectIds(db, "SELECT * FROM users");
        System.out.println("  after first rollback: " + ids);
        assert ids.size() == 2 : "Expected [1, 2], got " + ids;
        assert ids.contains(1L) && ids.contains(2L) : "Expected [1, 2], got " + ids;

        execute(db, "RELEASE SAVEPOINT same_name");
        execute(db, "ROLLBACK TO SAVEPOINT same_name");
        execute(db, "COMMIT");

        ids = selectIds(db, "SELECT * FROM users");
        System.out.println("  after release+rollback+commit: " + ids);
        assert ids.size() == 1 : "Expected [1], got " + ids;
        assert ids.contains(1L) : "Expected [1], got " + ids;
        System.out.println("  PASSED");
        return true;
    }

    static boolean testCommitAndRollbackOutsideTransactionAreNoOps() throws Exception {
        DBManager db = buildDbManager();
        execute(db, "CREATE TABLE users (id int)");

        // Should not throw
        try {
            execute(db, "COMMIT");
            execute(db, "ROLLBACK");
        } catch (Exception e) {
            assert false : "COMMIT/ROLLBACK outside transaction should not throw: " + e.getMessage();
        }

        List<Long> ids = selectIds(db, "SELECT * FROM users");
        assert ids.isEmpty() : "Expected empty, got " + ids;
        System.out.println("  PASSED");
        return true;
    }

    // === Helpers ===
    static DBManager buildDbManager() throws Exception {
        // Create a unique directory per test to avoid cross-test state pollution
        Path testDir = tempDir.resolve("test_" + System.nanoTime());
        Files.createDirectories(testDir);
        HashMap<String, Integer> fileOffsets = new HashMap<>();
        DiskManager diskManager = new DiskManager(testDir.toString(), fileOffsets);
        IntFunction<PageReplacer> replacerFactory = ClockReplacer::new;
        BufferPool bufferPool = new BufferPool(16, diskManager, replacerFactory.apply(16));
        RecordManager recordManager = new RecordManager(diskManager, bufferPool);
        MetaManager metaManager = new MetaManager(testDir.resolve("meta").toString());
        DBManager dbManager = new DBManager(diskManager, bufferPool, recordManager, metaManager, null,
                replacerFactory);
        dbManager.setTransactionManager(new TransactionManager(dbManager));
        return dbManager;
    }

    static void execute(DBManager db, String sql) throws DBException {
        LogicalOperator op = LogicalPlanner.resolveAndPlan(db, sql);
        if (op == null) return;
        PhysicalOperator phys = PhysicalPlanner.generateOperator(db, op);
        phys.Begin();
        while (phys.hasNext()) {
            phys.Next();
            phys.Current();
        }
        phys.Close();
        db.getBufferPool().FlushAllPages("");
    }

    static List<Long> selectIds(DBManager db, String sql) throws DBException {
        LogicalOperator op = LogicalPlanner.resolveAndPlan(db, sql);
        PhysicalOperator phys = PhysicalPlanner.generateOperator(db, op);
        List<Long> ids = new ArrayList<>();
        phys.Begin();
        while (phys.hasNext()) {
            phys.Next();
            Tuple tuple = phys.Current();
            ids.add((Long) tuple.getValues()[0].value);
        }
        phys.Close();
        return ids;
    }
}
