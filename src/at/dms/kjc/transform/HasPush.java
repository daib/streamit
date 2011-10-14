package at.dms.kjc.transform;

import at.dms.kjc.CType;
import at.dms.kjc.JExpression;
import at.dms.kjc.SLIRReplacingVisitor;
import at.dms.kjc.sir.SIRPushExpression;

public class HasPush extends SLIRReplacingVisitor {
    boolean hasPush = false;

    public boolean hasPush() {
        return hasPush;
    }
    
    /**
     * Visits a push expression.
     */
    public Object visitPushExpression(SIRPushExpression self, CType tapeType,
            JExpression arg) {
        JExpression newExp = (JExpression) arg.accept(this);
        if (newExp != null && newExp != arg) {
            self.setArg(newExp);
        }
        hasPush = true;
        return self;
    }

}
