package at.dms.kjc.vanillaSlice;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;

import antlr.collections.List;
import at.dms.kjc.JEmittedTextExpression;
import at.dms.kjc.JExpressionStatement;
import at.dms.kjc.backendSupport.BackEndFactory;
import at.dms.kjc.backendSupport.BackEndScaffold;
import at.dms.kjc.backendSupport.ComputeCodeStore;
import at.dms.kjc.backendSupport.ComputeNode;
import at.dms.kjc.backendSupport.ComputeNodesI;
import at.dms.kjc.backendSupport.SchedulingPhase;
import at.dms.kjc.slicegraph.DataFlowOrder;
import at.dms.kjc.slicegraph.InputSliceNode;
import at.dms.kjc.slicegraph.OutputSliceNode;
import at.dms.kjc.slicegraph.Slice;
import at.dms.kjc.spacetime.BasicSpaceTimeSchedule;

public class ShareMemBackEndScaffold extends BackEndScaffold {

	static HashMap<Slice, Integer> sliceInitLevel = null;
	static int currentIndex;
	static HashMap<ComputeCodeStore, Integer> lastSyncLevelMap = new HashMap<ComputeCodeStore, Integer>();

	protected void betweenScheduling(BasicSpaceTimeSchedule schedule,
			BackEndFactory resources) {
		LinkedList<Integer> vals = new LinkedList<Integer>(
				lastSyncLevelMap.values());
		Collections.sort(vals);
		int max_level = vals.getLast() + 1;
		for (ComputeNode n : ((BackEndFactory<UniProcessors, UniProcessor, UniComputeCodeStore, Integer>) resources)
				.getComputeNodes().toArray()) {
			int lastSyncLevel = 0;
			if (lastSyncLevelMap.get(n.getComputeCode()) != null) {
				lastSyncLevel = lastSyncLevelMap.get(n.getComputeCode());
			}
			for (int i = lastSyncLevel; i < max_level; i++) {
				n.getComputeCode().addInitStatement(
						new JExpressionStatement(new JEmittedTextExpression(
								"\tpthread_barrier_wait(&barr)")));
			}
		}

		lastSyncLevelMap.clear();
	}

	/**
	 * Iterate over the schedule of slices and over each node of each slice and
	 * generate the code necessary to fire the schedule. Generate splitters and
	 * joiners intermixed with the trace execution...
	 * 
	 * @param slices
	 *            The schedule to execute.
	 * @param whichPhase
	 *            True if the init stage.
	 * @param computeNodes
	 *            The collection of compute nodes.
	 */
	protected void iterateInorder(Slice slices[], SchedulingPhase whichPhase,
			ComputeNodesI computeNodes) {
		Slice slice;

		sliceInitLevel = DataFlowOrder.getTopologicalSort(slices);

		for (int i = 0; i < slices.length; i++) {
			slice = (Slice) slices[i];
			currentIndex = i;
			// create code for joining input to the trace
			resources.processInputSliceNode((InputSliceNode) slice.getHead(),
					whichPhase, computeNodes);
			// create the compute code and the communication code for the
			// filters of the trace
			resources.processFilterSliceNode(slice.getFilterNodes().get(0),
					whichPhase, computeNodes);
			// create communication code for splitting the output
			resources.processOutputSliceNode((OutputSliceNode) slice.getTail(),
					whichPhase, computeNodes);

		}
	}

	public static int addPrimePumpBarriers(ComputeCodeStore codeStore) {
		// Slice slice = filterNode.getParent();
		Slice[][] primePumpSched = UniBackEnd._schedule.getPrimePumpSchedule();
		int sliceGroup = 0;

		int lastSyncLevel = 0;
		int index = currentIndex;

		for (int i = 0; i < primePumpSched.length; i++) {
			if (index < primePumpSched[i].length) {
				sliceGroup = i;
				break;
			} else {
				index -= primePumpSched[i].length;
			}

		}

		if (lastSyncLevelMap.get(codeStore) != null) {
			lastSyncLevel = lastSyncLevelMap.get(codeStore);
		} else {
			lastSyncLevelMap.put(codeStore, 0);
		}

		for (int i = lastSyncLevel; i < sliceGroup; i++) {
			// add barrier statement
			codeStore
					.addInitStatement(new JExpressionStatement(
							new JEmittedTextExpression(
									"\tpthread_barrier_wait(&barr)")));
		}

		if (lastSyncLevel < sliceGroup) {
			ShareMemBackEndScaffold.lastSyncLevelMap.put(codeStore, sliceGroup);
		}

		return sliceGroup;
	}

	public static int addInitBarriers(ComputeCodeStore codeStore, Slice slice) {
		// get slice level
		int level = ShareMemBackEndScaffold.sliceInitLevel.get(slice);

		int lastSyncLevel = 0;
		if (ShareMemBackEndScaffold.lastSyncLevelMap.get(codeStore) != null) {
			lastSyncLevel = ShareMemBackEndScaffold.lastSyncLevelMap
					.get(codeStore);
		}

		for (int i = lastSyncLevel; i < level; i++) {
			// add barrier statement
			codeStore
					.addInitStatement(new JExpressionStatement(
							new JEmittedTextExpression(
									"\tpthread_barrier_wait(&barr)")));
		}

		if (lastSyncLevel <= level) {
			ShareMemBackEndScaffold.lastSyncLevelMap.put(codeStore, level);
		}

		return level;
	}

}
