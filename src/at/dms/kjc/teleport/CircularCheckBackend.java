// $Header: /n/tiamat/y/repository/StreamItNew/streams/src/at/dms/kjc/cluster/ClusterBackend.java,v 1.1 2009/02/24 18:14:55 hormati Exp $
package at.dms.kjc.teleport;

import at.dms.kjc.flatgraph.FlatNode;
import at.dms.kjc.flatgraph.DumpSymbolicGraph;
import at.dms.kjc.flatgraph.GraphFlattener;
//import at.dms.kjc.flatgraph.*;
//import at.dms.util.IRPrinter;
//import at.dms.util.SIRPrinter;
import at.dms.util.Utils;
import at.dms.kjc.*;
import at.dms.kjc.cluster.LatencyConstraints;
import at.dms.kjc.common.*;
import at.dms.kjc.iterator.*;
import at.dms.kjc.sir.*;
import at.dms.kjc.sir.stats.StatisticsGathering;
import at.dms.kjc.sir.lowering.*;
import at.dms.kjc.sir.lowering.partition.*;
import at.dms.kjc.sir.lowering.partition.cache.*;
import at.dms.kjc.sir.lowering.partition.dynamicprog.*;
import at.dms.kjc.sir.lowering.fusion.*;
import java.util.*;
//import java.io.*;
//import streamit.scheduler2.print.PrintProgram;
//import streamit.scheduler2.*;
//import streamit.scheduler2.constrained.*;

import streamit.scheduler2.Schedule;

/**
 * Top level of back ends for cluster and uniprocessor based on cluster. For a
 * cluster creates computation nodes connected with pipes. For a uniprocessor
 * creates computation nodes connected by buffers. <br/>
 * Starts with: Standard sequence of optimization passes. Followed by: Dynamic
 * region handling and partitioning, still standard in that it is similar to
 * SpaceDynamic. Followed by: Code generation for cluster or uniprocessor.
 */
public class CircularCheckBackend {

	// public static Simulator simulator;
	// get the execution counts from the scheduler

	/**
	 * Print out some debugging info if true.
	 */
	public static boolean debugging = false;

	/**
	 * If true have each filter print out each value it is pushing onto its
	 * output tape.
	 */
	public static final boolean FILTER_DEBUG_MODE = false;

	/**
	 * Given a flatnode, map to the init execution count.
	 */
	static HashMap<FlatNode, Integer> initExecutionCounts;
	/**
	 * Given a flatnode, map to steady-state execution count.
	 * 
	 * <br/>
	 * Also read in several other modules.
	 */
	static HashMap<FlatNode, Integer> steadyExecutionCounts;

	/**
	 * Given a filter, map to original push/pop/peek rates
	 */
	static HashMap<SIRFilter, Integer[]> originalRates;

	/**
	 * Given a joiner, map to following filter
	 */
	static HashMap<SIRJoiner, SIRFilter> joinerWork;

	/**
	 * Holds passed structures until they can be handeed off to
	 * {@link StructureIncludeFile}.
	 */
	private static SIRStructure[] structures;

	// /**
	// * Used to iterate over str structure ignoring flattening.
	// * <br/> Also used in {@link ClusterCodeGenerator} and {@link
	// FlatIrToCluster2}
	// */
	// static streamit.scheduler2.iriter.Iterator topStreamIter;

	/**
	 * The cluster backend. Called via reflection.
	 */
	public static void run(SIRStream str, JInterfaceDeclaration[] interfaces,
			SIRInterfaceTable[] interfaceTables, SIRStructure[] structs,
			SIRHelper[] helpers, SIRGlobal global) {

		System.out.println("Entry to E2 CircularCheckBacken");

		structures = structs;

		// Pull pop, push, peek into own statements (must be done eventually
		// before
		// generating C code, and is best done here while type info is still
		// available
		// for tmps.
		SimplifyPopPeekPush.simplify(str);

		// Perform propagation on fields from 'static' sections.
		Set<SIRGlobal> statics = new HashSet<SIRGlobal>();
		if (global != null)
			statics.add(global);
		StaticsProp.propagate/* IntoContainers */(str, statics);
		
		// propagate constants and unroll loop
        System.err.print("Running Constant Prop and Unroll...");

        // Constant propagate and unroll.
        // Set unrolling factor to <= 4 for loops that don't involve
        //  any tape operations.
        Unroller.setLimitNoTapeLoops(true, 4);
    
        ConstantProp.propagateAndUnroll(str);
        

        System.err.println(" done.");

        // do constant propagation on fields
        System.err.print("Running Constant Field Propagation...");
        ConstantProp.propagateAndUnroll(str, true);
        
		// construct stream hierarchy from SIRInitStatements
//		ConstructSIRTree.doit(str);

		// this must be run now, Further passes expect unique names!!!
		RenameAll.renameAllFilters(str);

		// System.err.println("Analyzing Branches..");
		// new BlockFlattener().flattenBlocks(str);
		// new BranchAnalyzer().analyzeBranches(str);

		SIRPortal.findMessageStatements(str);

		// canonicalize stream graph, reorganizing some splits and joins
		Lifter.liftAggressiveSync(str);

		// Unroll and propagate maximally within each (not phased) filter.
		// Initially justified as necessary for IncreaseFilterMult which is
		// now obsolete.
		StreamItDot.printGraph(str, "canonical-graph.dot");

		// put timers for each filters
		// InsertFilterPerfCounters.doit(str);

		// run constrained scheduler

		System.err.print("Constrained Scheduler Begin...");
		streamit.scheduler2.iriter.Iterator topStreamIter = IterFactory
				.createFactory().createIter(str);

		streamit.scheduler2.constrained.Scheduler scheduler = streamit.scheduler2.constrained.Scheduler
				.create(topStreamIter);
		
		scheduler.computeSchedule();
        scheduler.computeBufferUse();
        Schedule initSched = scheduler.getOptimizedInitSchedule();
        Schedule steadySched = scheduler.getOptimizedSteadySchedule();
        
        System.err.println(" done.");
        
		scheduler.printReps();
		//cscheduler.computeSchedule();

		new streamit.scheduler2.print.PrintGraph().printProgram(topStreamIter);
//		new streamit.scheduler2.print.PrintProgram().printProgram(topStreamIter);

		 

		// end constrained scheduler

		// calculate latency constraints for all portals
		// and save for later query.
		LatencyConstraints.detectConstraints(SIRPortal.getPortals());

		System.exit(0);
	}

}
