package at.dms.kjc.simulator;

import at.dms.kjc.flatgraph.FlatNode;
import at.dms.kjc.sir.SIRFilter;
import at.dms.kjc.sir.SIRJoiner;
import at.dms.kjc.sir.SIROperator;
import at.dms.kjc.sir.SIRSplitter;
import at.dms.kjc.sir.lowering.partition.WorkEstimate;

public class Estimator {
	private WorkEstimate workEstimate;
	private RateMatcher rateMatcher;

	public Estimator(WorkEstimate workEstimate, RateMatcher rateMatcher) {
		this.workEstimate = workEstimate;
		this.rateMatcher = rateMatcher;
	}
	
	public double getEstimate(FlatNode node) {
		SIROperator operator = node.contents;
		double work = 0.0;
		if (operator instanceof SIRFilter) {
			work = workEstimate.getWork((SIRFilter) operator);
		} else if (operator instanceof SIRSplitter) {
			
			SIRSplitter s = (SIRSplitter) operator;
			if (s.getType().isDuplicate()) {
				/* 1 pop + n.ways push */
				work = node.ways * 3.0 + 1.0;
			} else if (s.getType().isRoundRobin()) {
				/* sum_of_weights pop + sum_of_weights push */
				work = s.getSumOfWeights() * 3.0 * 2.0;
				
			} else {
				assert false : "getWork : unknown splitter type";
			}
			work = (int) ((work * rateMatcher.steadyCount.get(node)));
		} else if (operator instanceof SIRJoiner) {
			SIRJoiner j = (SIRJoiner) node.contents;
			if (j.getType().isRoundRobin()) {
				/* sum_of_weights pop + sum_of_weights push */
				work = j.getSumOfWeights() * 3.0 * 2.0;
			} else {
				assert false : "getWork : unknown joiner type";
			}
			work = (int) ((work * rateMatcher.steadyCount.get(node)));
		}
		return work;
	}
}
