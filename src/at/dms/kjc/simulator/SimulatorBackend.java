package at.dms.kjc.simulator;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import sim.model.MachineModel;
import at.dms.kjc.JInterfaceDeclaration;
import at.dms.kjc.cluster.Estimator;
import at.dms.kjc.cluster.Optimizer;
import at.dms.kjc.flatgraph.FlatNode;
import at.dms.kjc.flatgraph.GraphFlattener;
import at.dms.kjc.flatgraph.StaticStreamGraph;
import at.dms.kjc.flatgraph.StreamGraph;
import at.dms.kjc.sir.SIRGlobal;
import at.dms.kjc.sir.SIRHelper;
import at.dms.kjc.sir.SIRInterfaceTable;
import at.dms.kjc.sir.SIRPortal;
import at.dms.kjc.sir.SIRStream;
import at.dms.kjc.sir.SIRStructure;
import at.dms.kjc.sir.lowering.ArrayInitExpander;
import at.dms.kjc.sir.lowering.ConstantProp;
import at.dms.kjc.sir.lowering.ConstructSIRTree;
import at.dms.kjc.sir.lowering.EnqueueToInitPath;
import at.dms.kjc.sir.lowering.FieldProp;
import at.dms.kjc.sir.lowering.IntroduceMultiPops;
import at.dms.kjc.sir.lowering.MarkFilterBoundaries;
import at.dms.kjc.sir.lowering.RenameAll;
import at.dms.kjc.sir.lowering.RoundToFloor;
import at.dms.kjc.sir.lowering.StaticsProp;
import at.dms.kjc.sir.lowering.Unroller;
import at.dms.kjc.sir.lowering.VarDeclRaiser;
import at.dms.kjc.sir.lowering.fission.StatelessDuplicate;
import at.dms.kjc.sir.lowering.fusion.Lifter;
import at.dms.kjc.sir.lowering.partition.WorkEstimate;

public class SimulatorBackend {
	private static boolean debugPrint = false;

	public static void run(SIRStream str, JInterfaceDeclaration[] interfaces,
			SIRInterfaceTable[] interfaceTables, SIRStructure[] structs, SIRHelper[] helpers,
			SIRGlobal global) {

		StreamGraph streamGraph;

		IntroduceMultiPops.doit(str);

		// propagate constants and unroll loop
		System.out.println("Running Constant Prop and Unroll...");

		Unroller.setLimitNoTapeLoops(true, 4);

		ConstantProp.propagateAndUnroll(str);
		ConstantProp.propagateAndUnroll(str, true);
		System.err.println(" done.");

		// convert round(x) to floor(0.5+x) to avoid obscure errors
		RoundToFloor.doit(str);

		// add initPath functions
		EnqueueToInitPath.doInitPath(str);

		// construct stream hierarchy from SIRInitStatements
		ConstructSIRTree.doit(str);

		// this must be run now, FlatIRToC relies on it!!!
		RenameAll.renameAllFilters(str);

		FieldProp.doPropagate(str);

		new VarDeclRaiser().raiseVars(str);

		// expand array initializers loaded from a file
		ArrayInitExpander.doit(str);

		Optimizer.optimize(str);
		// estimate code and local variable size for each filter (and store
		// where???)
		Estimator.estimate(str);

		// canonicalize stream graph, reorganizing some splits and joins
		Lifter.liftAggressiveSync(str);
		System.out.println("done.");

		Optimizer.optimize(str);

		System.out.print("Marking filter boundaries...");
		MarkFilterBoundaries.doit(str);
		System.out.println("done.");

		// Do maximal fissing
		Duplicator.maximallyDuplicate(str);

		Optimizer.optimize(str);
		// estimate code and local variable size for each filter (and store
		// where???)
		Estimator.estimate(str);

		System.out.println("getting work estimate.");
		WorkEstimate wrkest = WorkEstimate.getWorkEstimate(str);
		wrkest.printWork();
		
		streamGraph = new StreamGraph((new GraphFlattener(str)).top);

		streamGraph.createStaticStreamGraphs();
		int numSsgs = streamGraph.getStaticSubGraphs().length;
		System.err.println("Number of StaticStreamGraphs = " + numSsgs);
		if (numSsgs > 1) {
			if (debugPrint)
				streamGraph.dumpStaticStreamGraph();
			System.exit(1);
		}

		SIRPortal portals[] = SIRPortal.getPortals();
		if (portals.length > 0) {
			System.out.println("Contains portals : " + portals.length);
			System.exit(1);
		}

	
		BufferedWriter outputStream = null;
		try {
			File outputFile = new File("app.in");
			outputStream = new BufferedWriter(new FileWriter(outputFile));

			
			StaticStreamGraph ssg = (StaticStreamGraph) streamGraph.getStaticSubGraphs()[0];
			new VarDeclRaiser().raiseVars(ssg.getTopLevelSIR());
			new VarDeclRaiser().raiseVars(ssg.getTopLevelSIR());

			FlatNode strTop = ssg.getTopLevel();
			RateMatcher rateMatcher = new RateMatcher(ssg);
			
			/**********/
			SimulatorInputFilterNameVisitor filterNameVisitor = new SimulatorInputFilterNameVisitor();
			strTop.accept(filterNameVisitor, null, true);
			System.out.println(filterNameVisitor.getFilterNames());

			outputStream.write(filterNameVisitor.getFilterNames());
			
			/**********/
			DMAInformationVisitor dmaInfoVisitor = new DMAInformationVisitor();
			strTop.accept(dmaInfoVisitor, null, true);
			System.out.println(dmaInfoVisitor.getDmaInfo());

			outputStream.write("\n\n");
			outputStream.write(dmaInfoVisitor.getDmaInfo());
			outputStream.write("\n***\n");
			
			/**********/
			FilterInformationVisitor filterInformationVisitor = new FilterInformationVisitor(
					rateMatcher, wrkest);
			strTop.accept(filterInformationVisitor, null, true);
			System.out.println(filterInformationVisitor.getFilterInfoString());

			outputStream.write("\n");
			outputStream.write(filterInformationVisitor.getFilterInfoString());
			outputStream.write("\n***\n");

			/**********/
			String streamSimulatorDirectory = System.getenv("STREAM_SIMULATOR_HOME");
			MachineModel model = MachineModel
					.getInstance(streamSimulatorDirectory + "/config/machine.model");

			outputStream.write("\n");
			ExecutionTimeVisitor executionTimeVisitor = new ExecutionTimeVisitor(model, wrkest, rateMatcher);
			strTop.accept(executionTimeVisitor, null, true);
			outputStream.write(executionTimeVisitor.getExecutionTimes());
			outputStream.write("\n***");
			
			outputStream.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
