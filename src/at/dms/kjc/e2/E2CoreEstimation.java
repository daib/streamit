/**
 * 
 */
package at.dms.kjc.e2;

import at.dms.kjc.sir.SIRFilter;
import at.dms.kjc.sir.lowering.partition.WorkEstimate.WorkVisitor;

/**
 * This class estimates the number of physical cores in a composed logical core
 * for one filter. We perhaps use simulation based approach. We try to add as more core
 * as possible as long as the scalability is close to linear. Other wise, we will stop adding
 * more cores.
 * @author t-daibui
 *
 */
public class E2CoreEstimation {
	final static int CORE_POWER = 10;
	/**
	 * Estimate the number of composed core to run a filter with good performace
	 * @param filter
	 * @return Number of composed cores
	 */
	public static int estimateNumberCore(SIRFilter filter) {
		int numCores = 1;
		//roughly estimate the number of cores
		//other optimization e.g. loop unrolling, ...
		
		//estimate the work
		int workEstimate = WorkVisitor.getWork((SIRFilter)filter);
		
		int calculatedNumCores = workEstimate/CORE_POWER;
		
		//run simulation to get the exact number of cores
		//based on cores' utilization and performance scaling of cores 
		
		return numCores;
	}

}
