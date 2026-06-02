package edu.sustech.cs307.optimizer;

import java.io.StringReader;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.sustech.cs307.index.InMemoryOrderedIndex;
import edu.sustech.cs307.logicalOperator.ddl.*;
import edu.sustech.cs307.physicalOperator.SeqScanOperator;
import edu.sustech.cs307.tuple.Tuple;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
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
import edu.sustech.cs307.meta.TableMeta;
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
        } else if (stmt instanceof net.sf.jsqlparser.statement.create.index.CreateIndex createIndexStmt) {
            handleCreateIndex(dbManager, createIndexStmt);
            return null;
        } else if (stmt instanceof net.sf.jsqlparser.statement.drop.Drop dropStmt
                && "INDEX".equalsIgnoreCase(dropStmt.getType())) {
            handleDropIndex(dbManager, dropStmt);
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

    @SuppressWarnings("deprecation")
    private static void handleCreateIndex(DBManager dbManager,
                                           net.sf.jsqlparser.statement.create.index.CreateIndex stmt)
            throws DBException {
        String tableName = stmt.getTable().getName();
        net.sf.jsqlparser.statement.create.table.Index indexObj = stmt.getIndex();
        String indexName = indexObj.getName();
        java.util.List<String> columnNames = indexObj.getColumnsNames();
        if (columnNames == null || columnNames.isEmpty()) {
            throw new DBException(ExceptionTypes.InvalidSQL("CREATE INDEX", "No columns specified"));
        }
        String columnName = columnNames.get(0);

        TableMeta tableMeta = dbManager.getMetaManager().getTable(tableName);
        dbManager.getMetaManager().addIndex(tableName, columnName);

        // Build index data by scanning the table
        String indexDir = dbManager.getDiskManager().getCurrentDir() + "/" + tableName;
        String indexFile = indexDir + "/index_" + columnName + ".json";
        new java.io.File(indexDir).mkdirs();

        // Create comparator for TreeMap (Value doesn't implement Comparable)
        java.util.Comparator<edu.sustech.cs307.value.Value> comparator = (v1, v2) -> {
            try {
                return edu.sustech.cs307.value.ValueComparer.compare(v1, v2);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };

        InMemoryOrderedIndex index = new InMemoryOrderedIndex(indexFile);
        SeqScanOperator scanner = new SeqScanOperator(tableName, dbManager);
        scanner.Begin();
        try {
            java.lang.reflect.Field mapField = InMemoryOrderedIndex.class.getDeclaredField("indexMap");
            mapField.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.TreeMap<edu.sustech.cs307.value.Value, edu.sustech.cs307.record.RID> map =
                    (java.util.TreeMap<edu.sustech.cs307.value.Value, edu.sustech.cs307.record.RID>)
                            mapField.get(index);
            if (map == null) {
                map = new java.util.TreeMap<>(comparator);
                mapField.set(index, map);
            }
            while (scanner.hasNext()) {
                scanner.Next();
                Tuple tuple = scanner.Current();
                if (tuple != null) {
                    edu.sustech.cs307.value.Value key = tuple.getValue(
                            new edu.sustech.cs307.meta.TabCol(tableName, columnName));
                    edu.sustech.cs307.record.RID rid = ((edu.sustech.cs307.tuple.TableTuple) tuple).getRID();
                    if (key != null && rid != null) {
                        map.put(key, rid);
                    }
                }
            }
            // Persist to JSON
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.writeValue(new java.io.File(indexFile), map);
        } catch (Exception e) {
            throw new DBException(ExceptionTypes.BadIOError("Failed to build index: " + e.getMessage()));
        } finally {
            scanner.Close();
        }
    }

    @SuppressWarnings("deprecation")
    private static void handleDropIndex(DBManager dbManager,
                                         net.sf.jsqlparser.statement.drop.Drop dropStmt)
            throws DBException {
        String indexName = dropStmt.getName().getName();
        // Find which table and column this index belongs to
        for (String tableName : dbManager.getMetaManager().getTableNames()) {
            try {
                TableMeta tableMeta = dbManager.getMetaManager().getTable(tableName);
                if (tableMeta.getIndexes() != null) {
                    for (String colName : tableMeta.getIndexes().keySet()) {
                        String expectedIndexName = "idx_" + tableName + "_" + colName;
                        if (indexName.equalsIgnoreCase(expectedIndexName)) {
                            dbManager.getMetaManager().dropIndex(tableName, colName);
                            String indexFile = dbManager.getDiskManager().getCurrentDir()
                                    + "/" + tableName + "/index_" + colName + ".json";
                            new java.io.File(indexFile).delete();
                            return;
                        }
                    }
                }
            } catch (DBException ignored) {}
        }
        throw new DBException(ExceptionTypes.IndexDoesNotExist("*", indexName));
    }

    @SuppressWarnings("unchecked")
    private static java.util.TreeMap<edu.sustech.cs307.value.Value, edu.sustech.cs307.record.RID>
            getIndexMap(InMemoryOrderedIndex index) {
        try {
            java.lang.reflect.Field mapField = InMemoryOrderedIndex.class.getDeclaredField("indexMap");
            mapField.setAccessible(true);
            return (java.util.TreeMap<edu.sustech.cs307.value.Value, edu.sustech.cs307.record.RID>)
                    mapField.get(index);
        } catch (Exception e) {
            return null;
        }
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

        // 在 Join 之后应用 Filter，Filter 的输入是 Join 的结果 (root)
        if (plainSelect.getWhere() != null) {
            root = new LogicalFilterOperator(root, plainSelect.getWhere());
        }

        // Handle GROUP BY and aggregate functions (MAX, MIN)
        boolean hasGroupBy = plainSelect.getGroupBy() != null;
        boolean hasAggregates = hasAggregateFunctions(plainSelect.getSelectItems());

        if (hasGroupBy || hasAggregates) {
            List<Expression> groupByCols = hasGroupBy
                    ? plainSelect.getGroupBy().getGroupByExpressions()
                    : Collections.emptyList();
            root = new LogicalAggregateOperator(root, groupByCols, plainSelect.getSelectItems());
        }

        // Handle ORDER BY (before Project so SortOperator can access all columns)
        List<OrderByElement> orderByElements = selectStmt.getOrderByElements();
        if (orderByElements != null && !orderByElements.isEmpty()) {
            root = new LogicalSortOperator(root, orderByElements);
        }

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
    private static boolean hasAggregateFunctions(List<SelectItem<?>> selectItems) {
        for (SelectItem<?> item : selectItems) {
            if (item.getExpression() instanceof Function) {
                return true;
            }
        }
        return false;
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
        if (normalizedSql.equalsIgnoreCase("ROLLBACK")) {
            dbManager.rollbackTransaction();
            return true;
        }
        // SAVEPOINT <name>
        if (normalizedSql.matches("(?i)^SAVEPOINT\\s+(.+)$")) {
            String name = normalizedSql.replaceFirst("(?i)^SAVEPOINT\\s+", "").trim();
            dbManager.savepoint(name);
            return true;
        }
        // ROLLBACK TO SAVEPOINT <name>
        if (normalizedSql.matches("(?i)^ROLLBACK\\s+TO\\s+SAVEPOINT\\s+(.+)$")) {
            String name = normalizedSql.replaceFirst("(?i)^ROLLBACK\\s+TO\\s+SAVEPOINT\\s+", "").trim();
            dbManager.rollbackToSavepoint(name);
            return true;
        }
        // RELEASE SAVEPOINT <name>
        if (RELEASE_SAVEPOINT_PATTERN.matcher(normalizedSql).matches() || normalizedSql.matches("(?i)^RELEASE\\s+SAVEPOINT\\s+(.+)$")) {
            String name = normalizedSql.replaceFirst("(?i)^RELEASE(?:\\s+SAVEPOINT)?\\s+", "").trim();
            dbManager.releaseSavepoint(name);
            return true;
        }
        return false;
    }


}
