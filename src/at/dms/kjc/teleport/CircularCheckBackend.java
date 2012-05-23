// $Header: /n/tiamat/y/repository/StreamItNew/streams/src/at/dms/kjc/cluster/ClusterBackend.java,v 1.1 2009/02/24 18:14:55 hormati Exp $
package at.dms.kjc.teleport;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import at.dms.kjc.JInterfaceDeclaration;
import at.dms.kjc.StreamItDot;
import at.dms.kjc.cluster.LatencyConstraints;
import at.dms.kjc.common.CodegenPrintWriter;
import at.dms.kjc.iterator.IterFactory;
import at.dms.kjc.linprog.LPSolve;
import at.dms.kjc.sir.SIRFilter;
import at.dms.kjc.sir.SIRGlobal;
import at.dms.kjc.sir.SIRHelper;
import at.dms.kjc.sir.SIRInterfaceTable;
import at.dms.kjc.sir.SIRLatency;
import at.dms.kjc.sir.SIRLatencyMax;
import at.dms.kjc.sir.SIRLatencyRange;
import at.dms.kjc.sir.SIRLatencySet;
import at.dms.kjc.sir.SIROperator;
import at.dms.kjc.sir.SIRPipeline;
import at.dms.kjc.sir.SIRPortal;
import at.dms.kjc.sir.SIRPortalSender;
import at.dms.kjc.sir.SIRSplitJoin;
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

/**
 */
public class CircularCheckBackend {

    // public static Simulator simulator;
    // get the execution counts from the scheduler

    /**
     * Print out some debugging info if true.
     */
    protected static boolean debugging = false;


    // /**
    // * Used to iterate over str structure ignoring flattening.
    // * <br/> Also used in {@link ClusterCodeGenerator} and {@link
    // FlatIrToCluster2}
    // */
    static streamit.scheduler2.iriter.Iterator topStreamIter;

    /**
     * The cluster backend. Called via reflection.
     */
    public static void run(SIRStream str, JInterfaceDeclaration[] interfaces,
            SIRInterfaceTable[] interfaceTables, SIRStructure[] structs,
            SIRHelper[] helpers, SIRGlobal global) {

        System.out.println("Entry to CircularCheckBackend");

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
        System.err.println(" done.");   // announce end of ConstantProp and Unroll

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
        Set<Vertex> vertices = createVertices(scheduler);
        Set<Edge> edges = new HashSet<Edge>();

        addCausalityDependencyEdges(scheduler, edges, vertices);

        addDataDependencyEdges(scheduler, edges, vertices);

        addControlDependencyEdges(scheduler, edges, vertices);

        if (debugging)
            printGraph(edges);
        
        System.gc();
        
        System.err.println("\nStart checking ... with " + vertices.size() + " nodes");

        //checking for zero edges
        if (zeroCycleDetection(edges, vertices)) {
            System.out.println("Found a circular dependency");
            System.exit(1);
        }
        
        long endTime = (new Date()).getTime();

        System.out.println("\nCircular checking time: " + (endTime - startTime) + " n vertices " + vertices.size());

        startTime = endTime;
        
        //sorting
        System.err.println("\nStart sorting ...");
        System.gc();
        
        double gamma = topoSort(edges, vertices);

        endTime = (new Date()).getTime();

        System.out.println("\nSorting time: " + (endTime - startTime) + " n vertices " + vertices.size());

        System.exit(0);
    }


    /*
     * O(n^3) 
     */
    protected static boolean zeroCycleDetection(Set<Edge> edges,
            Set<Vertex> vertices) {
        int n = vertices.size();

        Object[] vs = vertices.toArray();

        Integer[][] path = new Integer[n][n];
        int[][] next = new int[n][n];

        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++) {
                next[i][j] = -1;

                if (i == j)
                    path[i][j] = 0;
                else
                    path[i][j] = null;

                Vertex u = (Vertex) vs[i];
                Vertex v = (Vertex) vs[j];
                //get the edge weight
                Edge e = getEdge(u, v, edges);
                if (e != null) {
                    path[i][j] = e.getWeight();
                    if (debugging)
                        System.out.println("Edge (" + u.getStream().getIdent()
                                + ", " + u.getIndex() + ") - " + e.getWeight()
                                + " -> (" + v.getStream().getIdent() + ","
                                + v.getIndex() + ")");
                }
            }

        for (int k = 0; k < n; k++)
            for (int i = 0; i < n; i++)
                for (int j = 0; j < n; j++) {
                    if (path[i][k] != null && path[k][j] != null) {
                        if (path[i][j] == null) { //infinity
                            path[i][j] = path[i][k] + path[k][j];
                            next[i][j] = k;
                        } else {
                            if (path[i][j] > path[i][k] + path[k][j]) {
                                path[i][j] = path[i][k] + path[k][j];
                                next[i][j] = k;
                            }
                        }
                    }
                }

        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++) {
                if (i != j && path[i][j] != null && path[j][i] != null) {
                    if (path[i][j] + path[j][i] <= 0) {
                        List<Integer> vers = new LinkedList<Integer>();
                        GetPath(i, j, path, next, vers);
                        vers.add(0, i);
                        vers.add(j);
                        Vertex lastV = null;
                        for (int index : vers) {
                            Vertex v = (Vertex) vs[index];

                            if (lastV != null) {
                                System.out.print("["
                                        + getEdge(lastV, v, edges).getWeight()
                                        + "]-> ");
                            }

                            System.out.print("(" + v.getStream().getIdent()
                                    + "," + v.getIndex() + ") -");
                            lastV = v;
                        }
                        vers.clear();
                        GetPath(j, i, path, next, vers);
                        vers.add(i);
                        for (int index : vers) {
                            Vertex v = (Vertex) vs[index];
                            if (lastV != null) {
                                System.out.print("["
                                        + getEdge(lastV, v, edges).getWeight()
                                        + "]-> ");
                            }
                            System.out.print("(" + v.getStream().getIdent()
                                    + "," + v.getIndex() + ") -");
                            lastV = v;
                        }
                        return true;
                    }
                }
            }
        return false;
    }

    static void GetPath(int i, int j, Integer[][] path, int[][] next,
            List<Integer> vertices) {
        if (path[i][j] != null) {
            int intermediate = next[i][j];
            if (intermediate != -1) {
                GetPath(i, intermediate, path, next, vertices);
                vertices.add(intermediate);
                GetPath(intermediate, j, path, next, vertices);
            }
        }
    }

    protected static double topoSort(Set<Edge> edges, Set<Vertex> vertices) {

        if (debugging) {
            CodegenPrintWriter p;
            try {
                p = new CodegenPrintWriter(new BufferedWriter(new FileWriter(
                        "sort.lp", false)));

                //objective function
                for (Edge e : edges) {
                    assert e != null : "wrong value for an edge";
                    p.print("-sigma_" + e.getName() + " ");
                }
                p.println(";");

                for (Edge e : edges) {
                    p.println("pi_" + e.getSrc().getName() + " - pi_"
                            + e.getDst().getName() + " + " + e.getWeight()
                            + " gamma  >= 0;");
                    p.println("pi_" + e.getSrc().getName() + " - pi_"
                            + e.getDst().getName() + " + " + e.getWeight()
                            + " gamma " + "sigma_" + e.getName() + " >= 1;");
                    p.println("sigma_" + e.getName() + " >= 0;");

                }

                p.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        int nVars = edges.size() + vertices.size() + 1;
        int nConstraints = edges.size() * 3;

        LPSolve solver = new LPSolve(nConstraints, nVars);

        double[] obj = solver.getEmptyConstraint();

        for (int i = 0; i < nVars; i++) {
            if (i < edges.size())
                obj[i] = 1;
            else
                obj[i] = 0;
        }

        solver.setObjective(obj);

        Object[] es = edges.toArray();
        Object[] vs = vertices.toArray();

        for (int i = 0; i < es.length; i++) {
            Edge e = (Edge) es[i];
            double[] constraints1 = solver.getEmptyConstraint();
            double[] constraints2 = solver.getEmptyConstraint();
            double[] constraints3 = solver.getEmptyConstraint();

            int u = -1, v = -1;

            for (int j = 0; j < vs.length; j++) {
                if (vs[j] == e.getSrc()) {
                    u = j;
                    if (v != -1)
                        break;
                }
                if (vs[j] == e.getDst()) {
                    v = j;
                    if (u != -1)
                        break;
                }
            }

            constraints1[u + es.length] = -1;
            constraints1[v + es.length] = 1;
            constraints1[nVars - 1] = e.getWeight();

            solver.addConstraintGE(constraints1, 0);

            constraints2[u + es.length] = -1;
            constraints2[v + es.length] = 1;
            constraints2[nVars - 1] = e.getWeight();
            constraints2[i] = 1;

            solver.addConstraintGE(constraints2, 1);

            constraints3[i] = 1;

            solver.addConstraintGE(constraints3, 0);
        }

        double[] result = solver.solve();

        double gamma = result[nVars - 1];
        if (debugging)
            System.out.println("Gamma = " + gamma);

        double maxPi = Double.NEGATIVE_INFINITY;
        double minPi = Double.POSITIVE_INFINITY;
        for (int i = 0; i < vs.length; i++) {
            Vertex v = ((Vertex) vs[i]);
            v.setPi(result[i + es.length]);
            if (maxPi < v.getPi()) {
                maxPi = v.getPi();
            }

            if (minPi > v.getPi())
                minPi = v.getPi();

            if (debugging)
                System.out.println("Vertex " + v.getName() + " pi = "
                        + v.getPi());
        }

        int iterationRange = (int) Math.ceil((maxPi - minPi) / gamma);

        int currentIteration = iterationRange + 2;

        double currentIterLowerBound = minPi - gamma * currentIteration;
        double currentIterUpperBound = maxPi - gamma * currentIteration;

        Map<Vertex, Integer> v2Iter = new HashMap<Vertex, Integer>();

        for (int i = 0; i < vs.length; i++) {
            Vertex v = (Vertex) vs[i];

            //compute iteration of the vertex
            int iter = (int) Math.ceil((v.getPi() - currentIterUpperBound)
                    / gamma);
            v2Iter.put(v, iter);
        }

        List<Vertex> verticesList = new ArrayList<Vertex>(vertices);
        java.util.Comparator<Vertex> comparer = new Comparator();
        ((Comparator) comparer).gamma = gamma;
        ((Comparator) comparer).v2Iter = v2Iter;
        Collections.sort(verticesList, comparer);

        if (debugging) {
            System.out.println("Sorted list:");
            for (Vertex v : verticesList) {
                int offset = (v2Iter.get(v) - currentIteration);
                System.out.println("Vertex "
                        + v.getName()
                        + " pi = "
                        + v.getPi()
                        + " iter: n "
                        + (offset == 0 ? "" : (offset > 0 ? "+ " + offset
                                : offset)) + " value "
                        + (v.getPi() - gamma * (currentIteration + offset)));
                //                System.out.print("\\pi^*_{" + v.getName() + "}="
                //                        + (int) v.getPi() + ",");
            }

            for (Vertex v : verticesList) {
                int offset = (v2Iter.get(v) - currentIteration);
                System.out.print(v.getName()
                        + "^{n"
                        + (offset == 0 ? "" : (offset > 0 ? "+ " + offset
                                : offset)) + "},");
            }
        }

        return gamma;
    }

    protected static Edge getEdge(Vertex u, Vertex v, Set<Edge> edges) {
        //        for (Edge e : edges) {
        //            if (e.getSrc() == u && e.getDst() == v) {
        //                return e;
        //            }
        //        }
        for (Edge e : u.outEdges) {
            if (e.getDst() == v)
                return e; //FIXME: What happens if there are multiple edges
        }
        return null;
    }

    protected static int numVertices(streamit.scheduler2.Scheduler scheduler) {
        int num = 0;
        
        HashMap strRepetitions = scheduler.getExecutionCounts()[1];

        for (Object key : strRepetitions.keySet()) {
            if (!(key instanceof SIRFilter))
                continue;
            int[] reps = (int[]) strRepetitions.get(key);
            num += reps[0];
        }
        
        return num;
    }

    protected static void addCausalityDependencyEdges(
            streamit.scheduler2.Scheduler scheduler, Set<Edge> edges,
            Set<Vertex> vertices) {

        HashMap strRepetitions = scheduler.getExecutionCounts()[1];

        for (Object key : strRepetitions.keySet()) {
            if (!(key instanceof SIRFilter))
                continue;
            int[] reps = (int[]) strRepetitions.get(key);
            for (int i = 1; i < reps[0]; i++) {
                Vertex v = getVertex((SIRStream) key, i, vertices);
                Vertex u = getVertex((SIRStream) key, i + 1, vertices);

                if (u != null && v != null) {
                    Edge e = new Edge(u, v, 0);
                    edges.add(e);
                }
            }

            Vertex u = getVertex((SIRStream) key, 1, vertices);
            Vertex v = getVertex((SIRStream) key, reps[0], vertices);
            Edge e = new Edge(u, v, 1);
            edges.add(e);
        }

    }

    protected static void addDataDependencyEdges(
            streamit.scheduler2.Scheduler scheduler, Set<Edge> edges,
            Set<Vertex> vertices) {

        HashMap strRepetitions = scheduler.getExecutionCounts()[1];

        streamit.scheduler2.SDEPData sdep;
        streamit.scheduler2.constrained.Scheduler cscheduler = streamit.scheduler2.constrained.Scheduler
                .createForSDEP(topStreamIter);

        for (Object key : strRepetitions.keySet()) {
            if (!(key instanceof SIRFilter))
                continue;
            int[] reps = (int[]) strRepetitions.get(key);
            SIRStream str = (SIRStream) key;

            for (SIRFilter f : findClosestUpstreamFilters(str, false)) {

                streamit.scheduler2.iriter.Iterator srcIter = IterFactory
                        .createFactory().createIter(f);
                streamit.scheduler2.iriter.Iterator dstIter = IterFactory
                        .createFactory().createIter(str);

                try {
                    sdep = cscheduler.computeSDEP(srcIter, dstIter);

                    if (debugging) {
                        System.out.println("\n");
                        System.out.println("Source(" + f.getName()
                                + ") --> Sink(" + str.getName()
                                + ") Dependency:\n");

                        System.out.println("  Source Init Phases: "
                                + sdep.getNumSrcInitPhases());
                        System.out.println("  Destn. Init Phases: "
                                + sdep.getNumDstInitPhases());
                        System.out.println("  Source Steady Phases: "
                                + sdep.getNumSrcSteadyPhases());
                        System.out.println("  Destn. Steady Phases: "
                                + sdep.getNumDstSteadyPhases());
                    }

                    for (int t = 0; t < Math.max(sdep.getNumSrcSteadyPhases(),
                            sdep.getNumDstSteadyPhases()); t++) {
                        int phase = sdep.getSrcPhase4DstPhase(t);
                        int phaserev = sdep.getDstPhase4SrcPhase(t);
                        if (debugging)
                            System.out.println("sdep [" + t + "] = " + phase
                                    + " reverse_sdep[" + t + "] = " + phaserev);
                    }

                    for (int i = 1; i <= reps[0]; i++) {
                        Vertex u = getVertex(str, i, vertices);
                        int srcPhase = sdep.getSrcPhase4DstPhase(i);
                        int srcReps = ((int[]) strRepetitions.get(f))[0];
                        int relativeExe = (srcPhase - 1) % srcReps + 1;
                        int srcIteration = (srcPhase - 1) / srcReps;
                        Vertex v = getVertex(f, relativeExe, vertices);
                        Edge e = new Edge(u, v, -srcIteration);
                        edges.add(e);
                    }

                } catch (streamit.scheduler2.constrained.NoPathException ex) {
                    System.out.println(ex);

                }
            }

        }

    }

    protected static void addControlDependencyEdges(
            streamit.scheduler2.Scheduler scheduler, Set<Edge> edges,
            Set<Vertex> vertices) {

        SIRPortal[] portals = SIRPortal.getPortals();
        LatencyConstraints.detectConstraints(portals);

        streamit.scheduler2.SDEPData sdep;
        streamit.scheduler2.constrained.Scheduler cscheduler = streamit.scheduler2.constrained.Scheduler
                .createForSDEP(topStreamIter);

        for (int i = 0; i < portals.length; i++) {

            SIRPortal portal = portals[i];

            for (SIRPortalSender sender : portal.getSenders()) {
                for (SIRStream receiver : portal.getReceivers()) {

                    SIRStream src = sender.getStream();
                    SIRStream dst = receiver;

                    int latency = MinLatency(sender.getLatency());

                    streamit.scheduler2.iriter.Iterator srcIter = IterFactory
                            .createFactory().createIter(src);
                    streamit.scheduler2.iriter.Iterator dstIter = IterFactory
                            .createFactory().createIter(dst);

                    try {
                        boolean downstream = LatencyConstraints
                                .isMessageDirectionDownstream((SIRFilter) src,
                                        (SIRFilter) dst);

                        if (downstream)
                            sdep = cscheduler.computeSDEP(srcIter, dstIter);
                        else
                            sdep = cscheduler.computeSDEP(dstIter, srcIter);

                        //compute relative iteration, executions

                        //compute absolute iteration of sources
                        int srcIteration = 10000;
                        HashMap strRepetitions = scheduler.getExecutionCounts()[1];

                        int[] reps = (int[]) strRepetitions.get(src);

                        for (int j = 1; j <= reps[0]; j++) {
                            int srcAbsExe = srcIteration * reps[0] + j;

                            int dstAbsExe;

                            if (downstream)
                                dstAbsExe = sdep.getDstPhase4SrcPhase(srcAbsExe
                                        + latency);
                            else
                                dstAbsExe = sdep.getSrcPhase4DstPhase(srcAbsExe
                                        + latency) + 1;

                            int dstReps = ((int[]) strRepetitions.get(dst))[0];
                            int dstIteration = (dstAbsExe - 1) / dstReps;

                            int dstExe = (dstAbsExe - 1) % dstReps + 1;

                            int relativeIteration = dstIteration - srcIteration;

                            Vertex u = getVertex(dst, dstExe, vertices);
                            Vertex v = getVertex(src, j, vertices);

                            Edge e = new Edge(u, v, relativeIteration);
                            edges.add(e);
                        }

                    } catch (streamit.scheduler2.constrained.NoPathException ex) {
                        System.out.println(ex);

                    }
                }
            }
        }
    }

    private static Set<SIRFilter> findClosestUpstreamFilters(SIRStream str,
            boolean inside) {

        Set<SIRFilter> filters = new HashSet<SIRFilter>();

        if (str == null)
            return filters;

        if (str instanceof SIRSplitJoin && inside) {
            List<SIROperator> children = new LinkedList<SIROperator>(
                    ((SIRSplitJoin) str).getChildren());
            children.remove(0);
            children.remove(children.size() - 1);
            for (SIROperator child : children) {
                if (child instanceof SIRFilter) {
                    filters.add((SIRFilter) child);
                } else {
                    filters.addAll(findClosestUpstreamFilters(
                            (SIRStream) child, true));
                }
            }
        } else if (str instanceof SIRPipeline && inside) {
            SIRStream lastChild = ((SIRPipeline) str).get(((SIRPipeline) str)
                    .size() - 1);
            if (lastChild instanceof SIRFilter) {
                filters.add((SIRFilter) lastChild);
            } else {
                filters.addAll(findClosestUpstreamFilters(
                        (SIRStream) lastChild, true));
            }
        } else {
            //outside or SIRFilter
            if (str.getParent() instanceof SIRSplitJoin) {
                filters.addAll(findClosestUpstreamFilters(str.getParent(),
                        false));
            } else if (str.getParent() instanceof SIRPipeline) {
                SIRPipeline parent = (SIRPipeline) str.getParent();
                for (int i = 0; i < parent.size(); i++) {
                    if (parent.get(i) == str) {
                        if (i > 0) {
                            if (parent.get(i - 1) instanceof SIRFilter) {
                                filters.add((SIRFilter) parent.get(i - 1));
                            } else {
                                filters.addAll(findClosestUpstreamFilters(
                                        parent.get(i - 1), true));
                            }
                        } else {
                            filters.addAll(findClosestUpstreamFilters(
                                    str.getParent(), false));
                        }

                        break;
                    }
                }

            }
        }

        return filters;
    }

    /*
     * 
     */
    protected static Set<Vertex> createVertices(
            streamit.scheduler2.Scheduler scheduler) {
        Set<Vertex> vertices = new HashSet<Vertex>();

        HashMap strRepetitions = scheduler.getExecutionCounts()[1];

        for (Object key : strRepetitions.keySet()) {
            
            if (!(key instanceof SIRFilter))
                continue;
            int[] reps = (int[]) strRepetitions.get(key);
            for (int i = 0; i < reps[0]; i++) {
                Vertex v = new Vertex((SIRStream) key, i + 1);
                vertices.add(v);
                if (debugging)
                    System.out.println("Vertex " + v.getStream().getName()
                            + " " + v.getIndex());
            }
        }
        
        return vertices;
    }

    static Vertex getVertex(SIRStream str, int index, Set<Vertex> vertices) {
        for (Vertex v : vertices) {
            if (v.str == str && v.index == index) {
                return v;
            }
        }
        System.out.println("Could not find " + str.getName() + " " + index);
        return null;
    }

    /**
     * Returns min latency associated with a {@link SIRLatency} object
     * @param latency the latency object
     * @return the maximum latency as integer
     */

    private static int MinLatency(SIRLatency latency) {

        if (latency instanceof SIRLatencyMax) {
            return ((SIRLatencyMax) latency).getMax();
        }

        if (latency instanceof SIRLatencyRange) {
            return ((SIRLatencyRange) latency).getMax();
        }

        if (latency instanceof SIRLatencySet) {
            Iterator<Integer> it = ((SIRLatencySet) latency).iterator();

            int min = 1000000;

            while (it.hasNext()) {
                Integer i = it.next();

                if (i.intValue() < min)
                    min = i.intValue();
            }

            return min;
        }

        // this should never be reached!

        return 0;
    }

    static void printGraph(Set<Edge> edges) {
        CodegenPrintWriter p;
        try {
            p = new CodegenPrintWriter(new BufferedWriter(new FileWriter(
                    "depgraph.dot", false)));
            p.println("digraph G1 {");

            for (Edge e : edges) {
                assert e != null : "wrong value for an edge";
                p.println(e.getSrc().getStream().getIdent() + "_"
                        + e.getSrc().getIndex() + " -> "
                        + e.getDst().getStream().getIdent() + "_"
                        + e.getDst().getIndex() + "[label=\"" + e.getWeight()
                        + "\"];");
            }
            p.println("}");
            p.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
    * Just some debugging output.
    */
    protected static void debugOutput(SIRStream str) {
        streamit.scheduler2.constrained.Scheduler cscheduler = streamit.scheduler2.constrained.Scheduler
                .createForSDEP(topStreamIter);

        //cscheduler.computeSchedule(); //"Not Implemented"

        if (!(str instanceof SIRPipeline))
            return;

        SIRPortal[] portals = SIRPortal.getPortals();
        LatencyConstraints.detectConstraints(portals);

        for (int i = 0; i < portals.length; i++) {

            SIRPortal portal = portals[i];

            for (SIRPortalSender sender : portal.getSenders()) {
                for (SIRStream receiver : portal.getReceivers()) {

                    SIRStream src = sender.getStream();
                    SIRStream dst = receiver;

                    streamit.scheduler2.iriter.Iterator srcIter = IterFactory
                            .createFactory().createIter(src);
                    streamit.scheduler2.iriter.Iterator dstIter = IterFactory
                            .createFactory().createIter(dst);

                    streamit.scheduler2.SDEPData sdep;

                    try {
                        boolean downstream = LatencyConstraints
                                .isMessageDirectionDownstream((SIRFilter) src,
                                        (SIRFilter) dst);

                        if (downstream)
                            sdep = cscheduler.computeSDEP(srcIter, dstIter);
                        else
                            sdep = cscheduler.computeSDEP(dstIter, srcIter);

                        if (debugging) {
                            System.out.println("\n");
                            System.out.println("Source(" + src.getName()
                                    + ") --> Sink(" + dst.getName()
                                    + ") Dependency:\n");

                            System.out.println("  Source Init Phases: "
                                    + sdep.getNumSrcInitPhases());
                            System.out.println("  Destn. Init Phases: "
                                    + sdep.getNumDstInitPhases());
                            System.out.println("  Source Steady Phases: "
                                    + sdep.getNumSrcSteadyPhases());
                            System.out.println("  Destn. Steady Phases: "
                                    + sdep.getNumDstSteadyPhases());
                        }

                        for (int t = 0; t < Math.max(
                                sdep.getNumSrcSteadyPhases(),
                                sdep.getNumDstSteadyPhases()); t++) {
                            int phase = sdep.getSrcPhase4DstPhase(t);
                            int phaserev = sdep.getDstPhase4SrcPhase(t);
                            if (debugging)
                                System.out.println("sdep [" + t + "] = "
                                        + phase + " reverse_sdep[" + t + "] = "
                                        + phaserev);
                        }

                    } catch (streamit.scheduler2.constrained.NoPathException ex) {
                        System.out.println(ex);

                    }
                }
            }
        }
        //        DoSchedules.findSchedules(topStreamIter, firstIter, str);
    }

    static class Edge {
        private Vertex src, dst;

        private int weight = 0;

        public Edge(Vertex src, Vertex dst) {
            assert (src != dst);
            this.src = src;
            this.dst = dst;
            this.src.outEdges.add(this);
        }

        public Edge(Vertex src, Vertex dst, int weight) {
            assert (src != dst);
            this.src = src;
            this.dst = dst;
            this.weight = weight;
            this.src.outEdges.add(this);
            //this.dst.inEdges.add(this);
        }

        public void setWeight(int weight) {
            this.weight = weight;
        }

        public int getWeight() {
            return weight;
        }

        public Vertex getSrc() {
            return src;
        }

        public Vertex getDst() {
            return dst;
        }

        public String getName() {
            return src.getName() + "_" + src.getIndex() + "_" + dst.getName()
                    + "_" + dst.getIndex();
        }
    }

    static class Vertex {
        SIRStream str; //stream
        int index; //firing of actor
        double pi = Double.POSITIVE_INFINITY;
        double d = Double.POSITIVE_INFINITY; //distance
        int label = -1;
        List<Edge> outEdges;

        //List<Edge> inEdges;

        public Vertex(SIRStream str, int index) {
            this.str = str;
            this.index = index;
            outEdges = new LinkedList<Edge>();
            //            inEdges = new LinkedList<Edge>();
        }

        public SIRStream getStream() {
            return str;
        }

        public int getIndex() {
            return index;
        }

        public String getName() {
            return str.getIdent() + "_" + index;
        }

        public double getPi() {
            return pi;
        }

        public void setPi(double pi) {
            this.pi = pi;
        }
    }

    static class Comparator implements java.util.Comparator<Vertex> {
        public static double gamma;
        //        public static int iteration;
        public static Map<Vertex, Integer> v2Iter = null;

        @Override
        public int compare(Vertex v1, Vertex v2) {
            double a1 = v1.pi - gamma * v2Iter.get(v1);
            double a2 = v2.pi - gamma * v2Iter.get(v2);
            return (int) (a2 - a1);
        }

    }
}
