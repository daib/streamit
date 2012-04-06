/**
 * 
 */
package at.dms.kjc.sharedmemory;

import java.util.LinkedList;
import java.util.HashSet;

import at.dms.kjc.backendSupport.ComputeNode;
import at.dms.kjc.backendSupport.ComputeNodesI;
import at.dms.kjc.spacetime.RawComputeNode;
import at.dms.util.Utils;

/**
 * @author mgordon
 *
 */
public class SmarterRouter<T extends ComputeNode,  Ts extends ComputeNodesI> implements Router<T> {
    private int[] tileCosts;
    private Ts chip;
    
    public SmarterRouter(int[] tileCosts2, Ts chip) {
        this.tileCosts = tileCosts2;
        this.chip = chip;
    }
    
    public LinkedList<T> getRoute(T src, T dst) {
        //set this to the dst if the dst is an IODevice so we can
        //add it to the end of the route
        T realDst = null, realSrc = null;
    
//        //we cannot route between IODevices so first route to neighbor
//        if (src instanceof IODevice) {
//            realSrc = src;
//            src = ((IODevice)src).getNeighboringTile();
//        }
//
//        //if dst if an iodevice, set dest to be the neighboring tile of the dest
//        //and add the real dest to the end of the route 
//        if (dst instanceof IODevice) {
//            realDst = dst;
//            dst = ((IODevice)dst).getNeighboringTile();
//        }
    
    
        if (src == null || dst == null)
            Utils.fail("Trying to route from/to null");

        RouteAndHopWork<T> bestRoute = findBestRoute((T)src, (T)dst);        
        
//        if (realSrc != null)
//            bestRoute.route.addFirst(realSrc);
//        
//        if (realDst != null)
//            bestRoute.route.add(realDst);
        
        return bestRoute.route;
    }
    /** 
     * Find the best route, route with the lowest total work count. 
     */
    private RouteAndHopWork<T> findBestRoute(T src, T dst) 
    {
        LinkedList<T> route = new LinkedList<T>();
    

        //if the source == dst just add the dest and return, the end of the recursion
        if (src == dst) {
            route.add(dst);
            return new RouteAndHopWork<T>(route, 0);
        }
    
        int xDir = UniProcessors.getXDir(src, dst);
        int yDir = UniProcessors.getYDir(src, dst);
        
        //the route if we take X or Y
        //initialize the work count to a large integer that can never be 
        //obtained
        RouteAndHopWork<T> takeX = 
            new RouteAndHopWork<T>(new LinkedList<T>(),
                                      Integer.MAX_VALUE);
    
        RouteAndHopWork<T> takeY = 
            new RouteAndHopWork<T>(new LinkedList<T>(),
                                      Integer.MAX_VALUE);
//      only try the x direction if we need to route in that direction
        if (xDir != 0) {
            takeX = findBestRoute((T) ((UniProcessors) chip).getTile(src.getX() + xDir, 
                                                  src.getY()),
                                  dst);
        }
    
//      only try the y direction if we need to route in that direction
        if (yDir != 0) {
            takeY = findBestRoute((T) ((UniProcessors) chip).getTile(src.getX(),
                                                  src.getY() + yDir),
                                  dst);
        }
    
        //get the best route
        RouteAndHopWork bestRoute = 
            takeX.hopWork <= takeY.hopWork ? takeX : takeY;
    
        //Add this source's cost to the cost of the route
        bestRoute.hopWork += tileCosts[src.getTileNumber()];
        
        //add this src to the beginning of the route list
        bestRoute.route.addFirst(src);

        return bestRoute;
    }
    
    
    public int distance(T src, T dst) {
        return getRoute(src, dst).size();
    }
   
    
}

class RouteAndHopWork<T extends ComputeNode> {
    public int hopWork;
    public LinkedList<T> route;

    public RouteAndHopWork(LinkedList<T> r, int hw) {
        this.route = r;
        this.hopWork = hw;
    }
}

