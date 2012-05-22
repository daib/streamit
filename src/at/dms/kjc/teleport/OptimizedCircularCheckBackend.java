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
import at.dms.kjc.StreamItDot;
import at.dms.kjc.cluster.LatencyConstraints;
import at.dms.kjc.iterator.IterFactory;
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
public class OptimizedCircularCheckBackend extends CircularCheckBackend {

    // public static Simulator simulator;
    // get the execution counts from the scheduler

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

        numVertices(scheduler);

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

        System.err.println("\nStart checking ... with " + vertices.size()
                + " nodes");

        //checking for zero edges
        if (nonPositiveCycleDetection(edges, vertices)) {
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
        s.label = 0; //label

        while (!q.isEmpty()) {
            Vertex v = q.remove(); //next labeled node
            //scanning step
            if (v.label == 0) //labeled
                for (Edge e : v.outEdges) {
                    Vertex w = e.getDst();

                    //traverse the subtree rooted at w
                    //using the Tarjan subtree disassembly method
                    //to check for a potential negative cycle
                    Vertex childV = child.get(w);
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

                        childV.label = -1; //unreached

                        Vertex nextV = child.get(childV);
                        parent.remove(childV);
                        child.remove(childV);

                        parentV = childV;
                        childV = nextV;
                    }

                    //NOTE: Here we replace < with <= to allow 
                    //both negative and zero cycle detection
                    //as G_p will contain a cycle when G contain a zero
                    //or negative cycle
                    if (v.d + e.getWeight() <= w.d) {
                        w.d = v.d + e.getWeight();
                        w.label = 0; //labeled

                        if (!q.contains(w)) //possible different FIFO options
                            q.add(w);

                        parent.put(w, v);
                        child.put(v, w);
                    }
                }
            v.label = 1; //scanned
        }

        return false;
    }

    
}
