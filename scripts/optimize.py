# /usr/bin/python

import os
import sys
import re
import copy
import math
from pycpx import CPlexModel
import numpy as np

#######################################################################################
path = sys.argv[1]

iterations = 100


OneMhz = 1000000
OneGhz = 1000 * OneMhz

profiling_cpu_freq = 3 * OneGhz  # 3GHz
assumed_cpu_freq = OneGhz

os.chdir(path)

start_time = 10 # start sending packets at cycle 10
packet_flits = 6 # packet size in numbers of flits
flit_size = 8 #flit size in numbers of bytes
packet_bytes = packet_flits * flit_size
max_packet_bytes = packet_flits * flit_size
bus_width = flit_size

directions = 4

x_idx = 0
y_idx = 1

#flow indices
srcXIdx = 0
srcYIdx = 1

dstXIdx = 2
dstYIdx = 3

traffic_idx = 4
traffic_used_idx = 5

# routing directions
north = 0
south = 3
west = 1
east = 2

wire_config_opts = [[2.5, 1000 * OneMhz], [2.38, 950 * OneMhz], [2.27, 900 * OneMhz], 
                    [2.15, 850 * OneMhz], [2.02, 800 * OneMhz], [1.93, 760 * OneMhz], 
                    [1.84, 720 * OneMhz], [1.75, 680 * OneMhz], [1.66, 640 * OneMhz], 
                    [1.57, 600 * OneMhz]]

#######################################################################################

def incoming_edge_id(x, y, dir, dim, ndirs):
    id = -1
    if dir == east:
        id = ((x - 1) * dim + y) * ndirs + west
    if dir == west:
        id = ((x + 1) * dim + y) * ndirs + east
    if dir == north:
        id =  (x * dim + y + 1) * ndirs + south
    if dir == south:
        id = (x * dim + y - 1) * ndirs + north
    if id < 0 or id >= dim * dim * ndirs:
        id = -1
    return id

def optimal_routes_freqs(ncycles, flows, dim, ndirs):
    routes = []
    wire_freqs = []
    dirty_flows = np.array(copy.deepcopy(flows)) # make a copy of the flows in case it is modified
    
    m = CPlexModel()
    
    n_flows = len(flows)
    
    # flows demands
    nsent = np.array(copy.deepcopy([f[traffic_idx] for f in dirty_flows]))
    
    b = m.new((n_flows, dim * dim * ndirs), vtype = int)
    
    edge_freqs = m.new(dim * dim * ndirs, vtype = long)
    
    freq_levels = np.array([c[1] for c in wire_config_opts])
    
    power_levels = np.array([(c[0] * c[0] * c[1]) for c in wire_config_opts])
    
    s = m.new((len(wire_config_opts), dim * dim * ndirs), vtype = bool)    # frequency selection variables
    
    for x in range(0, dim):
        for y in range(0, dim):
            for dir in range(0, ndirs):
                edge_id = (x * dim + y) * ndirs + dir
                #capacity constraint for each edge
                #m.constrain((nsent * b[:, edge_id]) * ncycles <= bus_width * OneGhz * edge_freqs[edge_id])
                for i in range(0, n_flows):
                    m.constrain(0 <= b[i,edge_id] <= 1)
    
    #unsplitable constraints and flow conservation
    #for i in range(0, n_flows):
    #    for x in range(0, dim):
    #        for y in range(0, dim):
    #            edgeSrcId = (x * dim + y) * ndirs
    #            m.constrain(b[i, edgeSrcId:(edgeSrcId+ndirs)].sum() <= 1)
    
    # unsplitable constraints and flow conservation
    for x in range(0, dim):
        for y in range(0, dim):
            nodeId = (x * dim + y)
            for i in range(0, n_flows):
                #if this is the source of the flow
                #there must be some out going edge
                if x == flows[i][srcXIdx] and y == flows[i][srcYIdx]:
                    edgeSrcId = (x * dim + y) * ndirs
                    m.constrain(b[i, edgeSrcId:(edgeSrcId+ndirs)].sum() == 1)
                    continue
                     
                # if this is the destination for the flow
                # we do not need flow conservation
                # and all outgoing edge is invalid
                if x == flows[i][dstXIdx] and y == flows[i][dstYIdx]:
                    edgeSrcId = (x * dim + y) * ndirs
                    for dir in range(0, ndirs):
                        m.constrain(b[i, edgeSrcId + dir] == 0)
                    #m.constrain(b[i, edgeSrcId:(edgeSrcId+ndirs)].sum() == 0)
                    continue
                
                #this is an intermediate hop
                for dir in range(0, ndirs):
                    e_id = incoming_edge_id(x, y, dir, dim, ndirs)
                    #if this is a valid incoming edge
                    if e_id >= 0:
                        #e_indices = []
                        #possible outgoing edges
                        for dir1 in range(0, ndirs):
                            #do no go back
                            if dir1 + dir == 3:
                                e_out_id = (x * dim + y) * ndirs + dir1
                                m.constrain(b[i, e_out_id] == 0)
                                continue
                             #outgoing_edge_id(x,y, dir, dim, ndirs)
                            #e_indices.append(e_out_id)
                        edgeSrcId = (x * dim + y) * ndirs
                        #m.constrain(b[i, edgeSrcId:(edgeSrcId+ndirs)].sum() == b[i, e_id])
                              
                
    #minimal route
    for i in range(0, n_flows):
        fi = flows[i]
        hop = abs(fi[srcXIdx] - fi[dstXIdx]) + abs(fi[srcYIdx] - fi[dstYIdx])
        m.constrain(b[i,:].sum() <= hop)
    
        
    #single frequency constraints
    for x in range(0, dim):
        for y in range(0, dim):
            for dir in range(0, ndirs):
                edge_id = (x * dim + y) * ndirs + dir
                #each edge is allowed at most one freq
                m.constrain(s[:, edge_id].sum() <= 1)
                m.constrain(edge_freqs[edge_id] == freq_levels * s[:, edge_id])
    
    #optimal goal
    m.minimize((power_levels * s).sum())
    
    #print m[edge_freqs]
    #print m[b]
    for i in range(0, dim * dim * ndirs):
        print m[b][:, i]
    
    return [routes, wire_freqs]

def gen_wire_delays(name, wire_freqs, dim):
    wire_delays = []
    dirty_wire_freqs = copy.deepcopy(wire_freqs)
    for wf in dirty_wire_freqs:
        wire_delays.append([wf[0:2]].extend([1.0/x for x in wf[2:]]))
        
    #sort by id
    list_to_file(name, wire_delays)
    
def list_to_file(name, ls):
    FILE = open(name, 'w')
    for l in ls:
        line = ''
        for item in l:
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
        list_to_file(name + '.' + str(trace[0]) + '.' + str(trace[1]), trace[2])
        packets.extend(trace[2])
        
    packets.sort(key=lambda tup:tup[0])
    list_to_file(name, packets)
    
    os.chdir('../')
    
    
# this function returns the flows info
# a flow is a tuple of (srcX, srcY, dstX, dstY, bytes) 
def comm_prof(dim):
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
    
    return flows

def time_prof(dim):
    # compile from str to c
    #os.system("make -f Makefile.mk BACKEND=\'--spacetime --newSimple " + str(dim) + " --i " + str(iterations) + "\'")
    
    # compile the generated c program
    #os.system("cp /home/dai/prog/compiler/streamit/tmp/Makefile ./")
    #os.system("make")
    
    #store the files
    #os.system("mv str.cpp str" + str(dim) + 'x' + str(dim) + '.cpp')
    #os.system("mv stream stream"  + str(dim) + 'x' + str(dim))
    
    # run the program to obtain running time information
    logfile = str(dim) + "x" + str(dim) + "_time.log"
    os.system("./stream" + str(dim) + 'x' + str(dim) + " " + str(iterations) + " > " + logfile)
    
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
    dirty_flows = copy.deepcopy(flows) # make a copy of the flows in case it is modified
    #for each source node
    for x in range(0, dim):
        for y in range(0,dim):
            # find flows that have this node as the source node
            fs = []
            total_traffic = 0 #total traffic in bytes from this node in one iteration
            smallest_traffic = float('inf')
            
            packets = []    #packets from this source
            
            traffics.append([x,y,packets])
            
            for f in dirty_flows:
                if f[0] == x and f[1] == y:
                    dirty_flows.remove(f)
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

############################################################################################

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
        [routes, wire_freqs] = optimal_routes_freqs(ncycles, flows, dim, directions)
        
        #generate wire config
        gen_wire_delays('wire_config.txt', wire_freqs)
        
        #generate routing config
        
        
        #generate traffic
        traffics = traffic_gen(flows)
                                
        write_traffic(dir, traffics)
        
        #invoke simulation
        os.system('vnoc ' + dir)
        
        #collect data
        
        
    os.chdir("../../")
        
