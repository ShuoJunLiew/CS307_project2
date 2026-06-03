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
import edu.sustech.cs307.value.Value;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.IntFunction;

public class OrderByTestRunner {

    private static Path tempDir;

    public static void main(String[] args) throws Exception {
        tempDir = Files.createTempDirectory("cs307_orderby_");
        System.out.println("Test directory: " + tempDir);
        int passed = 0;
        int failed = 0;

        String[] tests = {
            "testOrderByAscInt",
            "testOrderByDescInt",
            "testOrderByAscChar",
            "testOrderByDescChar",
            "testOrderByMultiColSameDirection",
            "testOrderByMultiColMixedDirection",
            "testOrderByWithWhere",
            "testOrderByWithGroupByAsc",
            "testOrderByWithGroupByDesc",
            "testOrderByColumnNotInSelect",
            "testOrderByAllColumns",
            "testOrderByCharWithPadding",
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

        System.out.println("\n=== ORDER BY Results: " + passed + " passed, " + failed + " failed ===");
        if (failed > 0) {
            System.exit(1);
        }
    }

    static boolean runTest(String name) throws Exception {
        System.out.println("\n--- " + name + " ---");
        switch (name) {
            case "testOrderByAscInt":
                return testOrderByAscInt();
            case "testOrderByDescInt":
                return testOrderByDescInt();
            case "testOrderByAscChar":
                return testOrderByAscChar();
            case "testOrderByDescChar":
                return testOrderByDescChar();
            case "testOrderByMultiColSameDirection":
                return testOrderByMultiColSameDirection();
            case "testOrderByMultiColMixedDirection":
                return testOrderByMultiColMixedDirection();
            case "testOrderByWithWhere":
                return testOrderByWithWhere();
            case "testOrderByWithGroupByAsc":
                return testOrderByWithGroupByAsc();
            case "testOrderByWithGroupByDesc":
                return testOrderByWithGroupByDesc();
            case "testOrderByColumnNotInSelect":
                return testOrderByColumnNotInSelect();
            case "testOrderByAllColumns":
                return testOrderByAllColumns();
            case "testOrderByCharWithPadding":
                return testOrderByCharWithPadding();
            default:
                System.out.println("  Unknown test: " + name);
                return false;
        }
    }

    // Test 1: ORDER BY int ASC (default)
    static boolean testOrderByAscInt() throws Exception {
        DBManager db = buildDbManager();
        String t = "t1";
        execute(db, "CREATE TABLE " + t + " (id int)");
        execute(db, "INSERT INTO " + t + " (id) VALUES (30)");
        execute(db, "INSERT INTO " + t + " (id) VALUES (10)");
        execute(db, "INSERT INTO " + t + " (id) VALUES (20)");

        List<Value[]> rows = query(db, "SELECT id FROM " + t + " ORDER BY id");
        assert rows.size() == 3 : "Expected 3 rows, got " + rows.size();

        Long v1 = (Long) rows.get(0)[0].value;
        Long v2 = (Long) rows.get(1)[0].value;
        Long v3 = (Long) rows.get(2)[0].value;
        System.out.println("  sorted: " + v1 + " -> " + v2 + " -> " + v3);
        assert v1 == 10L && v2 == 20L && v3 == 30L :
                "Expected 10, 20, 30 but got " + v1 + ", " + v2 + ", " + v3;
        System.out.println("  PASSED");
        return true;
    }

    // Test 2: ORDER BY int DESC
    static boolean testOrderByDescInt() throws Exception {
        DBManager db = buildDbManager();
        String t = "t2";
        execute(db, "CREATE TABLE " + t + " (id int)");
        execute(db, "INSERT INTO " + t + " (id) VALUES (10)");
        execute(db, "INSERT INTO " + t + " (id) VALUES (30)");
        execute(db, "INSERT INTO " + t + " (id) VALUES (20)");

        List<Value[]> rows = query(db, "SELECT id FROM " + t + " ORDER BY id DESC");
        assert rows.size() == 3 : "Expected 3 rows, got " + rows.size();

        Long v1 = (Long) rows.get(0)[0].value;
        Long v2 = (Long) rows.get(1)[0].value;
        Long v3 = (Long) rows.get(2)[0].value;
        System.out.println("  sorted: " + v1 + " -> " + v2 + " -> " + v3);
        assert v1 == 30L && v2 == 20L && v3 == 10L :
                "Expected 30, 20, 10 but got " + v1 + ", " + v2 + ", " + v3;
        System.out.println("  PASSED");
        return true;
    }

    // Test 3: ORDER BY char ASC (default alphabetical)
    static boolean testOrderByAscChar() throws Exception {
        DBManager db = buildDbManager();
        String t = "t3";
        execute(db, "CREATE TABLE " + t + " (name char)");
        execute(db, "INSERT INTO " + t + " (name) VALUES ('Charlie')");
        execute(db, "INSERT INTO " + t + " (name) VALUES ('Alice')");
        execute(db, "INSERT INTO " + t + " (name) VALUES ('Bob')");

        List<Value[]> rows = query(db, "SELECT name FROM " + t + " ORDER BY name");
        assert rows.size() == 3 : "Expected 3 rows, got " + rows.size();

        // Check that names are in alphabetical order
        for (int i = 0; i < rows.size(); i++) {
            System.out.println("  [" + i + "] name=" + rows.get(i)[0].value);
        }
        String n0 = ((String) rows.get(0)[0].value).trim();
        String n1 = ((String) rows.get(1)[0].value).trim();
        String n2 = ((String) rows.get(2)[0].value).trim();
        assert n0.equals("Alice") : "Expected Alice first, got " + n0;
        assert n1.equals("Bob") : "Expected Bob second, got " + n1;
        assert n2.equals("Charlie") : "Expected Charlie third, got " + n2;
        System.out.println("  PASSED");
        return true;
    }

    // Test 4: ORDER BY char DESC
    static boolean testOrderByDescChar() throws Exception {
        DBManager db = buildDbManager();
        String t = "t4";
        execute(db, "CREATE TABLE " + t + " (name char)");
        execute(db, "INSERT INTO " + t + " (name) VALUES ('Alice')");
        execute(db, "INSERT INTO " + t + " (name) VALUES ('Charlie')");
        execute(db, "INSERT INTO " + t + " (name) VALUES ('Bob')");

        List<Value[]> rows = query(db, "SELECT name FROM " + t + " ORDER BY name DESC");
        assert rows.size() == 3 : "Expected 3 rows, got " + rows.size();

        for (int i = 0; i < rows.size(); i++) {
            System.out.println("  [" + i + "] name=" + rows.get(i)[0].value);
        }
        String n0 = ((String) rows.get(0)[0].value).trim();
        String n1 = ((String) rows.get(1)[0].value).trim();
        String n2 = ((String) rows.get(2)[0].value).trim();
        assert n0.equals("Charlie") : "Expected Charlie first, got " + n0;
        assert n1.equals("Bob") : "Expected Bob second, got " + n1;
        assert n2.equals("Alice") : "Expected Alice third, got " + n2;
        System.out.println("  PASSED");
        return true;
    }

    // Test 5: Multiple ORDER BY columns, same direction (all ASC)
    static boolean testOrderByMultiColSameDirection() throws Exception {
        DBManager db = buildDbManager();
        String t = "t5";
        execute(db, "CREATE TABLE " + t + " (a int, b int)");
        execute(db, "INSERT INTO " + t + " (a, b) VALUES (1, 20)");
        execute(db, "INSERT INTO " + t + " (a, b) VALUES (1, 10)");
        execute(db, "INSERT INTO " + t + " (a, b) VALUES (2, 5)");
        execute(db, "INSERT INTO " + t + " (a, b) VALUES (2, 30)");

        List<Value[]> rows = query(db, "SELECT a, b FROM " + t + " ORDER BY a, b");
        assert rows.size() == 4 : "Expected 4 rows, got " + rows.size();

        for (Value[] row : rows) {
            System.out.println("  a=" + row[0].value + " b=" + row[1].value);
        }
        // Expected: (1,10), (1,20), (2,5), (2,30)
        assert (Long) rows.get(0)[0].value == 1L && (Long) rows.get(0)[1].value == 10L;
        assert (Long) rows.get(1)[0].value == 1L && (Long) rows.get(1)[1].value == 20L;
        assert (Long) rows.get(2)[0].value == 2L && (Long) rows.get(2)[1].value == 5L;
        assert (Long) rows.get(3)[0].value == 2L && (Long) rows.get(3)[1].value == 30L;
        System.out.println("  PASSED");
        return true;
    }

    // Test 6: Multiple ORDER BY columns, mixed direction (a ASC, b DESC)
    static boolean testOrderByMultiColMixedDirection() throws Exception {
        DBManager db = buildDbManager();
        String t = "t6";
        execute(db, "CREATE TABLE " + t + " (a int, b int)");
        execute(db, "INSERT INTO " + t + " (a, b) VALUES (1, 10)");
        execute(db, "INSERT INTO " + t + " (a, b) VALUES (1, 20)");
        execute(db, "INSERT INTO " + t + " (a, b) VALUES (2, 5)");
        execute(db, "INSERT INTO " + t + " (a, b) VALUES (2, 30)");

        List<Value[]> rows = query(db, "SELECT a, b FROM " + t + " ORDER BY a ASC, b DESC");
        assert rows.size() == 4 : "Expected 4 rows, got " + rows.size();

        for (Value[] row : rows) {
            System.out.println("  a=" + row[0].value + " b=" + row[1].value);
        }
        // Expected: (1,20), (1,10), (2,30), (2,5) — a ASC, b within same a DESC
        assert (Long) rows.get(0)[0].value == 1L && (Long) rows.get(0)[1].value == 20L;
        assert (Long) rows.get(1)[0].value == 1L && (Long) rows.get(1)[1].value == 10L;
        assert (Long) rows.get(2)[0].value == 2L && (Long) rows.get(2)[1].value == 30L;
        assert (Long) rows.get(3)[0].value == 2L && (Long) rows.get(3)[1].value == 5L;
        System.out.println("  PASSED");
        return true;
    }

    // Test 7: ORDER BY with WHERE filter
    static boolean testOrderByWithWhere() throws Exception {
        DBManager db = buildDbManager();
        String t = "t7";
        execute(db, "CREATE TABLE " + t + " (id int, salary int)");
        execute(db, "INSERT INTO " + t + " (id, salary) VALUES (1, 5000)");
        execute(db, "INSERT INTO " + t + " (id, salary) VALUES (2, 3000)");
        execute(db, "INSERT INTO " + t + " (id, salary) VALUES (3, 7000)");
        execute(db, "INSERT INTO " + t + " (id, salary) VALUES (4, 4000)");

        // Only show salaries >= 4000, sorted descending
        List<Value[]> rows = query(db,
                "SELECT id, salary FROM " + t + " WHERE salary >= 4000 ORDER BY salary DESC");
        assert rows.size() == 3 : "Expected 3 rows (>= 4000), got " + rows.size();

        for (Value[] row : rows) {
            System.out.println("  id=" + row[0].value + " salary=" + row[1].value);
        }
        // Expected: (3,7000), (1,5000), (4,4000)
        assert (Long) rows.get(0)[1].value == 7000L;
        assert (Long) rows.get(1)[1].value == 5000L;
        assert (Long) rows.get(2)[1].value == 4000L;
        System.out.println("  PASSED");
        return true;
    }

    // Test 8: ORDER BY after GROUP BY (ASC)
    static boolean testOrderByWithGroupByAsc() throws Exception {
        DBManager db = buildDbManager();
        String t = "t8";
        execute(db, "CREATE TABLE " + t + " (dept char, salary int)");
        execute(db, "INSERT INTO " + t + " (dept, salary) VALUES ('HR', 5000)");
        execute(db, "INSERT INTO " + t + " (dept, salary) VALUES ('IT', 3000)");
        execute(db, "INSERT INTO " + t + " (dept, salary) VALUES ('IT', 7000)");
        execute(db, "INSERT INTO " + t + " (dept, salary) VALUES ('HR', 4000)");

        List<Value[]> rows = query(db,
                "SELECT dept, MAX(salary) FROM " + t + " GROUP BY dept ORDER BY dept ASC");
        assert rows.size() == 2 : "Expected 2 rows, got " + rows.size();

        for (Value[] row : rows) {
            System.out.println("  dept=" + row[0].value + " MAX=" + row[1].value);
        }
        // ASC: HR before IT
        assert ((String) rows.get(0)[0].value).trim().equals("HR") : "ASC: HR should be first";
        assert ((String) rows.get(1)[0].value).trim().equals("IT") : "ASC: IT should be second";
        // HR max = 5000, IT max = 7000
        assert (Long) rows.get(0)[1].value == 5000L;
        assert (Long) rows.get(1)[1].value == 7000L;
        System.out.println("  PASSED");
        return true;
    }

    // Test 9: ORDER BY after GROUP BY (DESC)
    static boolean testOrderByWithGroupByDesc() throws Exception {
        DBManager db = buildDbManager();
        String t = "t9";
        execute(db, "CREATE TABLE " + t + " (dept char, salary int)");
        execute(db, "INSERT INTO " + t + " (dept, salary) VALUES ('HR', 5000)");
        execute(db, "INSERT INTO " + t + " (dept, salary) VALUES ('IT', 3000)");
        execute(db, "INSERT INTO " + t + " (dept, salary) VALUES ('IT', 7000)");

        List<Value[]> rows = query(db,
                "SELECT dept, MAX(salary) FROM " + t + " GROUP BY dept ORDER BY MAX(salary) DESC");
        assert rows.size() == 2 : "Expected 2 rows, got " + rows.size();

        for (Value[] row : rows) {
            System.out.println("  dept=" + row[0].value + " MAX=" + row[1].value);
        }
        // DESC by MAX(salary): IT(7000) before HR(5000)
        assert ((String) rows.get(0)[0].value).trim().equals("IT") : "IT MAX=7000 should be first";
        assert (Long) rows.get(0)[1].value == 7000L;
        assert (Long) rows.get(1)[1].value == 5000L;
        System.out.println("  PASSED");
        return true;
    }

    // Test 10: ORDER BY column not in SELECT list
    static boolean testOrderByColumnNotInSelect() throws Exception {
        DBManager db = buildDbManager();
        String t = "t10";
        execute(db, "CREATE TABLE " + t + " (id int, name char, salary int)");
        execute(db, "INSERT INTO " + t + " (id, name, salary) VALUES (1, 'Alice', 5000)");
        execute(db, "INSERT INTO " + t + " (id, name, salary) VALUES (2, 'Bob', 3000)");
        execute(db, "INSERT INTO " + t + " (id, name, salary) VALUES (3, 'Charlie', 7000)");

        // salary is not in SELECT, but used in ORDER BY
        List<Value[]> rows = query(db, "SELECT name FROM " + t + " ORDER BY salary ASC");
        assert rows.size() == 3 : "Expected 3 rows, got " + rows.size();

        for (Value[] row : rows) {
            System.out.println("  name=" + row[0].value);
        }
        // Expected: Bob(3000), Alice(5000), Charlie(7000)
        String n0 = ((String) rows.get(0)[0].value).trim();
        String n1 = ((String) rows.get(1)[0].value).trim();
        String n2 = ((String) rows.get(2)[0].value).trim();
        assert n0.equals("Bob") : "Expected Bob (salary=3000) first, got " + n0;
        assert n1.equals("Alice") : "Expected Alice (salary=5000) second, got " + n1;
        assert n2.equals("Charlie") : "Expected Charlie (salary=7000) third, got " + n2;
        System.out.println("  PASSED");
        return true;
    }

    // Test 11: SELECT * with ORDER BY
    static boolean testOrderByAllColumns() throws Exception {
        DBManager db = buildDbManager();
        String t = "t11";
        execute(db, "CREATE TABLE " + t + " (id int, value int)");
        execute(db, "INSERT INTO " + t + " (id, value) VALUES (3, 100)");
        execute(db, "INSERT INTO " + t + " (id, value) VALUES (1, 300)");
        execute(db, "INSERT INTO " + t + " (id, value) VALUES (2, 200)");

        List<Value[]> rows = query(db, "SELECT * FROM " + t + " ORDER BY id");
        assert rows.size() == 3 : "Expected 3 rows, got " + rows.size();

        for (Value[] row : rows) {
            System.out.println("  id=" + row[0].value + " value=" + row[1].value);
        }
        // Sorted by id ASC
        assert (Long) rows.get(0)[0].value == 1L;
        assert (Long) rows.get(1)[0].value == 2L;
        assert (Long) rows.get(2)[0].value == 3L;
        System.out.println("  PASSED");
        return true;
    }

    // Test 12: ORDER BY with CHAR values that have padding
    static boolean testOrderByCharWithPadding() throws Exception {
        DBManager db = buildDbManager();
        String t = "t12";
        execute(db, "CREATE TABLE " + t + " (code char, value int)");
        execute(db, "INSERT INTO " + t + " (code, value) VALUES ('ZZZ', 3)");
        execute(db, "INSERT INTO " + t + " (code, value) VALUES ('AAA', 1)");
        execute(db, "INSERT INTO " + t + " (code, value) VALUES ('MMM', 2)");

        List<Value[]> rows = query(db, "SELECT code, value FROM " + t + " ORDER BY code");
        assert rows.size() == 3 : "Expected 3 rows, got " + rows.size();

        for (Value[] row : rows) {
            System.out.println("  code=" + row[0].value + " value=" + row[1].value);
        }
        // Sorted alphabetically: AAA, MMM, ZZZ
        String c0 = ((String) rows.get(0)[0].value).trim();
        String c1 = ((String) rows.get(1)[0].value).trim();
        String c2 = ((String) rows.get(2)[0].value).trim();
        assert c0.equals("AAA") : "Expected AAA first, got " + c0;
        assert c1.equals("MMM") : "Expected MMM second, got " + c1;
        assert c2.equals("ZZZ") : "Expected ZZZ third, got " + c2;
        System.out.println("  PASSED");
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
        System.out.println("  Plan: " + op);
        List<Value[]> results = new ArrayList<>();
        phys.Begin();
        while (phys.hasNext()) {
            phys.Next();
            Tuple tuple = phys.Current();
            results.add(tuple.getValues());
        }
        phys.Close();
        return results;
    }
}
