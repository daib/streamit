# /usr/bin/python

import os
import sys
import re
import copy

def trace_to_file(name, trace):
    FILE = open(name, 'w')
    for packet in trace:
        line = ''
        for item in packet:
            line = line + ' ' + str(item)
        line = line.strip()
        print>>FILE, line
        
    FILE.close()
    
def write_traffic(name, traces):
    #write to files
    os.system('mkdir traffics')
    
    os.chdir('./traffics')
    packets = []
    for trace in traces:
        trace_to_file(name + '.' + str(trace[0]) + '.' + str(trace[1]), trace[2])
        packets.extend(trace[2])
        
    packets.sort(key=lambda tup:tup[0])
    trace_to_file(name, packets)
    
    os.chdir('../')
    
    
def comm_prof(dim):
    # use the streamit compiler to obtain intercore communication bandwidth in one iteration 
    commlog = str(dim) + "x" + str(dim) + "_com.log"
    os.system("make -f Makefile.mk BACKEND=\'--spacetime --profile --newSimple " + str(dim) + " --i " + str(iterations) + " \' > " + str(commlog))
    
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
    
    return flows

def time_prof(dim):
    # compile from str to c
    os.system("make -f Makefile.mk BACKEND=\'--spacetime --newSimple " + str(dim) + " --i " + str(iterations) + "\'")
    
    # compile the generated c program
    os.system("cp /home/dai/prog/compiler/streamit/tmp/Makefile ./")
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
    
    return ncycles

def traffic_gen(flows):
    traffics = []
    #for each source node
    for x in range(0, dim):
        for y in range(0,dim):
            # find flows that have this node as the source node
            fs = []
            total_traffic = 0 #total traffic in bytes from this node in one iteration
            smallest_traffic = float('inf')
            
            packets = []    #packets from this source
            
            traffics.append([x,y,packets])
            
            for f in flows:
                if f[0] == x and f[1] == y:
                    flows.remove(f)
                    f.append(0)  #total traffic used for this flow
                    fs.append(f)
                    total_traffic += f[traffic_idx]
                    if smallest_traffic > f[traffic_idx]:
                        smallest_traffic = f[traffic_idx]
            
            iter = 0
            while len(fs) > 0:
                iter = iter + 1
                current_time = iter + start_time
                # sequentially send traffic for each flow
                for f in fs:
                    # get the traffic fraction of this flow
                    max_allow = packet_bytes * f[traffic_idx] * iter / smallest_traffic    # max traffic amount till now for this flow
                    traffic_left = max_allow - f[traffic_used_idx] # the amount of traffic left till now
                    
                    if f[traffic_idx] - max_allow < packet_bytes:   # if the amount of traffic left smaller than one packet
                        if f[traffic_idx] - max_allow > 0:
                            packet = copy.deepcopy(f[0:4])
                            packet.insert(0,current_time)
                            packet.append(f[traffic_idx] - max_allow)
                            packets.append(packet)
                        fs.remove(f)
                    else:
                        # convert this amount traffic to numbers of packets
                        npackets = traffic_left / packet_bytes
                    
                        for n in range(0, npackets):
                            packet = copy.deepcopy(f[0:4])
                            packet.insert(0,current_time)
                            packet.append(packet_bytes)
                            packets.append(packet)
                            f[traffic_used_idx] = f[traffic_used_idx] + packet_bytes
                            
    return traffics

#######################################################################################
path = sys.argv[1]

iterations = 100

OneGhz = 1000000000
 
profiling_cpu_freq = 3 * OneGhz  # 3GHz
assumed_cpu_freq = OneGhz

os.chdir(path)

start_time = 10 # start sending packets at cycle 10
packet_flits = 6 # packet size in numbers of flits
flit_size = 8 #flit size in numbers of bytes
packet_bytes = packet_flits * flit_size
max_packet_bytes = packet_flits * flit_size

traffic_idx = 4
traffic_used_idx = 5

for dir in os.listdir(path):
    
    if os.path.isfile('./dir'):
        continue
    # run to obtain profiling information first
    os.chdir('./' + dir + '/streamit')
    
    os.system("pwd")
    
    for dim in [4, 6, 8]:
        
        ncycles = time_prof(dim)
        
        flows = comm_prof(dim)
        
        #generate ILP files
        
        #generate wire config
        
        #generate routing config
        
        #generate traffic
        dirty_flows = copy.deepcopy(flows) # make a copy of the flows in case it is modified
        
        traffics = traffic_gen(dirty_flows)
                                
        write_traffic(dir, traffics)
        
        #invoke simulation
        os.system('vnoc ' + dir)
        
        #collect data
        
        
    os.chdir("../../")
        
