package at.dms.kjc.spacetime;

import java.util.HashMap;
import java.util.Iterator;
import at.dms.kjc.*;
import at.dms.kjc.backendSupport.Channel;
import at.dms.kjc.backendSupport.ComputeNode;
import at.dms.kjc.common.CommonUtils;
import at.dms.kjc.slicegraph.FilterSliceNode;
import at.dms.kjc.slicegraph.InputSliceNode;
import at.dms.kjc.slicegraph.OutputSliceNode;
import at.dms.kjc.slicegraph.SliceNode;
import at.dms.kjc.slicegraph.Edge;


/**
 * This abstract class represents a buffer in the partitioner slice graph.  A 
 * buffer appears between slices and inside slices between the input node and the first
 * filter and between the last filter and the output node.  
 * 
 * Now derived from {@link at.dms.kjc.backendSupport.Channel Buffer}.
 * Assumes all buffers are actuall OffChipBuffers.
 * @author mgordon
 *
 */
public abstract class OffChipBuffer extends Channel {
    /** The sending or receiving tile*/
    protected ComputeNode owner;
     /** the size of the buffer in the steady stage */ 
    protected Address sizeSteady;
    /** the dram that we are reading/writing */
    protected StreamingDram dram;
           
    protected OffChipBuffer(SliceNode src, SliceNode dst) {
        super(src,dst);
    }

    protected OffChipBuffer(Edge edge) {
        super(edge);
    }
    
    public abstract boolean redundant();

    /**
     * if this buffer is redundant return the first upstream buffer that is not
     * redundant, return null if this is a input->filter buffer with no input or
     * a filter->output buffer with no output
     */
    public abstract OffChipBuffer getNonRedundant();

    // return true if the inputtracenode does anything necessary
    public static boolean unnecessary(InputSliceNode input) {
        if (input.noInputs())
            return true;
        if (input.oneInput()
            && (InterSliceBuffer.getBuffer(input.getSingleEdge()).getDRAM() == IntraSliceBuffer
                .getBuffer(input, (FilterSliceNode) input.getNext())
                .getDRAM()))
            return true;
        return false;
    }

    // return true if OutputSliceNode does nothing useful
    public static boolean unnecessary(OutputSliceNode output) {
        if (output.noOutputs())
            return true;
        if (output.oneOutput()
            && (IntraSliceBuffer.getBuffer(
                                           (FilterSliceNode) output.getPrevious(), output)
                .getDRAM() == InterSliceBuffer.getBuffer(
                                                         output.getSingleEdge()).getDRAM()))
            return true;
        return false;
    }

    public void setDRAM(StreamingDram DRAM) {
        //assert !redundant() : "calling setDRAM() on redundant buffer";
        this.dram = DRAM;
        
        CommonUtils.println_debugging("Assign " + this.toString() + " to " + DRAM);
        //System.out.println("Assign " + this.toString() + " to " + DRAM);
    }

    /** 
     * @return true if we have assigned a dram to this buffer. 
     */
    public boolean isAssigned() {
        return dram != null;
    }

    /**
     * Remove the dram assignment of this buffer. 
     */
    public void unsetDRAM() {
        dram = null;
    }
    
    /**
     * Return the dram assignment of this buffer.
     * 
     * @return the dram assignment of this buffer.
     */
    public StreamingDram getDRAM() {
        assert dram != null : "need to assign buffer to streaming dram "
            + this.toString();
        
        // assert !redundant() : "calling getDRAM() on redundant buffer";
        return dram;
    }

    /** 
     * @param i
     * @return The string for the rotation structure for <pre>node</pre> 
     *  
     **/
    public String getIdent(int i) {
        assert !redundant() : this.toString() + " is redundant";
        assert i < rotationLength : "Trying to use a buffer rotation length that is too large";
        return getIdent() + "_" + i;
    }
    
    
    
    /** 
     * @param read
     * @return return the rotating buffer structure name that is used for either reading
     * or writing.  Reading and writing have separate rotation structures.
     */
    public String getIdent(boolean read) {
        assert !redundant() : this.toString() + " is redundant";
        String post = (read ? "0" : "1"); 
        return ident + post;
    }
    
    /** return the prefix buffer name for this buffer **/
    public String getIdent() {
        assert !redundant() : this.toString() + " is redundant";
        return ident;
    }

    /** 
     * Reset all the dram assignments of the buffers to null.
     *
     */
    public static void resetDRAMAssignment() {
        Iterator<Channel> buffers = getBuffers().iterator();
        while (buffers.hasNext()) {
            OffChipBuffer buf = (OffChipBuffer)buffers.next();
            buf.setDRAM(null);
        }
    }
    
    public Address getSize() {
        return sizeSteady;
    }
    
    public int getRotationLength() {
        return rotationLength;
    }
    
    abstract protected void calculateSize();

    /**
     * return the owner tile of the dram this buffer is assigned to,
     * this is set in the static section of this class.
     */
    public RawTile getOwner() {
        assert (dram != null) : "dram not set yet";
        return LogicalDramTileMapping.getOwnerTile(dram);
    }

    public String toString() {
        return theEdge.getSrc() + "->" + theEdge.getDest() + "[" + dram + "]";
    }


    public boolean isIntraSlice() {
        return (this instanceof IntraSliceBuffer);
    }

    public boolean isInterSlice() {
        return (this instanceof InterSliceBuffer);
    }
    
    /**
     * Iterate over all the buffers and set the rotation length of each buffer
     * based on the prime pump schedule and the multiplicity difference between the source node
     * and the dest node.
     * 
     * This method also counts the number of intertracebuffers that are assigned
     * to each dram and stores it in the dramToBuffer's hashmap in intertracebuffer.
     * 
     * @param spaceTime The BasicSpaceTimeSchedule
     */
    public static void setRotationLengths(BasicSpaceTimeSchedule spaceTime) {
        InterSliceBuffer.dramsToBuffers = new HashMap<StreamingDram, Integer>();
        Iterator<Channel> buffers = getBuffers().iterator();
        //iterate over the buffers and communicate each buffer
        //address from its declaring tile to the tile neighboring
        //the dram it is assigned to
        while (buffers.hasNext()) {
            OffChipBuffer buffer = (OffChipBuffer)buffers.next();
            if (buffer.isInterSlice()) {
                //set the rotation length for the buffer
                setRotationLength(spaceTime, (InterSliceBuffer)buffer);
                //record that this dram has buffer mapped to it
                if (InterSliceBuffer.dramsToBuffers.containsKey(buffer.getDRAM())) {
                    //we have seen this buffer before, so just 
                    //add one to its count...
                    InterSliceBuffer.dramsToBuffers.put(buffer.getDRAM(),
                            new Integer
                            (InterSliceBuffer.dramsToBuffers.
                                    get(buffer.getDRAM()) + 1));
                }
                else //haven't seen dram before so just put 1
                    InterSliceBuffer.dramsToBuffers 
                    .put(buffer.getDRAM(), 1);
            }
        }
    }
    
    /**
     * Set the rotation length of the buffer based on the multiplicities 
     * of the source trace and the dest trace in the prime pump schedule and add one
     * so we can double buffer also!
     * 
     * @param buffer
     */
    private static void setRotationLength(BasicSpaceTimeSchedule spaceTimeSchedule, InterSliceBuffer buffer) {
        int sourceMult = spaceTimeSchedule.getPrimePumpMult(buffer.getSource().getParent());
        int destMult = spaceTimeSchedule.getPrimePumpMult(buffer.getDest().getParent());
        //fix for file readers and writers!!!!
        
        int length = 0;
        
        //if we have either of these cases we are not rotating this buffer
        //and it a probably a buffer that will never be generated because it is
        //a connected to a file reader or a file writer...
        if (sourceMult < destMult || sourceMult == destMult)
            length = 1;
        else 
            length = sourceMult - destMult + 1; 
      
        buffer.rotationLength = length;
        
        //System.out.println("Setting rotation length: " + buffer + " " + length);
        
        //this is buffer is redundant, meaning it is just a copy of its its upstream 
        //output trace node, then we have to set the rotation for its upstream
        //output trace node!!
        if (length > 1 && buffer.redundant()) {
            //System.out.println("Setting upstream rotation length " + length);
            IntraSliceBuffer upstream = IntraSliceBuffer.getBuffer((FilterSliceNode)buffer.getSource().getPrevious(), 
                    (OutputSliceNode)buffer.getSource());
            upstream.rotationLength = length;
        }
    }
    
    /**
     * @return True if all of the buffers of this program are 
     * assigned to drams.
     */
    public static boolean areAllAssigned() {
        Iterator<Channel> buffers = getBuffers().iterator();
        boolean returnVal = true;
        while (buffers.hasNext()) {
            OffChipBuffer buf = (OffChipBuffer)buffers.next();
            if (!buf.isAssigned()) {
                if (buf.isInterSlice()) {
                    System.out.println("No assignment for : " + buf + ": " + 
                            ((InterSliceBuffer)buf).getEdge().getSrc().getPrevious() + " -> " + 
                            ((InterSliceBuffer)buf).getEdge().getDest().getNext());
                    //printBuffers();
                }
                returnVal = false;;
            }
        }
        return returnVal;
    }
    
    /**
     * Return the total number of bytes used by the offchip buffers.
     * 
     * @return the total number of bytes used by the offchip buffers.
     */
    public static Address totalBufferSizeInBytes() {
        Address bytes = Address.ZERO;
        
        Iterator<Edge> keys = bufferStore.keySet().iterator();
        while (keys.hasNext()) {
            OffChipBuffer buf = (OffChipBuffer)bufferStore.get(keys.next());
            
            if (!buf.redundant()) {
                bytes = bytes.add(buf.sizeSteady.mult(buf.rotationLength));
            }
        }
        
        return bytes;
    }
}
