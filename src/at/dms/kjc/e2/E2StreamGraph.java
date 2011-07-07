package at.dms.kjc.e2;

import at.dms.kjc.flatgraph.*;
import java.util.*;

public class E2StreamGraph extends ScheduledStreamGraph {

    public E2StreamGraph(FlatNode top) {
        super(top);
    }

    /**
     * Use in place of "new StaticStreamGraph" for subclassing.
     * 
     * A subclass of StreamGraph may refer to a subclass of StaticStreamGraph.
     * If we just used "new StaticStreamGraph" in this class we would
     * only be making StaticStreamGraph's of the type in this package.
     * 
     * 
     * @param sg       a StreamGraph
     * @param realTop  the top node
     * @return         a new StaticStreamGraph
     */
    @Override protected E2StaticStreamGraph new_StaticStreamGraph(StreamGraph sg, FlatNode realTop) {
        return new E2StaticStreamGraph(sg,realTop);
    }
    
    /** Clean up any data structures affected by fusion. */
    public void cleanupForFused() {
        // handle fusion in each StaticStreamGraph
        for (StaticStreamGraph ssg : staticSubGraphs) {
            E2StaticStreamGraph csg = (E2StaticStreamGraph)ssg;
            csg.cleanupForFused();
        }
        // handle fusion in parentMap
        HashMap<FlatNode,StaticStreamGraph> newParentMap = new HashMap<FlatNode,StaticStreamGraph>();
        for (Map.Entry<FlatNode,StaticStreamGraph> parent : parentMap.entrySet()) {
            FlatNode k = parent.getKey();
            StaticStreamGraph v = parent.getValue();
            if (E2Fusion.isEliminated(k)) {
                k = E2Fusion.getMaster(k);
            }
            newParentMap.put(k,v);
        }
        parentMap = newParentMap;

        // topLevelFlatNode was listed as protected, but should have been private...
//        // handle fusion in topLevelFlatNode
//        if (ClusterFusion.isEliminated(topLevelFlatNode)) {
//            topLevelFlatNode = ClusterFusion.getMaster(topLevelFlatNode);
//        }
    }
}
