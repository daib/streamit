package at.dms.kjc.sir.lowering.partition;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;

import at.dms.kjc.sir.SIRContainer;
import at.dms.kjc.sir.SIROperator;
import at.dms.kjc.sir.SIRStream;
import at.dms.kjc.sir.lowering.partition.dynamicprog.DynamicProgPartitioner;

public class IterativePartitioner extends ListPartitioner {

	protected boolean joinersNeedTiles;
	protected boolean limitICode;
	protected boolean strict;
	protected HashSet noHorizFuse;

	public IterativePartitioner(SIRStream str, WorkEstimate work, int numTiles) {
		super(str, work, numTiles);
		// TODO Auto-generated constructor stub
	}

	public IterativePartitioner(SIRStream str, WorkEstimate work, int numTiles,
			boolean joinersNeedTiles, boolean limitICode, boolean strict,
			HashSet noHorizFuse) {
		super(str, work, numTiles);
		this.joinersNeedTiles = joinersNeedTiles;
		this.limitICode = limitICode;
		this.strict = strict;
		this.noHorizFuse = noHorizFuse;
	}

	/**
	 * This is the toplevel call for doing partitioning. Returns the partitioned
	 * stream.
	 */
	public SIRStream toplevel() {
		HashMap<Object, Integer> partitions = calcPartitions();
		if (str instanceof SIRContainer) {
			((SIRContainer) str).reclaimChildren();
		}
		str.setParent(null);
		
		ApplyPartitions.doit(str, partitions);
		return str;
	}

	/**
	 * 
	 * @return A map from each object of the stream graph to a partition number
	 */
	private HashMap<Object, Integer> calcPartitions() {
		Map<SIROperator, Integer> partitionMap = new HashMap<SIROperator, Integer>();

		new DynamicProgPartitioner(str, work, numTiles, false, false, strict,
				noHorizFuse).calcPartitions(partitionMap);

		HashMap<Object, Integer> partitions = new HashMap<Object, Integer>();
		partitions.putAll(partitionMap);
		return partitions;
	}
}
