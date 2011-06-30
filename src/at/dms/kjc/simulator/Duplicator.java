package at.dms.kjc.simulator;

import at.dms.kjc.sir.SIRFilter;
import at.dms.kjc.sir.SIRStream;
import at.dms.kjc.sir.lowering.fission.StatelessDuplicate;
import at.dms.kjc.sir.lowering.partition.WorkEstimate;
import at.dms.kjc.sir.lowering.partition.WorkList;

public class Duplicator {

	public static void maximallyDuplicate(SIRStream str) {
		int numberOfTimes = 4;
		WorkEstimate work = WorkEstimate.getWorkEstimate(str);
		WorkList workList = work.getSortedFilterWork();

		for (int i = workList.size() - 1; i >= 0; i--) {
			SIRFilter filter = workList.getFilter(i);
			if (!StatelessDuplicate.isFissable(filter))
				continue;
			StatelessDuplicate.doit(filter, numberOfTimes);
		}
	}
}
