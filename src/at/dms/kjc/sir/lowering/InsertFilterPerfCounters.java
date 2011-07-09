package at.dms.kjc.sir.lowering;

import at.dms.kjc.JEmittedTextExpression;
import at.dms.kjc.JExpressionStatement;
import at.dms.kjc.JMethodDeclaration;
import at.dms.kjc.iterator.IterFactory;
import at.dms.kjc.iterator.SIRFilterIter;
import at.dms.kjc.sir.EmptyStreamVisitor;
import at.dms.kjc.sir.SIRFilter;
import at.dms.kjc.sir.SIRStream;

	/**
	 * This class insert performance counters for filters
	 * @author t-daibui
	 *
	 */

public class InsertFilterPerfCounters extends EmptyStreamVisitor {

	public static void doit(SIRStream str) {
        IterFactory.createFactory().createIter(str).accept(new InsertFilterPerfCounters());
    }
	
	 /* visit a filter */
    public void visitFilter(SIRFilter self,
                            SIRFilterIter iter) {
        JMethodDeclaration work = self.getWork();
        work.addStatementFirst(makeInitCounter(self));
        work.addStatement(makeEndCounter(self));
    }	

	private JExpressionStatement makeInitCounter(SIRFilter self) {
		return new JExpressionStatement(new JEmittedTextExpression("global_counter = GET_CUR_CYCLE();"));
	}
    
	private JExpressionStatement makeEndCounter(SIRFilter self) {
		return new JExpressionStatement(new JEmittedTextExpression(self.toString()+ "_perf_counter += GET_CUR_CYCLE() - global_perf_counter;"));
	}
}
