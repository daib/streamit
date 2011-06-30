package at.dms.kjc.simulator;

import java.util.ArrayList;

import sim.model.MachineModel;
import sim.model.Processor;
import at.dms.kjc.flatgraph.FlatNode;
import at.dms.kjc.flatgraph.FlatVisitor;
import at.dms.kjc.sir.lowering.partition.WorkEstimate;

public class ExecutionTimeVisitor implements FlatVisitor {
	private String executionTimes = "";

	private MachineModel model;
	private Estimator estimator;

	public ExecutionTimeVisitor(MachineModel model, WorkEstimate workEstimate,
			RateMatcher rateMatcher) {
		this.model = model;
		this.estimator = new Estimator(workEstimate, rateMatcher);

	}

	public void visitNode(FlatNode node) {
		String filterName = node.getName();

		executionTimes += filterName + " ";
		ArrayList<Processor> processors = model.getProcessors();
		for (Processor processor : processors) {
			executionTimes += "(" + processor.getName() + ",";

			executionTimes += estimator.getEstimate(node);

			executionTimes += ") ";
		}
		executionTimes += "\n";
	}

	public String getExecutionTimes() {
		return executionTimes;
	}

}
