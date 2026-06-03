package edu.sustech.cs307;

import edu.sustech.cs307.exception.DBException;
import edu.sustech.cs307.index.BPlusTree;
import edu.sustech.cs307.index.BPlusTreeIndex;
import edu.sustech.cs307.index.InMemoryOrderedIndex;
import edu.sustech.cs307.logicalOperator.LogicalOperator;
import edu.sustech.cs307.meta.MetaManager;
import edu.sustech.cs307.meta.TableMeta;
import edu.sustech.cs307.optimizer.LogicalPlanner;
import edu.sustech.cs307.optimizer.PhysicalPlanner;
import edu.sustech.cs307.physicalOperator.PhysicalOperator;
import edu.sustech.cs307.record.RID;
import edu.sustech.cs307.storage.BufferPool;
import edu.sustech.cs307.storage.DiskManager;
import edu.sustech.cs307.storage.replacer.ClockReplacer;
import edu.sustech.cs307.storage.replacer.PageReplacer;
import edu.sustech.cs307.system.DBManager;
import edu.sustech.cs307.system.RecordManager;
import edu.sustech.cs307.system.TransactionManager;
import edu.sustech.cs307.tuple.Tuple;
import edu.sustech.cs307.value.Value;
import edu.sustech.cs307.value.ValueType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.IntFunction;

public class IndexTestRunner {

    private static Path tempDir;

    public static void main(String[] args) throws Exception {
        tempDir = Files.createTempDirectory("cs307_index_");
        System.out.println("Test directory: " + tempDir);
        int passed = 0;
        int failed = 0;

        String[] tests = {
            "testBPlusTreeInsertSearch",
            "testBPlusTreeRangeSearch",
            "testBPlusTreeIterator",
            "testBPlusTreeEmpty",
            "testBPlusTreeLargeInsert",
            "testCreateIndex",
            "testCreateIndexDuplicate",
            "testDropIndex",
            "testIndexScanWithCreateIndex",
            "testInMemoryOrderedIndex",
            "testBPlusTreeIndexPersist",
            "testIndexScanOrder",
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

        System.out.println("\n=== Index Results: " + passed + " passed, " + failed + " failed ===");
        if (failed > 0) {
            System.exit(1);
        }
    }

    static boolean runTest(String name) throws Exception {
        System.out.println("\n--- " + name + " ---");
        switch (name) {
            case "testBPlusTreeInsertSearch": return testBPlusTreeInsertSearch();
            case "testBPlusTreeRangeSearch": return testBPlusTreeRangeSearch();
            case "testBPlusTreeIterator": return testBPlusTreeIterator();
            case "testBPlusTreeEmpty": return testBPlusTreeEmpty();
            case "testBPlusTreeLargeInsert": return testBPlusTreeLargeInsert();
            case "testCreateIndex": return testCreateIndex();
            case "testCreateIndexDuplicate": return testCreateIndexDuplicate();
            case "testDropIndex": return testDropIndex();
            case "testIndexScanWithCreateIndex": return testIndexScanWithCreateIndex();
            case "testInMemoryOrderedIndex": return testInMemoryOrderedIndex();
            case "testBPlusTreeIndexPersist": return testBPlusTreeIndexPersist();
            case "testIndexScanOrder": return testIndexScanOrder();
            default: return false;
        }
    }

    // Test 1: B+ Tree basic insert and search
    static boolean testBPlusTreeInsertSearch() throws Exception {
        BPlusTree tree = new BPlusTree(4);
        tree.insert(new Value(10L), new RID(1, 0));
        tree.insert(new Value(5L), new RID(2, 0));
        tree.insert(new Value(15L), new RID(3, 0));
        tree.insert(new Value(3L), new RID(4, 0));

        assert tree.size() == 4 : "Expected size 4, got " + tree.size();
        RID r1 = (RID) tree.search(new Value(5L));
        assert r1 != null && r1.pageNum == 2 : "Search for 5 failed";
        RID r2 = (RID) tree.search(new Value(10L));
        assert r2 != null && r2.pageNum == 1 : "Search for 10 failed";
        RID r3 = (RID) tree.search(new Value(100L));
        assert r3 == null : "Search for 100 should return null";

        System.out.println("  Insert/Search PASSED (size=" + tree.size() + ")");
        return true;
    }

    // Test 2: B+ Tree range search
    static boolean testBPlusTreeRangeSearch() throws Exception {
        BPlusTree tree = new BPlusTree(4);
        for (long i = 1; i <= 20; i++) {
            tree.insert(new Value(i * 10), new RID((int) i, 0));
        }

        List<Map.Entry<Value, Object>> range = tree.rangeSearch(
                new Value(30L), true, new Value(80L), true);
        assert range.size() == 6 : "Expected 6 items in [30,80], got " + range.size();

        // Exclusive range
        List<Map.Entry<Value, Object>> range2 = tree.rangeSearch(
                new Value(30L), false, new Value(80L), false);
        assert range2.size() == 4 : "Expected 4 items in (30,80), got " + range2.size();

        System.out.println("  Range search PASSED");
        return true;
    }

    // Test 3: B+ Tree iterator
    static boolean testBPlusTreeIterator() throws Exception {
        BPlusTree tree = new BPlusTree(4);
        tree.insert(new Value(30L), new RID(3, 0));
        tree.insert(new Value(10L), new RID(1, 0));
        tree.insert(new Value(20L), new RID(2, 0));

        Iterator<Map.Entry<Value, Object>> it = tree.scanAll();
        long prev = Long.MIN_VALUE;
        int count = 0;
        while (it.hasNext()) {
            Map.Entry<Value, Object> e = it.next();
            Long val = (Long) e.getKey().value;
            assert val >= prev : "Order violated: " + val + " < " + prev;
            prev = val;
            count++;
            System.out.println("  key=" + val + " rid=" + e.getValue());
        }
        assert count == 3 : "Expected 3 items, got " + count;
        System.out.println("  Iterator PASSED");
        return true;
    }

    // Test 4: Empty B+ Tree
    static boolean testBPlusTreeEmpty() throws Exception {
        BPlusTree tree = new BPlusTree(4);
        assert tree.isEmpty() : "New tree should be empty";
        assert tree.size() == 0 : "New tree should have size 0";
        assert tree.search(new Value(1L)) == null : "Search on empty tree should return null";
        System.out.println("  Empty tree PASSED");
        return true;
    }

    // Test 5: Large insert (tests node splitting)
    static boolean testBPlusTreeLargeInsert() throws Exception {
        BPlusTree tree = new BPlusTree(4);
        int N = 100;
        for (long i = 0; i < N; i++) {
            tree.insert(new Value(i * 10), new RID((int) i, 0));
        }
        assert tree.size() == N : "Expected " + N + " items, got " + tree.size();

        // Verify sorted order
        Iterator<Map.Entry<Value, Object>> it = tree.scanAll();
        long prev = Long.MIN_VALUE;
        int count = 0;
        while (it.hasNext()) {
            Long val = (Long) it.next().getKey().value;
            assert val >= prev : "Order violated after large insert";
            prev = val;
            count++;
        }
        assert count == N : "Expected " + N + " items in iteration";

        // Search for every 10th item
        for (long i = 0; i < N; i += 10) {
            assert tree.search(new Value(i * 10)) != null : "Missing key " + (i * 10);
        }

        System.out.println("  Large insert (" + N + " items) PASSED");
        return true;
    }

    // Test 6: CREATE INDEX SQL
    static boolean testCreateIndex() throws Exception {
        DBManager db = buildDbManager();
        String t = "t_idx1";
        execute(db, "CREATE TABLE " + t + " (id int, name char, salary int)");
        execute(db, "INSERT INTO " + t + " (id, name, salary) VALUES (1, 'Alice', 5000)");
        execute(db, "INSERT INTO " + t + " (id, name, salary) VALUES (2, 'Bob', 7000)");
        execute(db, "INSERT INTO " + t + " (id, name, salary) VALUES (3, 'Charlie', 6000)");
        execute(db, "CREATE INDEX idx1 ON " + t + " (id)");

        TableMeta meta = db.getMetaManager().getTable(t);
        assert meta.getIndexes() != null && !meta.getIndexes().isEmpty() :
                "Index should be created in metadata";
        assert meta.getIndexes().containsKey("id") : "Index on id column should exist";

        System.out.println("  CREATE INDEX PASSED");
        return true;
    }

    // Test 7: Duplicate CREATE INDEX should fail
    static boolean testCreateIndexDuplicate() throws Exception {
        DBManager db = buildDbManager();
        String t = "t_idx2";
        execute(db, "CREATE TABLE " + t + " (id int, name char)");
        execute(db, "INSERT INTO " + t + " (id, name) VALUES (1, 'Alice')");
        execute(db, "CREATE INDEX idx2 ON " + t + " (id)");

        boolean gotError = false;
        try {
            execute(db, "CREATE INDEX idx2 ON " + t + " (id)");
        } catch (DBException e) {
            if (e.getMessage().contains("Index already exist")) {
                gotError = true;
            }
        }
        assert gotError : "Should throw IndexAlreadyExist for duplicate index";
        System.out.println("  Duplicate index detection PASSED");
        return true;
    }

    // Test 8: DROP INDEX
    static boolean testDropIndex() throws Exception {
        DBManager db = buildDbManager();
        String t = "t_idx3";
        execute(db, "CREATE TABLE " + t + " (id int, name char)");
        execute(db, "INSERT INTO " + t + " (id, name) VALUES (1, 'Alice')");
        execute(db, "CREATE INDEX idx3 ON " + t + " (id)");
        execute(db, "DROP INDEX idx_" + t + "_id");

        TableMeta meta = db.getMetaManager().getTable(t);
        assert meta.getIndexes() == null || !meta.getIndexes().containsKey("id") :
                "Index should be removed after DROP INDEX";

        // Can recreate after drop
        execute(db, "CREATE INDEX idx3 ON " + t + " (id)");
        assert db.getMetaManager().getTable(t).getIndexes().containsKey("id") :
                "Index should be recreatable after drop";
        System.out.println("  DROP INDEX PASSED");
        return true;
    }

    // Test 9: Index scan after CREATE INDEX
    static boolean testIndexScanWithCreateIndex() throws Exception {
        DBManager db = buildDbManager();
        String t = "t_idx4";
        execute(db, "CREATE TABLE " + t + " (id int, value int)");
        execute(db, "INSERT INTO " + t + " (id, value) VALUES (3, 300)");
        execute(db, "INSERT INTO " + t + " (id, value) VALUES (1, 100)");
        execute(db, "INSERT INTO " + t + " (id, value) VALUES (2, 200)");
        execute(db, "CREATE INDEX idx4 ON " + t + " (id)");

        // Now query - should use index scan and return in sorted order
        List<Value[]> rows = query(db, "SELECT id, value FROM " + t);
        assert rows.size() == 3 : "Expected 3 rows, got " + rows.size();

        System.out.println("  Plan: " + getPlan(db, "SELECT id, value FROM " + t));

        // Verify order (index scan should return in sorted order)
        for (int i = 0; i < rows.size(); i++) {
            System.out.println("  id=" + rows.get(i)[0].value + " value=" + rows.get(i)[1].value);
        }
        // Index scan returns in id order: 1, 2, 3
        assert (Long) rows.get(0)[0].value == 1L : "Index scan should return in sorted key order";
        assert (Long) rows.get(1)[0].value == 2L;
        assert (Long) rows.get(2)[0].value == 3L;
        System.out.println("  Index scan after CREATE INDEX PASSED");
        return true;
    }

    // Test 10: InMemoryOrderedIndex persistence
    static boolean testInMemoryOrderedIndex() throws Exception {
        Path indexFile = tempDir.resolve("test_index.json");
        // Create and populate
        InMemoryOrderedIndex idx1 = new InMemoryOrderedIndex(indexFile.toString());
        java.lang.reflect.Field mapField = InMemoryOrderedIndex.class.getDeclaredField("indexMap");
        mapField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.TreeMap<Value, RID> map = new java.util.TreeMap<>((v1, v2) -> {
            try {
                return edu.sustech.cs307.value.ValueComparer.compare(v1, v2);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        map.put(new Value(10L), new RID(1, 0));
        map.put(new Value(5L), new RID(2, 0));
        map.put(new Value(15L), new RID(3, 0));
        mapField.set(idx1, map);
        // Save manually via JSON
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.writeValue(indexFile.toFile(), map);

        // Load from file
        InMemoryOrderedIndex idx2 = new InMemoryOrderedIndex(indexFile.toString());
        RID r = idx2.EqualTo(new Value(10L));
        assert r != null && r.pageNum == 1 : "Loaded index should find key 10";
        r = idx2.EqualTo(new Value(5L));
        assert r != null && r.pageNum == 2 : "Loaded index should find key 5";
        r = idx2.EqualTo(new Value(999L));
        assert r == null : "Non-existent key should return null";

        System.out.println("  InMemoryOrderedIndex persistence PASSED");
        return true;
    }

    // Test 11: BPlusTreeIndex persistence
    static boolean testBPlusTreeIndexPersist() throws Exception {
        Path indexFile = tempDir.resolve("bptree_index.json");
        BPlusTreeIndex idx1 = new BPlusTreeIndex(indexFile.toString());
        idx1.insert(new Value(10L), new RID(1, 0));
        idx1.insert(new Value(5L), new RID(2, 0));
        idx1.insert(new Value(15L), new RID(3, 0));
        idx1.persist();

        BPlusTreeIndex idx2 = new BPlusTreeIndex(indexFile.toString());
        assert idx2.size() == 3 : "Expected 3 items after reload, got " + idx2.size();
        RID r = idx2.EqualTo(new Value(10L));
        assert r != null : "Reloaded index should find key 10";
        System.out.println("  BPlusTreeIndex persistence PASSED");
        return true;
    }

    // Test 12: Index scan returns data in sorted order
    static boolean testIndexScanOrder() throws Exception {
        DBManager db = buildDbManager();
        String t = "t_idx5";
        execute(db, "CREATE TABLE " + t + " (id int, name char)");
        // Insert in random order
        execute(db, "INSERT INTO " + t + " (id, name) VALUES (50, 'Fifty')");
        execute(db, "INSERT INTO " + t + " (id, name) VALUES (10, 'Ten')");
        execute(db, "INSERT INTO " + t + " (id, name) VALUES (30, 'Thirty')");
        execute(db, "INSERT INTO " + t + " (id, name) VALUES (20, 'Twenty')");
        execute(db, "INSERT INTO " + t + " (id, name) VALUES (40, 'Forty')");
        execute(db, "CREATE INDEX idx5 ON " + t + " (id)");

        List<Value[]> rows = query(db, "SELECT id, name FROM " + t);
        assert rows.size() == 5 : "Expected 5 rows, got " + rows.size();

        long prev = Long.MIN_VALUE;
        for (Value[] row : rows) {
            Long id = (Long) row[0].value;
            System.out.println("  id=" + id);
            assert id >= prev : "Index scan should return sorted: " + id + " < " + prev;
            prev = id;
        }
        System.out.println("  Index scan sorted order PASSED");
        return true;
    }

    // === Helper methods ===
    static DBManager buildDbManager() throws Exception {
        HashMap<String, Integer> fileOffsets = new HashMap<>();
        DiskManager diskManager = new DiskManager(tempDir.toString(), fileOffsets);
        IntFunction<PageReplacer> replacerFactory = ClockReplacer::new;
        BufferPool bufferPool = new BufferPool(16, diskManager, replacerFactory.apply(16));
        RecordManager recordManager = new RecordManager(diskManager, bufferPool);
        MetaManager metaManager = new MetaManager(tempDir.resolve("meta").toString());
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

    static List<Value[]> query(DBManager db, String sql) throws DBException {
        LogicalOperator op = LogicalPlanner.resolveAndPlan(db, sql);
        PhysicalOperator phys = PhysicalPlanner.generateOperator(db, op);
        List<Value[]> results = new ArrayList<>();
        phys.Begin();
        while (phys.hasNext()) {
            phys.Next();
            Tuple tuple = phys.Current();
            if (tuple != null) {
                results.add(tuple.getValues());
            }
        }
        phys.Close();
        return results;
    }

    static String getPlan(DBManager db, String sql) throws DBException {
        LogicalOperator op = LogicalPlanner.resolveAndPlan(db, sql);
        return op != null ? op.toString() : "null";
    }
}
