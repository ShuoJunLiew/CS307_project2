package edu.sustech.cs307.tuple;

import edu.sustech.cs307.meta.TabCol;
import edu.sustech.cs307.value.Value;
import edu.sustech.cs307.exception.DBException;

public class AggregationTuple extends Tuple {
    private final String columnName;
    private final long aggregateValue;

    public AggregationTuple(String columnName, long aggregateValue) {
        this.columnName = columnName;
        this.aggregateValue = aggregateValue;
    }

    @Override
    public Value getValue(TabCol tabCol) throws DBException {
        // If the requested column matches our aggregation header name
        if (tabCol.getColumnName().equalsIgnoreCase(columnName)) {
            return new Value(aggregateValue);
        }
        return null;
    }

    @Override
    public TabCol[] getTupleSchema() {
        return new TabCol[]{ new TabCol("AGGREGATE", columnName) };
    }

    @Override
    public Value[] getValues() throws DBException {
        return new Value[]{ new Value(aggregateValue) };
    }
}