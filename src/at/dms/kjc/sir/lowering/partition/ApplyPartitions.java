package at.dms.kjc.sir.lowering.partition;

import java.util.*;

import at.dms.kjc.*;
import at.dms.kjc.sir.*;
import at.dms.kjc.sir.lowering.fusion.*;

/*
 This is the class that performs the fusion dictated by a
 partitioner.  The general strategy is this:

 1. make copy of children so you can look them up later

 2. visit each of children and replace any of them with what they returned

 3. fuse yourself (or pieces of yourself) according to hashmap for your original kids

 4. return the new version of yourself
 */
class ApplyPartitions extends EmptyAttributeStreamVisitor {
	/**
	 * The partition mapping.
	 */
	private HashMap<Object, Integer> partitions;

	private ApplyPartitions(HashMap<Object, Integer> partitions) {
		this.partitions = partitions;
	}

	public static void doit(SIRStream str, HashMap<Object, Integer> partitions) {
		str.accept(new ApplyPartitions(partitions));
	}

	/******************************************************************/
	// local methods for the ILPFuser

	/**
	 * Visits/replaces the children of 'cont'
	 */
	private void replaceChildren(SIRContainer cont) {
		// visit children
		for (int i = 0; i < cont.size(); i++) {
			SIRStream newChild = (SIRStream) cont.get(i).accept(this);
			cont.set(i, newChild);
			// if we got a pipeline, try lifting it. note that this
			// will mutate the children array and the init function of
			// <self>
			if (newChild instanceof SIRPipeline) {
				Lifter.eliminatePipe((SIRPipeline) newChild);
			}
		}
	}

	/******************************************************************/
	// these are methods of empty attribute visitor

	/* visit a pipeline */
	public Object visitPipeline(SIRPipeline self, JFieldDeclaration[] fields,
			JMethodDeclaration[] methods, JMethodDeclaration init) {
		// replace children
		replaceChildren(self);
		// fuse children internally
		PartitionGroup group = PartitionGroup.createFromAssignments(
				self.getSequentialStreams(), partitions);
		Map<Integer, Integer> groupId2Partition = new HashMap<Integer, Integer>();
		int childIndex = 0;
		for (int i = 0; i < group.size(); i++) {
			childIndex += group.get(i);
			groupId2Partition.put(i, partitions.get(self.get(childIndex - 1)));
		}

		FusePipe.fuse(self, group);

		for (int i = 0; i < group.size(); i++) {
			partitions.put(self.get(i), groupId2Partition.get(i));
		}
		return self;
	}

	/* visit a splitjoin */
	public Object visitSplitJoin(SIRSplitJoin self, JFieldDeclaration[] fields,
			JMethodDeclaration[] methods, JMethodDeclaration init,
			SIRSplitter splitter, SIRJoiner joiner) {
		// System.err.println("visiting " + self);
		// replace children
		replaceChildren(self);
		List<SIRStream> children = self.getParallelStreams();
		PartitionGroup group = PartitionGroup.createFromAssignments(children,
				partitions);

		// fuse
		SIRStream result = FuseSplit.fuse(self, group);
		// if we got pipelines back, that means we used old fusion,
		// and we should fuse the pipe again
		if (group.size() == 1 && result instanceof SIRPipeline) {
			// if the whole thing is a pipeline
			FusePipe.fuse((SIRPipeline) result);
		} else if (result instanceof SIRSplitJoin) {
			// if we might have component pipelines
			for (int i = 0; i < group.size(); i++) {
				if (group.get(i) > 1
						&& ((SIRSplitJoin) result).get(i) instanceof SIRPipeline) {
					FusePipe.fuse((SIRPipeline) ((SIRSplitJoin) result).get(i));
				}
			}
		}

		if (result instanceof SIRContainer) {
			int index = 0;
			for (int i = 0; i < group.size(); i++) {
				index += group.get(i);
				// the partition value of fused filters is the partition value
				// of any fused filters
				partitions.put(((SIRContainer) result).get(i),
						partitions.get(children.get(index - 1)));
			}

			// horizontal fuse if possible
			if (result instanceof SIRSplitJoin) {
				// check if we could do that

				// add sync points
				// fuse filters of different children in the split/join

				int part = -1;
				boolean makeCut = true;
				for (SIROperator child : ((SIRSplitJoin) result).getChildren()) {
					if (child instanceof SIRSplitter
							|| child instanceof SIRJoiner)
						continue;

					int tmpPart = -1;
					// child can only be filer or pipelines
					if (child instanceof SIRPipeline) {
						// get the first child to see which partition it belongs
						// to
						tmpPart = partitions.get(((SIRContainer) child).get(0));
					} else if (child instanceof SIRFilter) {
						tmpPart = partitions.get(child);
					} else
						break;
					if (part == -1)
						part = tmpPart;
					if (part != tmpPart) {
						makeCut = false;
						break;
					}
				}

				if (makeCut) {
					// make horizontal cut
					HorizontalCutTransform cutter = new HorizontalCutTransform(
							0);
					result = cutter.doMyTransform(result);
					
					replaceChildren((SIRContainer) result);
				}
			}
		} else
			partitions.put(result, partitions.get(children.get(0)));

		return result;
	}

	/* visit a feedbackloop */
	public Object visitFeedbackLoop(SIRFeedbackLoop self,
			JFieldDeclaration[] fields, JMethodDeclaration[] methods,
			JMethodDeclaration init, JMethodDeclaration initPath) {
		// System.err.println("visiting " + self);
		// fusing a whole feedback loop isn't supported yet
		assert getPartition(self) == -1;
		// replace children
		replaceChildren(self);
		return self;
	}

	/******************************************************************/

	/**
	 * Returns int partition for 'str'.
	 */
	private int getPartition(Object str) {
		assert partitions.containsKey(str) : "No partition recorded for: "
				+ str;
		return partitions.get(str).intValue();
	}
}
