#!/usr/bin/python

import cplex
from cplex.exceptions import CplexError
import sys
import os
import re
import copy
import random

#######################################################################################
path = sys.argv[1]

recompile = False

iterations = 100


OneMhz = 1
OneGhz = 1000 * OneMhz

profiling_cpu_freq = 3 * OneGhz  # 3GHz
assumed_cpu_freq = OneGhz

os.chdir(path)

start_time = 10 # start sending packets at cycle 10
packet_flits = 6 # packet size in numbers of flits
flit_size = 8 #flit size in numbers of bytes
packet_bytes = packet_flits * flit_size
max_packet_bytes = packet_flits * flit_size
channel_width = flit_size

channel_width_bits = channel_width * 8

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

#######################################################################################


#wire_config_opts = [[2.5, 1000 * OneMhz], [2.38, 950 * OneMhz], [2.27, 900 * OneMhz], 
#                    [2.15, 850 * OneMhz], [2.02, 800 * OneMhz], [1.93, 760 * OneMhz], 
#                    [1.84, 720 * OneMhz], [1.75, 680 * OneMhz], [1.66, 640 * OneMhz], 
#                    [1.57, 600 * OneMhz], [0.9, 125 * OneMhz]]

wire_config_opts = [[2.50, 1000 * OneMhz], [2.20, 870 * OneMhz], [1.95, 750 * OneMhz],
                    [1.75, 640 * OneMhz], [1.58, 540 * OneMhz], [1.43, 450 * OneMhz],
                    [1.30, 370 * OneMhz], [1.19, 300 * OneMhz], [1.10, 240 * OneMhz],
                    [1.02, 190 * OneMhz], [0,0]]

#wire_config_opts = [[2.50, 1000 * OneMhz], [0, 0]]

#wire_config_opts = [[2.50, 1000 * OneMhz], [1.95, 750 * OneMhz],
#                    [1.58, 540 * OneMhz],
#                    [1.30, 370 * OneMhz], [1.10, 240 * OneMhz],
#                    [0.9, 125 * OneMhz]]

wire_config_opts.reverse()

max_wire_freq = max([opt[1]  for opt in wire_config_opts])
freq_levels = [c[1] for c in wire_config_opts]

from orion_power import Link

link_len =  0.001
link = Link(link_len, channel_width_bits)

#######################################################################################

def format_var(name, idx1, idx2):
    return name + '_' + str(idx1) + '_' + str(idx2)
#dir is the direction of incomming edge

def incoming_edge_id(x, y, dir, dim, ndirs):
    id = -1
    if dir == east:
        if x > 0:
            id = ((x - 1) * dim + y) * ndirs
    elif dir == west:
        if x < dim - 1:
            id = ((x + 1) * dim + y) * ndirs
    elif dir == north:
        if y > 0:
            id = (x * dim + y - 1) * ndirs
    elif dir == south:
        if y < dim - 1:
            id =  (x * dim + y + 1) * ndirs

    if id >= 0:
        id = id + dir        

    return id

def edges_on_k_paths(srcX, srcY, dstX, dstY, k, dim, ndirs):
    
    edges = []
    dir = -1
     
    #we will start with minimal paths first
    if srcX < dstX:
        dir = east
        for x in range(srcX, dstX):
            for y in range(min(srcY,dstY), max(srcY,dstY) + 1):
                edge_id = (x * dim + y) * ndirs + dir
                edges.append(edge_id)
    elif srcX > dstX:
        dir = west
        for x in range(dstX + 1, srcX + 1):
            for y in range(min(srcY,dstY), max(srcY,dstY) + 1):
                edge_id = (x * dim + y) * ndirs + dir
                edges.append(edge_id)
    
    
    if srcY < dstY:
        dir = north
        for x in range(min(srcX, dstX), max(srcX, dstX) + 1):
            for y in range(srcY,dstY):
                edge_id = (x * dim + y) * ndirs + dir
                edges.append(edge_id)
    elif srcY > dstY:
        dir = south
        for x in range(min(srcX, dstX), max(srcX, dstX) + 1):
            for y in range(dstY + 1, srcY + 1):
                edge_id = (x * dim + y) * ndirs + dir
                edges.append(edge_id)
    
    #return edge ids
    return edges

def edges_not_on_k_paths(srcX, srcY, dstX, dstY, k, dim, ndirs):
    edges_excluded = []
    
    edges_included = edges_on_k_paths(srcX, srcY, dstX, dstY, k, dim, ndirs)
    
    edges_included.sort()

    included_idx = -1
    if len(edges_included) >= 0:
        included_idx = edges_included.pop(0)
    
    for i in range(dim * dim * ndirs):
        if i == included_idx:
            if len(edges_included) > 0:
                included_idx = edges_included.pop(0)
            continue
        
        edges_excluded.append(i)
    
    return edges_excluded

def convert_to_wire_delays(edge_freqs, dim, ndirs):
    wire_delays = []
    
    vnoc_dir = [0] * 5
    
    vnoc_dir[4] = north
    vnoc_dir[3] = south
    vnoc_dir[2] = east
    vnoc_dir[1] = west
    
    for x  in range(dim):
        for y in range(dim):
            edgeSrcId = (x * dim + y) * ndirs
            # we need to convert to the vnoc direction
            # can convert from frequency to delays
                
            #the format is:  node_address dirs
            #                (x,y) local, 1, 2, 3, 4
                
            wire_delay = [x, y, 1]   #FIXME: compute minimal local link power?
            for dir in range(1, ndirs + 1):
                if(round(edge_freqs[edgeSrcId + vnoc_dir[dir]]) == 0):
                    wire_delay.append(-1)   #this link is disabled
                else:
                    wire_delay.append(float(max_wire_freq)/round(edge_freqs[edgeSrcId + vnoc_dir[dir]]))
                    
            wire_delays.append(wire_delay)
    return wire_delays

def calculate_routes(b, dim, ndirs, flows, ncycles):
    routes = []
    
    dirty_flows = copy.deepcopy(flows)
    
    vnoc_dir = [0] * ndirs
    
    vnoc_dir[north] = 4
    vnoc_dir[south] = 3
    vnoc_dir[east] = 2
    vnoc_dir[west] = 1
    
    for i in range(len(dirty_flows)):
        f = dirty_flows[i]
        
        # src and dst information
        route = ['(' + str(f[srcXIdx]) + ',' + str(f[srcYIdx]) + ')', '(' +  str(f[dstXIdx]) + ',' + str(f[dstYIdx]) + ')']
        
        #vc
        vc = -1 #any VC
        if f[srcXIdx] < f[dstXIdx]:
            if f[srcYIdx] <= f[dstYIdx]:
                vc = 0
        elif f[srcYIdx] > f[dstYIdx]:
            vc = 1
        route.append(vc)
        
        route.append(f[traffic_idx])
        route.append(ncycles)
        route.append('|')
        
        #trace from source to destination
        currentX = f[srcXIdx]
        currentY = f[srcYIdx]
        
        while currentX != f[dstXIdx] or currentY != f[dstYIdx]:
            #base_id = ((currentX * dim + currentY) * ndirs) * len(dirty_flows) + i
            base_id = ((currentX * dim + currentY) * ndirs) + i * dim * dim * ndirs
            for dir in range(ndirs):
                if round(b[base_id + dir]) == 1:
                    route.append(vnoc_dir[dir])
                    
                    #move to the next node
                    if dir == north:
                        currentY = currentY + 1
                    elif dir == south:
                        currentY = currentY - 1
                    elif dir == west:
                        currentX = currentX - 1
                    elif dir == east:
                        currentX = currentX + 1
                    break
        
        route.append('')
        routes.append(route)
        
    return routes
def calculate_optimal_wire_delays_2(edge_traffic, dim, ndirs, ncycles):
    wire_delays = []
    
    vnoc_dir = [0] * 5
    
    vnoc_dir[4] = north
    vnoc_dir[3] = south
    vnoc_dir[2] = east
    vnoc_dir[1] = west
    
    dirty_flows = copy.deepcopy(flows)
    
    for x  in range(dim):
        for y in range(dim):
            # we need to convert to the vnoc direction
            # can convert from frequency to delays
                
            #the format is:  node_address dirs
            #                (x,y) local, 1, 2, 3, 4
                
            wire_delay = [x, y, 1]   #FIXME: compute minimal local link power?
            
            for d in range(1, ndirs + 1):
                dir = vnoc_dir[d]
                edge_id = (x * dim + y) * ndirs + dir

                total_traffic = edge_traffic[edge_id]
                
                freq_levels.sort()
             
                exists = False 
                for freq in freq_levels:
                    if  channel_width * ncycles * freq >= total_traffic * max_wire_freq:
                        if freq == 0:
                            wire_delay.append(-1)
                        else:
                            wire_delay.append(float(max_wire_freq)/freq)
                            #print 'Edge ' + str(x) + ',' + str(y) + ' ' + str(dir) + ' utilization ' + str(float(total_traffic * max_wire_freq)/(channel_width * ncycles * freq))
                        exists = True
                        break
                if not exists:
                    print 'Traffic is too much for the link'
                    #quit()
                    wire_delay.append(1)
                    
            wire_delays.append(wire_delay)
    return wire_delays

def calculate_optimal_wire_delays(b, dim, ndirs, flows, ncycles):
    wire_delays = []
    
    vnoc_dir = [0] * 5
    
    vnoc_dir[4] = north
    vnoc_dir[3] = south
    vnoc_dir[2] = east
    vnoc_dir[1] = west
    
    dirty_flows = copy.deepcopy(flows)
    
    for x  in range(dim):
        for y in range(dim):
            # we need to convert to the vnoc direction
            # can convert from frequency to delays
                
            #the format is:  node_address dirs
            #                (x,y) local, 1, 2, 3, 4
                
            wire_delay = [x, y, 1]   #FIXME: compute minimal local link power?
            
            for d in range(1, ndirs + 1):
                dir = vnoc_dir[d]
                edge_id = (x * dim + y) * ndirs + dir
                total_traffic = 0
                for i in range(len(dirty_flows)):
                    #if the flow goes through the link
                    if round(b[i * dim * dim * ndirs + edge_id]) == 1:
                        total_traffic = total_traffic + dirty_flows[i][traffic_idx]
                #find the minimal suitable frequency for the amount of traffic

                freq_levels.sort()
             
                exists = False 
                for freq in freq_levels:
                    if  channel_width * ncycles * freq >= total_traffic * max_wire_freq:
                        if freq == 0:
                            wire_delay.append(-1)
                        else:
                            wire_delay.append(float(max_wire_freq)/freq)
                            #print 'Edge ' + str(x) + ',' + str(y) + ' ' + str(dir) + ' utilization ' + str(float(total_traffic * max_wire_freq)/(channel_width * ncycles * freq))
                        exists = True
                        break
                if not exists:
                    wire_delay.append(1)
                    
            wire_delays.append(wire_delay)
            
    return wire_delays
                        
def optimal_routes_freqs(ncycles, flows, dim, ndirs):
    
    debug = False
    
    dirty_flows = copy.deepcopy(flows) # make a copy of the flows in case it is modified
    
    n_flows = len(dirty_flows)
    
    # flows demands
    nsent = copy.deepcopy([f[traffic_idx] for f in dirty_flows])
    
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
                edge_freqs_type = edge_freqs_type + 'I'
                #for i in range(n_flows):
                #    b.append(format_var('b', i, edge_id))
                #    b_type = b_type + 'B'
                for i in range(len(wire_config_opts)):
                    s.append(format_var('s', i, edge_id))
                    s_type = s_type + 'B'
    
    for i in range(n_flows):
        for x in range(dim):
            for y in range(dim):
                for dir in range(ndirs):
                    edge_id = (x * dim + y) * ndirs + dir
                    b.append(format_var('b', i, edge_id))
                    b_type = b_type + 'B'
                    
    b_lb = [0] * len(b)
    b_ub = [1] * len(b)
    s_lb = [0] * len(s)
    s_ub = [1] * len(s)
    
    edge_freqs_lb = [0] * len(edge_freqs)
    edge_freqs_ub = [max(freq_levels)] * len(edge_freqs)
    
    power_levels = [(link.calc_dynamic_energy(channel_width_bits/2, opt[0]) / (channel_width * ncycles * opt[1]) * opt[1] + link.get_static_power(opt[0])) for opt in wire_config_opts[1:]]
    power_levels.insert(0, 0)
    
    rows = []
    my_rhs = []
    my_senses = ''
    
    for x in range(dim):
        for y in range(dim):
            for dir in range(ndirs):
                edge_id = (x * dim + y) * ndirs + dir
                #capacity constraint for each edge
                #m.constrain((nsent * b[:, edge_id]) * OneGhz <= channel_width * ncycles * edge_freqs[edge_id]) 
                var_names = []
                coefs = []
                coefs.extend([(item * OneGhz) for item in nsent])
                
                for i in range(0, n_flows):
                    var_names.append(format_var('b', i, edge_id))
                    
                var_names.append(format_var('edge_freqs', 0, edge_id))
                coefs.append(-channel_width * ncycles)
                
                if debug:
                    continue

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
    for x in range(dim):
        for y in range(dim):
            for i in range(n_flows):
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
                    #m.constrain(b[i, edgeSoutrcId:(edgeSrcId+ndirs)].sum() == 1)
                    
                    #all incoming edges are invalid
                    for dir in range(0, ndirs):
                        e_in_id = incoming_edge_id(x, y, dir, dim, ndirs)
                        
                        if e_in_id >= 0:
                            rows.append([[format_var('b', i, e_in_id)], [1]])
                            my_rhs.append(0)
                            my_senses = my_senses + 'E'
                    
                    continue
                     
                # if this is the destination for the flow
                # we do not need flow conservation
                # and all outgoing edge is invalid
                if x == dirty_flows[i][dstXIdx] and y == dirty_flows[i][dstYIdx]:
                    edgeSrcId = (x * dim + y) * ndirs
                    for dir in range(ndirs):
                        rows.append([[format_var('b', i, edgeSrcId + dir)], [1]])
                        my_rhs.append(0)
                        my_senses = my_senses + 'E'
                        #m.constrain(b[i, edgeSrcId + dir] == 0)
                    #m.constrain(b[i, edgeSrcId:(edgeSrcId+ndirs)].sum() == 0)
                    
                    #one incoming edge is true
                    var_names = []
                    for dir in range(ndirs):
                        e_in_id = incoming_edge_id(x, y, dir, dim, ndirs)
                        
                        if e_in_id >= 0:
                            var_names.append(format_var('b', i, e_in_id))
                    
                    coefs = [1] * len(var_names)
                    rows.append([var_names, coefs])
                    my_rhs.append(1)
                    my_senses = my_senses + 'E'
                    
                    continue
                
                #this is an intermediate hop
                var_names = []
                coefs = []
                edgeSrcId = (x * dim + y) * ndirs
                    
                for dir in range(ndirs):
                    #flows out
                    var_names.append(format_var('b', i, edgeSrcId + dir))
                    coefs.append(1)
                    
                    #flows in
                    e_in_id = incoming_edge_id(x, y, dir, dim, ndirs)
                    
                    #if this is a valid incoming edge
                    if e_in_id >= 0:
                        #do no go back
                        if dir < 2: # avoid duplication
                            e_back_id = (x * dim + y) * ndirs + (3-dir)
                            rows.append([[format_var('b', i, e_in_id), format_var('b', i, e_back_id)],[1,1]])
                            my_rhs.append(1)
                            my_senses = my_senses + 'L'
                            
                        var_names.append(format_var('b', i, e_in_id))
                        coefs.append(-1)
                    

                rows.append([var_names, coefs])
                my_rhs.append(0)
                my_senses = my_senses + 'E'

                var_names = []
                coefs = []
                edgeSrcId = (x * dim + y) * ndirs
                    
                for dir in range(ndirs):
                    #flows out
                    var_names.append(format_var('b', i, edgeSrcId + dir))
                    coefs.append(1)
                 
                rows.append([var_names, coefs])
                my_rhs.append(1)
                my_senses = my_senses + 'L'
                        
    #edge conditions
    for y in range(dim):
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
        my_senses = my_senses + 'E'             
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
                
                #coefs = [1] * len(var_names)
                coefs = [-1]
                coefs.extend(copy.deepcopy(freq_levels))
                
                
                if debug:
                    continue
            
                rows.append([var_names, coefs])
                my_rhs.append(0)
                my_senses = my_senses + 'E'
        
                #m.constrain(edge_freqs[edge_id] <= freq_levels * s[:, edge_id])
    #pruning the application
    for i in range(n_flows):
        f = dirty_flows[i]
        edges_excluded = edges_not_on_k_paths(f[srcXIdx], f[srcYIdx], f[dstXIdx], f[dstYIdx], 0, dim, ndirs)
        for e in edges_excluded:
            rows.append([[format_var('b', i, e)], [1]])
            my_rhs.append(0)
            my_senses = my_senses + 'E'
                
    colnames = []
    colnames.extend(b)
    colnames.extend(edge_freqs)
    colnames.extend(s)
    
    var_lb = []
    var_lb.extend(b_lb)
    var_lb.extend(edge_freqs_lb)
    var_lb.extend(s_lb)
    
    var_ub = []
    var_ub.extend(b_ub)
    var_ub.extend(edge_freqs_ub)
    var_ub.extend(s_ub)
    
    #optimal goal
    power_obj = []
    if debug:
        power_obj = [1] * len(b)
        power_obj.extend([0] * (len(edge_freqs) + len(s)))
    else:
        power_obj = [0] * (len(b) + len(edge_freqs))
    
        for x in range(dim):
            for y in range(dim):
                for dir in range(ndirs):
                    power_obj.extend(power_levels)
        
    my_prob = cplex.Cplex()
              
    my_prob.objective.set_sense(my_prob.objective.sense.minimize)

    var_type = b_type + edge_freqs_type + s_type
    
    my_prob.variables.add(obj = power_obj, lb = var_lb, ub = var_ub, types = var_type,
                       names = colnames)

    my_prob.linear_constraints.add(lin_expr = rows, senses = my_senses,
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
    

    #for j in range(numrows):
    #    print "Row %d:  Slack = %10f" % (j, slack[j])
    for j in range(numcols):
        print colnames[j] + " %d:  Value = %d" % (j, x[j])
    
    #calculate wire frequencies and routes
    wire_delays = convert_to_wire_delays(x[len(b):(len(b) + len(edge_freqs))], dim, ndirs)
    
    routes = calculate_routes(x[0:len(b)], dim, ndirs, flows, ncycles)

    return [routes, wire_delays]


def minimize_used_links(ncycles, flows, dim, ndirs):
    
    debug = False
    
    dirty_flows = copy.deepcopy(flows) # make a copy of the flows in case it is modified
    
    n_flows = len(dirty_flows)
    
    # flows demands
    nsent = copy.deepcopy([f[traffic_idx] for f in dirty_flows])
    
    b = []
    b_type = ''
    used_edges = []
    used_edges_type = ''
     
    for x in range(dim):
        for y in range(dim):
            for dir in range(ndirs):
                edge_id = (x * dim + y) * ndirs + dir
                used_edges.append(format_var('used_edges', 0, edge_id))
                used_edges_type = used_edges_type + 'B'
    
    for i in range(n_flows):
        for x in range(dim):
            for y in range(dim):
                for dir in range(ndirs):
                    edge_id = (x * dim + y) * ndirs + dir
                    b.append(format_var('b', i, edge_id))
                    b_type = b_type + 'B'
                    
    b_lb = [0] * len(b)
    b_ub = [1] * len(b)
    used_edges_lb = [0] * len(used_edges)
    used_edges_ub = [1] * len(used_edges)
    
    rows = []
    my_rhs = []
    my_senses = ''
    
    for x in range(dim):
        for y in range(dim):
            for dir in range(ndirs):
                edge_id = (x * dim + y) * ndirs + dir
                #capacity constraint for each edge
                #m.constrain((nsent * b[:, edge_id])  <= channel_width * ncycles]) 
                var_names = []
                coefs = []
                coefs.extend(nsent)
                
                for i in range(n_flows):
                    var_names.append(format_var('b', i, edge_id))
                    
                if debug:
                    continue

                rows.append([var_names,coefs])
                my_rhs.append(channel_width * ncycles)
                my_senses = my_senses + 'L'
                    
    
    #unsplitable constraints and flow conservation
    #for i in range(0, n_flows):
    #    for x in range(0, dim):
    #        for y in range(0, dim):
    #            edgeSrcId = (x * dim + y) * ndirs
    #            m.constrain(b[i, edgeSrcId:(edgeSrcId+ndirs)].sum() <= 1)
    
    # unsplitable constraints and flow conservation
    for x in range(dim):
        for y in range(dim):
            for i in range(n_flows):
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
                    #m.constrain(b[i, edgeSoutrcId:(edgeSrcId+ndirs)].sum() == 1)edge_freqs
                    
                    #all incoming edges are invalid
                    for dir in range(0, ndirs):
                        e_in_id = incoming_edge_id(x, y, dir, dim, ndirs)
                        
                        if e_in_id >= 0:
                            rows.append([[format_var('b', i, e_in_id)], [1]])
                            my_rhs.append(0)
                            my_senses = my_senses + 'E'
                    
                    continue
                     
                # if this is the destination for the flowfreq_levels
                # we do not need flow conservation
                # and all outgoing edge is invalid
                if x == dirty_flows[i][dstXIdx] and y == dirty_flows[i][dstYIdx]:
                    edgeSrcId = (x * dim + y) * ndirs
                    for dir in range(ndirs):
                        rows.append([[format_var('b', i, edgeSrcId + dir)], [1]])
                        my_rhs.append(0)
                        my_senses = my_senses + 'E'
                        #m.constrain(b[i, edgeSrcId + dir] == 0)
                    #m.constrain(b[i, edgeSrcId:(edgeSrcId+ndirs)].sum() == 0)
                    
                    #one incoming edge is true
                    var_names = []
                    for dir in range(ndirs):
                        e_in_id = incoming_edge_id(x, y, dir, dim, ndirs)
                        
                        if e_in_id >= 0:
                            var_names.append(format_var('b', i, e_in_id))
                    
                    coefs = [1] * len(var_names)
                    rows.append([var_names, coefs])
                    my_rhs.append(1)
                    my_senses = my_senses + 'E'
                    
                    continue
                
                #this is an intermediate hop
                var_names = []
                coefs = []
                edgeSrcId = (x * dim + y) * ndirs
                    
                for dir in range(ndirs):
                    #flows out
                    var_names.append(format_var('b', i, edgeSrcId + dir))
                    coefs.append(1)
                    
                    #flows in
                    e_in_id = incoming_edge_id(x, y, dir, dim, ndirs)
                    
                    #if this is a valid incoming edge
                    if e_in_id >= 0:
                        #do no go back
                        if dir < 2: # avoid duplication
                            e_back_id = (x * dim + y) * ndirs + (3-dir)
                            rows.append([[format_var('b', i, e_in_id), format_var('b', i, e_back_id)],[1,1]])
                            my_rhs.append(1)
                            my_senses = my_senses + 'L'
                            
                        var_names.append(format_var('b', i, e_in_id))
                        coefs.append(-1)
                    

                rows.append([var_names, coefs])
                my_rhs.append(0)
                my_senses = my_senses + 'E'
                        #m.constrain(b[i, edgeSrcId:(edgeSrcId+ndirs)].sum() - b[i, e_back_id] == b[i, e_id])
                        
    #edge conditions
    for y in range(dim):
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
        my_senses = my_senses + 'E'             
        #m.constrain(b[i,:].sum() <= hop)
    
    # constraints for used links
    # if there is flow using a link, the link is marked as used
    for x in range(dim):
        for y in range(dim):
            for dir in range(ndirs):
                edge_id = (x * dim + y) * ndirs + dir
                

                for i in range(n_flows):
                    if debug:
                        continue
            
                    rows.append([[format_var('used_edges', 0, edge_id), format_var('b', i, edge_id)], [1, -1]])
                    my_rhs.append(0)
                    my_senses = my_senses + 'G'
        
    #pruning the application
    for i in range(n_flows):
        f = dirty_flows[i]
        edges_excluded = edges_not_on_k_paths(f[srcXIdx], f[srcYIdx], f[dstXIdx], f[dstYIdx], 0, dim, ndirs)
        for e in edges_excluded:
            rows.append([[format_var('b', i, e)], [1]])
            my_rhs.append(0)
            my_senses = my_senses + 'E'
                
    colnames = []
    colnames.extend(b)
    colnames.extend(used_edges)
    
    var_lb = []
    var_lb.extend(b_lb)
    var_lb.extend(used_edges_lb)
    
    var_ub = []
    var_ub.extend(b_ub)
    var_ub.extend(used_edges_ub)
    
    #optimal goal
    link_obj = []
    
    link_obj = [0] * len(b)
    
    link_obj.extend([1] * len(used_edges))
        
    my_prob = cplex.Cplex()
              
    my_prob.objective.set_sense(my_prob.objective.sense.minimize)

    var_type = b_type + used_edges_type
    
    my_prob.variables.add(obj = link_obj, lb = var_lb, ub = var_ub, types = var_type,
                       names = colnames)

    my_prob.linear_constraints.add(lin_expr = rows, senses = my_senses,
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
    

    #for j in range(numrows):
    #    print "Row %d:  Slack = %10f" % (j, slack[j])
    for j in range(numcols):
        print colnames[j] + " %d:  Value = %d" % (j, x[j])
    
    #calculate wire frequencies and routes
    wire_delays = calculate_optimal_wire_delays(x[0:len(b)], dim, ndirs, flows, ncycles)
    
    routes = calculate_routes(x[0:len(b)], dim, ndirs, flows, ncycles)

    return [routes, wire_delays]

def power_cost(traffic_amount, ncycles):
    
    for opt in wire_config_opts:
        freq = opt[1]
        if  channel_width * ncycles * freq >= traffic_amount * max_wire_freq:
            if freq == 0:
                return 0
            power = link.calc_dynamic_energy(channel_width_bits/2, opt[0]) * float(traffic_amount * max_wire_freq)/ (channel_width * ncycles)
            #power = power + link.get_static_power(opt[0])
            return power
            break
    return -1

def utilization(traffic_amount, ncycles):
    return float(traffic_amount/channel_width * ncycles)

def xy_routing(ncycles, flows, dim, ndirs):
    
    vnoc_dir = [0] * ndirs
    
    vnoc_dir[north] = 4
    vnoc_dir[south] = 3
    vnoc_dir[east] = 2
    vnoc_dir[west] = 1
    
     #make a copy of the flows in case we advertently modify it
    dirty_flows = copy.deepcopy(flows)
    
    edge_traffic = [0] * (dim * dim * ndirs) 
    #gradually route flows through the network
    #such that each time a new flow is added
    #the power increment is minimal
    routes = []
    for f in dirty_flows:
        currentX = f[srcXIdx]
        currentY = f[srcYIdx]
        
        dstX = f[dstXIdx]
        dstY = f[dstYIdx]
        
        route = ['(' + str(f[srcXIdx]) + ',' + str(f[srcYIdx]) + ')', '(' +  str(f[dstXIdx]) + ',' + str(f[dstYIdx]) + ')']
         #vc
        vc = -1 #any VC
        if f[srcXIdx] < f[dstXIdx]:
            if f[srcYIdx] <= f[dstYIdx]:
                vc = 0
        elif f[srcYIdx] > f[dstYIdx]:
            vc = 1
        route.append(vc)
        
        route.append(f[traffic_idx])
        route.append(ncycles)
        route.append('|')
        
        while currentX != dstX or currentY != dstY:
            
            nextX = currentX
            nextY = currentY
            
            if currentX < dstX:
                dir = east
                nextX = currentX + 1
            elif currentX > dstX:
                dir = west
                nextX = currentX - 1
            elif currentY < dstY:
                dir = north
                nextY = currentY + 1
            elif currentY > dstY:
                dir = south
                nextY = currentY - 1

            #update traffic on the edge
            edge_id = (currentX * dim + currentY) * ndirs + dir
            edge_traffic[edge_id] = edge_traffic[edge_id] + f[traffic_idx]
             
            route.append(vnoc_dir[dir])
            
            currentX = nextX
            currentY = nextY  
            
        route.append(' ')
        
        routes.append(route)
        
    wire_delays = calculate_optimal_wire_delays_2(edge_traffic, dim, ndirs, ncycles)
    
    return [routes, wire_delays]
    
def sort_flows_by_routing_freedom(flows):
    for f in flows:
        deltaX = abs(f[srcXIdx] - f[dstXIdx])
        deltaY = abs(f[srcYIdx] - f[dstYIdx])
        n_routes = 1
        divisor = 1
        for i in range(1, deltaX + 1):
            n_routes = n_routes * (deltaY + i)
            divisor = divisor * i
        n_routes = n_routes/divisor
        f.insert(0,n_routes)
    flows.sort(key=lambda tup:tup[0])
    for f in flows:
        f.pop(0)
    return flows

#testing flow-sorting function
def sort_flows(flows):
    for f in flows:
        deltaX = abs(f[srcXIdx] - f[dstXIdx])
        deltaY = abs(f[srcYIdx] - f[dstYIdx])
        distance = deltaX + deltaY
        f.insert(0, distance)
    flows.sort(key=lambda tup:tup[0])
    for f in flows:
        f.pop(0)
    return flows

def dp_routing(ncycles, flows, dim, ndirs):
    
    vnoc_dir = [0] * ndirs
    
    vnoc_dir[north] = 4
    vnoc_dir[south] = 3
    vnoc_dir[east] = 2
    vnoc_dir[west] = 1
    
    node_cost_id = 2
    node_X_id = 0
    node_Y_id = 1
    
    wire_delays = []
    routes = []
    
    #make a copy of the flows in case we advertently modify it
    dirty_flows = copy.deepcopy(flows)
    
    #sort the flows by traffic freedom
    dirty_flows = sort_flows_by_routing_freedom(dirty_flows)
    
    edge_traffic = [0] * (dim * dim * ndirs) 
    #gradually route flows through the network
    #such that each time a new flow is added
    #the power increment is minimal
    for f in dirty_flows:
        currentX = f[srcXIdx]
        currentY = f[srcYIdx]
        
        dstX = f[dstXIdx]
        dstY = f[dstYIdx]
        
        node_layers = []
        node_layers.append([[currentX, currentY, 0]]) #coordinate and cost from the node
        
        finished = False
        #spreading out to correct directions
        while not finished:
            next_layer = []
            
            #derive nodes that is one hop from the current layer's node
            current_layer = node_layers[-1]
            
            if len(current_layer) == 0:
                print 'Cannot route, exiting ...'
                quit()
                
            for node in current_layer:
                if node[node_X_id] == dstX and node[node_Y_id] == dstY:
                    finished = True
                    break
                
                next_nodes = []
                #exam the node in x direction
                nextX = -1
                dir = -1
                
                if node[node_X_id] > dstX:
                    nextX = node[node_X_id] - 1
                    dir = west 
                elif node[node_X_id] < dstX:
                    nextX = node[node_X_id] + 1
                    dir = east
                if nextX != -1:
                    next_nodes.append([nextX, node[node_Y_id], dir])
                
                nextY = -1 
                
                if node[node_Y_id] > dstY:
                    nextY = node[node_Y_id] - 1
                    dir = south
                elif node[node_Y_id] < dstY:
                    nextY = node[node_Y_id] + 1
                    dir = north
                if nextY !=  -1:
                    next_nodes.append([node[node_X_id], nextY, dir])

                for next_node in next_nodes:
                    edge_id = (node[node_X_id] * dim + node[node_Y_id]) * ndirs + next_node[2]
                    
                    #if this link can not afford the additional traffic
                    if power_cost(edge_traffic[edge_id] + f[traffic_idx], ncycles) < 0:
                        continue
                    
                    #cost of increasing traffic on this edge
                    cost = power_cost(edge_traffic[edge_id] + f[traffic_idx], ncycles) - power_cost(edge_traffic[edge_id], ncycles) + node[node_cost_id]
                    #cost = utilization(edge_traffic[edge_id] + f[traffic_idx], ncycles) + node[node_cost_id]
                    exists = False
                    
                    for nn in next_layer:
                        if nn[node_X_id] == next_node[node_X_id] and nn[node_Y_id] == next_node[node_Y_id]:
                            prev_edge_id = (nn[node_cost_id + 1] * dim + nn[node_cost_id + 2]) * ndirs + 3 - nn[node_cost_id + 3]
                            
                            if nn[node_cost_id] > cost or (nn[node_cost_id] == cost and edge_traffic[edge_id] < edge_traffic[prev_edge_id]):
                                # this is more optimal route
                                nn[node_cost_id] = cost
                                nn[node_cost_id + 1] = node[node_X_id]
                                nn[node_cost_id + 2] = node[node_Y_id]
                                nn[node_cost_id + 3] = next_node[2]
                            exists = True
                    if not exists:
                        next_layer.append([next_node[node_X_id], next_node[node_Y_id], cost, node[node_X_id], node[node_Y_id], next_node[2]]) #include last node address  
            if not finished:      
                node_layers.append(next_layer)
            
        #commit the route
        # src and dst information
        route = ['(' + str(f[srcXIdx]) + ',' + str(f[srcYIdx]) + ')', '(' +  str(f[dstXIdx]) + ',' + str(f[dstYIdx]) + ')']
        
        #vc
        vc = -1 #any VC
        if f[srcXIdx] < f[dstXIdx]:
            if f[srcYIdx] <= f[dstYIdx]:
                vc = 0
        elif f[srcYIdx] > f[dstYIdx]:
            vc = 1
        route.append(vc)
        
        route.append(f[traffic_idx])
        route.append(ncycles)
        route.append('|')
        
        currentX = dstX
        currentY = dstY
        
        dirs = []
        while len(node_layers) > 1:
            current_layer = node_layers.pop()
            for node in current_layer:
                if node[node_X_id] == currentX and node[node_Y_id] == currentY:
                    dir  = node[node_cost_id + 3]
                    dirs.insert(0, vnoc_dir[dir])
                    
                    currentX = node[node_cost_id + 1]
                    currentY = node[node_cost_id + 2]
                    
                    edge_id = (currentX * dim + currentY) * ndirs + dir
                    
                    #commit the traffic
                    edge_traffic[edge_id] = edge_traffic[edge_id] + f[traffic_idx]
                    
                    break
        
        
        route.extend(dirs)
        
        route.append(' ')
        routes.append(route)
        #new compute routes and delays
    wire_delays = calculate_optimal_wire_delays_2(edge_traffic, dim, ndirs, ncycles)      
        
    return [routes, wire_delays]

def dijkstra_routing(ncycles, flows, dim, ndirs):
    
    minimal_route = True
    
    vnoc_dir = [0] * ndirs
    
    vnoc_dir[north] = 4 
    vnoc_dir[south] = 3
    vnoc_dir[east] = 2
    vnoc_dir[west] = 1
    
    wire_delays = []
    routes = []
    
    #make a copy of the flows in case we advertently modify it
    dirty_flows = copy.deepcopy(flows)
    
    #sort the flows by traffic freedom
    dirty_flows = sort_flows_by_routing_freedom(dirty_flows)
    #dirty_flows = sort_flows(dirty_flows)
    
    edge_traffic = [0] * (dim * dim * ndirs) 
    #gradually route flows through the network
    #such that each time a new flow is added
    #the power increment is minimal
    for f in dirty_flows:
        dst = f[dstXIdx] * dim + f[dstYIdx]
        src = f[srcXIdx] * dim + f[srcYIdx]
        
        dist = [float('inf')] * dim * dim
        
        previous = [-1] * dim * dim
        previous_dir = [-1] * dim * dim
        
        dist[src] = 0

        Q = range(dim * dim)
        visited = set([])
        
        while len(Q) > 0:
            min_val = float('inf')
            min_idx = 0
            
            for i in range(len(Q)):
                if dist[Q[i]] < min_val:
                    min_val = dist[Q[i]]
                    min_idx = i
            
            u = Q[min_idx]
            del Q[min_idx]
            
            #reach the source
            if u == dst:
                break
            
            visited.add(u)
            if dist[u] == float('inf'):
                print 'Error: Cannot route'
                break
            u_x = u/dim
            u_y = u%dim
            
            #next vertices
            next_vertices = []
            if minimal_route:
                if u_x < f[dstXIdx]:
                    next_vertices.append([u_x + 1, u_y, east])
                if u_x > f[dstXIdx]:
                    next_vertices.append([u_x - 1, u_y, west])
                if u_y < f[dstYIdx]:
                    next_vertices.append([u_x, u_y + 1, north])
                if u_y > f[dstYIdx]:
                    next_vertices.append([u_x, u_y - 1, south])
            else:
                if u_x < dim - 1:
                    next_vertices.append([u_x + 1, u_y, east])
                if u_x > 0:
                    next_vertices.append([u_x - 1, u_y, west])
                if u_y < dim - 1:
                    next_vertices.append([u_x, u_y + 1, north])
                if u_y > 0:
                    next_vertices.append([u_x, u_y - 1, south])
                
            for v in next_vertices:
                v_id = v[0] * dim + v[1]
                if not (v_id in visited):
                    #forbids 180 deg turns
                    if previous_dir[u] != -1 and previous_dir[u] == 3 - v[2]:
                        continue
                    edge_id = u * ndirs + v[2]
                    #if this link can not afford the additional traffic
                    if power_cost(edge_traffic[edge_id] + f[traffic_idx], ncycles) < 0:
                        continue
                    
                    #cost of increasing traffic on this edge
                    alt = dist[u] + power_cost(edge_traffic[edge_id] + f[traffic_idx], ncycles) - power_cost(edge_traffic[edge_id], ncycles)
                    
                    if alt < dist[v_id]:
                        dist[v_id] = alt
                        previous[v_id] = u
                        previous_dir[v_id] = v[2]
        
         #commit the route
        # src and dst information
        route = ['(' + str(f[srcXIdx]) + ',' + str(f[srcYIdx]) + ')', '(' +  str(f[dstXIdx]) + ',' + str(f[dstYIdx]) + ')']
        
        #vc
        vc = -1 #any VC
        if f[srcXIdx] < f[dstXIdx]:
            if f[srcYIdx] <= f[dstYIdx]:
                vc = 0
        elif f[srcYIdx] > f[dstYIdx]:
            vc = 1
        route.append(vc)
        
        route.append(f[traffic_idx])
        route.append(ncycles)
        route.append('|')
        
        dirs = []
        
        u = dst
        
        while previous_dir[u] != -1:
            edge_id = previous[u] * ndirs + previous_dir[u]
                    
            #commit the traffic
            edge_traffic[edge_id] = edge_traffic[edge_id] + f[traffic_idx]
                    
            dirs.insert(0, vnoc_dir[previous_dir[u]])
            u = previous[u]
        
        assert(u == src)
        
        route.extend(dirs)
        
        route.append(' ')
        routes.append(route)
    
    #new wire delays
    wire_delays = calculate_optimal_wire_delays_2(edge_traffic, dim, ndirs, ncycles)      
        
    return [routes, wire_delays]

            
    
def list_to_file(name, ls):
    FILE = open(name, 'w')
    for l in ls:
        line = ''
        for item in l:
            if isinstance(item, str):
                line = line + ' ' + item
            else:
                line = line + ' ' + str(item)
        #line = line.strip()
        print>>FILE, line[1:]
        
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
    commlog = str(dim) + "x" + str(dim) + "_comm.log"
    if recompile or not os.path.isfile(commlog):
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
    
    dimstr = str(dim) + "x" + str(dim)
    logfile = dimstr + "_time.log"
    cfile = "str" + dimstr + '.cpp'
    
    if recompile or not os.path.isfile(cfile):
        # compile from str to c
        os.system("make -f Makefile.mk BACKEND=\'--spacetime --newSimple " + str(dim) + " --i " + str(iterations) + "\'")
    
        # compile the generated c program
        os.system("cp /home/dai/prog/compiler/streamit/tmp/Makefile ./")
        os.system("make")
    
        #store the files
        os.system("mv str.cpp " + cfile)
        os.system("mv stream stream"  + str(dim) + 'x' + str(dim))
    
    # run the program to obtain running time information
    if recompile or not os.path.isfile(logfile):
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

def traffic_gen(flows, ncycles):
    traffics = []
    dirty_flows = copy.deepcopy(flows) # make a copy of the flows in case it is modified
    #for each source node
    for x in range(0, dim):
        for y in range(0,dim):
            # find flows that have this node as the source node
            fs = []
            
            packets = []    #packets from this source
            
            traffics.append([x,y,packets])
            
            for f in dirty_flows:
                if f[srcXIdx] == x and f[srcYIdx] == y:
                    #f.append(0)  #total traffic used for this flow
                    fs.append(f)
                    
            for f in fs:
                dirty_flows.remove(f)
                interval = packet_bytes * ncycles/f[traffic_idx];
                current_time = 0;
                traffic_left = f[traffic_idx] * 5;
                
                while traffic_left >= packet_bytes:
                    packet = copy.deepcopy(f[0:4])
                    packet.insert(0,current_time)
                    packet.append(packet_bytes/flit_size)
                    packets.append(packet)
                    
                    current_time =  current_time + interval;
                    traffic_left = traffic_left - packet_bytes
                    
                if traffic_left > 0:
                    packet = copy.deepcopy(f[0:4])
                    packet.insert(0,current_time)
                    n_bytes = int(round(float(traffic_left + 0.5)/flit_size))
                    if n_bytes <= 1:
                        n_bytes = 2
                    packet.append(n_bytes)
                    packets.append(packet)
                    
            packets.sort(key=lambda tup:tup[0])
                            
    return traffics

def logfile_to_power(logfile, wire_delays, dim, ndirs):
    FILE = open(logfile, 'r')
    power = 0
    
    for line in FILE.readlines():
        m = re.search('Wire\s*\((\d+)\s*,\s*(\d+)\)\s*(\d+)\s+(\d+.?\d*)\s+(\d+.?\d*)', line)
        if m != None:
            x = int(m.group(1))
            y = int(m.group(2))
            dir = int(m.group(3))
            utilization = float(m.group(5))
            
            if utilization > 0:
                wire_delay = wire_delays[x * dim + y][dir + 2]
                freq = int(round(OneGhz / wire_delay))
            
                for opt in wire_config_opts:
                    if abs(opt[1] - freq) < 20:
                        freq = opt[1] * 1000000/OneMhz
                        vdd = opt[0]
                        p_dyn = link.calc_dynamic_energy(channel_width_bits/2, vdd) * utilization * freq
                        p_static = link.get_static_power(vdd)
                        power = power + p_dyn # + p_static
                        break
                    
        m = re.search('Current time:\s*(\d+.?\d*)', line)
        if m != None:
            running_time = m.group(1)            
    FILE.close()
    
    print 'Total power consumption ' + str(power) + ' time ' + running_time + ' total power ' + str(power * float(running_time))
    
############################################################################################

for dir in os.listdir(path)[4:]:
    
    if os.path.isfile('./dir'):
        continue
    # run to obtain profiling information first
    os.chdir('./' + dir + '/streamit')
    
    os.system("pwd")
    
    optimize = True
    
    for dim in [8]:
        
        ncycles = time_prof(dim)
        
        flows = comm_prof(dim)
        
        ncycles = ncycles/15
        print ncycles
        
        #generate ILP files
        if optimize:
        #[routes, wire_delays] = optimal_routes_freqs(ncycles, flows, dim, directions)
        #[routes, wire_delays] = minimize_used_links(ncycles, flows, dim, directions)
            #[routes, wire_delays] = dp_routing(ncycles, flows, dim, directions)
            [routes, wire_delays] = dijkstra_routing(ncycles, flows, dim, directions)
        else:
            [routes, wire_delays] = xy_routing(ncycles, flows, dim, directions)

        #generate wire config
        list_to_file('wire_config.txt', wire_delays)
        
        #generate routing config
        list_to_file('traffic_config.txt', routes)
        
        #generate traffic
        traffics = traffic_gen(flows, ncycles)
                                
        write_traffic(dir, traffics)

        #invoke simulation
        logfile = dir + str(dim) + 'x' + str(dim) + '_sim.log'
        
        if optimize:
            os.system('vnoc ./traffics/' + dir + ' noc_size: ' + str(dim) + ' routing: TABLE vc_n: 8 > ' + logfile)
        else:
            os.system('vnoc ./traffics/' + dir + ' noc_size: ' + str(dim) + ' routing: XY vc_n: 8 > ' + logfile)
        #collect data
        
        logfile_to_power(logfile, wire_delays, dim, directions)
        
        quit()
        
        
    os.chdir("../../")
        

