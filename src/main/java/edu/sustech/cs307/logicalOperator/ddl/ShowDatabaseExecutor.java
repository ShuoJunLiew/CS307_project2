package edu.sustech.cs307.logicalOperator.ddl;

import edu.sustech.cs307.exception.DBException;
import edu.sustech.cs307.exception.ExceptionTypes;
import edu.sustech.cs307.meta.MetaManager;
import edu.sustech.cs307.system.DBManager; // Verify if DBManager is where your shared meta lives
import net.sf.jsqlparser.statement.ShowStatement;
import org.pmw.tinylog.Logger;

import java.util.Set;

public class ShowDatabaseExecutor implements DMLExecutor {

    private final ShowStatement showStatement;
    private final MetaManager metaManager;

    // Updated constructor to pass MetaManager from the physical planner/system layer
    public ShowDatabaseExecutor(ShowStatement showStatement, MetaManager metaManager) {
        this.showStatement = showStatement;
        this.metaManager = metaManager;
    }

    @Override
    public void execute() throws DBException {
        String command = showStatement.getName();

        if (command.equalsIgnoreCase("DATABASES")) {
            Logger.info("|-----------|");
            Logger.info("| Databases |");
            Logger.info("|-----------|");
            Logger.info("|   CS307   |");
            Logger.info("|-----------|");
        } else if (command.equalsIgnoreCase("TABLES")) {
            // Task 2 1.1: Fetch active tables dynamically from metadata manager
            Set<String> tableNames = metaManager.getTableNames();

            // Format matching the expected output specification in the document
            Logger.info(""); // Empty line matches example log sequence
            Logger.info("Tables");
            Logger.info("");

            for (String tableName : tableNames) {
                Logger.info(tableName);
            }
            Logger.info("");
        } else {
            throw new DBException(ExceptionTypes.UnsupportedCommand(String.format("SHOW %s", command)));
        }
    }
}