package at.dms.kjc.bandwidthEvaluation;

import java.util.HashSet;
import java.util.Set;

import at.dms.kjc.CStdType;
import at.dms.kjc.JBlock;
import at.dms.kjc.JEmittedTextExpression;
import at.dms.kjc.JExpressionStatement;
import at.dms.kjc.JIntLiteral;
import at.dms.kjc.JStatement;
import at.dms.kjc.JVariableDeclarationStatement;
import at.dms.kjc.JVariableDefinition;
import at.dms.kjc.KjcOptions;
import at.dms.kjc.backendSupport.BackEndFactory;
import at.dms.kjc.backendSupport.CodeStoreHelperSimple;
import at.dms.kjc.slicegraph.FilterSliceNode;
import at.dms.util.Utils;

public class CodeStoreHelperSharedMem extends CodeStoreHelperSimple {

//    public static boolean PERF_COUNTER = true;
    
    public static Set<String> runtimeObjs = new HashSet<String>();

    
    public CodeStoreHelperSharedMem(FilterSliceNode node,
            BackEndFactory backEndBits) {
        super(node, backEndBits);
        INLINE_WORK = true;
    }

    /**
     * Return a JBlock that iterates <b>mult</b> times the result of calling <b>getWorkFunctionCall()</b>.
     * @param mult Number of times to iterate work function.
     * @return as described, or <b>null</b> if <b>getWorkFunctionCall()</b> returns null;
     */
    protected JBlock getWorkFunctionBlock(int mult) {
        if (getWorkMethod() == null) {
            return null;
        }

        JBlock block = new JBlock();

        if (KjcOptions.profile && sliceNode.isFilterSlice()) {
            String filterName = sliceNode.toString();

            if (filterName.indexOf("__") > 0) {
                filterName = filterName.substring(0,
                        filterName.lastIndexOf("__"));
            }

            filterName = (sliceNode.isFilterSlice() ? "Filter" : (sliceNode
                    .isInputSlice() ? "Splitter"
                    : (sliceNode.isOutputSlice() ? "Joiner" : "UnkownClass")))
                    + "_" + filterName;

            block.addStatement(new JExpressionStatement(
                    new JEmittedTextExpression(filterName + "_runtime_obj.prework_checkpoint(" + mult + ")")));
            
            runtimeObjs.add(filterName);
        }
        
        JStatement workStmt = getWorkFunctionCall();
        if (mult > 1) {
            JVariableDefinition loopCounter = new JVariableDefinition(null, 0,
                    CStdType.Integer, workCounter, null);

            JStatement loop = Utils.makeForLoopLocalIndex(workStmt,
                    loopCounter, new JIntLiteral(mult));
            block.addStatement(new JVariableDeclarationStatement(null,
                    loopCounter, null)); //declaration of loop counter
            block.addStatement(loop);
        } else
            block.addStatement(workStmt);
        
        if (KjcOptions.profile && sliceNode.isFilterSlice()) {
            String filterName = sliceNode.toString();

            if (filterName.indexOf("__") > 0) {
                filterName = filterName.substring(0,
                        filterName.lastIndexOf("__"));
            }

            filterName = (sliceNode.isFilterSlice() ? "Filter" : (sliceNode
                    .isInputSlice() ? "Splitter"
                    : (sliceNode.isOutputSlice() ? "Joiner" : "UnkownClass")))
                    + "_" + filterName;

            block.addStatement(new JExpressionStatement(
                    new JEmittedTextExpression( filterName + "_runtime_obj.postwork_checkpoint()")));
        }

        return block;
    }
}
