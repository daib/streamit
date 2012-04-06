package at.dms.kjc.sharedmemory;

import at.dms.kjc.backendSupport.ComputeNode;
import at.dms.kjc.backendSupport.ComputeNodesI;
import java.util.*;
import at.dms.kjc.backendSupport.ComputeNodes;

/**
 * Implementation of {@link at.backendSupport.ComputeNodesI} to provide a collection of {@link UniProcessor}s.
 * Provides mapping of int -> {@link UniProcessor}.
 * @author dimock
 *
 */

public class UniProcessors implements ComputeNodesI<UniComputeCodeStore> {

    /** our collection of nodes... */
    private Vector<UniProcessor> nodes;

    private int nRows, nCols;

    public int getNRows() {
        return nRows;
    }

    public int getNCols() {
        return nCols;
    }

    /**
     * Construct a new collection and fill it with {@link ComputeNode}s.
     * 
     * @param numberOfNodes
     */
    public UniProcessors(Integer nRows, Integer nCols) {
        int numberOfNodes = nRows * nCols;

        this.nRows = nRows;
        this.nCols = nCols;

        nodes = new Vector<UniProcessor>(numberOfNodes);
        //for (int i = 0; i < numberOfNodes; i++) {
        for (int x = 0; x < nCols; x++) {
            for (int y = 0; y < nRows; y++) {
                int i = y + x * nRows;
                UniProcessor node = new UniProcessor(i, x, y);
                nodes.add(node);
            }
        }
    }

    /**
     * Assume that it is easy to add more nodes...
     */
    public boolean canAllocateNewComputeNode() {
        return true;
    }

    public UniProcessor getNthComputeNode(int n) {
        return nodes.elementAt(n);
    }

    public UniProcessor getTile(int x, int y) {
        return getNthComputeNode(y + x * nRows);

    }

    public boolean isValidComputeNodeNumber(int nodeNumber) {
        return 0 <= nodeNumber && nodeNumber < nodes.size();
    }

    public int newComputeNode() {
        nodes.add(new UniProcessor(nodes.size(), -1, -1));
        return nodes.size() - 1;
    }

    public int size() {
        return nodes.size();
    }

    public UniProcessor[] toArray() {
        return nodes.toArray(new UniProcessor[nodes.size()]);
    }

    public static int getXDir(ComputeNode src, ComputeNode dst) {
        if (dst.getX() - src.getX() < 0)
            return -1;
        if (dst.getX() - src.getX() > 0)
            return 1;
        return 0;
    }

    public static int getYDir(ComputeNode src, ComputeNode dst) {
        if (dst.getY() - src.getY() < 0)
            return -1;
        if (dst.getY() - src.getY() > 0)
            return 1;
        return 0;
    }
}
