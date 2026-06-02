package edu.sustech.cs307.physicalOperator;

import edu.sustech.cs307.exception.DBException;
import edu.sustech.cs307.meta.ColumnMeta;
import edu.sustech.cs307.meta.TabCol;
import edu.sustech.cs307.record.RID;
import edu.sustech.cs307.record.RecordFileHandle;
import edu.sustech.cs307.system.DBManager;
import edu.sustech.cs307.tuple.TableTuple;
import edu.sustech.cs307.tuple.Tuple;
import edu.sustech.cs307.value.Value;
import edu.sustech.cs307.value.ValueType;
import java.util.ArrayList;

public class DeleteOperator implements PhysicalOperator {
    private final PhysicalOperator child;
    private final String tableName;
    private final DBManager dbManager;
    private boolean hasExecuted = false;
    private int deletedCount = 0;
    private Tuple summaryTuple = null; // Track our summary layout

    public DeleteOperator(PhysicalOperator child, String tableName, DBManager dbManager) {
        this.child = child;
        this.tableName = tableName;
        this.dbManager = dbManager;
    }

    @Override
    public void Begin() throws DBException {
        child.Begin();
        deletedCount = 0;
        hasExecuted = false;

        RecordFileHandle fileHandle = dbManager.getRecordManager().OpenFile(tableName);

        try {
            while (child.hasNext()) {
                child.Next();
                Tuple currentTuple = child.Current();

                if (currentTuple instanceof TableTuple tableTuple) {
                    RID targetRid = tableTuple.getRID();
                    if (targetRid != null) {
                        fileHandle.DeleteRecord(targetRid);
                        deletedCount++;
                    }
                }
            }
        } finally {
            dbManager.getRecordManager().CloseFile(fileHandle);
            child.Close();
        }

        // Create a mock single-column row containing the mutation count summary
        this.summaryTuple = new Tuple() {
            @Override
            public Value getValue(TabCol tabCol) throws DBException {
                return new Value((long) deletedCount);
            }

            @Override
            public TabCol[] getTupleSchema() {
                return new TabCol[]{ new TabCol("Rows Affected", "Count") };
            }

            @Override
            public Value[] getValues() throws DBException {
                return new Value[]{ new Value((long) deletedCount) };
            }
        };
    }

    @Override
    public boolean hasNext() throws DBException {
        return !hasExecuted;
    }

    @Override
    public void Next() throws DBException {
        hasExecuted = true;
    }

    @Override
    public Tuple Current() {
        return this.summaryTuple; // Pass our mock summary tuple instead of null!
    }

    @Override
    public void Close() {
        child.Close();
    }

    @Override
    public ArrayList<ColumnMeta> outputSchema() {
        ArrayList<ColumnMeta> schema = new ArrayList<>();
        // Set a clean description header for the output console grid
        schema.add(new ColumnMeta("Rows Affected", "Count", ValueType.INTEGER, 8, 0));
        return schema;
    }
}