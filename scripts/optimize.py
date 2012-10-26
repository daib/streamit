# /usr/bin/python

import os
import sys
import re

path = sys.argv[1]

iterations = 100

OneGhz = 1000000000
 
profiling_cpu_freq = 3 * OneGhz  # 3GHz
assumed_cpu_freq = OneGhz

os.chdir(path)

for dir in os.listdir(path):
    # run to obtain profiling information first
    os.chdir('./' + dir + '/streamit')
    
    os.system("pwd")
    
    for dim in [4, 6, 8]:
        # compile from str to c
        os.system("make -f Makefile.mk BACKEND=\'--spacetime --newSimple " + str(dim) + " --i " + str(iterations) + "\'")
        
        # compile the generated c program
        os.system("cp /home/dai/prog/compiler/streamit/tmp/Makefile ./")
        #shutil.copy2('$STREAMIT_HOME/tmp/Makefile', './')
        os.system("make")
        
        # run the program to obtain running time information
        logfile = str(dim) + "x" + str(dim) + "_time.log"
        os.system("./stream > " + logfile)
        
        ncycles = 0
        # parse log file for running time
        FILE = open(logfile, 'r')
        for line in FILE.readlines():
            m = re.search('Running time\s*:?\s*(\d+.?\d+(E|e)(\+|-)?\d+)', line)
            if m != None:
                rt = float(m.group(1))  # running time in nsec
                ncycles = int(rt * profiling_cpu_freq / assumed_cpu_freq / (dim * dim)/ iterations)  # convert to the numbers of cycles if runned in 1GHz
                break
        
        FILE.close()
        
        # use the streamit compiler to obtain intercore communication bandwidth in one iteration 
        commlog = str(dim) + "x" + str(dim) + "_com.log"
        #os.system("make -f Makefile.mk BACKEND=\'--spacetime --profile --newSimple " + str(dim) + " --i " + str(iterations) + " \' > " + str(commlog))
        
        FILE = open(commlog, 'r')
        flows = []
        for line in FILE.readlines():
            line = line.replace(' ', '')
            m = re.search('Node\((\d+),(\d+)\)->Node\((\d+),(\d+)\):?(\d+)bytes', line)
            if m != None:
                flow = []
                for i in range(1,6):
                    flow.append(int(m.group(i)))
                flows.append(flow)
        
        FILE.close()
        
        # generate ILP files
        
    os.chdir("../../")
        
