package at.dms.kjc.transform;

import java.util.HashSet;
import java.util.Set;

import at.dms.kjc.JAssignmentExpression;
import at.dms.kjc.JExpression;
import at.dms.kjc.JLocalVariable;
import at.dms.kjc.JLocalVariableExpression;
import at.dms.kjc.KjcEmptyVisitor;

public class ForLoopIndexVarCollector extends KjcEmptyVisitor {
    public Set<JLocalVariable> indexVars = new HashSet<JLocalVariable>();
    
    /**
     * prints an assignment expression
     */
    public void visitAssignmentExpression(JAssignmentExpression self,
            JExpression left, JExpression right) {
        assert (left instanceof JLocalVariableExpression) : "left of the assignment is not a variable";
        indexVars.add(((JLocalVariableExpression)left).getVariable());
        left.accept(this);
        right.accept(this);
    }
}
