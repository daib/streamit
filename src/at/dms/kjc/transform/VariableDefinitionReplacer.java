package at.dms.kjc.transform;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import at.dms.kjc.CArrayType;
import at.dms.kjc.CType;
import at.dms.kjc.JAssignmentExpression;
import at.dms.kjc.JExpression;
import at.dms.kjc.JForStatement;
import at.dms.kjc.JLiteral;
import at.dms.kjc.JLocalVariableExpression;
import at.dms.kjc.JStatement;
import at.dms.kjc.JVariableDefinition;
import at.dms.kjc.KjcEmptyVisitor;
import at.dms.kjc.SLIRReplacingVisitor;
import at.dms.kjc.sir.SIRPushExpression;

public class VariableDefinitionReplacer extends KjcEmptyVisitor {

    Set<JVariableDefinition> variables = new HashSet<JVariableDefinition>();
    public Set<JAssignmentExpression> firstAssignments = new HashSet<JAssignmentExpression>();

    /**
     * prints a variable declaration statement
     */

    public void visitVariableDefinition(JVariableDefinition self,
            int modifiers, CType type, String ident, JExpression expr) {
        if (expr != null) {
            expr.accept(this);
        }
        // visit static array dimensions
        if (type.isArrayType()) {
            JExpression[] dims = ((CArrayType) type).getDims();
            for (int i = 0; i < dims.length; i++) {
                dims[i].accept(this);
            }
        }

        variables.add(self);
    }

    /**
     * init the definition of variables to its first assignment 
     */
    public void visitAssignmentExpression(JAssignmentExpression self,
            JExpression left, JExpression right) {
        left.accept(this);
        right.accept(this);
        if (left instanceof JLocalVariableExpression) {
            String leftName = ((JLocalVariableExpression) left).getVariable()
                    .getIdent();
            for (JVariableDefinition var : variables) {
                if (var.getIdent().equals(leftName)) {
                    if(right instanceof JLiteral)
                        var.setExpression(right);
                    variables.remove(var);
                    firstAssignments.add(self);
                    break;
                }
            }
        }

    }

}
