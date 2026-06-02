package edu.sustech.cs307.physicalOperator;

import edu.sustech.cs307.exception.DBException;
import edu.sustech.cs307.meta.ColumnMeta;
import edu.sustech.cs307.tuple.ProjectTuple;
import edu.sustech.cs307.tuple.Tuple;
import edu.sustech.cs307.meta.TabCol;
import edu.sustech.cs307.value.ValueType;

import java.util.ArrayList;
import java.util.List;

public class ProjectOperator implements PhysicalOperator {
    private PhysicalOperator child;
    private List<TabCol> outputSchema; // Use bounded wildcard
    private Tuple currentTuple;

    public ProjectOperator(PhysicalOperator child, List<TabCol> outputSchema) { // Use bounded wildcard
        this.child = child;
        this.outputSchema = outputSchema;

        // Target an explicit SELECT * by checking if BOTH fields match the wildcards
        if (this.outputSchema.size() == 1 &&
                this.outputSchema.get(0).getTableName().equals("*") &&
                this.outputSchema.get(0).getColumnName().equals("*")) {

            List<TabCol> newOutputSchema = new ArrayList<>();
            for (ColumnMeta tabCol : child.outputSchema()) {
                newOutputSchema.add(new TabCol(tabCol.tableName, tabCol.name));
            }
            this.outputSchema = newOutputSchema;
        }
    }

    @Override
    public boolean hasNext() throws DBException {
        return child.hasNext();
    }

    @Override
    public void Begin() throws DBException {
        child.Begin();
    }

    @Override
    public void Next() throws DBException {
        child.Next();
        Tuple inputTuple = child.Current();
        if (inputTuple != null) {
            currentTuple = new ProjectTuple(inputTuple, outputSchema);
        } else {
            currentTuple = null;
        }
    }

    @Override
    public Tuple Current() {
        return currentTuple;
    }

    @Override
    public void Close() {
        child.Close();
        currentTuple = null;
    }

    @Override
    public ArrayList<ColumnMeta> outputSchema() {
        //todo: return the fields only appear in select items.
        ArrayList<ColumnMeta> columns = new ArrayList<>();
        ArrayList<ColumnMeta> childSchema = child.outputSchema();

        // Match every requested column in our projection list against the child's raw schema
        for (TabCol projectedCol : this.outputSchema) {
            for (ColumnMeta childCol : childSchema) {
                // If column names match (and table names match if explicitly specified)
                if (projectedCol.getColumnName().equalsIgnoreCase(childCol.name)) {
                    // Create a clean metadata representation for this output column field
                    columns.add(childCol);
                    break;
                }
            }
        }
        return columns;
        //return child.outputSchema();
    }
}
