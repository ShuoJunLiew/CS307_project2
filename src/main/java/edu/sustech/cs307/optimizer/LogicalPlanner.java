package edu.sustech.cs307.optimizer;

import java.io.StringReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.sustech.cs307.logicalOperator.ddl.*;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserManager;
import net.sf.jsqlparser.parser.JSqlParser;
import net.sf.jsqlparser.statement.Commit;
import net.sf.jsqlparser.statement.ExplainStatement;
import net.sf.jsqlparser.statement.ShowStatement;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.*;
import net.sf.jsqlparser.statement.update.Update;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.create.table.CreateTable;

import edu.sustech.cs307.exception.ExceptionTypes;
import edu.sustech.cs307.logicalOperator.*;
import edu.sustech.cs307.system.DBManager;
import edu.sustech.cs307.exception.DBException;

public class LogicalPlanner {
    private static final Pattern BEGIN_PATTERN = Pattern.compile("(?i)^BEGIN(?:\\s+(?:WORK|TRANSACTION))?$");
    private static final Pattern START_TRANSACTION_PATTERN = Pattern.compile("(?i)^START\\s+TRANSACTION$");
    private static final Pattern RELEASE_SAVEPOINT_PATTERN =
            Pattern.compile("(?i)^RELEASE(?:\\s+SAVEPOINT)?\\s+([A-Za-z_][A-Za-z0-9_]*)$");

    public static LogicalOperator resolveAndPlan(DBManager dbManager, String sql) throws DBException {
        if (sql == null || sql.isBlank()) {
            return null;
        }
        if (handleManualTransactionCommand(dbManager, sql)) {
            return null;
        }

        /// //////
        String normalized = normalizeSql(sql);
        if (normalized.equalsIgnoreCase("SHOW TABLES")) {
            // Create a dummy or empty ShowStatement if needed, or pass null since we match manually
            net.sf.jsqlparser.statement.ShowStatement dummyStmt = new net.sf.jsqlparser.statement.ShowStatement();
            dummyStmt.setName("TABLES");

            ShowDatabaseExecutor showDatabaseExecutor = new ShowDatabaseExecutor(dummyStmt, dbManager.getMetaManager());
            showDatabaseExecutor.execute();
            return null;
        }
        if (normalized.toLowerCase().startsWith("describe ")) {
            // Extract everything after the word "describe " as the table name target
            String targetTable = normalized.substring(9).trim();

            // Instantiate and run our new executor class
            DescribeTableExecutor describeExecutor = new DescribeTableExecutor(targetTable, dbManager.getMetaManager());
            describeExecutor.execute();
            return null;
        }
        if (normalized.toLowerCase().startsWith("drop table ")) {
            // Extract the table name text following the space
            String targetTable = normalized.substring(11).trim();

            // Instantiate and run our new Drop executor
            DropTableExecutor dropExecutor = new DropTableExecutor(
                    targetTable,
                    dbManager.getMetaManager(),
                    dbManager.getMetaManager().getTableNames().isEmpty() ? "CS307-DB" : "CS307-DB"
            );
            // Note: double check if dbManager has a direct getter for ROOT_DIR,
            // if not, "CS307-DB" is your default relative root path string.

            dropExecutor.execute();
            return null;
        }
        /// //////
        JSqlParser parser = new CCJSqlParserManager();
        Statement stmt = null;
        try {
            stmt = parser.parse(new StringReader(sql));
        } catch (JSQLParserException e) {
            throw new DBException(ExceptionTypes.InvalidSQL(sql, e.getMessage()));
        }
        LogicalOperator operator = null;
        // Query
        if (stmt instanceof Select selectStmt) {
            operator = handleSelect(dbManager, selectStmt);
        } else if (stmt instanceof Insert insertStmt) {
            operator = handleInsert(dbManager, insertStmt);
        } else if (stmt instanceof Update updateStmt) {
            operator = handleUpdate(dbManager, updateStmt);
        }else if (stmt instanceof Commit) {
            dbManager.commitTransaction();
            return null;
        }
        //todo: add condition of handleDelete
        // functional
        // Handle physical data deletions via our new DML logic
        else if (stmt instanceof net.sf.jsqlparser.statement.delete.Delete deleteStmt) {
            operator = handleDelete(dbManager, deleteStmt);
        }
        else if (stmt instanceof CreateTable createTableStmt) {
            CreateTableExecutor createTable = new CreateTableExecutor(createTableStmt, dbManager, sql);
            createTable.execute();
            return null;
        } else if (stmt instanceof ExplainStatement explainStatement) {
            ExplainExecutor explainExecutor = new ExplainExecutor(explainStatement, dbManager);
            explainExecutor.execute();
            return null;
        } else if (stmt instanceof ShowStatement showStatement) {
            ShowDatabaseExecutor showDatabaseExecutor = new ShowDatabaseExecutor(showStatement,dbManager.getMetaManager());
            showDatabaseExecutor.execute();
            return null;
        } else {
            throw new DBException(ExceptionTypes.UnsupportedCommand((stmt.toString())));
        }
        return operator;
    }

    // handle delete method
    private static LogicalOperator handleDelete(DBManager dbManager, net.sf.jsqlparser.statement.delete.Delete deleteStmt) throws DBException {
        String tableName = deleteStmt.getTable().getName();

        // 1. Start with a baseline scan operator to traverse the physical file pages
        LogicalOperator root = new LogicalTableScanOperator(tableName, dbManager);

        // 2. If a WHERE predicate clause exists, interject a Filter operator into the tree stream
        if (deleteStmt.getWhere() != null) {
            root = new LogicalFilterOperator(root, deleteStmt.getWhere());
        }

        // 3. Return the mutation wrapper to pass to the physical execution engine planner
        return new LogicalDeleteOperator(tableName, root);
    }


    public static LogicalOperator handleSelect(DBManager dbManager, Select selectStmt) throws DBException {
        PlainSelect plainSelect = selectStmt.getPlainSelect();
        if (plainSelect.getFromItem() == null) {
            throw new DBException(ExceptionTypes.UnsupportedCommand((plainSelect.toString())));
        }
        LogicalOperator root = new LogicalTableScanOperator(plainSelect.getFromItem().toString(), dbManager);

        int depth = 0;
        if (plainSelect.getJoins() != null) {
            for (Join join : plainSelect.getJoins()) {
                root = new LogicalJoinOperator(
                        root,
                        new LogicalTableScanOperator(join.getRightItem().toString(), dbManager),
                        join.getOnExpressions(),
                        depth);
                depth += 1;
            }
        }

        // Apply filtering after joins
        if (plainSelect.getWhere() != null) {
            root = new LogicalFilterOperator(root, plainSelect.getWhere());
        }
        /// //////
        // --- AGGREGATION DETECTOR INTERCEPTOR ---
        if (plainSelect.getSelectItems() != null && !plainSelect.getSelectItems().isEmpty()) {
            var firstItem = plainSelect.getSelectItems().get(0).getExpression();
            if (firstItem instanceof net.sf.jsqlparser.expression.Function function) {
                if (function.getName().equalsIgnoreCase("COUNT")) {
                    // It's a COUNT query! Wrap our plan in a LogicalAggregationOperator container
                    return new LogicalAggregationOperator(root, function.toString());
                }
            }
        }
        /// //////

        // Baseline Projection for standard columns
        root = new LogicalProjectOperator(root, plainSelect.getSelectItems());
        return root;
    }

    private static LogicalOperator handleInsert(DBManager dbManager, Insert insertStmt) {
        return new LogicalInsertOperator(insertStmt.getTable().getName(), insertStmt.getColumns(),
                insertStmt.getValues());
    }

    private static LogicalOperator handleUpdate(DBManager dbManager, Update updateStmt) throws DBException {
        LogicalOperator root = new LogicalTableScanOperator(updateStmt.getTable().getName(), dbManager);
        return new LogicalUpdateOperator(root, updateStmt.getTable().getName(), updateStmt.getUpdateSets(),
                updateStmt.getWhere());
    }
    private static String normalizeSql(String sql) {
        String normalizedSql = sql == null ? "" : sql.trim();
        while (normalizedSql.endsWith(";")) {
            normalizedSql = normalizedSql.substring(0, normalizedSql.length() - 1).trim();
        }
        return normalizedSql;
    }

    private static boolean handleManualTransactionCommand(DBManager dbManager, String sql) throws DBException {
        String normalizedSql = normalizeSql(sql);
        if (BEGIN_PATTERN.matcher(normalizedSql).matches() || START_TRANSACTION_PATTERN.matcher(normalizedSql).matches()) {
            dbManager.beginTransaction();
            return true;
        }
        return false;
    }


}
