package at.dms.kjc.vanillaSlice;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;

import at.dms.kjc.backendSupport.ComputeNode;
import at.dms.kjc.backendSupport.ComputeNodesI;
import at.dms.kjc.backendSupport.Layout;
import at.dms.kjc.backendSupport.NoSWPipeLayout;
import at.dms.kjc.backendSupport.SpaceTimeScheduleAndPartitioner;
import at.dms.kjc.slicegraph.DataFlowOrder;
import at.dms.kjc.slicegraph.FilterSliceNode;
import at.dms.kjc.slicegraph.InputSliceNode;
import at.dms.kjc.slicegraph.InterSliceEdge;
import at.dms.kjc.slicegraph.OutputSliceNode;
import at.dms.kjc.slicegraph.Slice;
import at.dms.kjc.slicegraph.SliceNode;
import at.dms.kjc.spacetime.AnnealedLayout;
import at.dms.kjc.spacetime.CompareSliceBNWork;
import at.dms.kjc.spacetime.InterSliceBuffer;
import at.dms.kjc.spacetime.IntraSliceBuffer;
import at.dms.kjc.spacetime.LogicalDramTileMapping;
import at.dms.kjc.spacetime.RawChip;
import at.dms.kjc.spacetime.RawComputeNode;
import at.dms.kjc.spacetime.RawTile;
import at.dms.kjc.spacetime.Router;
import at.dms.kjc.spacetime.SmarterRouter;
import at.dms.kjc.spacetime.SpaceTimeSchedule;
import at.dms.kjc.spacetime.StreamingDram;

public class SWPipeLayout<T extends ComputeNode, Ts extends ComputeNodesI>
        extends NoSWPipeLayout<T, Ts> {

    /** the cost of outputing one item using the gdn versus using the static net
    for the final filter of a Slice */
   public static final int GDN_PUSH_COST = 2;
   /** the cost of issue a read or a write dram command on the tiles assigned 
    * to trace enpoints for each trace.
    */
   public static final int DRAM_ISSUE_COST = 5;
   
   SpaceTimeScheduleAndPartitioner spaceTime;
   
   /** array of total work estimation for each tile including blocking*/
   private int[] tileCosts;
   /** Map of filter to start time on the tile they are assigned */
   private HashMap<FilterSliceNode, Integer> startTime;
   /** Map if filter to end time on the tile they are assigned */
   private HashMap<FilterSliceNode, Integer> endTime;
   /** the tile with the most work */
   private int bottleNeckTile;
   /** the amount of work for the bottleneck tile */
   private int bottleNeckCost;
   
    public SWPipeLayout(SpaceTimeScheduleAndPartitioner spaceTime, Ts chip) {
        super(spaceTime, chip);

        this.spaceTime = spaceTime;
        
        //if we are software pipelining then sort the traces by work
        Slice[] tempArray = (Slice[]) spaceTime.getPartitioner()
                .getSliceGraph().clone();
        Arrays.sort(tempArray,
                new CompareSliceBNWork(spaceTime.getPartitioner()));
        scheduleOrder = new LinkedList<Slice>(Arrays.asList(tempArray));
        //reverse the list, we want the list in descending order!
        Collections.reverse(scheduleOrder);
        
        startTime = new HashMap<FilterSliceNode, Integer>();
        endTime = new HashMap<FilterSliceNode, Integer>(); 
    }

    public double placementCost(boolean debug) {
        tileCosts = new int[chip.size()];
        Iterator<Slice> slices = scheduleOrder.iterator();

        while (slices.hasNext()) {
            Slice slice = slices.next();

            //don't do anything for predefined filters...
            if (slice.getHead().getNextFilter().isPredefined())
                continue;

            int prevStart = 0;
            int prevEnd = 0;

            if (debug)
                System.out.println("Scheduling: " + slice);

            if (debug)
                System.out.println("Finding correct times for last filter: ");

            //find the correct starting & ending time for the last filter of the trace
            for (FilterSliceNode current : slice.getFilterNodes()) {

                T tile = getComputeNodeT(current);
                if (debug)
                    System.out.println("  Tile Cost of " + tile.getTileNumber()
                            + " is " + tileCosts[tile.getTileNumber()]);

                //the current filter can start at the max of when the last filter
                //has produced enough data for the current to start and when its
                //tile is avail
                int currentStart = Math.max(tileCosts[tile.getTileNumber()],
                        prevStart
                                + spaceTime.getPartitioner()
                                        .getFilterStartupCost(current));

                if (debug)
                    System.out.println("  prev start + startUp Cost: "
                            + (prevStart + spaceTime.getPartitioner()
                                    .getFilterStartupCost(current)));

                //now the tile avail for this current tile is the max of the current
                //start plus the current occupancy and the previous end plus one iteration
                //of the current, this is because the have to give the current enough
                //cycles after the last filter completes to complete one iteration
                int tileAvail = Math.max(spaceTime.getPartitioner()
                        .getFilterOccupancy(current) + currentStart,
                        prevEnd
                                + spaceTime.getPartitioner()
                                        .getWorkEstOneFiring(current));
                if (debug)
                    System.out.println("  Occ + start = "
                            + spaceTime.getPartitioner().getFilterOccupancy(
                                    current) + " " + currentStart);
                if (debug)
                    System.out.println("  PrevEnd + One firing = "
                            + prevEnd
                            + " "
                            + spaceTime.getPartitioner().getWorkEstOneFiring(
                                    current));

                if (debug)
                    System.out.println("Checking start of " + current + " on "
                            + tile + "start: " + currentStart
                            + ", tile avail: " + tileAvail);

                //remember the start time and end time
                startTime.put(current, new Integer(currentStart));
                //we will over write the end time below for filters 
                //downstream of bottleneck (the last guy)
                endTime.put(current, new Integer(tileAvail));

                assert tileAvail >= prevEnd : "Impossible state reached in schedule model "
                        + tileAvail + " should be >= " + prevEnd;

                prevStart = currentStart;
                prevEnd = tileAvail;
            }

            //when finished prev avail will have the ending time of the last filter!

            //the last filter is always the bottleneck of the filter, meaning
            //it finishes last and base everyone else on it
            FilterSliceNode bottleNeck = slice.getTail().getPrevFilter();

            T bottleNeckTile = getComputeNodeT(bottleNeck);

            //calculate when the bottle neck tile will finish, 
            //and base everything off of that, traversing backward and 
            //foward in the trace
            tileCosts[bottleNeckTile.getTileNumber()] = prevEnd;

            if (bottleNeck.getPrevious().isInputSlice()) {
                tileCosts[bottleNeckTile.getTileNumber()] += DRAM_ISSUE_COST;
            }
//            if (bottleNeck.getNext().isOutputSlice()) {
//                //account for the
//                //cost of sending an item over the gdn if it uses it...
//                if (LogicalDramTileMapping.mustUseGdn(bottleNeckTile)) {
//                    tileCosts[bottleNeckTile.getTileNumber()] += (bottleNeck
//                            .getFilter().getPushInt()
//                            * bottleNeck.getFilter().getSteadyMult() * GDN_PUSH_COST);
//                }
//            }
            if (debug)
                System.out.println("Setting bottleneck finish: " + bottleNeck
                        + " " + tileCosts[bottleNeckTile.getTileNumber()]);

            int nextFinish = tileCosts[bottleNeckTile.getTileNumber()];
            int next1Iter = spaceTime.getPartitioner().getWorkEstOneFiring(
                    bottleNeck);
            SliceNode current = bottleNeck.getPrevious();
            //record the end time for the bottleneck filter
            endTime.put(bottleNeck, new Integer(nextFinish));

            //traverse backwards and set the finish times of the traces...
            while (current.isFilterSlice()) {
                T tile = getComputeNodeT(current.getAsFilter());
                tileCosts[tile.getTileNumber()] = (nextFinish - next1Iter);

                if (debug)
                    System.out.println("Setting " + tile + " " + current
                            + " to " + tileCosts[tile.getTileNumber()]);

                nextFinish = tileCosts[tile.getTileNumber()];
                //record the end time of this filter on the tile
                endTime.put(current.getAsFilter(), new Integer(nextFinish));
                //get ready for next iteration
                next1Iter = spaceTime.getPartitioner().getWorkEstOneFiring(
                        current.getAsFilter());
                current = current.getPrevious();
            }

            //some checks
            for (FilterSliceNode fsn : slice.getFilterNodes()) {
                assert getFilterStart(fsn) <= (getFilterEnd(fsn) - spaceTime
                        .getPartitioner().getFilterWorkSteadyMult(fsn)) :

                fsn
                        + " "
                        + getFilterStart(fsn)
                        + " <= "
                        + getFilterEnd(fsn)
                        + " - "
                        + spaceTime.getPartitioner().getFilterWorkSteadyMult(
                                fsn) + " (bottleneck: " + bottleNeck + ")";
            }
        }

        //remember the bottleneck tile
        bottleNeckTile = -1;
        bottleNeckCost = -1;
        for (int i = 0; i < tileCosts.length; i++) {
            if (tileCosts[i] > bottleNeckCost) {
                bottleNeckCost = tileCosts[i];
                bottleNeckTile = i;
            }
        }
        
        return commCost(tileCosts, (T) chip.getNthComputeNode(bottleNeckTile));
    }

    /**
     * Get the raw tile that was assigned to filter in the layout.
     * 
     * Must call {@link AnnealedLayout#run} first.
     */
    public T getComputeNodeT(SliceNode filter) {
        assert assignment.containsKey(filter) : "AnnealedLayout does have a mapping for "
                + filter;
        //assert !partitioner.isIO(filter.getParent());
        return (T) assignment.get(filter);
    }

    
    private int getFilterEnd(FilterSliceNode fsn) {
        // TODO Auto-generated method stub
        return 0;
    }

    private int getFilterStart(FilterSliceNode fsn) {
        // TODO Auto-generated method stub
        return 0;
    }

    private double commCost(int[] tileCosts2, T computeNode) {
        Slice[] slices = partitioner.getSliceGraph();

        //assignBuffers.run(spaceTime, this);
        //Router router = new SmarterRouter(tileCosts, rawChip);
        HashMap<T, Integer> commCosts = new HashMap<T, Integer>();
        at.dms.kjc.vanillaSlice.Router<T> router = new at.dms.kjc.vanillaSlice.SmarterRouter<T, Ts>(
                tileCosts2, chip);

        for (int i = 0; i < chip.size(); i++) {
            commCosts.put((T) chip.getNthComputeNode(i), 0);
        }

        for (int i = 0; i < slices.length; i++) {
            Slice slice = slices[i];
            Iterator edges = slice.getTail().getDestSet().iterator();
            while (edges.hasNext()) {
                InterSliceEdge edge = (InterSliceEdge) edges.next();

                // System.out.println(" Checking if " + edge + " crosses.");
//                InterSliceBuffer buf = InterSliceBuffer.getBuffer(edge);
//
//                //nothing is transfered for this buffer.
//                if (buf.redundant())
//                    continue;

                OutputSliceNode output = edge.getSrc();
                InputSliceNode input = edge.getDest();

                //get the cost from src to dst
                T src = (T) assignment.get(output.getPrevFilter());
                T dst = (T) assignment.get(input.getNextFilter());

                //do not need to account for this cost as the communication is
                //within a node
                if(src == dst)
                    continue;
                
                //get the the route and add the cost for each node on the route
                Iterator<T> route = router.getRoute(src, dst).iterator();

                while (route.hasNext()) {
                    T hop = route.next();

                    commCosts.put(hop, commCosts.get(hop) + edge.steadyItems());
                }

            }
        }

        //return the comm cost
        int max = 0;
        for (int i = 0; i < chip.size(); i++) {
            int cost = (int) (commCosts.get(chip.getNthComputeNode(i)) + tileCosts2[i]);
            if (cost > max)
                max = cost;
        }
        return max;
    }
}
