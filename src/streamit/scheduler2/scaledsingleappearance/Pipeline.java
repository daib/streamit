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
import streamit.scheduler2.iriter./*persistent.*/
PipelineIter;
import streamit.scheduler2.hierarchical.StreamInterface;
import streamit.scheduler2.base.StreamFactory;
import streamit.scheduler2.hierarchical.PhasingSchedule;

/**
 * This class implements a single-appearance algorithm for creating
 * schedules.
 * 
 * @version 2
 * @author  Michal Karczmarek
 */

public class Pipeline extends streamit.scheduler2.hierarchical.Pipeline {
    private PipelineIter pipeline;

    public Pipeline(PipelineIter iterator, StreamFactory factory) {
        super(iterator, factory);
    }

    public void computeSchedule() {
        // first compute schedules for all my children
        {
            int nChild;
            for (nChild = 0; nChild < getNumChildren(); nChild++) {
                getChild(nChild).computeSchedule();
            }
        }

        // now go through all the children again and figure out how many
        // data each child needs to be executed to fill up the buffers
        // enough to initialize the entire pipeline without producing
        // any data - this means that the last child doesn't get executed
        // even once
        // I do this by iterating from end to beggining and computing the
        // number of elements that each child needs to produce
        int numExecutionsForInit[] = new int[getNumChildren()];
        {
            int neededByNext = 0;

            // go through the children from last to first
            int nChild = getNumChildren() - 1;
            for (; nChild >= 0; nChild--) {
                StreamInterface child = getHierarchicalChild(nChild);

                int producesForInit = child.getInitPush();
                int producesPerIter = child.getSteadyPush();
                int numItersInit;
                if (producesPerIter != 0) {
                    // this child actually produces some data - this is
                    // the common case
                    numItersInit = ((neededByNext - producesForInit)
                            + producesPerIter - 1)
                            / producesPerIter;
                    if (numItersInit < 0)
                        numItersInit = 0;
                } else {
                    // this child does not produce any data
                    // make sure that consumedByPrev is 0 (otherwise
                    // I cannot execute the next child, 'cause it will
                    // never get any input)
                    assert neededByNext == 0;

                    // there will be no cycles executed for initialization
                    // of children downstream
                    numItersInit = 0;
                }

                // associate the child with the number of executions required
                // by the child to fill up the pipeline
                numExecutionsForInit[nChild] = numItersInit;

                // and figure out how many data this particular child
                // needs to initialize the pipeline.  this is complicated
                // by the fact that there are essentially two separate
                // init stages, and because the extra peek on one of these 
                // might be different from the steady-state peek
                // explanation in notebook, 02/07/02
                {
                    // compute the two-stage-init peek and pop values
                    int pop_i = child.getInitPop() + numItersInit
                            * child.getSteadyPop();
                    int peek_i = MAX(
                            child.getInitPeek(),
                            child.getInitPop()
                                    + numItersInit
                                    * child.getSteadyPop()
                                    + (child.getSteadyPeek() - child
                                            .getSteadyPop()));

                    // now save how much data is needed before peek adjust:
                    neededByNext = peek_i;

                    // and perform the peek adjust
                    neededByNext += MAX(
                            (child.getSteadyPeek() - child.getSteadyPop())
                                    - (peek_i - pop_i), 0);

                }
            }
        }

        // compute an initialization schedule
        // now that I know how many times each child needs to be executed,
        // it should be easy to compute this - just execute each stream
        // an appropriate number of times.
        // In this approach, I do not care about the size of the buffers, and
        // may in fact be enlarging them much more than necessary
        {
            // the entire schedule will be just a single phase, so I need
            // to wrap it in a unique PhasingSchedule myself
            PhasingSchedule initStage;
            initStage = new PhasingSchedule(this);

            int nChild;
            for (nChild = 0; nChild < getNumChildren(); nChild++) {
                StreamInterface child = getHierarchicalChild(nChild);
                // add the initialization schedule:
                initStage.appendPhase(child.getPhasingInitSchedule());

                // add the steady schedule an appropriate number of times:
                {
                    int n;
                    for (n = 0; n < numExecutionsForInit[nChild]; n++) {
                        initStage.appendPhase(child.getPhasingSteadySchedule());
                    }
                }
            }

            // now append the one init phase to my initialization phase
            if (initStage.getNumPhases() != 0)
                addInitScheduleStage(initStage);
        }

        // compute the steady state schedule
        // this one is quite easy - I go through the children and execute them
        // an appropriate number of times
        {
            PhasingSchedule steadyPhase;
            steadyPhase = new PhasingSchedule(this);

            int nChild;
            for (nChild = 0; nChild < getNumChildren(); nChild++) {
                StreamInterface child = getHierarchicalChild(nChild);

                int n;
                for (n = 0; n < getChildNumExecs(nChild); n++) {
                    steadyPhase.appendPhase(child.getPhasingSteadySchedule());
                }
            }

            addSteadySchedulePhase(steadyPhase);
        }
    }

    /**
     * Compute the number of times each child needs to execute
     * for the entire pipeline to execute a minimal full steady
     * state execution.
     * 
     * This function is essentially copied from the old scheduler.
     */
    public void computeSteadyState() {
        childrenNumExecs = new BigInteger[nChildren];
        
        int scale = Scheduler.scaled;
        //if all children are filters
        //then scale their iterations
        for (int nChild = 0; nChild < nChildren; nChild++) {
            streamit.scheduler2.base.StreamInterface child = children[nChild];
            if (child.getStreamIter().isFilter() == null) {
                scale = 1;
                break;
            }
        }

        // multiply the children's num of executions
        // by the appropriate output factors
        {
            // use this to keep track of product of all output rates
            BigInteger outProduct = BigInteger.ONE;

            int nChild;
            for (nChild = 0; nChild < nChildren; nChild++) {
                streamit.scheduler2.base.StreamInterface child = children[nChild];
                assert child != null;

                childrenNumExecs[nChild] = outProduct;

                outProduct = outProduct.multiply(BigInteger.valueOf(child
                        .getSteadyPush()));
            }
        }

        // now multiply the children's num of executions
        // by the appropriate input factors
        {
            // use this value to keep track of product of input rates
            BigInteger inProduct = BigInteger.ONE;

            int nChild;
            for (nChild = nChildren - 1; nChild >= 0; nChild--) {
                streamit.scheduler2.base.StreamInterface child = children[nChild];
                assert child != null;

                // continue computing the number of executions this child needs
                childrenNumExecs[nChild] = childrenNumExecs[nChild]
                        .multiply(inProduct);

                inProduct = inProduct.multiply(BigInteger.valueOf(child
                        .getSteadyPop()));
            }
        }

        // compute the GCD of number of executions of the children
        // and didivde those numbers by the GCD
        BigInteger gcd;

        {
            gcd = childrenNumExecs[0];

            int nChild;
            for (nChild = 0; nChild < nChildren; nChild++) {
                gcd = gcd.gcd(childrenNumExecs[nChild]);

                // Debugging:
                if (debugrates) {
                    if (librarydebug) {
                        System.err.println(pipeline.getObject().getClass()
                                .getName()
                                + "["
                                + nChild
                                + "] executions before gcd = "
                                + childrenNumExecs[nChild]);
                    } else {
                        System.err.println(((SIRStream) pipeline.getObject())
                                .getIdent()
                                + "["
                                + nChild
                                + "] executions before gcd = "
                                + childrenNumExecs[nChild]);
                    }
                }
                // End Debugging
            }
        }

        // divide the children's execution counts by the gcd
        {
            int nChild;
            for (nChild = 0; nChild < nChildren; nChild++) {
                streamit.scheduler2.base.StreamInterface child = children[nChild];
                assert child != null;
                assert childrenNumExecs[nChild].mod(gcd)
                        .equals(BigInteger.ZERO);

                childrenNumExecs[nChild] = childrenNumExecs[nChild].divide(gcd);

                // Debugging:
                if (debugrates) {
                    if (librarydebug) {
                        System.err.println(pipeline.getObject().getClass()
                                .getName()
                                + "["
                                + nChild
                                + "] executions = "
                                + childrenNumExecs[nChild]);
                    } else {
                        System.err.println(((SIRStream) pipeline.getObject())
                                .getIdent()
                                + "["
                                + nChild
                                + "] executions = "
                                + childrenNumExecs[nChild]);
                    }
                }
                // End Debugging

                // make sure that the child executes a positive
                // number of times!
                assert childrenNumExecs[nChild].signum() == 1 : ""
                        + childrenNumExecs[nChild].signum() + " == 1  "
                        + pipeline.getObject().getClass().getName() + "["
                        + nChild + "]";
            }
        }

        for (int nChild = 0; nChild < nChildren; nChild++) {
            childrenNumExecs[nChild] = childrenNumExecs[nChild].multiply(BigInteger.valueOf(scale));
        }
        
        // initialize self
        {
            int peekExtra = children[0].getSteadyPeek()
                    - children[0].getSteadyPop();
            int pop = childrenNumExecs[0].intValue()
                    * children[0].getSteadyPop();
            int push = childrenNumExecs[nChildren - 1].intValue()
                    * children[nChildren - 1].getSteadyPush();

            setSteadyPeek(pop + peekExtra);
            setSteadyPop(pop);
            setSteadyPush(push);

            // Debugging:
            if (debugrates) {
                if (librarydebug) {
                    System.err.print(pipeline.getObject().getClass().getName());
                } else {
                    System.err.print(((SIRStream) pipeline.getObject())
                            .getIdent());
                }
                System.err.println(" steady state: push " + push + " pop "
                        + pop + " peekExtra " + peekExtra);
            }
            // End Debugging
        }
    }

    public int getNumNodes() {
        int nodes = 0;
        for (int nChild = 0; nChild < nChildren; nChild++) {
            streamit.scheduler2.base.StreamInterface child = children[nChild];
            assert child != null;

            nodes += child.getNumNodes();
        }
        return nodes;
    }

    public int getNumNodeFirings() {
        int firings = 0;
        for (int nChild = 0; nChild < nChildren; nChild++) {
            streamit.scheduler2.base.StreamInterface child = children[nChild];
            assert child != null;

            firings += child.getNumNodeFirings() * getChildNumExecs(nChild);
        }
        return firings;
    }
}
