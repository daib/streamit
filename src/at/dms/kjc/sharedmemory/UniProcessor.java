package at.dms.kjc.sharedmemory;

import at.dms.kjc.backendSupport.ComputeNode;

/**
 * Completely vanilla extension to {@link at.dms.kjc.backendSupport.ComputeNode} for a processor (computation node)
 * with no quirks.
 * @author dimock
 *
 */
public class UniProcessor extends ComputeNode<UniComputeCodeStore> {
    public UniProcessor(int uniqueId, int x, int y) {
        super();
        setUniqueId(uniqueId);
        setX(x);
        setY(y);
        computeCode = new UniComputeCodeStore(this);
    }
}
