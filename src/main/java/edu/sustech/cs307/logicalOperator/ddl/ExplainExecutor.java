package edu.sustech.cs307.logicalOperator.ddl;

import edu.sustech.cs307.system.DBManager;
import edu.sustech.cs307.exception.DBException;
import edu.sustech.cs307.optimizer.LogicalPlanner;
import edu.sustech.cs307.optimizer.PhysicalPlanner;
import edu.sustech.cs307.logicalOperator.LogicalOperator;
import edu.sustech.cs307.physicalOperator.PhysicalOperator;

import net.sf.jsqlparser.statement.ExplainStatement;
import net.sf.jsqlparser.statement.Statement;

public class ExplainExecutor implements DMLExecutor {

    private final ExplainStatement explainStatement;
    private final DBManager dbManager;

    public ExplainExecutor(ExplainStatement explainStatement, DBManager dbManager) {
        this.explainStatement = explainStatement;
        this.dbManager = dbManager;
    }
    /// /////
    @Override
    public void execute() throws DBException {
        Statement innerStatement = explainStatement.getStatement();
        if (innerStatement == null) return;

        String innerSql = innerStatement.toString();
        LogicalOperator logicalPlan = LogicalPlanner.resolveAndPlan(dbManager, innerSql);
        if (logicalPlan == null) return;

        PhysicalOperator physicalPlan = PhysicalPlanner.generateOperator(dbManager, logicalPlan);
        String planVisualization = physicalPlan.explain("");

        // Print out the tree plan directly so it fits the standard console logger stream
        System.out.print(planVisualization);
    }
    /// /////
}