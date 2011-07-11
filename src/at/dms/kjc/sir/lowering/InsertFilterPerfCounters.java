package at.dms.kjc.sir.lowering;

import java.util.List;

import at.dms.kjc.JEmittedTextExpression;
import at.dms.kjc.JExpressionStatement;
import at.dms.kjc.JMethodDeclaration;
import at.dms.kjc.JStatement;
import at.dms.kjc.iterator.IterFactory;
import at.dms.kjc.iterator.SIRFilterIter;
import at.dms.kjc.sir.EmptyStreamVisitor;
import at.dms.kjc.sir.SIRFilter;
import at.dms.kjc.sir.SIROperator;
import at.dms.kjc.sir.SIRPushExpression;
import at.dms.kjc.sir.SIRStream;

/**
 * This class insert performance counters for filters
 * 
 * @author t-daibui
 * 
 */

public class InsertFilterPerfCounters extends EmptyStreamVisitor {

	private static final boolean FINE_GRAINED = false;

	public static void doit(SIRStream str) {
		IterFactory.createFactory().createIter(str)
				.accept(new InsertFilterPerfCounters());
	}

	/**
	 * Returns name for a given SIROperator. This name is different than some
	 * other names because it is designed to be useful for profiling. Currently
	 * we collapse all instances of a given filter type across the stream graph
	 * into a single name.
	 */
	private static String getName(SIROperator op) {
		// for example: "class at.dms.kjc.sir.SIRSplitter"
		String longClass = "" + op.getClass();
		// for example: "SIRSplitter"
		String shortClass = longClass.substring(1 + longClass.lastIndexOf("."));

		String shortIdent;
		if (FINE_GRAINED) {
			// for example: "DCT__100_10_45"
			shortIdent = op.getName();
			// also output the enclosing stream to uniquely identify anonymous
			// streams
			shortIdent += " parent= " + op.getParent().getIdent();
		} else {
			// for example: "DCT__100"
			String longIdent = op.getIdent();
			// for example: "DCT"
			if (longIdent.indexOf("__") > 0) {
				shortIdent = longIdent
						.substring(0, longIdent.lastIndexOf("__"));
			} else {
				shortIdent = longIdent;
			}
		}

		// for example: "SIRFilter DCT"
		return shortClass + " " + shortIdent;
	}

	/* visit a filter */
	public void visitFilter(SIRFilter self, SIRFilterIter iter) {
		JMethodDeclaration work = self.getWork();
		work.addStatementFirst(makeInitCounter(self));
		List<JStatement> statements = work.getStatements();
		if (((JExpressionStatement) statements.get(statements.size() - 1))
				.getExpression() instanceof SIRPushExpression) {
			statements.add(statements.size() - 1, makeEndCounter(self));
		}
		// work.addStatement(makeEndCounter(self));
	}

	private JExpressionStatement makeInitCounter(SIRFilter self) {
		return new JExpressionStatement(new JEmittedTextExpression(
				"global_counter = GET_CUR_CYCLE();"));
	}

	private JExpressionStatement makeEndCounter(SIRFilter self) {
		return new JExpressionStatement(
				new JEmittedTextExpression(
						getName(self)
								+ "_perf_counter += GET_CUR_CYCLE() - global_perf_counter;"));
	}
}
