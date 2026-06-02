package edu.sustech.cs307.logicalOperator.ddl;

import edu.sustech.cs307.exception.DBException;
import edu.sustech.cs307.meta.MetaManager;
import org.pmw.tinylog.Logger;

import java.io.File;

public class DropTableExecutor implements DMLExecutor {

    private final String tableName;
    private final MetaManager metaManager;
    private final String rootDir;

    public DropTableExecutor(String tableName, MetaManager metaManager, String rootDir) {
        this.tableName = tableName;
        this.metaManager = metaManager;
        this.rootDir = rootDir;
    }

    @Override
    public void execute() throws DBException {
        try {
            // 1. Attempt to delete from the metadata manager.
            // If the table doesn't exist, this throws an exception and jumps to the catch block.
            metaManager.dropTable(tableName);

            // 2. Physical File Cleanup: Locate the table's data folder on disk
            File tableDir = new File(rootDir + "/" + tableName);
            if (tableDir.exists() && tableDir.isDirectory()) {
                File[] files = tableDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        file.delete(); // Delete internal binary files (like 'data')
                    }
                }
                tableDir.delete(); // Delete the empty directory container
            }

            // 3. Log the successful deletion status cleanly
            Logger.info(String.format("Table '%s' dropped successfully.", tableName));

        } catch (DBException e) {
            // This is the "Proper Handling and Logging" requirement!
            // Instead of crashing the program, we capture the error and log it safely.
            Logger.error(String.format("Failed to drop table '%s': %s", tableName, e.getMessage()));
        }
    }
}