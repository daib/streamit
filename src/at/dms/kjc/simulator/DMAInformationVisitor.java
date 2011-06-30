package at.dms.kjc.simulator;

import at.dms.kjc.flatgraph.FlatNode;
import at.dms.kjc.flatgraph.FlatVisitor;

public class DMAInformationVisitor implements FlatVisitor {
	private String dmaInfo = "";

	public void visitNode(FlatNode node) {
		if (node.getEdges().length == 0)
			return;

		for (int i = 0; i < node.getEdges().length; i++) {
			FlatNode outgoingNode = node.getEdges()[i];
			dmaInfo += "DMA " + node.getName() + "_" + outgoingNode.getName() + " " + node.getName()
					+ " " + outgoingNode.getName();
			dmaInfo += "\n";
		}
		
	}

	public String getDmaInfo() {
		return dmaInfo;
	}
}
