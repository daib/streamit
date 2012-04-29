// $Header: /n/tiamat/y/repository/StreamItNew/streams/src/at/dms/kjc/cluster/GenerateClusterDotH.java,v 1.1 2009/02/24 18:14:55 hormati Exp $
package at.dms.kjc.coroutine;

import java.io.FileWriter;

import at.dms.kjc.common.CodegenPrintWriter;

/**
 * Generate file cluster.h
 * 
 * <p>Probably legacy code since the generated file
 * contains a single line of code and that is commented out.</p>
 * 
 * @author Janis
 *
 */

public class GenerateClusterDotH {

    /**
     * Generate file cluster.h
     *
     */
    public static void generateClusterDotH() {

        CodegenPrintWriter p = new CodegenPrintWriter();

        p.println("#ifndef __CLUSTER_H");
        p.println("#define __CLUSTER_H");
        
        p.println("//#define __CHECKPOINT_FREQ 10000");
        
        p.println("#endif // __CLUSTER_H");
        try {
            FileWriter fw = new FileWriter("cluster.h");
            fw.write(p.getString());
            fw.close();
        }
        catch (Exception e) {
            System.err.println("Unable to write cluster.h");
            System.exit(1);
        }   
    }


}