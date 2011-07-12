package at.dms.kjc.sir.lowering;

import java.util.*;

import at.dms.kjc.*;
import at.dms.kjc.iterator.IterFactory;
import at.dms.kjc.iterator.SIRFilterIter;
import at.dms.kjc.sir.EmptyStreamVisitor;
import at.dms.kjc.sir.SIRFilter;
import at.dms.kjc.sir.SIROperator;
import at.dms.kjc.sir.SIRStream;

/**
 * This class insert performance counters for filters
 * 
 * @author t-daibui
 * 
 */

public class InsertFilterPerfCounters extends EmptyStreamVisitor {

	private static final boolean FINE_GRAINED = false;
	private static final String GLOBAL_COUNTER = "global_counter";
	private static final String GET_CURRENT_TIME = "GET_CUR_CYCLE()";
	private static final String PERF_COUNTER_TAG = "_perf_counter";
	private static Set<String> counterSet = new HashSet<String>();
	private static final String PERF_COUNTER_TYPE = "long int";

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

		// for example: "SIRFilter_DCT"
		return shortClass + "__" + shortIdent;
	}

	/* visit a filter */
	public void visitFilter(SIRFilter self, SIRFilterIter iter) {
		JMethodDeclaration work = self.getWork();
		// cannot look for push operators as
		// they could be any where of the code
		work.addStatementFirst(makeInitCounter(self));
		work.addStatement(makeEndCounter(self));
		
		//FIXME: need to check for duplication?
		counterSet.add(makePerfCounterName(self));
	}

	private static JExpressionStatement makeInitCounter(SIRFilter self) {
		return new JExpressionStatement(new JEmittedTextExpression(
				GLOBAL_COUNTER + " = " + GET_CURRENT_TIME));
	}

	private static JExpressionStatement makeEndCounter(SIRFilter self) {
		return new JExpressionStatement(new JEmittedTextExpression(
				makePerfCounterName(self) + " += " + GET_CURRENT_TIME
						+ "-" + GLOBAL_COUNTER));
	}
	
	private static String makePerfCounterName(SIRFilter self) {
		return getName(self) + PERF_COUNTER_TAG;
	}
	
	public static Set<String> getPerfCounters() {
		return counterSet;
	}
	
	public static String getGlobalCounter() {
		return GLOBAL_COUNTER;
	}
	
	public static String getPerfCounterType() {
		return PERF_COUNTER_TYPE;
	}
	
	public static String getCurrentTimeDeclaration() {
		return GET_CURRENT_TIME;
	}
}
