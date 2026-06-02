package edu.sustech.cs307.physicalOperator;

import edu.sustech.cs307.exception.DBException;
import edu.sustech.cs307.index.InMemoryOrderedIndex;
import edu.sustech.cs307.meta.ColumnMeta;
import edu.sustech.cs307.meta.TableMeta;
import edu.sustech.cs307.record.RID;
import edu.sustech.cs307.record.Record;
import edu.sustech.cs307.record.RecordFileHandle;
import edu.sustech.cs307.system.DBManager;
import edu.sustech.cs307.tuple.TableTuple;
import edu.sustech.cs307.tuple.Tuple;
import edu.sustech.cs307.value.Value;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;

/**
 * Physical operator that scans a table using an in-memory ordered index.
 * Returns tuples in index key order.
 */
public class InMemoryIndexScanOperator implements PhysicalOperator {

    private final InMemoryOrderedIndex index;
    private final String tableName;
    private final DBManager dbManager;
    private RecordFileHandle fileHandle;
    private Iterator<Map.Entry<Value, RID>> iterator;
    private Tuple currentTuple;
    private TableMeta tableMeta;
    private boolean isOpen;
    private boolean readyForNext;

    public InMemoryIndexScanOperator(InMemoryOrderedIndex index, String tableName, DBManager dbManager) {
        this.index = index;
        this.tableName = tableName;
        this.dbManager = dbManager;
        this.isOpen = false;
        this.readyForNext = false;
    }

    @Override
    public void Begin() throws DBException {
        fileHandle = dbManager.getRecordManager().OpenFile(tableName);
        tableMeta = dbManager.getMetaManager().getTable(tableName);
        // Get all entries from the index and create an iterator
        // Using MoreThan with null-like behavior via the index's internal TreeMap
        // Since the InMemoryOrderedIndex wraps a TreeMap, we use its MoreThan
        // with a sentinel value to get all entries
        iterator = getIndexIterator();
        isOpen = true;
        readyForNext = false;
        currentTuple = null;
    }

    @SuppressWarnings("unchecked")
    private Iterator<Map.Entry<Value, RID>> getIndexIterator() {
        return (Iterator) index.getAllEntries();
    }

    @Override
    public boolean hasNext() throws DBException {
        if (!isOpen) return false;
        if (!readyForNext) {
            return findNext();
        }
        return currentTuple != null;
    }

    private boolean findNext() throws DBException {
        currentTuple = null;
        if (iterator == null || !iterator.hasNext()) return false;

        Map.Entry<Value, RID> entry = iterator.next();
        RID rid = entry.getValue();
        if (rid == null) return false;

        try {
            Record record = fileHandle.GetRecord(rid);
            if (record != null) {
                currentTuple = new TableTuple(tableName, tableMeta, record, rid);
                readyForNext = true;
                return true;
            }
        } catch (DBException e) {
            // Skip invalid records, try next
            return findNext();
        }
        return false;
    }

    @Override
    public void Next() throws DBException {
        if (!isOpen) return;
        if (!readyForNext) {
            hasNext();
        }
        readyForNext = false;
    }

    @Override
    public Tuple Current() {
        return currentTuple;
    }

    @Override
    public void Close() {
        if (fileHandle != null) {
            try {
                dbManager.getRecordManager().CloseFile(fileHandle);
            } catch (DBException e) {
                // ignore
            }
        }
        isOpen = false;
        currentTuple = null;
        readyForNext = false;
        iterator = null;
    }

    @Override
    public ArrayList<ColumnMeta> outputSchema() {
        try {
            return dbManager.getMetaManager().getTable(tableName).columns_list;
        } catch (DBException e) {
            return new ArrayList<>();
        }
    }
}
