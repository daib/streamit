package at.dms.kjc.slicegraph;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Vector;

import at.dms.util.Utils;

/**
 * This class generates a data flow schedule of the slice graph (but does not
 * handle feedbackloops). More specifically, in the traversal, all ancestors of
 * a node are guaranteed to appear before the node.
 * 
 * @author mgordon
 */
public class DataFlowOrder {

	/**
	 * Generate a list of slices in data-flow order.
	 * <p>
	 * TODO: need to add markers for feedbackloops in original graph. Order
	 * would be: (1) fake node with just a preWork to push enqueued. (2) The
	 * body in order. (3) The loop in order. (one (bad) model would be
	 * {@link at.dms.kjc.cluster.DiscoverSchedule}.)
	 * </p>
	 * 
	 * @param topSlices
	 *            The slice forest.
	 * @return A LinkedList of slices in data-flow order
	 */
	// (don't add I/O slices to the traversal). // no longer true

	public static LinkedList<Slice> getTraversal(Slice[] topSlices) {
		LinkedList<Slice> schedule = new LinkedList<Slice>();
		HashSet<Slice> visited = new HashSet<Slice>();
		LinkedList<Slice> queue = new LinkedList<Slice>();
		for (int i = 0; i < topSlices.length; i++) {
			queue.add(topSlices[i]);
			while (!queue.isEmpty()) {
				Slice slice = queue.removeFirst();
				if (!visited.contains(slice)) {
					visited.add(slice);
					for (Edge destEdge : slice.getTail().getDestSet()) {
						Slice current = destEdge.getDest().getParent();
						if (!visited.contains(current)) {
							// only add if all sources has been visited
							boolean addMe = true;
							for (Edge oneSource : current.getHead()
									.getSourceSet()) {
								if (!visited.contains(oneSource.getSrc()
										.getParent())) {
									addMe = false;
									break;
								}
							}
							if (addMe)
								queue.add(current);
						}
					}
					// if (!slice.getHead().getNextFilter().isPredefined()) {
					schedule.add(slice);
					// }
				}
			}
		}

		return schedule;
	}

	/**
	 * Generate a list of slices in data-flow order.
	 * <p>
	 * TODO: need to add markers for feedbackloops in original graph. Order
	 * would be: (1) fake node with just a preWork to push enqueued. (2) The
	 * body in order. (3) The loop in order. (one (bad) model would be
	 * {@link at.dms.kjc.cluster.DiscoverSchedule}.)
	 * </p>
	 * 
	 * @param topSlices
	 *            The slice forest.
	 * @return A LinkedList of slices in data-flow order
	 */
	// (don't add I/O slices to the traversal). // no longer true

	public static HashMap<Slice, Integer> getTopologicalSort(Slice[] topSlices) {
		HashMap<Slice, Integer> result = new HashMap<Slice, Integer>();
		HashSet<Slice> visited = new HashSet<Slice>();
		LinkedList<Slice> queue = new LinkedList<Slice>();
		for (int i = 0; i < topSlices.length; i++) {
			queue.add(topSlices[i]);
			while (!queue.isEmpty()) {
				Slice slice = queue.removeFirst();
				if (!visited.contains(slice)) {
					visited.add(slice);
					
					for (Edge destEdge : slice.getTail().getDestSet()) {
						Slice current = destEdge.getDest().getParent();
						if (!visited.contains(current)) {
							// only add if all sources has been visited
							boolean addMe = true;
							for (Edge oneSource : current.getHead()
									.getSourceSet()) {

								if (!visited.contains(oneSource.getSrc()
										.getParent())) {
									addMe = false;
									break;
								}
							}
							if (addMe) {
								queue.add(current);
							}
						}
					}
					
					int value = -1;
					for (Edge srcEdge : slice.getHead().getSourceSet()) {
						if (result.get(srcEdge.getSrc().getParent()) != null) {
							value = Math.max(
									result.get(srcEdge.getSrc().getParent()),
									value);
						}
					}

					// if (!slice.getHead().getNextFilter().isPredefined()) {
					result.put(slice, value + 1);
					// }
				}
			}
		}

		return result;
	}
}
