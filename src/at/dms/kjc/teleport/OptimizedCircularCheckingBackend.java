package at.dms.kjc.teleport;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import at.dms.kjc.JInterfaceDeclaration;
import at.dms.kjc.StreamItDot;
import at.dms.kjc.cluster.LatencyConstraints;
import at.dms.kjc.iterator.IterFactory;
import at.dms.kjc.sir.SIRFilter;
import at.dms.kjc.sir.SIRGlobal;
import at.dms.kjc.sir.SIRHelper;
import at.dms.kjc.sir.SIRInterfaceTable;
import at.dms.kjc.sir.SIRPortal;
import at.dms.kjc.sir.SIRPortalSender;
import at.dms.kjc.sir.SIRStream;
import at.dms.kjc.sir.SIRStructure;
import at.dms.kjc.sir.lowering.ArrayInitExpander;
import at.dms.kjc.sir.lowering.ConstantProp;
import at.dms.kjc.sir.lowering.ConstructSIRTree;
import at.dms.kjc.sir.lowering.EnqueueToInitPath;
import at.dms.kjc.sir.lowering.IntroduceMultiPops;
import at.dms.kjc.sir.lowering.RenameAll;
import at.dms.kjc.sir.lowering.RoundToFloor;
import at.dms.kjc.sir.lowering.SimplifyArguments;
import at.dms.kjc.sir.lowering.SimplifyPopPeekPush;
import at.dms.kjc.sir.lowering.StaticsProp;
import at.dms.kjc.sir.lowering.Unroller;
import at.dms.kjc.sir.lowering.VarDeclRaiser;
import at.dms.kjc.teleport.CircularCheckBackend.Edge;
import at.dms.kjc.teleport.CircularCheckBackend.Vertex;

public class OptimizedCircularCheckingBackend extends CircularCheckBackend {

    /**
     * The cluster backend. Called via reflection.
     */
    public static void run(SIRStream str, JInterfaceDeclaration[] interfaces,
            SIRInterfaceTable[] interfaceTables, SIRStructure[] structs,
            SIRHelper[] helpers, SIRGlobal global) {

        System.out.println("Entry to CircularCheckBackend");

        structures = structs;

        // make arguments to functions be three-address code so can replace max, min, abs
        // and possibly others with macros, knowing that there will be no side effects.
        SimplifyArguments.simplify(str);

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
        System.err.println(" done.");

        // Introduce Multiple Pops where programmer
        // didn't take advantage of them (after parameters are propagated).
        IntroduceMultiPops.doit(str);

        // convert round(x) to floor(0.5+x) to avoid obscure errors
        RoundToFloor.doit(str);

        // add initPath functions
        EnqueueToInitPath.doInitPath(str);

        // construct stream hierarchy from SIRInitStatements
        ConstructSIRTree.doit(str);

        //this must be run now, Further passes expect unique names!!!
        RenameAll.renameAllFilters(str);

        //SIRPrinter printer1 = new SIRPrinter();
        //str.accept(printer1);
        //printer1.close();

        //VarDecl Raise to move array assignments up
        new VarDeclRaiser().raiseVars(str);

        // expand array initializers loaded from a file
        ArrayInitExpander.doit(str);
        System.err.println(" done."); // announce end of ConstantProp and Unroll

        // System.err.println("Analyzing Branches..");
        // new BlockFlattener().flattenBlocks(str);
        // new BranchAnalyzer().analyzeBranches(str);

        SIRPortal.findMessageStatements(str);

        // canonicalize stream graph, reorganizing some splits and joins
        //        Lifter.liftAggressiveSync(str);

        // Unroll and propagate maximally within each (not phased) filter.
        // Initially justified as necessary for IncreaseFilterMult which is
        // now obsolete.
        StreamItDot.printGraph(str, "canonical-graph.dot");

        // run constrained scheduler

        if (debugging)
            System.err.print("Constrained Scheduler Begin...");
        topStreamIter = IterFactory.createFactory().createIter(str);

        debugOutput(str);

        System.gc();

        long startTime = (new Date()).getTime();

        streamit.scheduler2.Scheduler scheduler = streamit.scheduler2.minlatency.Scheduler
                .create(topStreamIter);

        scheduler.computeSchedule();
        //        scheduler.computeBufferUse();
        scheduler.getOptimizedInitSchedule();
        scheduler.getOptimizedSteadySchedule();

        if (debugging)
            scheduler.printReps();
        //cscheduler.computeSchedule();

        if (debugging)
            new streamit.scheduler2.print.PrintGraph()
                    .printProgram(topStreamIter);
        //              new streamit.scheduler2.print.PrintProgram().printProgram(topStreamIter);

        // end constrained scheduler

        // calculate latency constraints for all portals
        // and save for later query.
        LatencyConstraints.detectConstraints(SIRPortal.getPortals());

        //create graph

        //create vertices
        Set<SIRStream> streams = new HashSet<SIRStream>();
        Set<Vertex> vertices = createVertices(scheduler, streams);

        System.err.println("Num vertices " + vertices.size());

        Set<Edge> edges = new HashSet<Edge>();

        addCausalityDependencyEdges(scheduler, edges, vertices, streams);

        addDataDependencyEdges(scheduler, edges, vertices, streams);

        addControlDependencyEdges(scheduler, edges, vertices);

        if (debugging)
            printGraph(edges);

        System.gc();

        System.err.println("\nStart checking ... with " + vertices.size()
                + " nodes");

        //checking for zero edges
        if (zeroCycleDetection(edges, vertices)) {
            System.out.println("Found a circular dependency");
            System.exit(1);
        }

        long endTime = (new Date()).getTime();

        System.out.println("\nCircular checking time: " + (endTime - startTime)
                + " n vertices " + vertices.size());

        startTime = endTime;

        //sorting
        System.err.println("\nStart sorting ...");
        System.gc();

        double gamma = topoSort(edges, vertices);

        endTime = (new Date()).getTime();

        System.out.println("\nSorting time: " + (endTime - startTime)
                + " n vertices " + vertices.size());

        System.exit(0);
    }

    protected static Set<Vertex> createVertices(
            streamit.scheduler2.Scheduler scheduler, Set<SIRStream> streams) {

        Set<Vertex> vertices = new HashSet<Vertex>();

        HashMap strRepetitions = scheduler.getExecutionCounts()[1];

        SIRPortal[] portals = SIRPortal.getPortals();
        //        LatencyConstraints.detectConstraints(portals);

        for (int p = 0; p < portals.length; p++) {

            SIRPortal portal = portals[p];

            for (SIRPortalSender sender : portal.getSenders()) {
                SIRStream str = sender.getStream();
                if (!streams.contains(str)) {
                    streams.add(str);

                    int[] reps = (int[]) strRepetitions.get(str);

                    for (int i = 0; i < reps[0]; i++) {
                        Vertex v = new Vertex((SIRStream) str, i + 1);
                        vertices.add(v);
                        if (debugging)
                            System.out.println("Vertex "
                                    + v.getStream().getName() + " "
                                    + v.getIndex());
                    }
                }
            }

            for (SIRStream str : portal.getReceivers()) {
                if (!streams.contains(str)) {
                    int[] reps = (int[]) strRepetitions.get(str);

                    for (int i = 0; i < reps[0]; i++) {
                        Vertex v = new Vertex((SIRStream) str, i + 1);
                        vertices.add(v);
                        if (debugging)
                            System.out.println("Vertex "
                                    + v.getStream().getName() + " "
                                    + v.getIndex());
                    }
                }
            }
        }

        return vertices;
    }

    protected static void addCausalityDependencyEdges(
            streamit.scheduler2.Scheduler scheduler, Set<Edge> edges,
            Set<Vertex> vertices, Set<SIRStream> streams) {

        HashMap strRepetitions = scheduler.getExecutionCounts()[1];

        for (SIRStream str : streams) {

            int[] reps = (int[]) strRepetitions.get(str);
            for (int i = 1; i < reps[0]; i++) {
                Vertex v = getVertex((SIRStream) str, i, vertices);
                Vertex u = getVertex((SIRStream) str, i + 1, vertices);

                if (u != null && v != null) {
                    Edge e = new Edge(u, v, 0);
                    edges.add(e);
                }
            }

            Vertex u = getVertex((SIRStream) str, 1, vertices);
            Vertex v = getVertex((SIRStream) str, reps[0], vertices);
            Edge e = new Edge(u, v, 1);
            edges.add(e);
        }

    }

    protected static void addDataDependencyEdges(
            streamit.scheduler2.Scheduler scheduler, Set<Edge> edges,
            Set<Vertex> vertices, Set<SIRStream> streams) {

        HashMap strRepetitions = scheduler.getExecutionCounts()[1];

        streamit.scheduler2.SDEPData sdep;
        streamit.scheduler2.constrained.Scheduler cscheduler = streamit.scheduler2.constrained.Scheduler
                .createForSDEP(topStreamIter);

        Object[] strs = streams.toArray();

        //for each pair of vertices
        for (int x = 0; x < strs.length; x++) {
            for (int y = x + 1; y < strs.length; y++) {
                SIRStream s1 = (SIRStream) strs[x];
                SIRStream s2 = (SIRStream) strs[y];

                streamit.scheduler2.iriter.Iterator s1Iter = IterFactory
                        .createFactory().createIter(s1);
                streamit.scheduler2.iriter.Iterator s2Iter = IterFactory
                        .createFactory().createIter(s2);

                try {
                    streamit.scheduler2.iriter.Iterator srcIter, dstIter;

                    SIRStream upstr, downstr; //downstream stream
                    downstr = s2;
                    upstr = s1;
                    srcIter = s1Iter;
                    dstIter = s2Iter;

                    try {
                        sdep = cscheduler.computeSDEP(s1Iter, s2Iter);

                    } catch (streamit.scheduler2.constrained.NoPathException ex) {
                        if (debugging)
                            System.out.println(ex);
                        sdep = cscheduler.computeSDEP(s2Iter, s1Iter);
                        downstr = s1;
                        upstr = s2;
                        srcIter = s2Iter;
                        dstIter = s1Iter;
                    }

                    int[] reps = (int[]) strRepetitions.get(downstr);

                    if (debugging)
                        for (int t = 0; t < Math.max(
                                sdep.getNumSrcSteadyPhases(),
                                sdep.getNumDstSteadyPhases()); t++) {
                            int phase = sdep.getSrcPhase4DstPhase(t);
                            int phaserev = sdep.getDstPhase4SrcPhase(t);

                            System.out.println("sdep [" + t + "] = " + phase
                                    + " reverse_sdep[" + t + "] = " + phaserev);
                        }

                    for (int i = 1; i <= reps[0]; i++) {
                        Vertex u = getVertex(downstr, i, vertices);
                        int srcPhase = sdep.getSrcPhase4DstPhase(i);
                        int srcReps = ((int[]) strRepetitions.get(upstr))[0];
                        int relativeExe = (srcPhase - 1) % srcReps + 1;
                        int srcIteration = (srcPhase - 1) / srcReps;
                        Vertex v = getVertex(upstr, relativeExe, vertices);
                        Edge e = new Edge(u, v, -srcIteration);
                        edges.add(e);
                    }

                } catch (streamit.scheduler2.constrained.NoPathException ex) {
                    if (debugging)
                        System.out.println(ex);

                }
            }

        }
    }
}
