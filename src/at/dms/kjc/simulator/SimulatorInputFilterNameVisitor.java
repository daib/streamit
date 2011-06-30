package at.dms.kjc.simulator;

import at.dms.kjc.flatgraph.FlatNode;
import at.dms.kjc.flatgraph.FlatVisitor;
import at.dms.kjc.iterator.SIRFilterIter;
import at.dms.kjc.sir.EmptyStreamVisitor;
import at.dms.kjc.sir.SIRFilter;
import at.dms.kjc.sir.lowering.fission.StatelessDuplicate;

public class SimulatorInputFilterNameVisitor implements FlatVisitor {
	String filterNames = "";

	public SimulatorInputFilterNameVisitor() {
		this.filterNames += "Filter ";
	}

	public String getFilterNames() {
		return filterNames;
	}

	public void visitNode(FlatNode node) {
		// 
		filterNames += " " + node.getName();
		System.out.println("adding " + node.getName());
	}

}
