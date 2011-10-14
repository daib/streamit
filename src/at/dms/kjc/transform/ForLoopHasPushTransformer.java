package at.dms.kjc.transform;

import at.dms.kjc.JBlock;
import at.dms.kjc.JExpression;
import at.dms.kjc.JExpressionStatement;
import at.dms.kjc.JForStatement;
import at.dms.kjc.JReturnStatement;
import at.dms.kjc.JStatement;
import at.dms.kjc.JWhileStatement;
import at.dms.kjc.SLIRReplacingVisitor;
import at.dms.kjc.sir.SIRPushExpression;

public class ForLoopHasPushTransformer extends SLIRReplacingVisitor {

    /**
     * visits a for statement
     */
    public Object visitForStatement(JForStatement self, JStatement init,
            JExpression cond, JStatement incr, JStatement body) {
        // recurse into init
        JStatement newInit = (JStatement) init.accept(this);
        if (newInit != null && newInit != init) {
            self.setInit(newInit);
        }

        JExpression newExp = (JExpression) cond.accept(this);
        if (newExp != null && newExp != cond) {
            self.setCond(newExp);
        }

        // recurse into incr
        JStatement newIncr = (JStatement) incr.accept(this);
        if (newIncr != null && newIncr != incr) {
            self.setIncr(newIncr);
        }

        // recurse into body
        JStatement newBody = (JStatement) body.accept(this);
        if (newBody != null && newBody != body) {
            self.setBody(newBody);
        }

        HasPush hp = new HasPush();
        newBody.accept(hp);

        if (hp.hasPush()) {
            JBlock whileBodyBlock = new JBlock();
            
            //replace push statement with return
            final JStatement tmpIncr = newIncr;
            
            newBody.accept(new SLIRReplacingVisitor() {
                /**
                 * prints an expression statement
                 */
                public Object visitExpressionStatement(JExpressionStatement self,
                                                       JExpression expr) {
                    JExpression newExp = (JExpression)expr.accept(this);
                    
                    if (newExp!=null && newExp!=expr) {
                        self.setExpression(newExp);
                    }
                    
                    if(newExp instanceof SIRPushExpression) {
                        JBlock tmpStatements = new JBlock();  
                        tmpStatements.addStatement(tmpIncr);
                        tmpStatements.addStatement( new JReturnStatement(self.getTokenReference(), ((SIRPushExpression)newExp).getArg(), self.getComments()));
                        return tmpStatements;
                    } else
                        return self;
                }
            });
            
            whileBodyBlock.addStatement(newBody);
            JWhileStatement whileStatement = new JWhileStatement(null, newExp, whileBodyBlock, self.getComments());
            
            JBlock newBlock = new JBlock();
            newBlock.addStatement(whileStatement);
            newBlock.addStatement(newInit);
            return newBlock;
        } else
            return self;
    }
}
