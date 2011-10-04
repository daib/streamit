package at.dms.kjc.optimizedrstream;

import at.dms.kjc.CType;
import at.dms.kjc.Constants;
import at.dms.kjc.JArrayAccessExpression;
import at.dms.kjc.JAssignmentExpression;
import at.dms.kjc.JExpression;
import at.dms.kjc.JLocalVariable;
import at.dms.kjc.JLocalVariableExpression;
import at.dms.kjc.JPrefixExpression;
import at.dms.kjc.SLIRReplacingVisitor;
import at.dms.kjc.sir.SIRPopExpression;
import at.dms.kjc.sir.SIRPushExpression;

public class ChannelReplacer extends SLIRReplacingVisitor {
    private JExpression popExpr, pushExpr, peekExpr; 
    ChannelReplacer(JExpression popExpr, JExpression peekExpr, JExpression pushExpr) {
        this.popExpr = popExpr;
        this.pushExpr = pushExpr;
        this.peekExpr = peekExpr;
    }
    
    /** 
     * visit a pop expression, converting the expression to a buffer access 
     **/
    public Object visitPopExpression(SIRPopExpression self,
            CType tapeType) {

        if(popExpr == null) 
            return self;
        else
            return popExpr;
    }
    
    /** 
     * visit a push expression, converting the expression to a buffer write
     **/
    public Object visitPushExpression(SIRPushExpression oldSelf,
                                      CType oldTapeType,
                                      JExpression oldArg) {
       if(pushExpr == null)
           return oldSelf;
       else {
           // do the super
           SIRPushExpression self = 
               (SIRPushExpression)
               super.visitPushExpression(oldSelf, oldTapeType, oldArg);
           
           return new JAssignmentExpression(null,
                   pushExpr,
                   self.getArg());
       }
    }
}
