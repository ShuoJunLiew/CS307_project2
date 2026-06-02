package edu.sustech.cs307.logicalOperator.ddl;

import edu.sustech.cs307.exception.DBException;
import edu.sustech.cs307.exception.ExceptionTypes;
import edu.sustech.cs307.meta.ColumnMeta;
import edu.sustech.cs307.meta.MetaManager;
import edu.sustech.cs307.meta.TableMeta;
import org.pmw.tinylog.Logger;

public class DescribeTableExecutor implements DMLExecutor {

    private final String tableName;
    private final MetaManager metaManager;

    public DescribeTableExecutor(String tableName, MetaManager metaManager) {
        this.tableName = tableName;
        this.metaManager = metaManager;
    }

    @Override
    public void execute() throws DBException {
        // 1. Attempt to fetch the table metadata.
        // If it doesn't exist, getTable() automatically throws a TableDoesNotExist exception.
        TableMeta tableMeta = metaManager.getTable(tableName);

        // 2. Format and print the schema header matching the project specification
        Logger.info("|----------------------|");
        Logger.info("| Field       | Type   |");
        Logger.info("|----------------------|");

        // 3. Loop through the columns and print their field names and database types
        for (ColumnMeta column : tableMeta.columns_list) {
            // Left-align text using String.format to maintain clean structural columns
            String formattedLine = String.format("| %-11s | %-6s |",
                    column.name,
                    column.type.toString().toLowerCase());
            Logger.info(formattedLine);
        }

        Logger.info("|----------------------|");
    }
}