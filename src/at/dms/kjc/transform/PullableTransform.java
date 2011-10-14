package at.dms.kjc.transform;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;

import at.dms.kjc.CArrayType;
import at.dms.kjc.CClassType;
import at.dms.kjc.CType;
import at.dms.kjc.Constants;
import at.dms.kjc.JBlock;
import at.dms.kjc.JEmptyStatement;
import at.dms.kjc.JExpression;
import at.dms.kjc.JExpressionStatement;
import at.dms.kjc.JForStatement;
import at.dms.kjc.JFormalParameter;
import at.dms.kjc.JMethodCallExpression;
import at.dms.kjc.JMethodDeclaration;
import at.dms.kjc.JReturnStatement;
import at.dms.kjc.JStatement;
import at.dms.kjc.JVariableDeclarationStatement;
import at.dms.kjc.JVariableDefinition;
import at.dms.kjc.ObjectDeepCloner;
import at.dms.kjc.SLIRReplacingVisitor;
import at.dms.kjc.flatgraph.DataFlowTraversal;
import at.dms.kjc.flatgraph.FlatNode;
import at.dms.kjc.optimizedrstream.ConvertChannelExprs;
import at.dms.kjc.optimizedrstream.FilterFusionState;
import at.dms.kjc.optimizedrstream.FusionState;
import at.dms.kjc.sir.SIREndMarker;
import at.dms.kjc.sir.SIRFilter;
import at.dms.kjc.sir.SIRPushExpression;

public class PullableTransform {
    public static List<FlatNode> pullableNodes = new LinkedList<FlatNode>();
    /* any helper functions of the application */
    static public Vector<JMethodDeclaration> functions = new Vector<JMethodDeclaration>();

    public static void findTransformableFilters(FlatNode top) {
        //get a data-flow ordered traversal for the graph, i.e. a node 
        //can only fire if its upstream filters have fired
        Iterator<FlatNode> traversal = DataFlowTraversal.getTraversal(top)
                .iterator();

        while (traversal.hasNext()) {
            FlatNode node = traversal.next();

            if (node.isFilter()) {
                //check if this node is transformable
                SIRFilter filter = (SIRFilter) node.contents;
                List<JStatement> workStatements = filter.getWork()
                        .getStatements();
                if (checkTranformableStatements(workStatements)) {
                    pullableNodes.add(node);
                }

            }

        }

        for (FlatNode node : pullableNodes) {
            SIRFilter filter = (SIRFilter) node.contents;
            //declare the transformed function
            JMethodDeclaration workMethod = (JMethodDeclaration) ObjectDeepCloner
                    .deepCopy(filter.getWork());

            functions.add(pullableTransform(workMethod, node));
        }

    }

    private static JMethodDeclaration pullableTransform(
            JMethodDeclaration workMethod, FlatNode node) {
        final String nodeName = node.getName();
        //transform the work function
        workMethod = (JMethodDeclaration) workMethod
                .accept(new SLIRReplacingVisitor() {
                    public Object visitMethodDeclaration(
                            JMethodDeclaration self, int modifiers,
                            CType returnType, String ident,
                            JFormalParameter[] parameters,
                            CClassType[] exceptions, JBlock body) {
                        for (int i = 0; i < parameters.length; i++) {
                            if (!parameters[i].isGenerated()) {
                                parameters[i].accept(this);
                            }
                        }
                        if (body != null) {
                            body.accept(this);
                        }
                        
                        body.addStatement(new SIREndMarker(nodeName));
                        
                        modifiers = modifiers | Constants.ACC_INLINE;

                        return new JMethodDeclaration(null, modifiers, self
                                .getPush().getType(), ident, parameters,
                                exceptions, body, null, null);
                        //return self;
                    };

                    /**
                     * prints a variable declaration statement
                     */
                    public Object visitVariableDefinition(
                            JVariableDefinition self, int modifiers,
                            CType type, String ident, JExpression expr) {
                        if (expr != null) {
                            JExpression newExp = (JExpression) expr
                                    .accept(this);
                            if (newExp != null && newExp != expr) {
                                self.setValue(newExp);
                            }

                        }
                        // visit static array dimensions
                        if (type.isArrayType()) {
                            JExpression[] dims = ((CArrayType) type).getDims();
                            for (int i = 0; i < dims.length; i++) {
                                JExpression newExp = (JExpression) dims[i]
                                        .accept(this);
                                if (newExp != null && newExp != dims[i]) {
                                    dims[i] = newExp;
                                }
                            }
                        }
                        return new JVariableDefinition(null, modifiers
                                | Constants.ACC_STATIC, type, ident, null);
                    }
                });

        workMethod.accept(new VariableDefinitionReplacer());

        workMethod.accept(new ForLoopHasPushTransformer());

        workMethod.accept(new SLIRReplacingVisitor() {
            /**
             * prints an expression statement
             */
            public Object visitExpressionStatement(JExpressionStatement self,
                    JExpression expr) {
                JExpression newExp = (JExpression) expr.accept(this);

                if (newExp != null && newExp != expr) {
                    self.setExpression(newExp);
                }

                if (newExp instanceof SIRPushExpression) {
                    return new JReturnStatement(self
                            .getTokenReference(), ((SIRPushExpression) newExp)
                            .getArg(), self.getComments());
                } else
                    return self;
            }
        });

        if (node.incoming.length > 0) {
            System.out.println("cur " + node.getName() + " pre "
                    + node.incoming[0].getName());
        }

        //if this node has a previous pullable node, then replace
        //its pop by calls to previous node's work functions
        if (node.incoming.length > 0 // && node.incoming[0].isFilter()
                && pullableNodes.contains(node.incoming[0])) {
            workMethod.getBody().accept(
                    new ChannelReplacer(new JMethodCallExpression(null,
                            ((SIRFilter) node.incoming[0].contents).getWork()
                                    .getName(), null), null, null));
        }

        workMethod.accept(new ConvertChannelExprs(
                (FilterFusionState) FusionState.getFusionState(node), false));

        return workMethod;
    }

    /**
     * check if the first statement if a for statement and
     * a push expression is at the end of that for loop body
     * @param statements
     * @return
     */

    private static boolean checkTranformableStatements(
            List<JStatement> statements) {

        //check the first useful statement to see if it a for statement
        for (int i = 0; i < statements.size(); i++) {

            JStatement statement = statements.get(i);
            if (statement instanceof JEmptyStatement
                    || statement instanceof JVariableDeclarationStatement) {
                continue;
            } else if (statement instanceof JBlock) {
                return checkTranformableStatements(((JBlock) statement)
                        .getStatements());
            } else if (statement instanceof JForStatement) {
                JStatement forBodyStatement = ((JForStatement) statement)
                        .getBody();
                if (forBodyStatement instanceof JBlock) {
                    return isSinglePushAtTheEnd(((JBlock) forBodyStatement)
                            .getStatements());
                } else if (forBodyStatement instanceof JExpressionStatement) {
                    if (((JExpressionStatement) forBodyStatement)
                            .getExpression() instanceof SIRPushExpression)
                        return true; //good
                    else
                        break;
                } else
                    break;

            } else if (isSinglePushAtTheEnd(statements)) {
                return true;
            } else
                break;

        }
        return false; //not a valid block

    }

    private static boolean isSinglePushAtTheEnd(List<JStatement> statements) {
        //check the first useful statement to see if it a for statement
        for (int i = statements.size() - 1; i >= 0; i--) {

            JStatement statement = statements.get(i);
            if (statement instanceof JEmptyStatement) {
                continue;
            } else if (statement instanceof JBlock) {
                return isSinglePushAtTheEnd(((JBlock) statement)
                        .getStatements());
            } else if (statement instanceof JExpressionStatement) {
                if (((JExpressionStatement) statement).getExpression() instanceof SIRPushExpression) {
                    for (int j = 0; j < i; j++) {
                        HasPush hp = new HasPush();
                        statements.get(j).accept(hp);
                        if (hp.hasPush())
                            return false;
                    }

                    return true;
                } else {
                    return false;
                }

            } else
                break;

        }
        return false; //not a valid block
    }

}
