package at.dms.kjc.bandwidthEvaluation;

import at.dms.kjc.sir.*;
import at.dms.kjc.*;
import at.dms.kjc.backendSupport.*;
import at.dms.kjc.slicegraph.*;
import at.dms.kjc.spacetime.AnnealedLayout;
import at.dms.kjc.spacetime.MultiplySteadyState;
import at.dms.kjc.spacetime.RawChip;
import at.dms.kjc.spacetime.RawTile;
import at.dms.kjc.spacetime.SpaceTimeSchedule;
import at.dms.kjc.common.CodegenPrintWriter;
import at.dms.kjc.common.CommonUtils;

import java.io.*;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The entry to the back end for a uniprocesor or cluster.
 */
public class UniBackEnd {

    static SpaceTimeScheduleAndPartitioner _schedule = null;
    //    static int _numCores = 0;
    /** holds pointer to BackEndFactory instance during back end portion of this compiler. */
    public static BackEndFactory<UniProcessors, UniProcessor, UniComputeCodeStore, Integer> backEndBits = null;

    /**
     * Top level method for uniprocessor backend, called via reflection from {@link at.dms.kjc.StreaMITMain}.
     * @param str               SIRStream from {@link at.dms.kjc.Kopi2SIR}
     * @param interfaces        JInterfaceDeclaration[] from {@link at.dms.kjc.Kopi2SIR}
     * @param interfaceTables   SIRInterfaceTable[] from  {@link at.dms.kjc.Kopi2SIR}
     * @param structs           SIRStructure[] from  {@link at.dms.kjc.Kopi2SIR}
     * @param helpers           SIRHelper[] from {@link at.dms.kjc.Kopi2SIR}
     * @param global            SIRGlobal from  {@link at.dms.kjc.Kopi2SIR}
     */
    public static void run(SIRStream str, JInterfaceDeclaration[] interfaces,
            SIRInterfaceTable[] interfaceTables, SIRStructure[] structs,
            SIRHelper[] helpers, SIRGlobal global) {

        System.out.println("Enttering Bandwidth Evaluation Backend");
        int numCores = KjcOptions.newSimple * KjcOptions.newSimple;
        int nRows = KjcOptions.newSimple;
        int nCols = KjcOptions.newSimple;

        // The usual optimizations and transformation to slice graph
        CommonPasses commonPasses = new CommonPasses();
        // perform standard optimizations.
        commonPasses.run(str, interfaces, interfaceTables, structs, helpers,
                global, numCores);
        // perform some standard cleanup on the slice graph.
        commonPasses.simplifySlices();
        // Set schedules for initialization, prime-pump (if KjcOptions.spacetime), and steady state.
        SpaceTimeScheduleAndPartitioner schedule = commonPasses
                .scheduleSlices();
        // partitioner contains information about the Slice graph used by dumpGraph
        Partitioner partitioner = commonPasses.getPartitioner();

        System.out.println("Multiplying Steady-State...");
        MultiplySteadyState.doit(partitioner.getSliceGraph());

        //generate the schedule modeling values for each filter/slice 
        partitioner.calculateWorkStats();

        // create a collection of (very uninformative) processor descriptions.
        UniProcessors processors = new UniProcessors(nRows, nCols);

        // assign SliceNodes to processors
        Layout<UniProcessor> layout;
        if (KjcOptions.spacetime && !KjcOptions.noswpipe) {
            //            layout = new BasicGreedyLayout<UniProcessor>(schedule,
            //                    processors.toArray());
//            if (KjcOptions.profile)
//                layout = new BasicGreedyLayout<UniProcessor>(schedule,
//                        processors.toArray());
//            else
                layout = new SWPipeLayout<UniProcessor, UniProcessors>(
                        schedule, processors);
            //                layout = new CompatibleFilterLayout<UniProcessor>(schedule,
            //                        processors.toArray());
        } else {
            layout = new NoSWPipeLayout<UniProcessor, UniProcessors>(schedule,
                    processors);
        }

        layout.run();

        // create other info needed to convert Slice graphs to Kopi code + Channels
        BackEndFactory<UniProcessors, UniProcessor, UniComputeCodeStore, Integer> uniBackEndBits = new UniBackEndFactory(
                processors);
        backEndBits = uniBackEndBits;
        backEndBits.setLayout(layout);

        // now convert to Kopi code plus channels.  (Javac gives error if folowing two lines are combined)
        BackEndScaffold top_call = backEndBits.getBackEndMain();
        _schedule = schedule;
        top_call.run(schedule, backEndBits);

        // Dump graphical representation
        DumpSlicesAndChannels.dumpGraph("slicesAndChannels.dot", partitioner,
                backEndBits);

        //communication profiling
        Map<ComputeNode, Map> commMap = new HashMap<ComputeNode, Map>();
        for (ComputeNode n : uniBackEndBits.getComputeNodes().toArray()) {
            //set of links from this compute nodes to other nodes
            Layout l = backEndBits.getLayout();
            Collection<Channel> channels = backEndBits.getChannels();

            Map<ComputeNode, Long> trafficTo = new HashMap<ComputeNode, Long>();
            commMap.put(n, trafficTo);

            for (Channel c : channels) {
                SliceNode s = c.getSource();
                SliceNode d = c.getDest();
                if (l.getComputeNode(s) == n && l.getComputeNode(d) != n) {
                    InterSliceEdge e = (InterSliceEdge) c.getEdge();
                    long nSends = e.steadyItems() * e.getType().getSizeInC();
                    if (trafficTo.containsKey(l.getComputeNode(d)))
                        nSends += trafficTo.get(l.getComputeNode(d));
                    trafficTo.put(l.getComputeNode(d), nSends);
                }
            }

        }

        for (ComputeNode s : commMap.keySet()) {
            Map<ComputeNode, Long> trafficTo = commMap.get(s);
            for (ComputeNode d : trafficTo.keySet()) {
                System.out.println("Node(" + s.getX() + "," + s.getY()
                        + ") -> Node(" + +d.getX() + "," + d.getY() + "):" + trafficTo.get(d) + " bytes");
            }
        }

        System.exit(0);
        
        /*
         * Emit code to structs.h
         */
        String outputFileName = "structs.h";
        try {
            CodegenPrintWriter p = new CodegenPrintWriter(new BufferedWriter(
                    new FileWriter(outputFileName, false)));
            // write out C code
            EmitStandaloneCode.emitTypedefs(structs, backEndBits, p);
            p.close();
        } catch (IOException e) {
            throw new AssertionError("I/O error on " + outputFileName + ": "
                    + e);
        }

        /*
         * Emit code to str.c
         */
        outputFileName = "str.cpp";
        try {
            CodegenPrintWriter p = new CodegenPrintWriter(new BufferedWriter(
                    new FileWriter(outputFileName, false)));

            p.println("#include <pthread.h>\npthread_barrier_t barr;");
            // write out C code
            EmitStandaloneCode codeEmitter = new EmitStandaloneCode(
                    uniBackEndBits);
            codeEmitter.generateCHeader(p);
            // Concat emitted code for all nodes into one file.
            codeEmitter.clearDeclaredFields();
            for (ComputeNode n : uniBackEndBits.getComputeNodes().toArray()) {
                codeEmitter.emitCodeForComputeNode(n, p);
            }
            codeEmitter.generateMain(p);
            p.close();
        } catch (IOException e) {
            throw new AssertionError("I/O error on " + outputFileName + ": "
                    + e);
        }
        // return success
        System.exit(0);
    }
}
