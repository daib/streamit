package at.dms.kjc.bandwidthEvaluation;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import at.dms.kjc.backendSupport.BasicGreedyLayout;
import at.dms.kjc.backendSupport.ComputeNode;
import at.dms.kjc.backendSupport.Layout;
import at.dms.kjc.backendSupport.SpaceTimeScheduleAndPartitioner;
import at.dms.kjc.slicegraph.DataFlowOrder;
import at.dms.kjc.slicegraph.FilterSliceNode;
import at.dms.kjc.slicegraph.InputSliceNode;
import at.dms.kjc.slicegraph.OutputSliceNode;
import at.dms.kjc.slicegraph.Slice;
import at.dms.kjc.slicegraph.SliceNode;
import at.dms.kjc.spacetime.CompareSliceBNWork;
import at.dms.kjc.spacetime.SpaceTimeBackend;

public class CompatibleFilterLayout<T extends ComputeNode> implements Layout<T> {

    private HashMap<SliceNode, T> assignment;
    private SpaceTimeScheduleAndPartitioner spaceTime;
    private int numBins;
    private LinkedList<SliceNode>[] bins;
    private int[] binWeight;
    //private int[] searchOrder;
    private int totalWork;
    private T[] nodes;

    public CompatibleFilterLayout(SpaceTimeScheduleAndPartitioner spaceTime,
            T[] nodes) {
        this.spaceTime = spaceTime;
        this.nodes = nodes;
        numBins = nodes.length;
    }

    /* Pack filters such that filters with the same # of composed cores are put together
     * This information is obtained by profiling.
     */
    private void pack() {
        
        //read the profiling information file
        HashMap<String, Integer> name2NCores = new HashMap<String, Integer>();
        HashMap<String, Integer> name2Workload = new HashMap<String, Integer>();
        
        //TODO: read profiling file
        
        
        //now sort the filters by work
        LinkedList<Slice> scheduleOrder;
        
        //get the schedule order of the graph!
        if (SpaceTimeBackend.NO_SWPIPELINE) {
            //if we are not software pipelining then use then respect
            //dataflow dependencies
            scheduleOrder = DataFlowOrder.getTraversal(spaceTime.getPartitioner().getSliceGraph());
        } else {
            //if we are software pipelining then sort the traces by work
            Slice[] tempArray = (Slice[]) spaceTime.getPartitioner().getSliceGraph().clone();
//            Arrays.sort(tempArray, new CompareSliceBNWork(spaceTime.getPartitioner()));
            scheduleOrder = new LinkedList<Slice>(Arrays.asList(tempArray));
            //reverse the list, we want the list in descending order!
            //Collections.reverse(scheduleOrder);
        }

        HashMap<Integer, List<SliceNode>> nCores2Filters = new HashMap<Integer, List<SliceNode>>();
        
        //classify filters
        for (int i = 0; i < scheduleOrder.size(); i++) {
            Slice slice = scheduleOrder.get(i);
            assert slice.getNumFilters() == 1 : "The greedy partitioner only works for Time!";
//            sortedList.add(slice.getHead().getNextFilter());
            SliceNode sliceNode = slice.getHead().getNextFilter().getAsFilter();
            
            
            //get the number of cores
            int nCores = name2NCores.get(getName(sliceNode));
            
            if(!nCores2Filters.keySet().contains(nCores)) {
                nCores2Filters.put(nCores, new LinkedList<SliceNode>());
            }
            
            nCores2Filters.get(nCores).add(sliceNode);
        }
        
        
        //distribute filters to cores
        //compute total workloads for filters of the same number of composed cores
        HashMap<Integer, Integer> nCores2Workload = new HashMap<Integer, Integer>();
        
        for(Integer ncores:nCores2Filters.keySet()) {
            
            int totalWorkload = 0;
            for(SliceNode sliceNode:nCores2Filters.get(ncores)) {
                int workload = name2Workload.get(getName(sliceNode));
                
                //get the number of times the slice execute
                int mult = spaceTime.getSteadyMult(sliceNode.getParent());
                
                totalWorkload += workload + mult;
            }
            
            nCores2Workload.put(ncores, totalWorkload);
        }
        
        //
    }

    @Override
    public T getComputeNode(SliceNode node) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void setComputeNode(SliceNode node, T computeNode) {
        // TODO Auto-generated method stub

    }

    @Override
    public void run() {
        // TODO Auto-generated method stub

    }

    private static String getName(SliceNode sliceNode) {
        String filterName = sliceNode.toString();

        if (filterName.indexOf("__") > 0) {
            filterName = filterName.substring(0, filterName.lastIndexOf("__"));
        }

        return filterName;
    }
}
