package at.dms.kjc.sharedmemory;

import at.dms.kjc.JEmittedTextExpression;
import at.dms.kjc.JExpression;
import at.dms.kjc.JExpressionStatement;
import at.dms.kjc.JMethodCallExpression;
import at.dms.kjc.JMethodDeclaration;
import at.dms.kjc.JThisExpression;
import at.dms.kjc.backendSupport.BackEndFactory;
import at.dms.kjc.backendSupport.ProcessInputSliceNode;
import at.dms.kjc.backendSupport.SchedulingPhase;
import at.dms.kjc.slicegraph.InputSliceNode;
import at.dms.kjc.slicegraph.Slice;

public class SharedMemProcessInputSliceNode extends ProcessInputSliceNode {

	public SharedMemProcessInputSliceNode(InputSliceNode inputNode,
			SchedulingPhase whichPhase, BackEndFactory backEndBits) {
		super(inputNode, whichPhase, backEndBits);
	}

	protected void standardInitProcessing() {
		// Have the main function for the CodeStore call our init if any
		codeStore.addInitFunctionCall(joiner_code.getInitMethod());
		JMethodDeclaration workAtInit = joiner_code.getInitStageMethod();
		if (workAtInit != null) {
			// if there are calls to work needed at init time then add
			// method to general pool of methods
			codeStore.addMethod(workAtInit);

			// add barrier if needed
			Slice slice = inputNode.getParent();

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
		JMethodDeclaration primePump = joiner_code.getPrimePumpMethod();
		if (primePump != null && !codeStore.hasMethod(primePump)) {
			// Add method -- but only once
			codeStore.addMethod(primePump);
		}
		if (primePump != null) {
			int sliceGroup = SharedMemBackEndScaffold
					.addPrimePumpBarriers(codeStore);
			// for each time this method is called, it adds another call
			// to the primePump routine to the initialization.
			codeStore.addInitStatement(new JExpressionStatement(null,
					new JMethodCallExpression(null, new JThisExpression(null),
							primePump.getName() + "/*" + sliceGroup + "*/",
							new JExpression[0]), null));

		}
	}

}
