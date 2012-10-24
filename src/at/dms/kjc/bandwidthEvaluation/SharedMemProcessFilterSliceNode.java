package at.dms.kjc.bandwidthEvaluation;

import java.util.HashMap;

import at.dms.kjc.JEmittedTextExpression;
import at.dms.kjc.JExpression;
import at.dms.kjc.JExpressionStatement;
import at.dms.kjc.JMethodCallExpression;
import at.dms.kjc.JMethodDeclaration;
import at.dms.kjc.JThisExpression;
import at.dms.kjc.backendSupport.BackEndFactory;
import at.dms.kjc.backendSupport.ComputeCodeStore;
import at.dms.kjc.backendSupport.ProcessFilterSliceNode;
import at.dms.kjc.backendSupport.SchedulingPhase;
import at.dms.kjc.slicegraph.FilterSliceNode;
import at.dms.kjc.slicegraph.Slice;

public class SharedMemProcessFilterSliceNode extends ProcessFilterSliceNode {

//    static HashMap<ComputeCodeStore, Integer> lastSyncLevelMap = new HashMap<ComputeCodeStore, Integer>();

    public SharedMemProcessFilterSliceNode(FilterSliceNode filterNode,
            SchedulingPhase whichPhase, BackEndFactory backEndBits) {
        super(filterNode, whichPhase, backEndBits);
        // TODO Auto-generated constructor stub
    }

    protected void standardInitProcessing() {
        // Have the main function for the CodeStore call out init.
        codeStore.addInitFunctionCall(filter_code.getInitMethod());
        JMethodDeclaration workAtInit = filter_code.getInitStageMethod();

        if (workAtInit != null) {
            // if there are calls to work needed at init time then add
            // method to general pool of methods
            codeStore.addMethod(workAtInit);

            //add barrier if needed
            Slice slice = filterNode.getParent();

            SharedMemBackEndScaffold.addInitBarriers(codeStore, slice);

            // and add call to list of calls made at init time.
            // Note: these calls must execute in the order of the
            // initialization schedule -- so caller of this routine 
            // must follow order of init schedule.
            codeStore.addInitStatement(new JExpressionStatement(null,
                    new JMethodCallExpression(null, new JThisExpression(null),
                            workAtInit.getName(), new JExpression[0]), null));

        }

    }

    protected void standardPrimePumpProcessing() {

        JMethodDeclaration primePump = filter_code.getPrimePumpMethod();
        if (primePump != null && !codeStore.hasMethod(primePump)) {
            // Add method -- but only once
            codeStore.addMethod(primePump);
        }
        if (primePump != null) {
        	int sliceGroup = SharedMemBackEndScaffold.addPrimePumpBarriers(codeStore);

            // for each time this method is called, it adds another call
            // to the primePump routine to the initialization.
            codeStore.addInitStatement(new JExpressionStatement(null,
                    new JMethodCallExpression(null, new JThisExpression(null),
                            primePump.getName() +"/*" + sliceGroup + "*/", new JExpression[0]), null));
        }

    }

}
