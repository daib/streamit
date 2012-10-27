#!/usr/bin/python

import cplex
from cplex.exceptions import CplexError
import sys
import os
import re
import copy

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

def format_var(name, idx1, idx2):
    return name + '_' + str(idx1) + '_' + str(idx2)
#dir is the direction of incomming edge

def incoming_edge_id(x, y, dir, dim, ndirs):
    id = -1
    if dir == east:
        id = ((x - 1) * dim + y) * ndirs
    elif dir == west:
        id = ((x + 1) * dim + y) * ndirs
    elif dir == north:
        id = (x * dim + y - 1) * ndirs
    elif dir == south:
        id =  (x * dim + y + 1) * ndirs
        
    id = id + dir
    if id < 0 or id >= dim * dim * ndirs:
        id = -1
    return id

def optimal_routes_freqs_direct(ncycles, flows, dim, ndirs):
    routes = []
    wire_freqs = []
    dirty_flows = copy.deepcopy(flows) # make a copy of the flows in case it is modified
    
    #m = CPlexModel()
    
    n_flows = len(dirty_flows)
    
    # flows demands
    nsent = copy.deepcopy([f[traffic_idx] for f in dirty_flows])
    
    #b = m.new((n_flows, dim * dim * ndirs), vtype = int)
    #edge_freqs = m.new(dim * dim * ndirs, vtype = long)
    #s = m.new((len(wire_config_opts), dim * dim * ndirs), vtype = bool)    # frequency selection variables
    
    b = []
    b_type = ''
    edge_freqs = []
    edge_freqs_type = ''
    s = []
    s_type = ''
     
    for x in range(dim):
        for y in range(dim):
            for dir in range(ndirs):
                edge_id = (x * dim + y) * ndirs + dir
                edge_freqs.append(format_var('edge_freqs', 0, edge_id))
                edge_freqs_type = edge_freqs_type + 'L'
                for i in range(n_flows):
                    b.append(format_var('b', i, edge_id))
                    b_type = b_type + 'I'
                for i in range(len(wire_config_opts)):
                    s.append(format_var('s', i, edge_id))
                    s_type = s_type + 'I'
    
    
    b_lb = [0] * len(b)
    b_ub = [1] * len(b)
    s_lb = [0] * len(s)
    s_ub = [1] * len(s)
    
    freq_levels = [c[1] for c in wire_config_opts]
    
    edge_freqs_lb = [min(freq_levels)] * len(edge_freqs)
    edge_freqs_ub = [max(freq_levels)] * len(edge_freqs)
    
    power_levels = [int(c[0] * c[0] * c[1]) for c in wire_config_opts]
    
    rows = []
    my_rhs = []
    my_senses = ''
    
    for x in range(0, dim):
        for y in range(0, dim):
            for dir in range(0, ndirs):
                edge_id = (x * dim + y) * ndirs + dir
                #capacity constraint for each edge
                #m.constrain((nsent * b[:, edge_id]) * ncycles <= bus_width * OneGhz * edge_freqs[edge_id])
                var_names = []
                coefs = []
                coefs.extend([(item * ncycles) for item in nsent])
                
                for i in range(0, n_flows):
                    var_names.append(format_var('b', i, edge_id))
                    
                var_names.append(format_var('edge_freqs', 0, edge_id))
                coefs.append(-bus_width * OneGhz)
                
                rows.append([var_names,coefs])
                my_rhs.append(0)
                my_senses = my_senses + 'L'
                    
    
    #unsplitable constraints and flow conservation
    #for i in range(0, n_flows):
    #    for x in range(0, dim):
    #        for y in range(0, dim):
    #            edgeSrcId = (x * dim + y) * ndirs
    #            m.constrain(b[i, edgeSrcId:(edgeSrcId+ndirs)].sum() <= 1)
    
    # unsplitable constraints and flow conservation
    for x in range(0, dim):
        for y in range(0, dim):
            for i in range(0, n_flows):
                #if this is the source of the flow
                #there must be some out going edge
                if x == dirty_flows[i][srcXIdx] and y == dirty_flows[i][srcYIdx]:
                    edgeSrcId = (x * dim + y) * ndirs
                    var_names = []
                    coefs = [1] * ndirs
                    for dir in range(ndirs):
                        var_names.append(format_var('b', i, edgeSrcId + dir))
                        
                    rows.append([var_names, coefs])
                    my_rhs.append(1)
                    my_senses = my_senses + 'E'
                    #m.constrain(b[i, edgeSrcId:(edgeSrcId+ndirs)].sum() == 1)
                    continue
                     
                # if this is the destination for the flow
                # we do not need flow conservation
                # and all outgoing edge is invalid
                if x == dirty_flows[i][dstXIdx] and y == dirty_flows[i][dstYIdx]:
                    edgeSrcId = (x * dim + y) * ndirs
                    var_names = []
                    coefs = [1] * ndirs
                    for dir in range(0, ndirs):
                        var_names.append(format_var('b', i, edgeSrcId + dir))
                    
                    rows.append([var_names, coefs])
                    my_rhs.append(0)
                    my_senses = my_senses + 'E'
                        #m.constrain(b[i, edgeSrcId + dir] == 0)
                    #m.constrain(b[i, edgeSrcId:(edgeSrcId+ndirs)].sum() == 0)
                    continue
                
                #this is an intermediate hop
                for dir in range(0, ndirs):
                    e_id = incoming_edge_id(x, y, dir, dim, ndirs)
                    #if this is a valid incoming edge
                    if e_id >= 0:
                        #possible outgoing edges
                        #do no go back
                        e_back_id = (x * dim + y) * ndirs + (3-dir)
                        edgeSrcId = (x * dim + y) * ndirs
                        var_names = []
                        coefs = []
                        for dir in range(ndirs):
                            e_out_id = edgeSrcId + dir
                            if e_out_id != e_back_id:
                                var_names.append(format_var('b', i, e_out_id))
                                coefs.append(1)
                        var_names.append(format_var('b', i, e_id))
                        coefs.append(-1)
                        
                        rows.append([var_names, coefs])
                        my_rhs.append(0)
                        my_senses = my_senses + 'E'
                        #m.constrain(b[i, edgeSrcId:(edgeSrcId+ndirs)].sum() - b[i, e_back_id] == b[i, e_id])
                        
    #edge conditions
    for y in range(0, dim):
        e_id_w = y * ndirs + west
        e_id_e = ((dim-1) * dim + y) * ndirs + east
        for i in range(n_flows):
            #m.constrain(b[i, e_id_w] == 0)
            #m.constrain(b[i, e_id_e] == 0)
            
            rows.append([[format_var('b', i, e_id_w)],[1]])
            my_rhs.append(0)
            my_senses = my_senses + 'E'
            rows.append([[format_var('b', i, e_id_e)],[1]])
            my_rhs.append(0)
            my_senses = my_senses + 'E'
            
    for x in range(0, dim):
        e_id_s = x * dim * ndirs + south
        e_id_n = (x * dim + dim -1) * ndirs + north
        for i in range(n_flows):
            #m.constrain(b[i, e_id_n] == 0)
            #m.constrain(b[i, e_id_s] == 0)
            
            rows.append([[format_var('b', i, e_id_n)],[1]])
            my_rhs.append(0)
            my_senses = my_senses + 'E'
            rows.append([[format_var('b', i, e_id_s)],[1]])
            my_rhs.append(0)
            my_senses = my_senses + 'E'
            
    #minimal route
    for i in range(0, n_flows):
        fi = dirty_flows[i]
        hop = abs(fi[srcXIdx] - fi[dstXIdx]) + abs(fi[srcYIdx] - fi[dstYIdx])
        n_edges = dim * dim * ndirs
        
        var_names = []
        for e in range(n_edges):
            var_names.append(format_var('b', i, e))
        coefs = [1] * len(var_names)
        
        rows.append([var_names, coefs])
        my_rhs.append(hop)
        my_senses = my_senses + 'L'             
        #m.constrain(b[i,:].sum() <= hop)
    
        
    #single frequency constraints
    for x in range(dim):
        for y in range(dim):
            for dir in range(ndirs):
                edge_id = (x * dim + y) * ndirs + dir
                #each edge is allowed at most one freq
                #m.constrain(s[:, edge_id].sum() <= 1)
                var_names = []
                for i in range(len(wire_config_opts)):
                    var_names.append(format_var('s', i, edge_id))
                coefs = [1] * len(var_names)
                
                rows.append([var_names, coefs])
                my_rhs.append(1)
                my_senses = my_senses + 'L'   
        
                var_names = [format_var('edge_freqs', 0, edge_id)]
                for i in range(len(wire_config_opts)):
                    var_names.append(format_var('s', i, edge_id))
                coefs = [1].extend([-1] * len(var_names))
                
                rows.append([var_names, coefs])
                my_rhs.append(0)
                my_senses = my_senses + 'E'   
        
                #m.constrain(edge_freqs[edge_id] == freq_levels * s[:, edge_id])
    var_names = []
    var_names.extend(b)
    var_names.extend(edge_freqs)
    var_names.extend(s)
    
    var_lb = []
    var_lb.extend(b_lb)
    var_lb.extend(edge_freqs_lb)
    var_lb.extend(s_lb)
    
    var_ub = []
    var_ub.extend(b_ub)
    var_ub.extend(edge_freqs_ub)
    var_ub.extend(s_ub)
    
    #optimal goal
    power_obj = [0] * (len(b) + len(edge_freqs))
    
    for x in range(dim):
        for y in range(dim):
            for dir in range(ndirs):
                power_obj.extend(power_levels)
    
    my_prob = cplex.Cplex()
              
    my_prob.objective.set_sense(my_prob.objective.sense.minimize)

    var_type = b_type + edge_freqs_type + s_type
    my_prob.variables.add(obj = power_obj, lb = var_lb, ub = var_ub, types = var_type,
                       names = var_names)

    my_prob.linear_constraints.add(lin_expr = rows, senses = my_sense,
                                rhs = my_rhs)

    #m.minimize((power_levels * s).sum())
    #m.minimize(b[0,:].sum())
    print 'Solving the problem ...'
    my_prob.solve()
    print
    # solution.get_status() returns an integer code
    print "Solution status = " , my_prob.solution.get_status(), ":",
    # the following line prints the corresponding string
    print my_prob.solution.status[my_prob.solution.get_status()]
    print "Solution value  = ", my_prob.solution.get_objective_value()

    numcols = my_prob.variables.get_num()
    numrows = my_prob.linear_constraints.get_num()

    slack = my_prob.solution.get_linear_slacks()
    x     = my_prob.solution.get_values()

    for j in range(numrows):
        print "Row %d:  Slack = %10f" % (j, slack[j])
    for j in range(numcols):
        print "Column %d:  Value = %10f" % (j, x[j])

    quit()
    
    return [routes, wire_freqs]

def optimal_routes_freqs(ncycles, flows, dim, ndirs):
    routes = []
    wire_freqs = []
    dirty_flows = np.array(copy.deepcopy(flows)) # make a copy of the flows in case it is modified
    
    m = CPlexModel()
    
    n_flows = len(dirty_flows)
    
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
                m.constrain((nsent * b[:, edge_id]) * ncycles <= bus_width * OneGhz * edge_freqs[edge_id])
                for i in range(0, n_flows):
                    m.constrain(0 <= b[i,edge_id] <= 1)
                    
                    print '0 <= ' + format_var('b', i, edge_id) + ' <= 1'
                    
    
    #unsplitable constraints and flow conservation
    #for i in range(0, n_flows):
    #    for x in range(0, dim):
    #        for y in range(0, dim):
    #            edgeSrcId = (x * dim + y) * ndirs
    #            m.constrain(b[i, edgeSrcId:(edgeSrcId+ndirs)].sum() <= 1)
    
    # unsplitable constraints and flow conservation
    for x in range(0, dim):
        for y in range(0, dim):
            for i in range(0, n_flows):
                #if this is the source of the flow
                #there must be some out going edge
                if x == dirty_flows[i][srcXIdx] and y == dirty_flows[i][srcYIdx]:
                    edgeSrcId = (x * dim + y) * ndirs
                    m.constrain(b[i, edgeSrcId:(edgeSrcId+ndirs)].sum() == 1)
                    
                    line = '0'
                    for j in range(edgeSrcId, edgeSrcId+ndirs):
                        line = line + ' + ' + format_var('b', i, j)
                    line = line + ' == 1'
                    print line
                     
                    continue
                     
                # if this is the destination for the flow
                # we do not need flow conservation
                # and all outgoing edge is invalid
                if x == dirty_flows[i][dstXIdx] and y == dirty_flows[i][dstYIdx]:
                    edgeSrcId = (x * dim + y) * ndirs
                    for dir in range(0, ndirs):
                        m.constrain(b[i, edgeSrcId + dir] == 0)
                        
                        print format_var('b', i, edgeSrcId + dir) + ' == 0'
                    #m.constrain(b[i, edgeSrcId:(edgeSrcId+ndirs)].sum() == 0)
                    continue
                
                #this is an intermediate hop
                for dir in range(0, ndirs):
                    e_id = incoming_edge_id(x, y, dir, dim, ndirs)
                    #if this is a valid incoming edge
                    if e_id >= 0:
                        #e_indices = []
                        #possible outgoing edges
                        #do no go back
                        e_back_id = (x * dim + y) * ndirs + (3-dir)
                        #outgoing_edge_id(x,y, dir, dim, ndirs)
                        #e_indices.append(e_out_id)
                        edgeSrcId = (x * dim + y) * ndirs
                        m.constrain(b[i, edgeSrcId:(edgeSrcId+ndirs)].sum() - b[i, e_back_id] == b[i, e_id])
                        
                        line = '0'
                        for j in range(edgeSrcId, edgeSrcId+ndirs):
                            line = line + ' + ' + format_var('b', i, j)
                        line = line + ' - ' + format_var('b', i, e_back_id) + ' == ' + format_var('b', i, e_id)
                        print line
    
    #edge conditions
    for y in range(0, dim):
        e_id_w = y * ndirs + west
        e_id_e = ((dim-1) * dim + y) * ndirs + east
        for i in range(n_flows):
            m.constrain(b[i, e_id_w] == 0)
            m.constrain(b[i, e_id_e] == 0)
            
            print format_var('b', i, e_id_w) + ' == 0'
            print format_var('b', i, e_id_e) + ' == 0'
            
    for x in range(0, dim):
        e_id_s = x * dim * ndirs + south
        e_id_n = (x * dim + dim -1) * ndirs + north
        for i in range(n_flows):
            m.constrain(b[i, e_id_n] == 0)
            m.constrain(b[i, e_id_s] == 0)
            
            print format_var('b', i, e_id_n) + ' == 0'
            print format_var('b', i, e_id_s) + ' == 0'
            
    #minimal route
    for i in range(0, n_flows):
        fi = dirty_flows[i]
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
    
    line = '0'
    for i in range(0, dim * dim * ndirs):
        line = line + ' + ' + format_var('b', 0, i) 
    print line
    
    #optimal goal
    m.minimize((power_levels * s).sum())
    #m.minimize(b[0,:].sum())
    
    #print m[edge_freqs]

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
        [routes, wire_freqs] = optimal_routes_freqs_direct(ncycles, flows, 2, directions)
        
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
        

