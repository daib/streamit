package at.dms.kjc.simulator;

import at.dms.kjc.flatgraph.FlatNode;
import at.dms.kjc.flatgraph.FlatVisitor;
import at.dms.kjc.iterator.SIRFilterIter;
import at.dms.kjc.sir.EmptyStreamVisitor;
import at.dms.kjc.sir.SIRFilter;
import at.dms.kjc.sir.SIRJoiner;
import at.dms.kjc.sir.SIROperator;
import at.dms.kjc.sir.SIRSplitter;
import at.dms.kjc.sir.lowering.partition.WorkEstimate;

public class FilterInformationVisitor implements FlatVisitor {
	private String filterInfo = "";
	private RateMatcher rateMatcher;
	private WorkEstimate workEstimate;

	public FilterInformationVisitor(RateMatcher rateMatcher, WorkEstimate workEstimate) {
		this.rateMatcher = rateMatcher;
		this.workEstimate = workEstimate;
	}

	public void visitNode(FlatNode node) {
		filterInfo += node.getName();
		SIROperator contents = node.contents;

		System.out.println("node is " + node);
		if (contents instanceof SIRJoiner || contents instanceof SIRSplitter) {
			filterInfo += " [";
			for (int i = 0; i < node.incoming.length; i++) {
				FlatNode incomingNode = node.incoming[i];
				int incomingRate = rateMatcher.steadyCount.get(incomingNode);
				incomingRate *= FlatNode.getItemsPushed(incomingNode, node);
				filterInfo += "(" + incomingRate + "." + incomingNode.getName() + ")";
				if (i != node.incoming.length - 1)
					filterInfo += ",";
			}
			filterInfo += "]";

			filterInfo += " [";
			for (int i = 0; i < node.getEdges().length; i++) {
				FlatNode outgoingNode = node.getEdges()[i];
				int outgoingRate = rateMatcher.steadyCount.get(node);
				outgoingRate *= FlatNode.getItemsPushed(node, outgoingNode);
				filterInfo += "(" + outgoingRate + "." + outgoingNode.getName() + ")";
				if (i != node.getEdges().length - 1)
					filterInfo += ",";
			}
			filterInfo += "]";
		} else if (contents instanceof SIRFilter) {
			filterInfo += " [";
			if (node.incoming.length > 0) {
				FlatNode incomingNode = node.incoming[0];
				int incomingRate = rateMatcher.steadyCount.get(incomingNode);
				incomingRate *= FlatNode.getItemsPushed(incomingNode, node);
				filterInfo += "(" + incomingRate + "." + incomingNode.getName() + ")";
			} else {
				filterInfo += "(0.-)";
			}
			filterInfo += "]";

			filterInfo += " [";
			if (node.getEdges().length > 0) {
				FlatNode outgoingNode = node.getEdges()[0];
				int outgoingRate = rateMatcher.steadyCount.get(node);
				System.out.println(" steady is " + outgoingRate);
				outgoingRate *= FlatNode.getItemsPushed(node, outgoingNode);
				System.out.println(" pushed from " + node + " to " + outgoingRate + " is " + FlatNode.getItemsPushed(node, outgoingNode));
				filterInfo += "(" + outgoingRate + "." + outgoingNode.getName() + ")";
			} else {
				filterInfo += "(0.-)";
			}
			filterInfo += "]";
		}

		filterInfo += " [";
		if (contents instanceof SIRFilter) {
			filterInfo += workEstimate.getICodeSize((SIRFilter) contents);
			// System.out.println("REP " + node + " " +
			// workEstimate.getReps((SIRFilter)contents));

		} else {
			filterInfo += "100";
		}
		filterInfo += "]";

		filterInfo += " [";
		if (contents instanceof SIRFilter) {
			filterInfo += workEstimate.getWork((SIRFilter) contents);
		} else {
			filterInfo += "0";
		}
		filterInfo += "]";

		filterInfo += "\n";
	}

	public String getFilterInfoString() {
		return filterInfo;
	}
}
