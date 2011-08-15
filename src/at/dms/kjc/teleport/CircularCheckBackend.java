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
import at.dms.kjc.cluster.DoSchedules;
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

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
//import java.io.*;
//import streamit.scheduler2.print.PrintProgram;
//import streamit.scheduler2.*;
//import streamit.scheduler2.constrained.*;

import streamit.misc.DLListIterator;
import streamit.misc.DLList_const;
import streamit.scheduler2.Schedule;
import streamit.scheduler2.constrained.Filter;
import streamit.scheduler2.constrained.LatencyEdge;
import streamit.scheduler2.constrained.LatencyNode;

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
    static streamit.scheduler2.iriter.Iterator topStreamIter;

    /**
     * The cluster backend. Called via reflection.
     */
    public static void run(SIRStream str, JInterfaceDeclaration[] interfaces,
            SIRInterfaceTable[] interfaceTables, SIRStructure[] structs,
            SIRHelper[] helpers, SIRGlobal global) {

        System.out.println("Entry to CircularCheckBacken");

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
        //        StaticsProp.propagate/* IntoContainers */(str, statics);

        // propagate constants and unroll loop
        System.err.print("Running Constant Prop and Unroll...");

        // Constant propagate and unroll.
        // Set unrolling factor to <= 4 for loops that don't involve
        //  any tape operations.
        //        Unroller.setLimitNoTapeLoops(true, 4);

        ConstantProp.propagateAndUnroll(str);

        System.err.println(" done.");

        // do constant propagation on fields
        System.err.print("Running Constant Field Propagation...");
        ConstantProp.propagateAndUnroll(str, true);

        // construct stream hierarchy from SIRInitStatements
        //		ConstructSIRTree.doit(str);

        // this must be run now, Further passes expect unique names!!!
        //        RenameAll.renameAllFilters(str);

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

        System.err.print("Constrained Scheduler Begin...");
        topStreamIter = IterFactory.createFactory().createIter(str);

        debugOutput(str);

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

        //create graph

        //create vertices
        Set<Vertex> vertices = createVertices(scheduler);
        Set<Edge> edges = new HashSet<Edge>();

        addCausalityEdges(scheduler, edges, vertices);

        addDataDependencyEdges(scheduler, edges, vertices);

        addControlDependencyEdges(scheduler, edges, vertices);

        printGraph(edges);
        
        //checking for zero edges
        if (zeroCycleDetection(edges, vertices))
            System.out.println("Found a circular dependency");
        //sorting

        System.exit(0);
    }

    //    static class PathLengths {
    //        public Set<Integer> lengths;
    //
    //        PathLengths() {
    //            lengths = new HashSet<Integer>();
    //        }
    //    }

    private static boolean zeroCycleDetection(Set<Edge> edges,
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
                            //                            path[i][j] = Math.min(path[i][j], path[i][k]
                            //                                    + path[k][j]);
                            if (path[i][j] > path[i][k] + path[k][j]) {
                                next[i][j] = k;
                                path[i][j] = path[i][k] + path[k][j];
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
                            
                            if(lastV != null) {
                                System.out.print("[" + getEdge(lastV, v, edges).getWeight() + "]-> ");
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
                            if(lastV != null) {
                                System.out.print("[" + getEdge(lastV, v, edges).getWeight() + "]-> ");
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
        //        PathLengths[][][] alpha = new PathLengths[n][n][n+1];
        //
        //        //initialize
        //        for (int i = 0; i < n; i++)
        //            for (int j = 0; j < n; j++)
        //                for (int k = 0; k < n+1; k++)
        //                    alpha[i][j][k] = new PathLengths();
        //        for (int i = 0; i < n; i++)
        //            for (int j = 0; j < n; j++) {
        //                Vertex u = (Vertex) vs[i];
        //                Vertex v = (Vertex) vs[j];
        //
        //                //get the edge weight
        //                Edge e = getEdge(u, v, edges);
        //                if (e != null) {
        //                    alpha[i][j][0].lengths.add(e.getWeight());
        //                }
        //            }
        //
        //        for (int k = 1; k < n+1; k++) {
        //            for (int i = 0; i < n; i++)
        //                for (int j = 0; j < n; j++) {
        //                    alpha[i][j][k].lengths.addAll(alpha[i][j][k - 1].lengths);
        //                    Set<Integer> alphaKKK_1Star = new HashSet<Integer>();
        //                    alphaKKK_1Star.add(0);      //a^0
        //                    Set<Integer> alphaKKK_1I = new HashSet<Integer>(
        //                            alpha[k-1][k-1][k - 1].lengths);
        //                    Set<Integer> mult = null;
        //                    //compute * operator
        //                    while (true) {
        //                        alphaKKK_1Star.addAll(alphaKKK_1I);
        //                        mult = setMult(alphaKKK_1I, alpha[k-1][k-1][k - 1].lengths);
        //
        //                        if (alphaKKK_1I.containsAll(mult)
        //                                && mult.containsAll(alphaKKK_1I)) {
        //                            break;
        //                        }
        //
        //                        alphaKKK_1I = mult;
        //                    }
        //                    
        //                    alpha[i][j][k].lengths
        //                            .addAll(setMult(
        //                                    setMult(alpha[i][k-1][k - 1].lengths,
        //                                            alphaKKK_1Star),
        //                                    alpha[k-1][j][k - 1].lengths));
        //                    
        //                    for(int t = 0; t < n; t++) {
        //                        if(alpha[t][t][k].lengths.contains(0))
        //                            return true;
        //                    }
        //
        //                }
        //        }

        //        return false;
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

    //    static Set<Integer> setMult(Set<Integer> s1, Set<Integer> s2) {
    //        Set<Integer> mult = new HashSet<Integer>();
    //        for (Integer a : s1) {
    //            for (Integer b : s2) {
    //                mult.add(a + b);
    //            }
    //        }
    //        return mult;
    //    }

    private static Edge getEdge(Vertex u, Vertex v, Set<Edge> edges) {
        for (Edge e : edges) {
            if (e.getSrc() == u && e.getDst() == v) {
                return e;
            }
        }
        return null;
    }

    private static void addCausalityEdges(
            streamit.scheduler2.constrained.Scheduler scheduler,
            Set<Edge> edges, Set<Vertex> vertices) {

        HashMap strRepetitions = scheduler.getExecutionCounts()[1];

        for (Object key : strRepetitions.keySet()) {
            if (!(key instanceof SIRFilter))
                continue;
            int[] reps = (int[]) strRepetitions.get(key);
            for (int i = 1; i < reps[0]; i++) {
                Vertex v = getVertex((SIRStream) key, i, vertices);
                Vertex u = getVertex((SIRStream) key, i + 1, vertices);
                Edge e = new Edge(u, v, 0);
                edges.add(e);
            }

            Vertex u = getVertex((SIRStream) key, 1, vertices);
            Vertex v = getVertex((SIRStream) key, reps[0], vertices);
            Edge e = new Edge(u, v, 1);
            edges.add(e);
        }

    }

    private static void addDataDependencyEdges(
            streamit.scheduler2.constrained.Scheduler scheduler,
            Set<Edge> edges, Set<Vertex> vertices) {

        HashMap strRepetitions = scheduler.getExecutionCounts()[1];

        streamit.scheduler2.SDEPData sdep;
        streamit.scheduler2.constrained.Scheduler cscheduler = streamit.scheduler2.constrained.Scheduler
                .createForSDEP(topStreamIter);

        for (Object key : strRepetitions.keySet()) {
            if (!(key instanceof SIRFilter))
                continue;
            int[] reps = (int[]) strRepetitions.get(key);
            SIRStream str = (SIRStream) key;

            for (SIRFilter f : findClosestUpstreamFilters(str, true)) {

                streamit.scheduler2.iriter.Iterator srcIter = IterFactory
                        .createFactory().createIter(f);
                streamit.scheduler2.iriter.Iterator dstIter = IterFactory
                        .createFactory().createIter(str);

                try {
                    sdep = cscheduler.computeSDEP(srcIter, dstIter);

                    System.out.println("\n");
                    System.out.println("Source(" + f.getName() + ") --> Sink("
                            + str.getName() + ") Dependency:\n");

                    System.out.println("  Source Init Phases: "
                            + sdep.getNumSrcInitPhases());
                    System.out.println("  Destn. Init Phases: "
                            + sdep.getNumDstInitPhases());
                    System.out.println("  Source Steady Phases: "
                            + sdep.getNumSrcSteadyPhases());
                    System.out.println("  Destn. Steady Phases: "
                            + sdep.getNumDstSteadyPhases());

                    for (int t = 0; t < Math.max(sdep.getNumSrcSteadyPhases(),
                            sdep.getNumDstSteadyPhases()); t++) {
                        int phase = sdep.getSrcPhase4DstPhase(t);
                        int phaserev = sdep.getDstPhase4SrcPhase(t);
                        System.out.println("sdep [" + t + "] = " + phase
                                + " reverse_sdep[" + t + "] = " + phaserev);
                    }

                    for (int i = 1; i <= reps[0]; i++) {
                        Vertex u = getVertex(str, i, vertices);
                        Vertex v = getVertex(f, sdep.getSrcPhase4DstPhase(i),
                                vertices);
                        Edge e = new Edge(u, v, 0);
                        edges.add(e);
                    }

                } catch (streamit.scheduler2.constrained.NoPathException ex) {
                    System.out.println(ex);

                }
            }

        }

    }

    private static void addControlDependencyEdges(
            streamit.scheduler2.constrained.Scheduler scheduler,
            Set<Edge> edges, Set<Vertex> vertices) {

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

                    int latency = LatencyConstraints.MinLatency(sender
                            .getLatency());

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
    private static Set<Vertex> createVertices(
            streamit.scheduler2.constrained.Scheduler scheduler) {
        Set<Vertex> vertices = new HashSet<Vertex>();

        HashMap strRepetitions = scheduler.getExecutionCounts()[1];

        for (Object key : strRepetitions.keySet()) {
            if (!(key instanceof SIRFilter))
                continue;
            int[] reps = (int[]) strRepetitions.get(key);
            for (int i = 0; i < reps[0]; i++) {
                Vertex v = new Vertex((SIRStream) key, i + 1);
                vertices.add(v);
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
        System.out.println("Could not find" + str.getIdent() + " " + index);
        return null;
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
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
    /**
    * Just some debugging output.
    */
    private static void debugOutput(SIRStream str) {
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

                        {
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
                            System.out.println("sdep [" + t + "] = " + phase
                                    + " reverse_sdep[" + t + "] = " + phaserev);
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
            this.src = src;
            this.dst = dst;
        }

        public Edge(Vertex src, Vertex dst, int weight) {
            this.src = src;
            this.dst = dst;
            this.weight = weight;
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
    }

    static class Vertex {
        SIRStream str;
        int index;

        public Vertex(SIRStream str, int index) {
            this.str = str;
            this.index = index;
        }

        public SIRStream getStream() {
            return str;
        }

        public int getIndex() {
            return index;
        }
    }
}
