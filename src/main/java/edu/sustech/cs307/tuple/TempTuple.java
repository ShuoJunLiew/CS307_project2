package edu.sustech.cs307.tuple;

import edu.sustech.cs307.exception.DBException;
import edu.sustech.cs307.exception.ExceptionTypes;
import edu.sustech.cs307.meta.TabCol;
import edu.sustech.cs307.value.Value;

import java.util.ArrayList;
import java.util.List;

public class TempTuple extends Tuple {
    private final List<Value> values;
    private final TabCol[] schema;

    public TempTuple(List<Value> values) {
        this.values = values;
        this.schema = null;
    }

    public TempTuple(List<Value> values, TabCol[] schema) {
        this.values = values;
        this.schema = schema;
    }

    @Override
    public Value getValue(TabCol tabCol) throws DBException {
        if (schema != null) {
            for (int i = 0; i < schema.length; i++) {
                if (schema[i].getColumnName().equalsIgnoreCase(tabCol.getColumnName())) {
                    return values.get(i);
                }
            }
            return null;
        }
        throw new DBException(ExceptionTypes.GetValueFromTempTuple());
    }

    @Override
    public TabCol[] getTupleSchema() {
        return schema;
    }

    @Override
    public Value[] getValues() throws DBException {
        return this.values.toArray(new Value[0]);
    }
}