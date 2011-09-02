/*
 * Copyright 2003 by the Massachusetts Institute of Technology.
 *
 * Permission to use, copy, modify, and distribute this
 * software and its documentation for any purpose and without
 * fee is hereby granted, provided that the above copyright
 * notice appear in all copies and that both that copyright
 * notice and this permission notice appear in supporting
 * documentation, and that the name of M.I.T. not be used in
 * advertising or publicity pertaining to distribution of the
 * software without specific, written prior permission.
 * M.I.T. makes no representations about the suitability of
 * this software for any purpose.  It is provided "as is"
 * without express or implied warranty.
 */

package streamit.scheduler2.scaledsingleappearance;

import java.math.BigInteger;

import at.dms.kjc.sir.SIRStream;
import streamit.misc.Fraction;
import streamit.scheduler2.iriter./*persistent.*/
SplitJoinIter;
import streamit.scheduler2.base.StreamFactory;
import streamit.scheduler2.hierarchical.StreamInterface;
import streamit.scheduler2.hierarchical.PhasingSchedule;

/**
 * This class implements a single-appearance algorithm for creating
 * schedules.
 * 
 * @version 2
 * @author  Michal Karczmarek
 */

public class SplitJoin extends streamit.scheduler2.hierarchical.SplitJoin {
    final private PhasingSchedule splitSched, joinSched;

    public SplitJoin(SplitJoinIter iterator, StreamFactory factory) {
        super(iterator, factory);

        // compute the splitter schedule
        {
            splitSched = new PhasingSchedule(this);
            int nPhase;
            for (nPhase = 0; nPhase < super.getNumSplitPhases(); nPhase++) {
                splitSched.appendPhase(super.getSplitPhase(nPhase));
            }
        }

        // compute the joiner schedule
        {
            joinSched = new PhasingSchedule(this);
            int nPhase;
            for (nPhase = 0; nPhase < super.getNumJoinPhases(); nPhase++) {
                joinSched.appendPhase(super.getJoinPhase(nPhase));
            }
        }
    }

    // Override the functions that deal with schedules for joiner
    // and splitter - single appearance schedules only need one
    // phase for the splitter and joiner schedules respectively

    public int getNumSplitPhases() {
        return 1;
    }

    public PhasingSchedule getSplitPhase(int nPhase) {
        // single appearance schedule has only one split phase
        assert nPhase == 0;
        return splitSched;
    }

    /**
     * @return one phase schedule for the splitter
     */
    public PhasingSchedule getSplitPhase() {
        return splitSched;
    }

    public int getNumJoinPhases() {
        return 1;
    }

    public PhasingSchedule getJoinPhase(int nPhase) {
        // single appearance schedule has only one join phase
        assert nPhase == 0;
        return joinSched;
    }

    /**
     * @return one phase schedule for the joiner
     */
    public PhasingSchedule getJoinPhase() {
        return joinSched;
    }


    // this function is basically copied from scheduler v1
    public void computeSchedule() {
        // compute the children's schedules and figure out
        // how many times the split needs to be executed to feed
        // all the buffers so the children can initialize (including the
        // peek - pop amounts!)
        int initSplitRunCount = 0;
        {
            // go through all the children and check how much
            int nChild;
            for (nChild = 0; nChild < getNumChildren(); nChild++) {
                // get the child
                StreamInterface child = getHierarchicalChild(nChild);
                assert child != null;

                // compute child's schedule
                child.computeSchedule();

                // get the amount of data needed to initilize this child
                int childInitDataConsumption = child.getInitPeek();

                // this child may need more data in order to safely enter
                // the steady state computation model (as per notes 02/07/02)
                childInitDataConsumption += MAX(
                        (child.getSteadyPeek() - child.getSteadyPop())
                                - (child.getInitPeek() - child.getInitPop()), 0);

                // now figure out how many times the split needs to be run in
                // initialization to accomodate this child
                int splitRunCount;
                if (childInitDataConsumption != 0) {
                    // just divide the amount of data needed by data received
                    // per iteration of the split
                    int splitDataSent = getSteadySplitFlow().getPushWeight(
                            nChild);
                    assert splitDataSent > 0;

                    splitRunCount = (childInitDataConsumption + splitDataSent - 1)
                            / splitDataSent;
                } else {
                    // the child doesn't need any data to intitialize, so I
                    // don't need to run the split for it at all
                    splitRunCount = 0;
                }

                // pick the max
                if (splitRunCount > initSplitRunCount) {
                    initSplitRunCount = splitRunCount;
                }
            }
        }

        // compute the init schedule
        {
            PhasingSchedule initSched = new PhasingSchedule(this);

            // run through the split an appropriate number of times
            // and append it to the init schedule
            {
                PhasingSchedule splitSched = getSplitPhase();

                int nRun;
                for (nRun = 0; nRun < initSplitRunCount; nRun++) {
                    initSched.appendPhase(splitSched);
                }
            }

            // now add the initialization schedules for all the children
            {
                int nChild;
                for (nChild = 0; nChild < getNumChildren(); nChild++) {
                    StreamInterface child = getHierarchicalChild(nChild);

                    int nStage = 0;
                    for (; nStage < child.getNumInitStages(); nStage++) {
                        initSched.appendPhase(child
                                .getInitScheduleStage(nStage));
                    }
                }
            }

            if (initSched.getNumPhases() != 0)
                addInitScheduleStage(initSched);
        }

        // compute the steady schedule
        {
            PhasingSchedule steadySched = new PhasingSchedule(this);

            // first add the split schedule the right # of times
            {
                int nReps;
                for (nReps = 0; nReps < getSplitNumRounds(); nReps++) {
                    steadySched.appendPhase(getSplitPhase());
                }
            }

            // add the schedule for execution of all the children
            // of the split join
            {
                int nChild;
                for (nChild = 0; nChild < getNumChildren(); nChild++) {
                    StreamInterface child = getHierarchicalChild(nChild);

                    int nRun;
                    for (nRun = 0; nRun < getChildNumExecs(nChild); nRun++) {
                        int nPhase;
                        for (nPhase = 0; nPhase < child.getNumSteadyPhases(); nPhase++) {
                            steadySched.appendPhase(child
                                    .getSteadySchedulePhase(nPhase));
                        }
                    }
                }
            }

            // finally add the join schedule the right # of times
            {
                int nReps;
                for (nReps = 0; nReps < getJoinNumRounds(); nReps++) {
                    steadySched.appendPhase(getJoinPhase());
                }
            }

            addSteadySchedulePhase(steadySched);
        }
    }

    /**
     * Compute the number of times each child, the split and the join
     * need to execute for the entire splitjoin to execute a minimal 
     * full steady state execution.
     * 
     * This function is essentially copied from the old scheduler,
     * and modified to work with the new interfaces.
     */
    public void computeSteadyState() {
        // amount of data distributed to and collected by the split
        // and join
        //        int splitPushWeights[];
        //        int joinPopWeights[];
        //        int splitPopWeight, joinPushWeight;

        Fraction childrenRates[] = new Fraction[nChildren];
        Fraction splitRate = null;
        Fraction joinRate = null;

        int scale = Scheduler.scaled;
        //if all children are filters
        //then scale their iterations
        for (int nChild = 0; nChild < nChildren; nChild++) {
            streamit.scheduler2.base.StreamInterface child = getChild(nChild);
            if (child.getStreamIter().isFilter() == null) {
                scale = 1;
                break;
            }
        }
        // go through all children and calculate the rates at which
        // they will be called w.r.t. the splitter.
        // also, compute the rate of execution of the joiner
        // (if it ever ends up being executed)
        {
            int nChild;
            for (nChild = 0; nChild < nChildren; nChild++) {
                streamit.scheduler2.base.StreamInterface child = getChild(nChild);
                assert child != null;

                // the rate at which the child should be executed
                Fraction childRate = null;

                // rates at which the splitter is producing the data
                // and the child is consuming it:
                int numOut = getSteadySplitFlow().getPushWeight(nChild);
                int numIn = child.getSteadyPop();

                // is the splitter actually producing any data?
                if (numOut != 0) {
                    // if the slitter is producing data, the child better
                    // be consuming it!
                    assert numIn != 0 : numOut;

                    if (splitRate == null) {
                        // if I hadn't set the split rate yet, do it now
                        splitRate = new Fraction(BigInteger.ONE, BigInteger.ONE);
                    }

                    // compute the rate at which the child should be executing
                    // (relative to the splitter)
                    childRate = new Fraction(numOut, numIn).multiply(splitRate)
                            .reduce();

                    // if I still hadn't computed the rate at which the joiner
                    // is executed, try to compute it:
                    if (joinRate == null && child.getSteadyPush() != 0) {
                        // if the child is producing data, the joiner
                        // better be consuming it!
                        assert getSteadyJoinFlow().getPopWeight(nChild) != 0;

                        int childOut = child.getSteadyPush();
                        int joinIn = getSteadyJoinFlow().getPopWeight(nChild);

                        joinRate = new Fraction(childOut, joinIn).multiply(
                                childRate).reduce();
                    }
                }

                childrenRates[nChild] = childRate;

                // Debugging:
                if (debugsplitjoin) {
                    if (librarydebug) {
                        System.err.print(splitjoin.getObject().getClass()
                                .getName());
                    } else {
                        System.err.print(((SIRStream) splitjoin.getObject())
                                .getIdent());
                    }
                    System.err.println("[" + nChild + "].splitRate = "
                            + childRate);
                }
                // End Debugging

            }
        }

        // compute the rate of execution of the joiner w.r.t. children
        // and make sure that everything will be executed at consistant
        // rates (to avoid overflowing the buffers)
        {
            // if the splitter never needs to get executed (doesn't produce
            // any data), the joiner rate should be set to ONE:
            if (splitRate == null) {
                // I better not have computed the join rate yet!
                assert joinRate == null;

                // okay, just set it to ONE/ONE
                joinRate = new Fraction(BigInteger.ONE, BigInteger.ONE);
            }

            int nChild;
            for (nChild = 0; nChild < nChildren; nChild++) {
                streamit.scheduler2.base.StreamInterface child = getChild(nChild);
                assert child != null;

                // get the child rate
                Fraction childRate = childrenRates[nChild];

                // compute the new childRate:
                Fraction newChildRate = null;
                {
                    int childOut = child.getSteadyPush();
                    int joinIn = getSteadyJoinFlow().getPopWeight(nChild);

                    // does the child produce any data?
                    if (childOut != 0) {
                        // yes
                        // the split better consume some data too!
                        assert joinIn != 0;

                        if (joinRate == null) {
                            String name = "" + splitjoin.getObject();
                            assert false : "Null join rate in "
                                    + name
                                    + ". This can occur if your stream graph is decomposable into multiple graphs with no data flowing between them.";
                        }

                        // compute the rate at which the child should execute
                        // w.r.t. the splitter
                        newChildRate = new Fraction(joinIn, childOut).multiply(
                                joinRate).reduce();
                    } else {
                        // no
                        // the splitter better not consume any data either
                        assert joinIn == 0;
                    }
                }

                // if this is a new rate, put it in the array
                if (childRate == null) {
                    // I better have the rate here, or the child
                    // neither produces nor consumes any data!
                    assert newChildRate != null;

                    // set the rate
                    childrenRates[nChild] = newChildRate;

                    // Debugging:
                    if (debugsplitjoin) {
                        if (librarydebug) {
                            System.err.print(splitjoin.getObject().getClass()
                                    .getName());
                        } else {
                            System.err
                                    .print(((SIRStream) splitjoin.getObject())
                                            .getIdent());
                        }
                        System.err.println("[" + nChild + "].joinRate = "
                                + childRate);
                    }
                    // End Debugging
                }

                // okay, if I have both rates, make sure that they agree!
                if (childRate != null && newChildRate != null) {
                    if (!childRate.equals(newChildRate)) {
                        String name = "" + splitjoin.getObject();
                        String msg = "ERROR:\n"
                                + "Two paths in the splitjoin \""
                                + name
                                + "\" have different I/O rates.\n"
                                + "One path produces items at a rate of "
                                + childRate
                                + ", while another has a rate of "
                                + newChildRate
                                + ".\n"
                                + "Thus, the program cannot be executed without growing a buffer infinitely.\n"
                                + "Please check that the round-robin weights match the I/O rates of the\n"
                                + "child streams.\n";
                        // anonymous names aren't too helpful
                        if (name.startsWith("Anon") /* for library */
                                || name.startsWith("name=Anon") /* for compiler */) {
                            msg += "\n"
                                    + "Note:  the name \""
                                    + name
                                    + "\" indicates that this splitjoin is an\n"
                                    + "anonymous wrapper in your program.  You can identify this wrapper by\n"
                                    + "opening up the <filename>.java file and searching for this identifier,\n"
                                    + "or by refactoring your program to give the splitjoin its own name.\n";
                        }
                        assert false : msg;
                    }
                }
            }
        }

        // normalize all the rates to be integers
        {
            BigInteger multiplier;

            if (joinRate != null) {
                multiplier = joinRate.getDenominator();
            } else {
                multiplier = BigInteger.ONE;
            }

            // find a factor to multiply all the fractional rates by
            {
                int index;
                for (index = 0; index < nChildren; index++) {
                    Fraction childRate = (Fraction) childrenRates[index];
                    assert childRate != null;

                    BigInteger rateDenom = childRate.getDenominator();
                    assert rateDenom != null;

                    BigInteger gcd = multiplier.gcd(rateDenom);
                    multiplier = multiplier.multiply(rateDenom).divide(gcd);
                }
            }

            multiplier = multiplier.multiply(BigInteger.valueOf(scale));

            // multiply all the rates by this factor and set the rates for
            // the children and splitter and joiner
            {
                if (splitRate != null) {
                    splitRate = splitRate.multiply(multiplier);
                    assert splitRate.getDenominator().equals(BigInteger.ONE);
                    splitNumRounds = splitRate.getNumerator();
                } else {
                    splitNumRounds = BigInteger.ZERO;
                }

                if (joinRate != null) {
                    joinRate = joinRate.multiply(multiplier);
                    assert joinRate.getDenominator().equals(BigInteger.ONE);
                    joinNumRounds = joinRate.getNumerator();
                } else {
                    joinNumRounds = BigInteger.ZERO;
                }

                // normalize the children's rates and store them in
                // childrenNumExecs
                {
                    childrenNumExecs = new BigInteger[nChildren];

                    int nChild;
                    for (nChild = 0; nChild < nChildren; nChild++) {
                        Fraction childRate = (Fraction) childrenRates[nChild];
                        assert childRate != null;

                        Fraction newChildRate = childRate.multiply(multiplier);
                        assert newChildRate.getDenominator().equals(
                                BigInteger.ONE);

                        // set the rate
                        childrenNumExecs[nChild] = newChildRate.getNumerator();

                        // Debugging:
                        if (debugsplitjoin) {
                            if (librarydebug) {
                                System.err.print(splitjoin.getObject()
                                        .getClass().getName());
                            } else {
                                System.err.print(((SIRStream) splitjoin
                                        .getObject()).getIdent());
                            }
                            System.err.println("[" + nChild + "] executions = "
                                    + childrenNumExecs[nChild]);
                        }
                        // End Debugging

                        // make sure that the child executes a positive
                        // number of times!
                        assert childrenNumExecs[nChild].signum() == 1;
                    }
                }
            }
        }

        // setup my variables that come for Stream:
        {
            int pop = splitNumRounds.intValue()
                    * getSteadySplitFlow().getPopWeight();
            int push = joinNumRounds.intValue()
                    * getSteadyJoinFlow().getPushWeight();

            setSteadyPeek(pop);
            setSteadyPop(pop);
            setSteadyPush(push);

            // Debugging:
            if (debugrates) {
                if (librarydebug) {
                    System.err
                            .print(splitjoin.getObject().getClass().getName());
                } else {
                    System.err.print(((SIRStream) splitjoin.getObject())
                            .getIdent());
                }
                System.err.println(" steady state: push " + push + " pop "
                        + pop);
            }
            // End Debugging
        }
    }

    public int getNumNodes() {
        int nodes = 0;
        for (int nChild = 0; nChild < nChildren; nChild++) {
            streamit.scheduler2.base.StreamInterface child = getChild(nChild);
            assert child != null;

            nodes += child.getNumNodes();
        }
        if (getSplitNumRounds() > 0)
            nodes++;
        if (getJoinNumRounds() > 0)
            nodes++;
        return nodes;
    }

    public int getNumNodeFirings() {
        int firings = 0;
        for (int nChild = 0; nChild < nChildren; nChild++) {
            streamit.scheduler2.base.StreamInterface child = getChild(nChild);
            assert child != null;

            firings += child.getNumNodeFirings() * getChildNumExecs(nChild);
        }
        firings += getSplitNumRounds();
        firings += getJoinNumRounds();

        return firings;
    }
}
