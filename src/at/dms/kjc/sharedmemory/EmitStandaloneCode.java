// $Id
package at.dms.kjc.sharedmemory;

//import java.util.*;
//import at.dms.kjc.*;
import at.dms.kjc.JExpression;
import at.dms.kjc.JMethodCallExpression;
import at.dms.kjc.KjcOptions;
import at.dms.kjc.backendSupport.BackEndFactory;
import at.dms.kjc.backendSupport.ComputeNode;
import at.dms.kjc.backendSupport.EmitCode;
import at.dms.kjc.backendSupport.EmitTypedefs;
import at.dms.kjc.common.CodegenPrintWriter;
import at.dms.kjc.common.CommonUtils;
import at.dms.kjc.sir.SIRStructure;

/**
 * Takes a ComputeNode collection, a collection of Channel's, 
 * and a mapping from Channel x end -> ComputeNode and emits code for the ComputeNode. 
 * Most work in superclass.
 * 
 * @author dimock
 *
 */
public class EmitStandaloneCode extends EmitCode {

    /**
     * Constructor.
     * @param backendbits indicates BackEndFactory containing all useful info.
     */
    public EmitStandaloneCode(
            BackEndFactory<UniProcessors, UniProcessor, UniComputeCodeStore, Integer> backendbits) {
        super(backendbits);
    }

    /**
     * Create typedefs and other general header info.
     * @param structs       Structures detected by front end.
     * @param backendbits   BackEndFactory to get access to rest of code.
     * @param p             a CodeGenPrintWriter on which to emit the C code.
     */
    static public void emitTypedefs(SIRStructure[] structs,
            BackEndFactory backendbits, CodegenPrintWriter p) {
        p.println("#ifndef __STRUCTS_H\n");
        p.println("#define __STRUCTS_H\n");
        EmitTypedefs.emitTypedefs(structs, backendbits, p);
        p.println("typedef int bit;\n");
        p.println("#ifndef round");
        p.println("#define round(x) (floor((x)+0.5))");
        p.println("#endif\n");

        p.println("#endif // __STRUCTS_H\n");
    }

    /**
     * Standard code for front of a C file here.
     * 
     */
    public void generateCHeader(CodegenPrintWriter p) {
        p.println("#include <math.h>"); // in case math functions
        p.println("#include <stdio.h>"); // in case of FileReader / FileWriter
        p.println("#include \"structs.h\"");
        p.newLine();
        p.println("int " + UniBackEndFactory.iterationBound + ";");
        p.newLine();
    }

    /**
     * Generate a "main" function.
     * Override!
     */
    public void generateMain(CodegenPrintWriter p) {
        p.println();

        p.println("void* run_threads(void* ptr) {");
        p.println("\tlong int i = (long int)ptr;");
        for (int i = 0; i < backendbits.getComputeNodes().size(); i++) {
            p.println("\tif(i == " + i + ") {\n\t\t_MAIN__" + i + "();\n\t}");
        }
        p.println("\treturn NULL;");
        p.println("}");

        p.println();
        p.println("// main() Function Here");
        //        p.println(
        //"/* helper routines to parse command line arguments */\n"+
        //"#include <unistd.h>\n" +
        //"\n"+
        //"/* retrieve iteration count for top level driver */\n"+
        //"static int __getIterationCounter(int argc, char** argv) {\n"+
        //"    int flag;\n"+
        //"    while ((flag = getopt(argc, argv, \"i:\")) != -1)\n"+
        //"       if (flag == 'i') return atoi(optarg);\n"+
        //"    return -1; /* default iteration count (run indefinitely) */\n"+
        //"}"+
        //"\n");
        p.println("int main(int argc, char** argv) {");
        p.indent();
        p.println("\tif(argc > 1)");
        p.println("\t\t" + UniBackEndFactory.iterationBound
                + " = atoi(argv[1]);");
        p.println("\telse");
        p.println("\t\t" + UniBackEndFactory.iterationBound
                + "   = 10;// __getIterationCounter(argc, argv);\n");

        p.println("\tif(pthread_barrier_init(&barr, NULL, "
                + backendbits.getComputeNodes().size()
                + "))\n"
                + "\t{\n\t\tprintf(\"Could not create a barrier...\");\n\t\treturn -1;\n"
                + "\t}\n");
        if (backendbits.getComputeNodes().size() > 1)
            p.println("\tpthread_t threads["
                    + (backendbits.getComputeNodes().size() - 1) + "];");

        // compute width and length of the logical core
        //        int numCores = 2 * CommonUtils.nextPow2(backendbits.getComputeNodes()
        //                .size());
        //        int log2 = (int) Math.ceil((Math.log10(numCores) / Math.log10(2)));
        //        int w = (int) Math.pow(2, log2 / 2);
        //        int l = numCores / w;
        //
        //        System.out.println("x = " + w + " y = " + l);

        p.println("\tunsigned long flag;");
        //estimate the number of cores

        int cx = 1;
        int cy = 1;

        if (KjcOptions.cx >= 1) {
            cx = KjcOptions.cx;
        }

        if (KjcOptions.cy >= 1) {
            cy = KjcOptions.cy;
        }

        int w = KjcOptions.newSimple * cx;

        int mainComputeNode = 0;
        for (int i = 0; i < backendbits.getComputeNodes().size(); i++) {
            ComputeNode cn = backendbits.getComputeNodes().getNthComputeNode(i);

//            long attr = 0x0020; //compose
            //            int firstCore = (i + 1) * 2;

            //            int x = firstCore % w;
            //            int y = firstCore / w;
            //
            //            for (int j = 0; j < 2; j++) {
            //                for (int k = 0; k < 1; k++) {
            //                    int index = (y + k) * w + j + x;
            //                    attr |= (1 << index) << 16;
            //                }
            //            }
            
            p.println("\tflag = 0;");
            p.println("\tsetComposition(flag, COMPOSITION_CORES);");

            int x = cn.getX() * cx;
            int y = cn.getY() * cy;

            String topology = null;
            
            for (int j = 0; j < cx; j++) {
                for (int k = 0; k < cy; k++) {
                    int index = (y + k) * w + (x + j);
                    if(topology == null) {
                        topology = "C"+index;
                    } else 
                        topology = topology + "|C" + index;
                }
            }

//            p.println("\tflag = " + attr + ";");
            p.println("\tsetTopology(flag, " + topology + ");");

            if (cn.getX() == 0 && cn.getY() == 0) {
                mainComputeNode = i;
                p.println("\tCompose(flag);");
                //                continue;
            } else
                p.println("\tpthread_create(&threads[" + i
                        + "], (void*)&flag, &run_threads, (void*)" + i + ");");
            p.println();
        }

        p.println("\t_MAIN__" + mainComputeNode + "();");

        for (int i = 0; i < backendbits.getComputeNodes().size(); i++) {
            if (i == mainComputeNode)
                continue;
            p.println("\tpthread_join(threads[" + i + "], NULL);");
        }

        if (KjcOptions.profile) {
            for (String s : CodeStoreHelperSharedMem.runtimeObjs) {
                p.println(s + "_runtime_obj.print_stats();");
            }
        }

        p.println("return 0;");
        p.outdent();
        p.println("}");
    }

    public void emitCodeForComputeNode(ComputeNode n, CodegenPrintWriter p) {
        codegen = new CPPCodeGen(p);
        emitCodeForComputeNode(n, p, codegen);
    }

    protected class CPPCodeGen extends CodeGen {

        protected CPPCodeGen(CodegenPrintWriter p) {
            super(p);
            // TODO Auto-generated constructor stub
        }

        /**
         * Prints a method call expression.
         */
        @Override
        public void visitMethodCallExpression(JMethodCallExpression self,
                JExpression prefix, String ident, JExpression[] args) {
            // math functions are converted to use their floating-point counterparts;
            p.print(ident);

            p.print("(");
            visitArgs(args, 0);
            p.print(")");
        }

    }

}
