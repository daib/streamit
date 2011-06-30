package at.dms.kjc.simulator;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Iterator;

import lpsolve.LpSolve;
import lpsolve.LpSolveException;
import at.dms.kjc.CStdType;
import at.dms.kjc.KjcOptions;
import at.dms.kjc.common.CommonUtils;
import at.dms.kjc.flatgraph.FlatNode;
import at.dms.kjc.flatgraph.FlatVisitor;
import at.dms.kjc.flatgraph.StaticStreamGraph;
import at.dms.kjc.sir.SIRFilter;
import at.dms.kjc.sir.SIRJoiner;
import at.dms.kjc.sir.SIRSplitter;

public class RateMatcher {

  private LpSolve prob;
  private HashMap<FlatNode, Integer> nodeMap;
  private HashMap<Integer, FlatNode> nodeRevMap;
  private int numnodes;

  public HashMap<FlatNode, Integer> initCount, steadyCount;

  class NodeCounter implements FlatVisitor {
    int count;
    public NodeCounter()
    {
      nodeMap = new HashMap<FlatNode, Integer>();
      nodeRevMap = new HashMap<Integer, FlatNode>();
      count = 0;
    }
    public void visitNode(FlatNode n)
    {
      nodeMap.put(n, count);
      nodeRevMap.put(count, n);
      System.out.println(n.contents.getName() + " -> " + count);
      count++;
    }
    public int getCount()
    {
      return count;
    }
  }

  class FormConstraints implements FlatVisitor {

    private double[] row;

    public FormConstraints()
    {
      row = new double[1+2*numnodes];
    }

    public void visitNode(FlatNode n)
    {
      int id = nodeMap.get(n);

      try {
        for(int i=0; i<1+2*numnodes; i++)
          row[i] = 0.0;

        row[1+id] = 1.0;
        prob.addConstraint(row, LpSolve.GE, 0.0);
        row[1+id] = 0.0;

        row[1+numnodes+id] = 1.0;
        prob.addConstraint(row, LpSolve.GE, 1.0);
        row[1+numnodes+id] = 0.0;

        for(int i=0; i<n.ways; i++) {
          FlatNode src = n;
          FlatNode dest = n.getEdges()[i];

          int push = 0, peek = 0, pop = 0;

          push = FlatNode.getItemsPushed(src, dest);

          if(dest.isFilter()) {
            SIRFilter d = dest.getFilter();
            pop = d.getPopInt();
            peek = d.getPeekInt();
          }
          else if(dest.contents instanceof SIRSplitter) {
            SIRSplitter d = (SIRSplitter)dest.contents;
            if(d.getType().isDuplicate()) {
              pop = 1;
              peek = 1;
            }
            else if(d.getType().isRoundRobin()) {
              pop = d.getSumOfWeights();
              peek = pop;
            }
            else {
              assert false : "Unknown splitter type";
            }
          }
          else if(dest.contents instanceof SIRJoiner) {
            SIRJoiner d = (SIRJoiner)dest.contents;
            int which_input = -1;
            for(int j=0; j<dest.inputs; j++) {
              if(dest.incoming[j] == src) {
                which_input = j;
                break;
              }
            }
            assert which_input >= 0 : "Unable to find edge";

            if(d.getType().isRoundRobin()) {
              pop = d.getWeights()[which_input];
              peek = pop;
            }
            else {
              assert false : "Unknown joiner type";
            }
          }
          else {
            assert false : "Unknown dest type";
          }

          int src_id = id;
          int dest_id = nodeMap.get(dest);

          assert peek >= pop : "Peek should be greater than or equal to pop";

          row[1+numnodes+src_id] = push;
          row[1+numnodes+dest_id] = -pop;
          prob.addConstraint(row, LpSolve.EQ, 0.0);
          row[1+numnodes+src_id] = 0.0;
          row[1+numnodes+dest_id] = 0.0;

          row[1+src_id] = push;
          row[1+dest_id] = -pop;
          prob.addConstraint(row, LpSolve.GE, peek-pop);
          row[1+src_id] = 0.0;
          row[1+dest_id] = 0.0;
        }
      }
      catch (LpSolveException e) {
        System.err.println("Problem in adding constraints");
        System.exit(1);
      }
    }

  }

  private BigInteger gcd(BigInteger[] list)
  {
    if(list.length == 1)
      return list[0];

    BigInteger cur = list[0].gcd(list[1]);

    for(int i=2; i<list.length; i++) {
      cur = cur.gcd(list[i]);
    }

    return cur;
  }

  public RateMatcher(StaticStreamGraph s)
  {
    initCount = new HashMap<FlatNode, Integer>();
    steadyCount = new HashMap<FlatNode, Integer>();
    NodeCounter nc = new NodeCounter();
    s.getTopLevel().accept(nc, null, true);

    numnodes = nc.getCount();

    try {
      prob = LpSolve.makeLp(0, 2 * numnodes);

      for(int i=0; i<numnodes; i++) {
        prob.setColName(i+1, "i_" + i);
        prob.setInt(i+1, true);
      }
      for(int i=0; i<numnodes; i++) {
        prob.setColName(numnodes+i+1, "s_" + i);
        prob.setInt(numnodes+i+1, true);
      }

      s.getTopLevel().accept(new FormConstraints(), null, true);

      prob.writeLp("test-rm.lp");

      int retval = prob.solve();

      if(retval != LpSolve.OPTIMAL) {
        assert false : "rate matching problem could not be solved";
      }

      prob.printSolution(1);

      double[] row = new double[2*numnodes];
      prob.getVariables(row);

      BigInteger[] init = new BigInteger[numnodes];
      BigInteger[] steady = new BigInteger[numnodes];

      for(int i=0; i<numnodes; i++) {
        init[i] = BigInteger.valueOf((long)row[i]);
      }
      for(int i=0; i<numnodes; i++) {
        steady[i] = BigInteger.valueOf((long)row[numnodes+i]);
      }

      BigInteger max = BigInteger.ZERO;

      for(int i=0; i<numnodes; i++) {
        if(max.compareTo(steady[i]) == -1)
          max = steady[i];
      }

      for(int i=0; i<numnodes; i++) {
        FlatNode f = nodeRevMap.get(i);
        if(f.contents instanceof SIRSplitter || f.contents instanceof SIRJoiner) {
          if(CommonUtils.getOutputType(f) == CStdType.Void)
            steady[i] = max;
        }
      }

      BigInteger gcdsteady = gcd(steady);

      for(int i=0; i<numnodes; i++) {
        steady[i] = steady[i].divide(gcdsteady);
      }

      Iterator iter=nodeMap.keySet().iterator();
      while(iter.hasNext()) {
        FlatNode f = (FlatNode)iter.next();
        int t = nodeMap.get(f).intValue();
        initCount.put(f, init[nodeMap.get(f).intValue()].intValue());
        steadyCount.put(f, steady[nodeMap.get(f).intValue()].intValue());
      }

      for(FlatNode n : steadyCount.keySet()) {
       /* int val = steadyCount.get(n).intValue();
        val = val;
        steadyCount.put(n, val);*/
        System.out.println("Steady count of " + n.contents.getName() + " = " + steadyCount.get(n).intValue());
      }
    }
    catch (LpSolveException e) {
      System.err.println("Problem in calling lp_solve");
      System.exit(1);
    }
  }

}