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

public class AggregateTestRunner {

    private static Path tempDir;

    public static void main(String[] args) throws Exception {
        tempDir = Files.createTempDirectory("cs307_test_");
        System.out.println("Test directory: " + tempDir);
        int passed = 0;
        int failed = 0;

        try {
            if (runTest("testMaxWithoutGroupBy")) passed++; else failed++;
            if (runTest("testMinWithoutGroupBy")) passed++; else failed++;
            if (runTest("testMaxWithGroupBy")) passed++; else failed++;
            if (runTest("testMinWithGroupBy")) passed++; else failed++;
            if (runTest("testMaxMinWithGroupBy")) passed++; else failed++;
            if (runTest("testMultipleGroupByColumns")) passed++; else failed++;
            if (runTest("testOrderByAsc")) passed++; else failed++;
            if (runTest("testOrderByDesc")) passed++; else failed++;
            if (runTest("testOrderByMultiColumn")) passed++; else failed++;
            if (runTest("testOrderByWithGroupBy")) passed++; else failed++;
        } catch (Exception e) {
            System.err.println("FATAL: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("\n=== Results: " + passed + " passed, " + failed + " failed ===");
        if (failed > 0) {
            System.exit(1);
        }
    }

    // Test 1: SELECT MAX(salary) FROM emp (no GROUP BY)
    static boolean runTest(String name) throws Exception {
        System.out.println("\n--- " + name + " ---");
        switch (name) {
            case "testMaxWithoutGroupBy":
                return testMaxWithoutGroupBy();
            case "testMinWithoutGroupBy":
                return testMinWithoutGroupBy();
            case "testMaxWithGroupBy":
                return testMaxWithGroupBy();
            case "testMinWithGroupBy":
                return testMinWithGroupBy();
            case "testMaxMinWithGroupBy":
                return testMaxMinWithGroupBy();
            case "testMultipleGroupByColumns":
                return testMultipleGroupByColumns();
            case "testOrderByAsc":
                return testOrderByAsc();
            case "testOrderByDesc":
                return testOrderByDesc();
            case "testOrderByMultiColumn":
                return testOrderByMultiColumn();
            case "testOrderByWithGroupBy":
                return testOrderByWithGroupBy();
            default:
                return false;
        }
    }

    static boolean testMaxWithoutGroupBy() throws Exception {
        DBManager db = buildDbManager();
        String t = "emp_max1";
        execute(db, "CREATE TABLE " + t + " (id int, name char, salary int, dept char)");
        execute(db, "INSERT INTO " + t + " (id, name, salary, dept) VALUES (1, 'Alice', 5000, 'IT')");
        execute(db, "INSERT INTO " + t + " (id, name, salary, dept) VALUES (2, 'Bob', 7000, 'HR')");
        execute(db, "INSERT INTO " + t + " (id, name, salary, dept) VALUES (3, 'Charlie', 6000, 'IT')");

        List<Value[]> rows = query(db, "SELECT MAX(salary) FROM " + t);
        assert rows.size() == 1 : "Expected 1 row, got " + rows.size();
        Value[] row = rows.get(0);
        assert row.length == 1 : "Expected 1 column, got " + row.length;
        Long maxSalary = (Long) row[0].value;
        assert maxSalary == 7000L : "Expected MAX(salary)=7000, got " + maxSalary;

        System.out.println("  MAX(salary) = " + maxSalary + " PASSED");
        return true;
    }

    static boolean testMinWithoutGroupBy() throws Exception {
        DBManager db = buildDbManager();
        String t = "emp_min1";
        execute(db, "CREATE TABLE " + t + " (id int, name char, salary int, dept char)");
        execute(db, "INSERT INTO " + t + " (id, name, salary, dept) VALUES (1, 'Alice', 5000, 'IT')");
        execute(db, "INSERT INTO " + t + " (id, name, salary, dept) VALUES (2, 'Bob', 7000, 'HR')");
        execute(db, "INSERT INTO " + t + " (id, name, salary, dept) VALUES (3, 'Charlie', 6000, 'IT')");

        List<Value[]> rows = query(db, "SELECT MIN(salary) FROM " + t);
        assert rows.size() == 1 : "Expected 1 row, got " + rows.size();
        Long minSalary = (Long) rows.get(0)[0].value;
        assert minSalary == 5000L : "Expected MIN(salary)=5000, got " + minSalary;

        System.out.println("  MIN(salary) = " + minSalary + " PASSED");
        return true;
    }

    static boolean testMaxWithGroupBy() throws Exception {
        DBManager db = buildDbManager();
        String t = "emp_max2";
        execute(db, "CREATE TABLE " + t + " (id int, name char, salary int, dept char)");
        execute(db, "INSERT INTO " + t + " (id, name, salary, dept) VALUES (1, 'Alice', 5000, 'IT')");
        execute(db, "INSERT INTO " + t + " (id, name, salary, dept) VALUES (2, 'Bob', 7000, 'HR')");
        execute(db, "INSERT INTO " + t + " (id, name, salary, dept) VALUES (3, 'Charlie', 6000, 'IT')");

        List<Value[]> rows = query(db, "SELECT dept, MAX(salary) FROM " + t + " GROUP BY dept");
        assert rows.size() == 2 : "Expected 2 rows (IT, HR), got " + rows.size();

        boolean foundIT = false, foundHR = false;
        for (Value[] row : rows) {
            String dept = ((String) row[0].value).trim();
            Long maxSal = (Long) row[1].value;
            System.out.println("  dept=" + dept + " MAX=" + maxSal);
            if (dept.equals("IT")) {
                assert maxSal == 6000L : "IT MAX salary expected 6000, got " + maxSal;
                foundIT = true;
            } else if (dept.equals("HR")) {
                assert maxSal == 7000L : "HR MAX salary expected 7000, got " + maxSal;
                foundHR = true;
            }
        }
        assert foundIT && foundHR : "Missing expected departments";
        System.out.println("  GROUP BY dept with MAX(salary) PASSED");
        return true;
    }

    static boolean testMinWithGroupBy() throws Exception {
        DBManager db = buildDbManager();
        String t = "emp_min2";
        execute(db, "CREATE TABLE " + t + " (id int, name char, salary int, dept char)");
        execute(db, "INSERT INTO " + t + " (id, name, salary, dept) VALUES (1, 'Alice', 5000, 'IT')");
        execute(db, "INSERT INTO " + t + " (id, name, salary, dept) VALUES (2, 'Bob', 7000, 'HR')");
        execute(db, "INSERT INTO " + t + " (id, name, salary, dept) VALUES (3, 'Charlie', 6000, 'IT')");

        List<Value[]> rows = query(db, "SELECT dept, MIN(salary) FROM " + t + " GROUP BY dept");
        assert rows.size() == 2 : "Expected 2 rows, got " + rows.size();

        for (Value[] row : rows) {
            String dept = ((String) row[0].value).trim();
            Long minSal = (Long) row[1].value;
            System.out.println("  dept=" + dept + " MIN=" + minSal);
            if (dept.equals("IT")) {
                assert minSal == 5000L : "IT MIN salary expected 5000, got " + minSal;
            } else if (dept.equals("HR")) {
                assert minSal == 7000L : "HR MIN salary expected 7000, got " + minSal;
            }
        }
        System.out.println("  GROUP BY dept with MIN(salary) PASSED");
        return true;
    }

    static boolean testMaxMinWithGroupBy() throws Exception {
        DBManager db = buildDbManager();
        String t = "emp_maxmin";
        execute(db, "CREATE TABLE " + t + " (id int, name char, salary int, dept char)");
        execute(db, "INSERT INTO " + t + " (id, name, salary, dept) VALUES (1, 'Alice', 5000, 'IT')");
        execute(db, "INSERT INTO " + t + " (id, name, salary, dept) VALUES (2, 'Bob', 7000, 'HR')");
        execute(db, "INSERT INTO " + t + " (id, name, salary, dept) VALUES (3, 'Charlie', 6000, 'IT')");
        execute(db, "INSERT INTO " + t + " (id, name, salary, dept) VALUES (4, 'Diana', 5500, 'IT')");

        List<Value[]> rows = query(db, "SELECT dept, MAX(salary), MIN(salary) FROM " + t + " GROUP BY dept");
        assert rows.size() == 2 : "Expected 2 rows, got " + rows.size();

        for (Value[] row : rows) {
            String dept = ((String) row[0].value).trim();
            Long maxSal = (Long) row[1].value;
            Long minSal = (Long) row[2].value;
            System.out.println("  dept=" + dept + " MAX=" + maxSal + " MIN=" + minSal);
            if (dept.equals("IT")) {
                assert maxSal == 6000L : "IT MAX expected 6000, got " + maxSal;
                assert minSal == 5000L : "IT MIN expected 5000, got " + minSal;
            } else if (dept.equals("HR")) {
                assert maxSal == 7000L : "HR MAX expected 7000, got " + maxSal;
                assert minSal == 7000L : "HR MIN expected 7000, got " + minSal;
            }
        }
        System.out.println("  MAX and MIN with GROUP BY PASSED");
        return true;
    }

    static boolean testMultipleGroupByColumns() throws Exception {
        DBManager db = buildDbManager();
        String t = "sales_multi";
        execute(db, "CREATE TABLE " + t + " (region char, product char, amount int)");
        execute(db, "INSERT INTO " + t + " (region, product, amount) VALUES ('North', 'A', 100)");
        execute(db, "INSERT INTO " + t + " (region, product, amount) VALUES ('North', 'B', 200)");
        execute(db, "INSERT INTO " + t + " (region, product, amount) VALUES ('South', 'A', 300)");
        execute(db, "INSERT INTO " + t + " (region, product, amount) VALUES ('North', 'A', 150)");

        List<Value[]> rows = query(db, "SELECT region, product, MAX(amount) FROM " + t + " GROUP BY region, product");
        assert rows.size() >= 3 : "Expected at least 3 groups, got " + rows.size();

        for (Value[] row : rows) {
            String region = ((String) row[0].value).trim();
            String product = ((String) row[1].value).trim();
            Long maxAmt = (Long) row[2].value;
            System.out.println("  region=" + region + " product=" + product + " MAX=" + maxAmt);
            if (region.equals("North") && product.equals("A")) {
                assert maxAmt == 150L : "North/A MAX expected 150, got " + maxAmt;
            } else if (region.equals("North") && product.equals("B")) {
                assert maxAmt == 200L : "North/B MAX expected 200, got " + maxAmt;
            } else if (region.equals("South") && product.equals("A")) {
                assert maxAmt == 300L : "South/A MAX expected 300, got " + maxAmt;
            }
        }
        System.out.println("  Multiple GROUP BY columns PASSED");
        return true;
    }

    // ORDER BY tests
    static boolean testOrderByAsc() throws Exception {
        DBManager db = buildDbManager();
        String t = "emp_ord1";
        execute(db, "CREATE TABLE " + t + " (id int, name char, salary int, dept char)");
        execute(db, "INSERT INTO " + t + " (id, name, salary, dept) VALUES (1, 'Alice', 5000, 'IT')");
        execute(db, "INSERT INTO " + t + " (id, name, salary, dept) VALUES (2, 'Bob', 7000, 'HR')");
        execute(db, "INSERT INTO " + t + " (id, name, salary, dept) VALUES (3, 'Charlie', 6000, 'IT')");

        List<Value[]> rows = query(db, "SELECT name, salary FROM " + t + " ORDER BY salary");
        assert rows.size() == 3 : "Expected 3 rows, got " + rows.size();

        Long prevSalary = Long.MIN_VALUE;
        for (Value[] row : rows) {
            Long salary = (Long) row[1].value;
            System.out.println("  name=" + row[0].value + " salary=" + salary);
            assert salary >= prevSalary : "ASC order violated: " + salary + " < " + prevSalary;
            prevSalary = salary;
        }
        System.out.println("  ORDER BY ASC PASSED");
        return true;
    }

    static boolean testOrderByDesc() throws Exception {
        DBManager db = buildDbManager();
        String t = "emp_ord2";
        execute(db, "CREATE TABLE " + t + " (id int, name char, salary int, dept char)");
        execute(db, "INSERT INTO " + t + " (id, name, salary, dept) VALUES (1, 'Alice', 5000, 'IT')");
        execute(db, "INSERT INTO " + t + " (id, name, salary, dept) VALUES (2, 'Bob', 7000, 'HR')");
        execute(db, "INSERT INTO " + t + " (id, name, salary, dept) VALUES (3, 'Charlie', 6000, 'IT')");

        List<Value[]> rows = query(db, "SELECT name, salary FROM " + t + " ORDER BY salary DESC");
        assert rows.size() == 3 : "Expected 3 rows, got " + rows.size();

        Long prevSalary = Long.MAX_VALUE;
        for (Value[] row : rows) {
            Long salary = (Long) row[1].value;
            System.out.println("  name=" + row[0].value + " salary=" + salary);
            assert salary <= prevSalary : "DESC order violated: " + salary + " > " + prevSalary;
            prevSalary = salary;
        }
        // First should be highest salary (Bob/7000)
        assert (Long) rows.get(0)[1].value == 7000L : "First should have highest salary";
        System.out.println("  ORDER BY DESC PASSED");
        return true;
    }

    static boolean testOrderByMultiColumn() throws Exception {
        DBManager db = buildDbManager();
        String t = "emp_ord3";
        execute(db, "CREATE TABLE " + t + " (id int, name char, salary int, dept char)");
        execute(db, "INSERT INTO " + t + " (id, name, salary, dept) VALUES (1, 'Alice', 5000, 'IT')");
        execute(db, "INSERT INTO " + t + " (id, name, salary, dept) VALUES (2, 'Bob', 7000, 'HR')");
        execute(db, "INSERT INTO " + t + " (id, name, salary, dept) VALUES (3, 'Charlie', 5000, 'IT')");
        execute(db, "INSERT INTO " + t + " (id, name, salary, dept) VALUES (4, 'Diana', 7000, 'IT')");

        List<Value[]> rows = query(db, "SELECT name, dept, salary FROM " + t + " ORDER BY salary, name");
        assert rows.size() == 4 : "Expected 4 rows, got " + rows.size();

        for (Value[] row : rows) {
            System.out.println("  name=" + row[0].value + " dept=" + row[1].value + " salary=" + row[2].value);
        }
        // Alice (5000) before Charlie (5000) alphabetically
        // Then Bob (7000) before Diana (7000) alphabetically
        String[] expectedNames = {"Alice", "Charlie", "Bob", "Diana"};
        for (int i = 0; i < expectedNames.length; i++) {
            String actualName = ((String) rows.get(i)[0].value).trim();
            assert expectedNames[i].equals(actualName) :
                    "Expected " + expectedNames[i] + " at index " + i + ", got " + actualName;
        }
        System.out.println("  ORDER BY multi-column PASSED");
        return true;
    }

    static boolean testOrderByWithGroupBy() throws Exception {
        DBManager db = buildDbManager();
        String t = "emp_ord4";
        execute(db, "CREATE TABLE " + t + " (id int, name char, salary int, dept char)");
        execute(db, "INSERT INTO " + t + " (id, name, salary, dept) VALUES (1, 'Alice', 5000, 'IT')");
        execute(db, "INSERT INTO " + t + " (id, name, salary, dept) VALUES (2, 'Bob', 7000, 'HR')");
        execute(db, "INSERT INTO " + t + " (id, name, salary, dept) VALUES (3, 'Charlie', 6000, 'IT')");

        List<Value[]> rows = query(db,
                "SELECT dept, MAX(salary) FROM " + t + " GROUP BY dept ORDER BY dept DESC");
        assert rows.size() == 2 : "Expected 2 rows, got " + rows.size();

        for (Value[] row : rows) {
            System.out.println("  dept=" + row[0].value + " MAX=" + row[1].value);
        }
        // DESC: IT should be first, HR second
        assert ((String) rows.get(0)[0].value).trim().equals("IT") : "DESC: IT should be first";
        assert ((String) rows.get(1)[0].value).trim().equals("HR") : "DESC: HR should be second";
        System.out.println("  ORDER BY with GROUP BY PASSED");
        return true;
    }

    // Helper methods
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
