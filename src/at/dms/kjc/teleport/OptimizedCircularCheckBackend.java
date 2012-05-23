// $Header: /n/tiamat/y/repository/StreamItNew/streams/src/at/dms/kjc/cluster/ClusterBackend.java,v 1.1 2009/02/24 18:14:55 hormati Exp $
package at.dms.kjc.teleport;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import at.dms.kjc.JInterfaceDeclaration;
import at.dms.kjc.KjcOptions;
import at.dms.kjc.StreamItDot;
import at.dms.kjc.cluster.LatencyConstraints;
import at.dms.kjc.iterator.IterFactory;
import at.dms.kjc.sir.SIRDynamicRateManager;
import at.dms.kjc.sir.SIRGlobal;
import at.dms.kjc.sir.SIRHelper;
import at.dms.kjc.sir.SIRInterfaceTable;
import at.dms.kjc.sir.SIRPortal;
import at.dms.kjc.sir.SIRPortalSender;
import at.dms.kjc.sir.SIRStream;
import at.dms.kjc.sir.SIRStructure;
import at.dms.kjc.sir.lowering.ArrayInitExpander;
import at.dms.kjc.sir.lowering.ConstantProp;
import at.dms.kjc.sir.lowering.RenameAll;
import at.dms.kjc.sir.lowering.SimplifyArguments;
import at.dms.kjc.sir.lowering.SimplifyPopPeekPush;
import at.dms.kjc.sir.lowering.StaticsProp;
import at.dms.kjc.sir.lowering.Unroller;
import at.dms.kjc.sir.lowering.VarDeclRaiser;

/**
 */
public class OptimizedCircularCheckBackend extends CircularCheckBackend {

    final static int WHITE = 0;

    final static int GREY = -1;

    final static int BLACK = 1;

    final static int SCANNED = 0;

    final static int LABELED = -1;

    final static int UNREACHED = 1;

    // public static Simulator simulator;
    // get the execution counts from the scheduler

    /**
     * The cluster backend. Called via reflection.
     */
    public static void run(SIRStream str, JInterfaceDeclaration[] interfaces,
            SIRInterfaceTable[] interfaceTables, SIRStructure[] structs,
            SIRHelper[] helpers, SIRGlobal global) {

        System.err.println("Entry to OptimizedCircularCheckBackend");

        if (KjcOptions.dynamicRatesEverywhere) {
            // force there to be only 1 static sub-graph by making all
            // rates static for now.
            SIRDynamicRateManager.pushConstantPolicy(1);
        }

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
        //IntroduceMultiPops.doit(str);

        // convert round(x) to floor(0.5+x) to avoid obscure errors
        //RoundToFloor.doit(str);

        // add initPath functions
        //EnqueueToInitPath.doInitPath(str);

        // construct stream hierarchy from SIRInitStatements
        //        ConstructSIRTree.doit(str);

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

        streamit.scheduler2.Scheduler scheduler = streamit.scheduler2.singleappearance.Scheduler
                .create(topStreamIter);

        scheduler.computeSchedule();
        //        scheduler.computeBufferUse();
        scheduler.getOptimizedInitSchedule();
        scheduler.getOptimizedSteadySchedule();

        System.err.print("num vertices " + numVertices(scheduler));

        if (debugging)
            scheduler.printReps();
        //cscheduler.computeSchedule();

        if (debugging)
            new streamit.scheduler2.print.PrintGraph()
                    .printProgram(topStreamIter);
        //		new streamit.scheduler2.print.PrintProgram().printProgram(topStreamIter);

        // end constrained scheduler

        // calculate latency constraints for all portals
        // and save for later query.
        LatencyConstraints.detectConstraints(SIRPortal.getPortals());

        //create graph

        //create vertices
        Set<SIRStream> streams = new HashSet<SIRStream>();
        Set<Vertex> vertices = createVertices(scheduler, streams);

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
        if (nonPositiveCycleDetection(edges, vertices)) {
            System.out.println("Found a circular dependency");
            long endTime = (new Date()).getTime();

            System.err.println("\nCircular checking time: "
                    + (endTime - startTime) + " n vertices " + vertices.size());
            System.exit(1);
        }

        long endTime = (new Date()).getTime();

        System.out.println("\nCircular checking time: " + (endTime - startTime)
                + " n vertices " + vertices.size());

        System.err.println("\nCircular checking time: " + (endTime - startTime)
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

                    //TODO: To optimize DGs, we check if there is 
                    //an intermediate stream is also in the graph
                    //by traverse from upstream to downstream
                    //find the paths between up/down streams
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
        }

        return vertices;
    }

    /*
     * A reduced dependency graph is valid when all cycles are positive
     * For a cycle contains x_i, then
     * length(x_i(I)->x_i(J)) = I-J = cycle_length
     * If I - J <= 0 => x_i(J) depends on x_i(I) through sequential dependency
     * As the cycle in the RDG is equivalent to a path in the dependency graph
     * then x_i(I) depends on x_i(J) through the path
     * O(ev)
     * Bellman-Fort-Moore
     */
    protected static boolean nonPositiveCycleDetection(Set<Edge> edges,
            Set<Vertex> vertices) {

        //check for negative cycles first
        //scanning algorithm
        //add an artificial source s
        Vertex s = new Vertex(null, 0);
        //add zero cost edge from artificial source s to each vertex v
        for (Vertex v : vertices) {
            Edge e = new Edge(s, v);
        }

        s.d = 0;
        //add queues
        Queue<Vertex> q = new LinkedList<Vertex>();

        Map<Vertex, Vertex> parent = new HashMap<Vertex, Vertex>();
        Map<Vertex, Vertex> child = new HashMap<Vertex, Vertex>();

        q.add(s);
        s.label = LABELED; //label

        while (!q.isEmpty()) {
            Vertex v = q.remove(); //next labeled node
            //scanning step
            if (v.label == LABELED) //labeled
                for (Edge e : v.outEdges) {
                    Vertex w = e.getDst();

                    //labeling operation
                    if (v.d + e.getWeight() < w.d) {
                        //traverse the subtree rooted at w
                        //using the Tarjan subtree disassembly technique
                        //to check for potential negative cycles
                        Vertex childV = child.get(w); //FIXME: A parent could have multiple children
                        child.remove(w);

                        String path = "";
                        if (debugging) {

                            if (w.getStream() != null)
                                path = "(" + w.getStream().getIdent() + ","
                                        + w.getIndex() + ") -";
                        }
                        Vertex parentV = w;

                        while (childV != null) {
                            if (debugging)
                                path = path
                                        + getEdge(parentV, childV, null)
                                                .getWeight() + "->" + "("
                                        + childV.getStream().getIdent() + ","
                                        + childV.getIndex() + ") -";
                            if (childV == v) {
                                //there is a potential negative or zero cycle
                                //print out that cycle
                                if (debugging)
                                    System.out.println(path);
                                return true;
                            }

                            childV.label = UNREACHED; //unreached

                            Vertex nextV = child.get(childV);
                            parent.remove(childV);
                            child.remove(childV);

                            parentV = childV;
                            childV = nextV;
                        }

                        w.d = v.d + e.getWeight();
                        w.label = LABELED; //labeled

                        if (!q.contains(w)) //possible different FIFO options
                            q.add(w);

                        assert (!(child.containsKey(v)));
                        parent.put(w, v); //parent of w is v
                        child.put(v, w); //child of v is w
                    }
                }
            v.label = SCANNED; //scanned
        }

        //there is no negative cycles, check for zero cycles
        for (Vertex v : vertices) {
            v.label = WHITE; //mark vertex as not visited
        }

        for (Vertex v : vertices) {
            if (v.label == WHITE) {
                if (visit(v)) {
                    return true; //there is a cycle
                }
            }
        }
        return false;
    }

    private static boolean visit(Vertex v) {
        v.label = GREY;
        for (Edge e : v.outEdges) {
            Vertex w = e.getDst();
            if (v.d + e.getWeight() == w.d) {//edge with zero reduction cost only
                if (w.label == GREY) {
                    return true; //there is a cycle of zero cost
                } else if (w.label == WHITE) {
                    if (visit(w)) {
                        return true;
                    }
                }

            }
        }
        v.label = BLACK;
        return false;
    }

}
