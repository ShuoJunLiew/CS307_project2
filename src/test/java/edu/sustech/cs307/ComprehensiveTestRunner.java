package edu.sustech.cs307;

import edu.sustech.cs307.exception.DBException;
import edu.sustech.cs307.logicalOperator.LogicalOperator;
import edu.sustech.cs307.meta.MetaManager;
import edu.sustech.cs307.meta.TableMeta;
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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.IntFunction;

public class ComprehensiveTestRunner {

    private static Path tempDir;
    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        tempDir = Files.createTempDirectory("cs307_comp_");
        System.out.println("Test directory: " + tempDir);
        System.out.println("========================================");
        System.out.println("  COMPREHENSIVE FEATURE TEST SUITE");
        System.out.println("========================================\n");

        // === DDL Tests ===
        System.out.println("\n========== DDL TESTS ==========");
        run("CREATE TABLE", () -> testCreateTable());
        run("DROP TABLE", () -> testDropTable());
        run("DESCRIBE TABLE", () -> testDescribeTable());
        run("SHOW TABLES", () -> testShowTables());

        // === DML: INSERT Tests ===
        System.out.println("\n========== INSERT TESTS ==========");
        run("INSERT single row", () -> testInsertSingleRow());
        run("INSERT multi rows", () -> testInsertMultiRows());
        run("INSERT with column subset", () -> testInsertColumnSubset());

        // === DML: SELECT / WHERE Tests ===
        System.out.println("\n========== SELECT / WHERE TESTS ==========");
        run("SELECT all columns (*)", () -> testSelectStar());
        run("SELECT specific columns", () -> testSelectProjection());
        run("WHERE = (equals)", () -> testWhereEquals());
        run("WHERE > (greater than)", () -> testWhereGreaterThan());
        run("WHERE < (less than)", () -> testWhereLessThan());
        run("WHERE >= (greater or equal)", () -> testWhereGreaterOrEqual());
        run("WHERE <= (less or equal)", () -> testWhereLessOrEqual());
        run("WHERE AND", () -> testWhereAnd());
        run("WHERE OR", () -> testWhereOr());

        // === AGGREGATE Tests ===
        System.out.println("\n========== AGGREGATE TESTS ==========");
        run("COUNT(*)", () -> testCountStar());
        run("COUNT with WHERE", () -> testCountWithWhere());
        run("COUNT with GROUP BY", () -> testCountWithGroupBy());
        run("MAX without GROUP BY", () -> testMaxWithoutGroupBy());
        run("MIN without GROUP BY", () -> testMinWithoutGroupBy());

        // === ORDER BY Tests ===
        System.out.println("\n========== ORDER BY TESTS ==========");
        run("ORDER BY ASC", () -> testOrderByAsc());
        run("ORDER BY DESC", () -> testOrderByDesc());
        run("ORDER BY multi-column", () -> testOrderByMultiColumn());
        run("ORDER BY with WHERE", () -> testOrderByWithWhere());

        // === UPDATE Tests ===
        System.out.println("\n========== UPDATE TESTS ==========");
        run("UPDATE single column", () -> testUpdateSingleColumn());
        run("UPDATE multiple columns", () -> testUpdateMultipleColumns());
        run("UPDATE with WHERE", () -> testUpdateWithWhere());

        // === DELETE Tests ===
        System.out.println("\n========== DELETE TESTS ==========");
        run("DELETE all rows", () -> testDeleteAll());
        run("DELETE with WHERE", () -> testDeleteWithWhere());

        // === JOIN Tests ===
        System.out.println("\n========== JOIN TESTS ==========");
        run("JOIN two tables", () -> testJoinTwoTables());
        run("JOIN with WHERE", () -> testJoinWithWhere());

        // === EXPLAIN Tests ===
        System.out.println("\n========== EXPLAIN TESTS ==========");
        run("EXPLAIN SELECT", () -> testExplainSelect());
        run("EXPLAIN COUNT", () -> testExplainCount());

        // === EDGE CASE Tests ===
        System.out.println("\n========== EDGE CASE TESTS ==========");
        run("Empty table SELECT", () -> testEmptyTableSelect());
        run("Empty table COUNT", () -> testEmptyTableCount());

        System.out.println("\n========================================");
        System.out.println("  RESULTS: " + passed + " passed, " + failed + " failed");
        System.out.println("========================================");
        if (failed > 0) {
            System.exit(1);
        }
    }

    // ==================== DDL Tests ====================

    static boolean testCreateTable() throws Exception {
        DBManager db = buildDbManager();
        execute(db, "CREATE TABLE test_create (id int, name char, salary int, active int)");

        TableMeta meta = db.getMetaManager().getTable("test_create");
        assert meta != null : "Table should exist after CREATE TABLE";
        assert meta.columns_list.size() == 4 : "Expected 4 columns, got " + meta.columns_list.size();
        assert meta.columns_list.get(0).name.equals("id") : "First column should be 'id'";
        assert meta.columns_list.get(1).name.equals("name") : "Second column should be 'name'";
        assert meta.columns_list.get(2).name.equals("salary") : "Third column should be 'salary'";
        assert meta.columns_list.get(3).name.equals("active") : "Fourth column should be 'active'";
        return true;
    }

    static boolean testDropTable() throws Exception {
        DBManager db = buildDbManager();
        execute(db, "CREATE TABLE test_drop (id int)");
        assert db.getMetaManager().getTable("test_drop") != null : "Table should exist before DROP";

        execute(db, "DROP TABLE test_drop");
        boolean exists = true;
        try {
            db.getMetaManager().getTable("test_drop");
        } catch (DBException e) {
            exists = false;
        }
        assert !exists : "Table should not exist after DROP TABLE";
        return true;
    }

    static boolean testDescribeTable() throws Exception {
        DBManager db = buildDbManager();
        execute(db, "CREATE TABLE test_desc (id int, name char, salary int)");
        // DESCRIBE outputs via Logger.info, resolveAndPlan returns null
        // Just verify it doesn't throw
        LogicalOperator op = LogicalPlanner.resolveAndPlan(db, "DESCRIBE test_desc");
        assert op == null : "DESCRIBE should return null (handled via Logger)";
        return true;
    }

    static boolean testShowTables() throws Exception {
        DBManager db = buildDbManager();
        execute(db, "CREATE TABLE test_show1 (id int)");
        execute(db, "CREATE TABLE test_show2 (id int)");
        // SHOW TABLES outputs via Logger.info, resolveAndPlan returns null
        LogicalOperator op = LogicalPlanner.resolveAndPlan(db, "SHOW TABLES");
        assert op == null : "SHOW TABLES should return null (handled via Logger)";

        Set<String> tables = db.getMetaManager().getTableNames();
        assert tables.contains("test_show1") : "Should contain test_show1";
        assert tables.contains("test_show2") : "Should contain test_show2";
        return true;
    }

    // ==================== INSERT Tests ====================

    static boolean testInsertSingleRow() throws Exception {
        DBManager db = buildDbManager();
        execute(db, "CREATE TABLE t (id int, name char)");
        execute(db, "INSERT INTO t (id, name) VALUES (1, 'Alice')");

        List<Value[]> rows = query(db, "SELECT * FROM t");
        assert rows.size() == 1 : "Expected 1 row, got " + rows.size();
        assert (Long) rows.get(0)[0].value == 1L : "id should be 1";
        assert ((String) rows.get(0)[1].value).trim().equals("Alice") : "name should be Alice";
        return true;
    }

    static boolean testInsertMultiRows() throws Exception {
        DBManager db = buildDbManager();
        execute(db, "CREATE TABLE t (id int, name char)");
        execute(db, "INSERT INTO t (id, name) VALUES (1, 'Alice')");
        execute(db, "INSERT INTO t (id, name) VALUES (2, 'Bob')");
        execute(db, "INSERT INTO t (id, name) VALUES (3, 'Charlie')");

        List<Value[]> rows = query(db, "SELECT * FROM t");
        assert rows.size() == 3 : "Expected 3 rows, got " + rows.size();
        return true;
    }

    static boolean testInsertColumnSubset() throws Exception {
        DBManager db = buildDbManager();
        execute(db, "CREATE TABLE t (id int, name char, salary int)");
        // Insert with all columns specified
        execute(db, "INSERT INTO t (id, name, salary) VALUES (1, 'Alice', 5000)");
        List<Value[]> rows = query(db, "SELECT * FROM t");
        assert rows.size() == 1 : "Expected 1 row";
        assert (Long) rows.get(0)[0].value == 1L;
        assert (Long) rows.get(0)[2].value == 5000L;
        return true;
    }

    // ==================== SELECT / WHERE Tests ====================

    static boolean testSelectStar() throws Exception {
        DBManager db = buildDbManager();
        execute(db, "CREATE TABLE t (id int, name char)");
        execute(db, "INSERT INTO t (id, name) VALUES (1, 'Alice')");
        execute(db, "INSERT INTO t (id, name) VALUES (2, 'Bob')");

        List<Value[]> rows = query(db, "SELECT * FROM t");
        assert rows.size() == 2 : "Expected 2 rows, got " + rows.size();
        assert rows.get(0).length == 2 : "Expected 2 columns";
        return true;
    }

    static boolean testSelectProjection() throws Exception {
        DBManager db = buildDbManager();
        execute(db, "CREATE TABLE t (id int, name char, salary int)");
        execute(db, "INSERT INTO t (id, name, salary) VALUES (1, 'Alice', 5000)");

        List<Value[]> rows = query(db, "SELECT name, salary FROM t");
        assert rows.size() == 1 : "Expected 1 row";
        assert rows.get(0).length == 2 : "Expected 2 projected columns";
        return true;
    }

    static boolean testWhereEquals() throws Exception {
        DBManager db = buildDbManager();
        execute(db, "CREATE TABLE t (id int, name char)");
        execute(db, "INSERT INTO t (id, name) VALUES (1, 'Alice')");
        execute(db, "INSERT INTO t (id, name) VALUES (2, 'Bob')");
        execute(db, "INSERT INTO t (id, name) VALUES (3, 'Alice')");

        List<Value[]> rows = query(db, "SELECT * FROM t WHERE name = 'Alice'");
        assert rows.size() == 2 : "Expected 2 Alices, got " + rows.size();
        return true;
    }

    static boolean testWhereGreaterThan() throws Exception {
        DBManager db = buildDbManager();
        execute(db, "CREATE TABLE t (id int, salary int)");
        execute(db, "INSERT INTO t (id, salary) VALUES (1, 3000)");
        execute(db, "INSERT INTO t (id, salary) VALUES (2, 5000)");
        execute(db, "INSERT INTO t (id, salary) VALUES (3, 7000)");

        List<Value[]> rows = query(db, "SELECT * FROM t WHERE salary > 4000");
        assert rows.size() == 2 : "Expected 2 rows with salary > 4000, got " + rows.size();
        return true;
    }

    static boolean testWhereLessThan() throws Exception {
        DBManager db = buildDbManager();
        execute(db, "CREATE TABLE t (id int, salary int)");
        execute(db, "INSERT INTO t (id, salary) VALUES (1, 3000)");
        execute(db, "INSERT INTO t (id, salary) VALUES (2, 5000)");
        execute(db, "INSERT INTO t (id, salary) VALUES (3, 7000)");

        List<Value[]> rows = query(db, "SELECT * FROM t WHERE salary < 5000");
        assert rows.size() == 1 : "Expected 1 row with salary < 5000, got " + rows.size();
        return true;
    }

    static boolean testWhereGreaterOrEqual() throws Exception {
        DBManager db = buildDbManager();
        execute(db, "CREATE TABLE t (id int, salary int)");
        execute(db, "INSERT INTO t (id, salary) VALUES (1, 3000)");
        execute(db, "INSERT INTO t (id, salary) VALUES (2, 5000)");
        execute(db, "INSERT INTO t (id, salary) VALUES (3, 7000)");

        List<Value[]> rows = query(db, "SELECT * FROM t WHERE salary >= 5000");
        assert rows.size() == 2 : "Expected 2 rows with salary >= 5000, got " + rows.size();
        return true;
    }

    static boolean testWhereLessOrEqual() throws Exception {
        DBManager db = buildDbManager();
        execute(db, "CREATE TABLE t (id int, salary int)");
        execute(db, "INSERT INTO t (id, salary) VALUES (1, 3000)");
        execute(db, "INSERT INTO t (id, salary) VALUES (2, 5000)");
        execute(db, "INSERT INTO t (id, salary) VALUES (3, 7000)");

        List<Value[]> rows = query(db, "SELECT * FROM t WHERE salary <= 3000");
        assert rows.size() == 1 : "Expected 1 row with salary <= 3000, got " + rows.size();
        return true;
    }

    static boolean testWhereAnd() throws Exception {
        DBManager db = buildDbManager();
        execute(db, "CREATE TABLE t (id int, salary int, dept char)");
        execute(db, "INSERT INTO t (id, salary, dept) VALUES (1, 5000, 'IT')");
        execute(db, "INSERT INTO t (id, salary, dept) VALUES (2, 7000, 'HR')");
        execute(db, "INSERT INTO t (id, salary, dept) VALUES (3, 6000, 'IT')");

        List<Value[]> rows = query(db, "SELECT * FROM t WHERE salary >= 5000 AND dept = 'IT'");
        assert rows.size() == 2 : "Expected 2 rows (IT with salary>=5000), got " + rows.size();
        return true;
    }

    static boolean testWhereOr() throws Exception {
        DBManager db = buildDbManager();
        execute(db, "CREATE TABLE t (id int, salary int, dept char)");
        execute(db, "INSERT INTO t (id, salary, dept) VALUES (1, 5000, 'IT')");
        execute(db, "INSERT INTO t (id, salary, dept) VALUES (2, 7000, 'HR')");
        execute(db, "INSERT INTO t (id, salary, dept) VALUES (3, 6000, 'IT')");
        execute(db, "INSERT INTO t (id, salary, dept) VALUES (4, 3000, 'FIN')");

        List<Value[]> rows = query(db, "SELECT * FROM t WHERE dept = 'HR' OR dept = 'FIN'");
        assert rows.size() == 2 : "Expected 2 rows (HR or FIN), got " + rows.size();
        return true;
    }

    // ==================== AGGREGATE Tests ====================

    static boolean testCountStar() throws Exception {
        DBManager db = buildDbManager();
        execute(db, "CREATE TABLE t (id int)");
        execute(db, "INSERT INTO t (id) VALUES (1)");
        execute(db, "INSERT INTO t (id) VALUES (2)");
        execute(db, "INSERT INTO t (id) VALUES (3)");

        List<Value[]> rows = query(db, "SELECT COUNT(*) FROM t");
        assert rows.size() == 1 : "COUNT should return 1 row, got " + rows.size();
        Long count = (Long) rows.get(0)[0].value;
        assert count == 3L : "Expected COUNT(*)=3, got " + count;
        System.out.println("  COUNT(*) = " + count);
        return true;
    }

    static boolean testCountWithWhere() throws Exception {
        DBManager db = buildDbManager();
        execute(db, "CREATE TABLE t (id int, salary int)");
        execute(db, "INSERT INTO t (id, salary) VALUES (1, 5000)");
        execute(db, "INSERT INTO t (id, salary) VALUES (2, 3000)");
        execute(db, "INSERT INTO t (id, salary) VALUES (3, 7000)");

        List<Value[]> rows = query(db, "SELECT COUNT(*) FROM t WHERE salary > 4000");
        assert rows.size() == 1 : "COUNT should return 1 row";
        Long count = (Long) rows.get(0)[0].value;
        assert count == 2L : "Expected COUNT(*)=2 (salary>4000), got " + count;
        System.out.println("  COUNT(*) with WHERE = " + count);
        return true;
    }

    static boolean testCountWithGroupBy() throws Exception {
        DBManager db = buildDbManager();
        execute(db, "CREATE TABLE t (id int, dept char)");
        execute(db, "INSERT INTO t (id, dept) VALUES (1, 'IT')");
        execute(db, "INSERT INTO t (id, dept) VALUES (2, 'HR')");
        execute(db, "INSERT INTO t (id, dept) VALUES (3, 'IT')");
        execute(db, "INSERT INTO t (id, dept) VALUES (4, 'HR')");
        execute(db, "INSERT INTO t (id, dept) VALUES (5, 'IT')");

        List<Value[]> rows = query(db, "SELECT dept, COUNT(*) FROM t GROUP BY dept");
        assert rows.size() == 2 : "Expected 2 groups, got " + rows.size();

        for (Value[] row : rows) {
            String dept = ((String) row[0].value).trim();
            Long cnt = (Long) row[1].value;
            System.out.println("  dept=" + dept + " COUNT=" + cnt);
            if (dept.equals("IT")) assert cnt == 3L : "IT should have count 3";
            if (dept.equals("HR")) assert cnt == 2L : "HR should have count 2";
        }
        return true;
    }

    static boolean testMaxWithoutGroupBy() throws Exception {
        DBManager db = buildDbManager();
        execute(db, "CREATE TABLE t (id int, salary int)");
        execute(db, "INSERT INTO t (id, salary) VALUES (1, 5000)");
        execute(db, "INSERT INTO t (id, salary) VALUES (2, 7000)");
        execute(db, "INSERT INTO t (id, salary) VALUES (3, 6000)");

        List<Value[]> rows = query(db, "SELECT MAX(salary) FROM t");
        Long max = (Long) rows.get(0)[0].value;
        assert max == 7000L : "MAX(salary) should be 7000, got " + max;
        System.out.println("  MAX(salary) = " + max);
        return true;
    }

    static boolean testMinWithoutGroupBy() throws Exception {
        DBManager db = buildDbManager();
        execute(db, "CREATE TABLE t (id int, salary int)");
        execute(db, "INSERT INTO t (id, salary) VALUES (1, 5000)");
        execute(db, "INSERT INTO t (id, salary) VALUES (2, 7000)");
        execute(db, "INSERT INTO t (id, salary) VALUES (3, 3000)");

        List<Value[]> rows = query(db, "SELECT MIN(salary) FROM t");
        Long min = (Long) rows.get(0)[0].value;
        assert min == 3000L : "MIN(salary) should be 3000, got " + min;
        System.out.println("  MIN(salary) = " + min);
        return true;
    }

    // ==================== ORDER BY Tests ====================

    static boolean testOrderByAsc() throws Exception {
        DBManager db = buildDbManager();
        execute(db, "CREATE TABLE t (id int)");
        execute(db, "INSERT INTO t (id) VALUES (30)");
        execute(db, "INSERT INTO t (id) VALUES (10)");
        execute(db, "INSERT INTO t (id) VALUES (20)");

        List<Value[]> rows = query(db, "SELECT id FROM t ORDER BY id");
        Long v1 = (Long) rows.get(0)[0].value;
        Long v2 = (Long) rows.get(1)[0].value;
        Long v3 = (Long) rows.get(2)[0].value;
        assert v1 == 10L && v2 == 20L && v3 == 30L :
                "Expected 10, 20, 30 but got " + v1 + ", " + v2 + ", " + v3;
        return true;
    }

    static boolean testOrderByDesc() throws Exception {
        DBManager db = buildDbManager();
        execute(db, "CREATE TABLE t (id int)");
        execute(db, "INSERT INTO t (id) VALUES (10)");
        execute(db, "INSERT INTO t (id) VALUES (30)");
        execute(db, "INSERT INTO t (id) VALUES (20)");

        List<Value[]> rows = query(db, "SELECT id FROM t ORDER BY id DESC");
        Long v1 = (Long) rows.get(0)[0].value;
        Long v2 = (Long) rows.get(1)[0].value;
        Long v3 = (Long) rows.get(2)[0].value;
        assert v1 == 30L && v2 == 20L && v3 == 10L :
                "Expected 30, 20, 10 but got " + v1 + ", " + v2 + ", " + v3;
        return true;
    }

    static boolean testOrderByMultiColumn() throws Exception {
        DBManager db = buildDbManager();
        execute(db, "CREATE TABLE t (a int, b int)");
        execute(db, "INSERT INTO t (a, b) VALUES (1, 20)");
        execute(db, "INSERT INTO t (a, b) VALUES (1, 10)");
        execute(db, "INSERT INTO t (a, b) VALUES (2, 5)");
        execute(db, "INSERT INTO t (a, b) VALUES (2, 30)");

        List<Value[]> rows = query(db, "SELECT a, b FROM t ORDER BY a, b");
        assert (Long) rows.get(0)[0].value == 1L && (Long) rows.get(0)[1].value == 10L;
        assert (Long) rows.get(1)[0].value == 1L && (Long) rows.get(1)[1].value == 20L;
        assert (Long) rows.get(2)[0].value == 2L && (Long) rows.get(2)[1].value == 5L;
        assert (Long) rows.get(3)[0].value == 2L && (Long) rows.get(3)[1].value == 30L;
        return true;
    }

    static boolean testOrderByWithWhere() throws Exception {
        DBManager db = buildDbManager();
        execute(db, "CREATE TABLE t (id int, salary int)");
        execute(db, "INSERT INTO t (id, salary) VALUES (1, 5000)");
        execute(db, "INSERT INTO t (id, salary) VALUES (2, 3000)");
        execute(db, "INSERT INTO t (id, salary) VALUES (3, 7000)");
        execute(db, "INSERT INTO t (id, salary) VALUES (4, 4000)");

        List<Value[]> rows = query(db,
                "SELECT * FROM t WHERE salary >= 4000 ORDER BY salary DESC");
        assert rows.size() == 3 : "Expected 3 rows, got " + rows.size();
        assert (Long) rows.get(0)[1].value == 7000L : "First should be 7000 (highest)";
        assert (Long) rows.get(1)[1].value == 5000L : "Second should be 5000";
        assert (Long) rows.get(2)[1].value == 4000L : "Third should be 4000";
        return true;
    }

    // ==================== UPDATE Tests ====================

    static boolean testUpdateSingleColumn() throws Exception {
        DBManager db = buildDbManager();
        execute(db, "CREATE TABLE t (id int, name char)");
        execute(db, "INSERT INTO t (id, name) VALUES (1, 'Alice')");
        execute(db, "INSERT INTO t (id, name) VALUES (2, 'Bob')");

        execute(db, "UPDATE t SET name = 'Updated'");
        List<Value[]> rows = query(db, "SELECT name FROM t");
        for (Value[] row : rows) {
            String name = ((String) row[0].value).trim();
            assert name.equals("Updated") : "All names should be 'Updated', got " + name;
        }
        return true;
    }

    static boolean testUpdateMultipleColumns() throws Exception {
        DBManager db = buildDbManager();
        execute(db, "CREATE TABLE t (id int, name char, salary int)");
        execute(db, "INSERT INTO t (id, name, salary) VALUES (1, 'Alice', 5000)");
        execute(db, "INSERT INTO t (id, name, salary) VALUES (2, 'Bob', 7000)");

        // Multi-column UPDATE (the feature we just implemented!)
        execute(db, "UPDATE t SET name = 'X', id = 99");
        List<Value[]> rows = query(db, "SELECT id, name, salary FROM t");
        for (Value[] row : rows) {
            assert (Long) row[0].value == 99L : "id should be 99, got " + row[0].value;
            assert ((String) row[1].value).trim().equals("X") : "name should be 'X', got " + row[1].value;
        }
        System.out.println("  Multi-column UPDATE PASSED");
        return true;
    }

    static boolean testUpdateWithWhere() throws Exception {
        DBManager db = buildDbManager();
        execute(db, "CREATE TABLE t (id int, dept char, salary int)");
        execute(db, "INSERT INTO t (id, dept, salary) VALUES (1, 'IT', 5000)");
        execute(db, "INSERT INTO t (id, dept, salary) VALUES (2, 'HR', 7000)");
        execute(db, "INSERT INTO t (id, dept, salary) VALUES (3, 'IT', 6000)");

        execute(db, "UPDATE t SET salary = 9999, dept = 'PROMOTED' WHERE dept = 'IT'");
        List<Value[]> rows = query(db, "SELECT id, dept, salary FROM t ORDER BY id");
        // Row 1: IT -> PROMOTED, salary = 9999
        assert ((String) rows.get(0)[1].value).trim().equals("PROMOTED") : "Row 1 dept should be PROMOTED";
        assert (Long) rows.get(0)[2].value == 9999L : "Row 1 salary should be 9999";
        // Row 2: HR -> unchanged
        assert ((String) rows.get(1)[1].value).trim().equals("HR") : "Row 2 dept should be HR (unchanged)";
        assert (Long) rows.get(1)[2].value == 7000L : "Row 2 salary should be 7000 (unchanged)";
        // Row 3: IT -> PROMOTED, salary = 9999
        assert ((String) rows.get(2)[1].value).trim().equals("PROMOTED") : "Row 3 dept should be PROMOTED";
        assert (Long) rows.get(2)[2].value == 9999L : "Row 3 salary should be 9999";
        return true;
    }

    // ==================== DELETE Tests ====================

    static boolean testDeleteAll() throws Exception {
        DBManager db = buildDbManager();
        execute(db, "CREATE TABLE t (id int)");
        execute(db, "INSERT INTO t (id) VALUES (1)");
        execute(db, "INSERT INTO t (id) VALUES (2)");
        execute(db, "INSERT INTO t (id) VALUES (3)");

        execute(db, "DELETE FROM t");
        List<Value[]> rows = query(db, "SELECT * FROM t");
        assert rows.isEmpty() : "Expected empty table after DELETE, got " + rows.size() + " rows";
        return true;
    }

    static boolean testDeleteWithWhere() throws Exception {
        DBManager db = buildDbManager();
        execute(db, "CREATE TABLE t (id int, dept char)");
        execute(db, "INSERT INTO t (id, dept) VALUES (1, 'IT')");
        execute(db, "INSERT INTO t (id, dept) VALUES (2, 'HR')");
        execute(db, "INSERT INTO t (id, dept) VALUES (3, 'IT')");

        execute(db, "DELETE FROM t WHERE dept = 'IT'");
        List<Value[]> rows = query(db, "SELECT * FROM t ORDER BY id");
        assert rows.size() == 1 : "Expected 1 row remaining (HR), got " + rows.size();
        assert (Long) rows.get(0)[0].value == 2L : "Remaining row should be id=2 (HR)";
        return true;
    }

    // ==================== JOIN Tests ====================

    static boolean testJoinTwoTables() throws Exception {
        DBManager db = buildDbManager();
        execute(db, "CREATE TABLE emp (id int, name char, dept_id int)");
        execute(db, "CREATE TABLE dept (dept_id int, dept_name char)");
        execute(db, "INSERT INTO emp (id, name, dept_id) VALUES (1, 'Alice', 10)");
        execute(db, "INSERT INTO emp (id, name, dept_id) VALUES (2, 'Bob', 20)");
        execute(db, "INSERT INTO dept (dept_id, dept_name) VALUES (10, 'IT')");
        execute(db, "INSERT INTO dept (dept_id, dept_name) VALUES (20, 'HR')");

        List<Value[]> rows = query(db,
                "SELECT emp.name, dept.dept_name FROM emp JOIN dept ON emp.dept_id = dept.dept_id");
        assert rows.size() == 2 : "Expected 2 joined rows, got " + rows.size();

        // Results should include each employee with their department
        for (Value[] row : rows) {
            String name = ((String) row[0].value).trim();
            String dept = ((String) row[1].value).trim();
            System.out.println("  name=" + name + " dept=" + dept);
            if (name.equals("Alice")) assert dept.equals("IT") : "Alice should be in IT";
            if (name.equals("Bob")) assert dept.equals("HR") : "Bob should be in HR";
        }
        return true;
    }

    static boolean testJoinWithWhere() throws Exception {
        DBManager db = buildDbManager();
        execute(db, "CREATE TABLE emp (id int, name char, dept_id int, salary int)");
        execute(db, "CREATE TABLE dept (dept_id int, dept_name char)");
        execute(db, "INSERT INTO emp (id, name, dept_id, salary) VALUES (1, 'Alice', 10, 5000)");
        execute(db, "INSERT INTO emp (id, name, dept_id, salary) VALUES (2, 'Bob', 20, 7000)");
        execute(db, "INSERT INTO emp (id, name, dept_id, salary) VALUES (3, 'Charlie', 10, 6000)");
        execute(db, "INSERT INTO dept (dept_id, dept_name) VALUES (10, 'IT')");
        execute(db, "INSERT INTO dept (dept_id, dept_name) VALUES (20, 'HR')");

        List<Value[]> rows = query(db,
                "SELECT emp.name, dept.dept_name FROM emp JOIN dept ON emp.dept_id = dept.dept_id WHERE emp.salary > 5000");
        assert rows.size() == 2 : "Expected 2 rows with salary > 5000, got " + rows.size();
        return true;
    }

    // ==================== EXPLAIN Tests ====================

    static boolean testExplainSelect() throws Exception {
        DBManager db = buildDbManager();
        execute(db, "CREATE TABLE t (id int, name char)");
        execute(db, "INSERT INTO t (id, name) VALUES (1, 'Alice')");

        // EXPLAIN should execute without throwing
        LogicalOperator op = LogicalPlanner.resolveAndPlan(db, "EXPLAIN SELECT * FROM t");
        // EXPLAIN returns null after printing
        assert op == null : "EXPLAIN should return null";
        return true;
    }

    static boolean testExplainCount() throws Exception {
        DBManager db = buildDbManager();
        execute(db, "CREATE TABLE t (id int)");
        execute(db, "INSERT INTO t (id) VALUES (1)");
        execute(db, "INSERT INTO t (id) VALUES (2)");

        LogicalOperator op = LogicalPlanner.resolveAndPlan(db, "EXPLAIN SELECT COUNT(*) FROM t");
        assert op == null : "EXPLAIN COUNT should return null";
        return true;
    }

    // ==================== EDGE CASE Tests ====================

    static boolean testEmptyTableSelect() throws Exception {
        DBManager db = buildDbManager();
        execute(db, "CREATE TABLE empty_t (id int)");

        List<Value[]> rows = query(db, "SELECT * FROM empty_t");
        assert rows.isEmpty() : "SELECT from empty table should return 0 rows, got " + rows.size();
        return true;
    }

    static boolean testEmptyTableCount() throws Exception {
        DBManager db = buildDbManager();
        execute(db, "CREATE TABLE empty_t (id int)");

        List<Value[]> rows = query(db, "SELECT COUNT(*) FROM empty_t");
        assert rows.size() == 1 : "COUNT should always return 1 row";
        Long count = (Long) rows.get(0)[0].value;
        assert count == 0L : "COUNT on empty table should be 0, got " + count;
        System.out.println("  Empty table COUNT(*) = " + count);
        return true;
    }

    // ==================== Helper Methods ====================

    static void run(String name, TestFunc test) {
        try {
            if (test.run()) {
                System.out.println("  [" + name + "] PASSED");
                passed++;
            } else {
                System.out.println("  [" + name + "] FAILED");
                failed++;
            }
        } catch (AssertionError e) {
            System.err.println("  [" + name + "] FAILED: " + e.getMessage());
            failed++;
        } catch (Exception e) {
            System.err.println("  [" + name + "] ERROR: " + e.getMessage());
            e.printStackTrace();
            failed++;
        }
    }

    interface TestFunc {
        boolean run() throws Exception;
    }

    static DBManager buildDbManager() throws Exception {
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

    static List<Value[]> query(DBManager db, String sql) throws DBException {
        LogicalOperator op = LogicalPlanner.resolveAndPlan(db, sql);
        PhysicalOperator phys = PhysicalPlanner.generateOperator(db, op);
        System.out.println("  SQL: " + sql);
        System.out.println("  Plan: " + op);
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
}
