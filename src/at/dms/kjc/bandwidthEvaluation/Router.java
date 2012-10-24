package at.dms.kjc.bandwidthEvaluation;

import java.util.LinkedList;

import at.dms.kjc.backendSupport.ComputeNode;

public interface Router<T extends ComputeNode>
{
    public LinkedList<T> getRoute(T src, T dst);
    public int distance(T src, T dst); 
}

