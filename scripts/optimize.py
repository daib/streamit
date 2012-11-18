#!/usr/bin/python

import cplex
from cplex.exceptions import CplexError
import sys
import os
import re
import copy
import random
import math
from orion2 import *
from cplex.exceptions import CplexError
import traceback
import time
from compiler.pyassem import Block

#######################################################################################
path = sys.argv[1]

os.chdir(path)

recompile = False 

iterations = 100

SOLVER_TIME_LIMIT = 1800

OneMhz = 1
OneGhz = 1000 * OneMhz

profiling_cpu_freq = 3 * OneGhz  # 3GHz
assumed_cpu_freq = OneGhz

start_time = 10  # start sending packets at cycle 10
packet_flits = 6  # packet size in numbers of flits
flit_size = 8  # flit size in numbers of bytes
packet_bytes = packet_flits * flit_size
max_packet_bytes = packet_flits * flit_size
channel_width = flit_size

channel_width_bits = channel_width * 8

directions = 4

router_speed_scale = 0.8

x_idx = 0
y_idx = 1

freq_idx = 1
vdd_idx = 0

in_edge_idx = 1
out_edge_idx = 0

# flow indices
srcXIdx = 0
srcYIdx = 1

dstXIdx = 2
dstYIdx = 3

traffic_idx = 4
flow_id_idx = 5
traffic_used_idx = 6

# routing directions
north = 0
south = 3
west = 1
east = 2

#######################################################################################


# wire_config_opts = [[2.5, 1000 * OneMhz], [2.38, 950 * OneMhz], [2.27, 900 * OneMhz], 
#                    [2.15, 850 * OneMhz], [2.02, 800 * OneMhz], [1.93, 760 * OneMhz], 
#                    [1.84, 720 * OneMhz], [1.75, 680 * OneMhz], [1.66, 640 * OneMhz], 
#                    [1.57, 600 * OneMhz], [0.9, 125 * OneMhz]]

wire_config_opts = [[2.50, 1000 * OneMhz], [2.20, 870 * OneMhz], [1.95, 750 * OneMhz],
                    [1.75, 640 * OneMhz], [1.58, 540 * OneMhz], [1.43, 450 * OneMhz],
                    [1.30, 370 * OneMhz], [1.19, 300 * OneMhz], [1.10, 240 * OneMhz],
                    [1.02, 190 * OneMhz], [0, 0]]

# wire_config_opts = [[2.50, 1000 * OneMhz], [0, 0]]

# wire_config_opts = [[2.50, 1000 * OneMhz], [1.95, 750 * OneMhz],
#                    [1.58, 540 * OneMhz],
#                    [1.30, 370 * OneMhz], [1.10, 240 * OneMhz],
#                    [0.9, 125 * OneMhz]]

wire_config_opts.reverse()

max_wire_freq = max([opt[1]  for opt in wire_config_opts])
freq_levels = [c[1] for c in wire_config_opts]

from orion_power import Link

link_len = 0.001
link = Link(link_len, channel_width_bits)

#######################################################################################

def new_cplex_solver():
    prob = cplex.Cplex()
    prob.parameters.timelimit.set(SOLVER_TIME_LIMIT)
    prob.parameters.tuning.timelimit.set(SOLVER_TIME_LIMIT)
    
    prob.set_results_stream(None)
    prob.set_log_stream(None)
    
    return prob
    
def format_var(name, idx1, idx2):
    return name + '_' + str(idx1) + '_' + str(idx2)

# dir is the direction of incomming edge

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
            id = (x * dim + y + 1) * ndirs

    if id >= 0:
        id = id + dir        

    return id

def edges_on_k_paths(srcX, srcY, dstX, dstY, k, dim, ndirs):
    
    edges = []
    dir = -1
     
    # we will start with minimal paths first
    if srcX < dstX:
        dir = east
        for x in range(srcX, dstX):
            for y in range(min(srcY, dstY), max(srcY, dstY) + 1):
                edge_id = (x * dim + y) * ndirs + dir
                edges.append(edge_id)
    elif srcX > dstX:
        dir = west
        for x in range(dstX + 1, srcX + 1):
            for y in range(min(srcY, dstY), max(srcY, dstY) + 1):
                edge_id = (x * dim + y) * ndirs + dir
                edges.append(edge_id)
    
    
    if srcY < dstY:
        dir = north
        for x in range(min(srcX, dstX), max(srcX, dstX) + 1):
            for y in range(srcY, dstY):
                edge_id = (x * dim + y) * ndirs + dir
                edges.append(edge_id)
    elif srcY > dstY:
        dir = south
        for x in range(min(srcX, dstX), max(srcX, dstX) + 1):
            for y in range(dstY + 1, srcY + 1):
                edge_id = (x * dim + y) * ndirs + dir
                edges.append(edge_id)
    
    # return edge ids
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
                
            # the format is:  node_address dirs
            #                (x,y) local, 1, 2, 3, 4
                
            wire_delay = [x, y, 1]  # FIXME: compute minimal local link power?
            for dir in range(1, ndirs + 1):
                if(round(edge_freqs[edgeSrcId + vnoc_dir[dir]]) == 0):
                    wire_delay.append(-1)  # this link is disabled
                else:
                    wire_delay.append(float(max_wire_freq) / round(edge_freqs[edgeSrcId + vnoc_dir[dir]]))
                    
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
        route = [0, '(' + str(f[srcXIdx]) + ',' + str(f[srcYIdx]) + ')', '(' + str(f[dstXIdx]) + ',' + str(f[dstYIdx]) + ')']
        
        # vc
        vc = vc_calculate(f)  

        route.append(vc)
        
        route.append(f[traffic_idx])
        route.append(ncycles)
        route.append('|')
        
        # trace from source to destination
        currentX = f[srcXIdx]
        currentY = f[srcYIdx]
        
        while currentX != f[dstXIdx] or currentY != f[dstYIdx]:
            # base_id = ((currentX * dim + currentY) * ndirs) * len(dirty_flows) + i
            base_id = ((currentX * dim + currentY) * ndirs) + i * dim * dim * ndirs
            for dir in range(ndirs):
                if round(b[base_id + dir]) == 1:
                    route.append(vnoc_dir[dir])
                    
                    # move to the next node
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

def calculate_local_edge_traffic(flows, dim):
    local_edge_traffic = [0] * (dim * dim * 2)
    for f in flows:
        local_edge_traffic[((f[srcXIdx] * dim  + f[srcYIdx]) * 2 + out_edge_idx)] = local_edge_traffic[((f[srcXIdx] * dim  + f[srcYIdx]) * 2 + out_edge_idx)] + f[traffic_idx]
        local_edge_traffic[((f[dstXIdx] * dim  + f[dstYIdx]) * 2 + in_edge_idx)] = local_edge_traffic[((f[dstXIdx] * dim  + f[dstYIdx]) * 2 + in_edge_idx)] + f[traffic_idx]
    return local_edge_traffic

def vc_calculate(f):
    vc = -1
#    if f[srcXIdx] < f[dstXIdx]:
#        vc = 0
#    elif f[srcXIdx] > f[dstXIdx]:
#        vc = 1
    if f[srcXIdx] < f[dstXIdx]:
        if f[srcYIdx] <= f[dstYIdx]:
            vc = 0
    elif f[srcXIdx] > f[dstYIdx]:
        vc = 1
    return vc

def resolve_cycles(b, q, n_splits, dim, ndirs, flows, ncycles):
    dirty_flows = copy.deepcopy(flows)
    
    flows_vertices = []
    
    for i in range(len(dirty_flows)):
        true_idx = 0;
        for j in range(n_splits):
            f = dirty_flows[i]
            
            k =  i * n_splits + j
            
            if q[k] == 0:
                continue
            
            this_flow = []
            this_flow.append([i , j])
            flows_vertices.append(this_flow)
            
            # src and dst information
            currentX = f[srcXIdx]
            currentY = f[srcYIdx]
            
            while currentX != f[dstXIdx] or currentY != f[dstYIdx]:
                
                # base_id = ((currentX * dim + currentY) * ndirs) * len(dirty_flows) + i
                edge_base_id = (currentX * dim + currentY) * ndirs
                base_id = cal_index(k, edge_base_id, dim, ndirs)#((currentX * dim + currentY) * ndirs) + k * dim * dim * ndirs
                for dir in range(ndirs):
                    if round(b[base_id + dir]) == 1:
                        this_flow.append([currentX, currentY, dir])
                        # move to the next node
                        if dir == north:
                            currentY = currentY + 1
                        elif dir == south:
                            currentY = currentY - 1
                        elif dir == west:
                            currentX = currentX - 1
                        elif dir == east:
                            currentX = currentX + 1
                            
                        break
            
            #this_flow.append([currentX, currentY])
    
    routes = []
    vcs = []
    
    edge_transfer = {}#[[False, []]] * ((dim * dim * ndirs) * (dim * dim * ndirs))
    
    def edge_idx(v):
        return (v[0] * dim + v[1]) * ndirs + v[2]
    
    #create connected graphs
    for this_flow in flows_vertices:
        for i in range(1, len(this_flow) - 1):
            #v = this_flow[i]
            #for j in range(i, len(this_flow)):
            entry_idx =  edge_idx(this_flow[i]) * (dim * dim * ndirs) + edge_idx(this_flow[i + 1])
            #edge_transfer[entry_idx][0] = True
            #edge_transfer[entry_idx][1].append(this_flow[0]) #id of the flow transferring between two edges
            if not edge_transfer.has_key(entry_idx):
                edge_transfer[entry_idx] = []
            edge_transfer[entry_idx].append(this_flow[0])
    
    index = [0]
    S = []

    v_index = [-1] * (dim * dim * ndirs)
    v_lowlink = [-1] * (dim * dim * ndirs)
    
    SCCs = []
    
    def strongconnect(v):
        v_index[edge_idx(v)] = index[0]
        v_lowlink[edge_idx(v)] = index[0]
        index[0] = index[0] + 1
        S.append(v)
        for x in range(dim):
            for y in range(dim):
                for dir in range(ndirs):
                    w = [x, y, dir]
                    entry_idx =  edge_idx(v) * (dim * dim * ndirs) + edge_idx(w)
                    if edge_transfer.has_key(entry_idx):
                        if v_index[edge_idx(w)] == -1:
                            strongconnect(w)
                            v_lowlink[edge_idx(v)] = min(v_lowlink[edge_idx(v)], v_lowlink[edge_idx(w)])
                        elif w in S:
                            v_lowlink[edge_idx(v)] = min(v_lowlink[edge_idx(v)], v_index[edge_idx(w)])
        if v_lowlink[edge_idx(v)] == v_index[edge_idx(v)]:
            SCC = []
            
            while True:
                w = S.pop()
                SCC.append(w)
                if w == v:
                    break
            if len(SCC) > 1:
                SCCs.append(SCC)
    
    for x in range(dim):
        for y in range(dim):
            for dir in range(ndirs):
                if v_index[edge_idx([x, y, dir])] == -1:
                    strongconnect([x, y, dir])
                    
    circles = []
    
    def circuit(v):
        def unblock(u):
            blocked[edge_idx(u)] = False
            for w in B[edge_idx(u)]:
                B[edge_idx(u)].remove(w)
                if blocked[edge_idx(w)]:
                    unblock(w)
        f = False
        
        stack.append(v)
        
        blocked[edge_idx(v)] = True
        
#        for x in range(dim):
#            for y in range(dim):
#                for dir in range(ndirs):
#                    w = [x, y, dir]
        for w in adj[edge_idx(v)]:
            if w == s:
                 circle = []
                 circle.extend(stack)
                 circle.append(s)
                 circles.append(circle)
                 f = True
            elif not blocked[edge_idx(w)]:
                if circuit(w):
                    f = True
        if f:
            unblock(v)
        else:
#            for x in range(dim):
#                for y in range(dim):
#                    for dir in range(ndirs):
#                        w = [x, y, dir]
            for w in adj[edge_idx(v)]:
                if not v in B[edge_idx(w)]:
                    B[edge_idx(w)].append(v)
        v = stack.pop()
        return f
    
    stack = []
    
    ss = 0
    blocked = [False] * dim * dim * ndirs
    B = {}
    
#    while ss < (dim * dim * ndirs):
#        x = (ss/ndirs)/dim
#        y = (ss/ndirs)%dim
#        dir = ss % ndirs
#        
    for SCC in SCCs:
        while(len(SCC) > 1):
            adj = {}
            for v in SCC:
               adj[edge_idx(v)] = []
               for w in SCC:
                   entry_idx = edge_idx(v) * (dim * dim * ndirs) + edge_idx(w)
                   if edge_transfer.has_key(entry_idx):
                       adj[edge_idx(v)].append(w)
            #find min element of SCC
            ndegs = float('inf')
            
            for s in SCC:
                degs = 0
                for w in SCC:
                    entry_idx = edge_idx(s) * (dim * dim * ndirs) + edge_idx(w)
                    if edge_transfer.has_key(entry_idx):
                        degs = degs + 1
                if degs < ndegs:
                    ndegs = degs
                    min_s = s
                        
            s = min_s
            for i in range(dim * dim * ndirs):
                blocked[i] = False
                B[i] = []
            
            circuit(s)
            SCC.remove(s)
            
                
                
                                       
#list the flows involved in connected components
    conflict_sccs = []
    resolving_flows = []
    for circle in circles:
        if len(circle) <= 1:
            continue
        conflict_flows = []
        for i in range(len(circle)):
            for j in range(i, len(circle)):
                entry_idx =  edge_idx(circle[i]) * (dim * dim * ndirs) + edge_idx(circle[j])
                if edge_transfer.has_key(entry_idx):
                    for f in edge_transfer[entry_idx]:
                        if not f in conflict_flows:
                            conflict_flows.append(f)
                        if not f in resolving_flows:
                            resolving_flows.append(f)
        conflict_sccs.append(conflict_flows)


    #use ILP to solve the problem
    
    vc_vars = []
    vc_vars_type = ''
    
    nvc = 2
    for f in resolving_flows:
        k = f[0] * n_splits + f[1]
        for i in range(nvc): 
            vc_vars.append(str(k) + '_' + str(i))
            vc_vars_type = vc_vars_type + 'B'
    
    vc_vars_ub = [1] * len(vc_vars)
    vc_vars_lb = [0] * len(vc_vars)
    rows = []
    my_rhs = []
    my_senses = []
    
    for cfs  in conflict_sccs:
        for i in range(nvc):
            var_names = []
            for f in cfs:
                k = f[0] * n_splits + f[1]
                var_names.append(str(k) + '_' + str(i))
            coefs = [1] * len(var_names)
            rows.append([var_names, coefs])
            my_rhs.append(len(var_names) - 1)
            my_senses.append('L')
    for f in resolving_flows:
        k = f[0] * n_splits + f[1]
        var_names = []
        for i in range(nvc):
            var_names.append(str(k) + '_' + str(i))
        coefs = [1] * len(var_names)
        rows.append([var_names, coefs])
        my_rhs.append(1)
        my_senses.append('E')
        
    
    load_obj = [1] * len(vc_vars)
    
    my_prob = new_cplex_solver()
              
    my_prob.objective.set_sense(my_prob.objective.sense.minimize)

    my_prob.variables.add(obj=load_obj, lb=vc_vars_lb, ub=vc_vars_ub, types=vc_vars_type,
                       names=vc_vars)

    my_prob.linear_constraints.add(lin_expr=rows, senses=my_senses,
                                rhs=my_rhs)

    print 'Finding vc values ...'
    my_prob.solve()
    x = my_prob.solution.get_values()
    
    for i in range(len(resolving_flows)):
        for j in range(nvc):
            if x[i * nvc + j] == 1:
                k = resolving_flows[i][0] * n_splits + resolving_flows[i][1] 
                vcs.append([k, j])
                break
    
    return vcs

def calculate_routes_2(b, q, n_splits, dim, ndirs, flows, ncycles):
    routes = []
    
    vcs = resolve_cycles(b, q, n_splits, dim, ndirs, flows, ncycles)
    
    dirty_flows = copy.deepcopy(flows)
    
    vnoc_dir = [0] * ndirs
    
    vnoc_dir[north] = 4
    vnoc_dir[south] = 3
    vnoc_dir[east] = 2
    vnoc_dir[west] = 1
    
    edge_traffic = [0] * (dim * dim * ndirs)
    
    for i in range(len(dirty_flows)):
        true_idx = 0;
        for j in range(n_splits):
            f = dirty_flows[i]
            
            k =  i * n_splits + j
            
            if q[k] == 0:
                continue
            
            # src and dst information
            route = [true_idx, '(' + str(f[srcXIdx]) + ',' + str(f[srcYIdx]) + ')', '(' + str(f[dstXIdx]) + ',' + str(f[dstYIdx]) + ')']
            true_idx = true_idx + 1
            # vc
            #vc = vc_calculate(f)
            vc = -1
            for v in vcs:
                if v[0] == k:
                    vc = v[1]
                    break
            
            route.append(vc)
            
            route.append(q[k])
            route.append(ncycles)
            route.append('|')
            
            # trace from source to destination
            currentX = f[srcXIdx]
            currentY = f[srcYIdx]
            
            while currentX != f[dstXIdx] or currentY != f[dstYIdx]:
                # base_id = ((currentX * dim + currentY) * ndirs) * len(dirty_flows) + i
                edge_base_id = (currentX * dim + currentY) * ndirs
                base_id = cal_index(k, edge_base_id, dim, ndirs)#((currentX * dim + currentY) * ndirs) + k * dim * dim * ndirs
                for dir in range(ndirs):
                    if round(b[base_id + dir]) == 1:
                        
                        edge_traffic[edge_base_id + dir] = edge_traffic[edge_base_id + dir] + q[k]
                        route.append(vnoc_dir[dir])
                        
                        # move to the next node
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
        
    return [routes, edge_traffic]

def calculate_optimal_router_delays(edge_traffic, local_edge_traffic, dim, ndirs, ncycles):
    router_delays = []
    for x in range(dim):
        for y in range(dim):
            u = x * dim + y
            [vdd, freq, avg_traffic] = estimate_router_Vdd_freq_traffic(u, edge_traffic, local_edge_traffic, ncycles, dim, ndirs)
            if freq > 0:
                router_delays.append([x, y, float(OneGhz)/freq])
            #router_delays.append([x, y, 1])
    return router_delays

def calculate_optimal_wire_delays_2(edge_traffic, dim, ndirs, ncycles):
    wire_delays = []
    
    vnoc_dir = [0] * 5
    
    vnoc_dir[4] = north
    vnoc_dir[3] = south
    vnoc_dir[2] = east
    vnoc_dir[1] = west
    
    for x  in range(dim):
        for y in range(dim):
            # we need to convert to the vnoc direction
            # can convert from frequency to delays
                
            # the format is:  node_address dirs
            #                (x,y) local, 1, 2, 3, 4
                
            wire_delay = [x, y, 1]  # FIXME: compute minimal local link power?
            
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
                            wire_delay.append(float(max_wire_freq) / freq)
                            #wire_delay.append(1)
                            # print 'Edge ' + str(x) + ',' + str(y) + ' ' + str(dir) + ' utilization ' + str(float(total_traffic * max_wire_freq)/(channel_width * ncycles * freq))
                        exists = True
                        break
                if not exists:
                    #print 'Traffic is too much for the link'
                    # quit()
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
                
            # the format is:  node_address dirs
            #                (x,y) local, 1, 2, 3, 4
                
            wire_delay = [x, y, 1]  # FIXME: compute minimal local link power?
            
            for d in range(1, ndirs + 1):
                dir = vnoc_dir[d]
                edge_id = (x * dim + y) * ndirs + dir
                total_traffic = 0
                for i in range(len(dirty_flows)):
                    # if the flow goes through the link
                    if round(b[i * dim * dim * ndirs + edge_id]) == 1:
                        total_traffic = total_traffic + dirty_flows[i][traffic_idx]
                # find the minimal suitable frequency for the amount of traffic

                freq_levels.sort()
             
                exists = False 
                for freq in freq_levels:
                    if  channel_width * ncycles * freq >= total_traffic * max_wire_freq:
                        if freq == 0:
                            wire_delay.append(-1)
                        else:
                            wire_delay.append(float(max_wire_freq) / freq)
                            # print 'Edge ' + str(x) + ',' + str(y) + ' ' + str(dir) + ' utilization ' + str(float(total_traffic * max_wire_freq)/(channel_width * ncycles * freq))
                        exists = True
                        break
                if not exists:
                    wire_delay.append(1)
                    
            wire_delays.append(wire_delay)
            
    return wire_delays

def calculate_optimal_wire_delays_3(fl, n_splits, dim, ndirs, flows, ncycles):
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
                
            # the format is:  node_address dirs
            #                (x,y) local, 1, 2, 3, 4
                
            wire_delay = [x, y, 1]  # FIXME: compute minimal local link power?
            
            for d in range(1, ndirs + 1):
                dir = vnoc_dir[d]
                edge_id = (x * dim + y) * ndirs + dir
                total_traffic = 0
                for i in range(len(dirty_flows) * n_splits):
                    # if the flow goes through the link
                    total_traffic = total_traffic + fl[i * dim * dim * ndirs + edge_id]
                # find the minimal suitable frequency for the amount of traffic

                freq_levels.sort()
             
                exists = False 
                for freq in freq_levels:
                    if  channel_width * ncycles * freq >= total_traffic * max_wire_freq:
                        if freq == 0:
                            wire_delay.append(-1)
                        else:
                            wire_delay.append(float(max_wire_freq) / freq)
                            # print 'Edge ' + str(x) + ',' + str(y) + ' ' + str(dir) + ' utilization ' + str(float(total_traffic * max_wire_freq)/(channel_width * ncycles * freq))
                        exists = True
                        break
                if not exists:
                    wire_delay.append(1)
                    
            wire_delays.append(wire_delay)
            
    return wire_delays


    
def minimize_max_load_fission(min_links, ncycles, flows, dim, ndirs, n_splits):
    
    debug = False
    
    dirty_flows = copy.deepcopy(flows)  # make a copy of the flows in case it is modified
    
    local_edge_traffic = calculate_local_edge_traffic(dirty_flows, dim)
    
    n_flows = len(dirty_flows)
    
    # flows demands
    nsent = copy.deepcopy([f[traffic_idx] for f in dirty_flows])
    
    b = []
    b_type = ''

    fl = []
    fl_type = ''
    fl_ub = []
    
    for i in range(n_flows):
        for j in range(n_splits):
            k = i * n_splits + j
            for x in range(dim):
                for y in range(dim):
                    for dir in range(ndirs):
                        edge_id = (x * dim + y) * ndirs + dir
                        b.append(format_var('b', k, edge_id))
                        b_type = b_type + 'B'
        
                        fl.append(format_var('fl', k, edge_id))
                        fl_type = fl_type + 'I'
                        fl_ub.append(dirty_flows[i][traffic_idx])
                        
    b_lb = [0] * len(b)
    b_ub = [1] * len(b)

    fl_lb = [0] * len(fl)

    q = []
    q_type = ''
    q_ub = []
        
    for i in range(n_flows):
        for j in range(n_splits):
            q.append(format_var('q', i, j))
            q_type = q_type + 'I'
            q_ub.append(dirty_flows[i][traffic_idx])

    q_lb = [0] * len(q)
    #q_ub = [cplex.infinity] * len(q)
    
    if min_links:
        used_edges = []
        used_edges_type = ''
        
        for x in range(dim):
            for y in range(dim):
                for dir in range(ndirs):
                    edge_id = (x * dim + y) * ndirs + dir
                    used_edges.append(format_var('ue', 0, edge_id))
                    used_edges_type = used_edges_type + 'B'
        
        used_edges_ub = [1] * len(used_edges)
        used_edges_lb = [0] * len(used_edges)
    
    rows = []
    my_rhs = []
    my_senses = ''
    
    for x in range(dim):
        for y in range(dim):
            for dir in range(ndirs):
                edge_id = (x * dim + y) * ndirs + dir
                # capacity constraint for each edge
                var_names = []
                coefs = [1] * (n_flows * n_splits)
                
                for i in range(n_flows * n_splits):
                    var_names.append(format_var('fl', i, edge_id))
                
                var_names.append('bound')
                coefs.append(-1)
                
                if debug:
                    continue

                rows.append([var_names, coefs])
                my_rhs.append(0)
                my_senses = my_senses + 'L'
    
    #bound <= channel load
    #rows.append([['bound'], [1]])
    #my_rhs.append(channel_width * ncycles)
    #my_senses = my_senses + 'L'
    
    if min_links:
        for x in range(dim):
            for y in range(dim):
                for dir in range(ndirs):
                    edge_id = (x * dim + y) * ndirs + dir
                    for i in range(n_flows * n_splits):
                        rows.append([[format_var('b', i, edge_id), format_var('ue', 0, edge_id)], [1, -1]])
                        my_rhs.append(0)
                        my_senses = my_senses + 'L'
                         
    
    # unsplitable constraints and flow conservation
    for x in range(dim):
        for y in range(dim):
            for i in range(n_flows):
                # if this is the source of the flow
                # there must be some out going edge
                if x == dirty_flows[i][srcXIdx] and y == dirty_flows[i][srcYIdx]:
                    edgeSrcId = (x * dim + y) * ndirs
                    
                    for j in range(n_splits):
                        k = i * n_splits + j
                        # C1_1
                        var_names = []
                        coefs = [1] * ndirs
                        for dir in range(ndirs):
                            var_names.append(format_var('fl', k, edgeSrcId + dir))
                        
                        var_names.append(format_var('q', i, j))
                        coefs.append(-1)
                        
                        rows.append([var_names, coefs])
                        my_rhs.append(0)
                        my_senses = my_senses + 'E'
                        
                        var_names = []
                        coefs = [1] * ndirs
                        for dir in range(ndirs):
                            var_names.append(format_var('b', k, edgeSrcId + dir))
                            
                        rows.append([var_names, coefs])
                        my_rhs.append(1)
                        my_senses = my_senses + 'E'
                        
                        #C4_1
                        # all incoming edges are invalid
                        for dir in range(0, ndirs):
                            e_in_id = incoming_edge_id(x, y, dir, dim, ndirs)
                            
                            if e_in_id >= 0:
#                                rows.append([[format_var('b', k, e_in_id)], [1]])
#                                my_rhs.append(0)
#                                my_senses = my_senses + 'E'
                                
                                rows.append([[format_var('fl', k, e_in_id)], [1]])
                                my_rhs.append(0)
                                my_senses = my_senses + 'E'
                    
                    continue
                     
                # if this is the destination for the flowfreq_levels
                # we do not need flow conservation
                # and all outgoing edge is invalid
                if x == dirty_flows[i][dstXIdx] and y == dirty_flows[i][dstYIdx]:
                    edgeSrcId = (x * dim + y) * ndirs
                    for j in range(n_splits):
                        k = i * n_splits + j
                        #C1_2
                        # one incoming edge is true
                        var_names = []
                        for dir in range(ndirs):
                            e_in_id = incoming_edge_id(x, y, dir, dim, ndirs)
                            
                            if e_in_id >= 0:
                                var_names.append(format_var('fl', k, e_in_id))
                        
                        coefs = [1] * len(var_names)
                        var_names.append(format_var('q', i, j))
                        coefs.append(-1)
                        rows.append([var_names, coefs])
                        my_rhs.append(0)
                        my_senses = my_senses + 'E'
                        
#                        var_names = []
#                        for dir in range(ndirs):
#                            e_in_id = incoming_edge_id(x, y, dir, dim, ndirs)
#                            
#                            if e_in_id >= 0:
#                                var_names.append(format_var('b', k, e_in_id))
#                        
#                        coefs = [1] * len(var_names)
#                        rows.append([var_names, coefs])
#                        my_rhs.append(1)
#                        my_senses = my_senses + 'E'
                        
                        
                        #C4_2
                        for dir in range(ndirs):
#                            rows.append([[format_var('b', k, edgeSrcId + dir)], [1]])
#                            my_rhs.append(0)
#                            my_senses = my_senses + 'E'
                            
                            rows.append([[format_var('fl', k, edgeSrcId + dir)], [1]])
                            my_rhs.append(0)
                            my_senses = my_senses + 'E'
                        
                    continue
                
                edgeSrcId = (x * dim + y) * ndirs
                    
                for j in range(n_splits):
                    # this is an intermediate hop
#                    var_names = []
#                    coefs = []
#                    k = i * n_splits + j    
#                    for dir in range(ndirs):
#                        #C2
#                        # flows out
#                        var_names.append(format_var('b', k, edgeSrcId + dir))
#                        coefs.append(1)
#                        
#                        # flows in
#                        e_in_id = incoming_edge_id(x, y, dir, dim, ndirs)
#                        
#                        # if this is a valid incoming edge
#                        if e_in_id >= 0:
#                            # do no go back, 180 u-turn
#                            #if dir < 2:  # avoid duplication
#                            e_back_id = (x * dim + y) * ndirs + (3 - dir)
#                            rows.append([[format_var('b', k, e_in_id), format_var('b', k, e_back_id)], [1, 1]])
#                            my_rhs.append(1)
#                            my_senses = my_senses + 'L'
#                            
#                            var_names.append(format_var('b', k, e_in_id))
#                            coefs.append(-1)
#                        
#    
#                    rows.append([var_names, coefs])
#                    my_rhs.append(0)
#                    my_senses = my_senses + 'E'
                    
                    var_names = []
                    coefs = []
                    k = i * n_splits + j    
                    for dir in range(ndirs):
                        #C2
                        # flows out
                        var_names.append(format_var('fl', k, edgeSrcId + dir))
                        coefs.append(1)
                        
                        # flows in
                        e_in_id = incoming_edge_id(x, y, dir, dim, ndirs)
                        
                        # if this is a valid incoming edge
                        if e_in_id >= 0:
                            var_names.append(format_var('fl', k, e_in_id))
                            coefs.append(-1)
                        
                    rows.append([var_names, coefs])
                    my_rhs.append(0)
                    my_senses = my_senses + 'E'
                    
                        # m.constrain(b[i, edgeSrcId:(edgeSrcId+ndirs)].sum() - b[i, e_back_id] == b[i, e_id])
                        
    # edge conditions
    for y in range(dim):
        e_id_w = y * ndirs + west
        e_id_e = ((dim - 1) * dim + y) * ndirs + east
        for i in range(n_flows * n_splits):
            # m.constrain(b[i, e_id_w] == 0)
            # m.constrain(b[i, e_id_e] == 0)

            rows.append([[format_var('fl', i, e_id_w)], [1]])
            my_rhs.append(0)
            my_senses = my_senses + 'E'
            rows.append([[format_var('fl', i, e_id_e)], [1]])
            my_rhs.append(0)
            my_senses = my_senses + 'E'
            
    for x in range(dim):
        e_id_s = x * dim * ndirs + south
        e_id_n = (x * dim + dim - 1) * ndirs + north
        for i in range(n_flows * n_splits):
            # m.constrain(b[i, e_id_n] == 0)
            # m.constrain(b[i, e_id_s] == 0)

            rows.append([[format_var('fl', i, e_id_n)], [1]])
            my_rhs.append(0)
            my_senses = my_senses + 'E'
            rows.append([[format_var('fl', i, e_id_s)], [1]])
            my_rhs.append(0)
            my_senses = my_senses + 'E'

# minimal route
#    for i in range(0, n_flows):
#        fi = dirty_flows[i]
#        hop = abs(fi[srcXIdx] - fi[dstXIdx]) + abs(fi[srcYIdx] - fi[dstYIdx])
#        n_edges = dim * dim * ndirs
#        
#        var_names = []
#        for e in range(n_edges):
#            var_names.append(format_var('b', i, e))
#        coefs = [1] * len(var_names)
#        
#        rows.append([var_names, coefs])
#        my_rhs.append(hop)
#        my_senses = my_senses + 'E'             
        # m.constrain(b[i,:].sum() <= hop)
    
    # pruning the application
#    for i in range(n_flows):
#        f = dirty_flows[i]
#        edges_excluded = edges_not_on_k_paths(f[srcXIdx], f[srcYIdx], f[dstXIdx], f[dstYIdx], 0, dim, ndirs)
#        for e in edges_excluded:
#            rows.append([[format_var('b', i, e)], [1]])
#            my_rhs.append(0)
#            my_senses = my_senses + 'E'
                
    #C3
    for i in range(n_flows):
        for j in range(n_splits):
            k = i * n_splits + j
            #\forall u
            for x in range(dim):
                for y in range(dim):
                    var_names = []
                    coefs = [1] * ndirs
                    for dir in range(ndirs):
                        edge_id = (x * dim + y) * ndirs + dir
                        #C3_1 split traffic bound 
                        rows.append([[format_var('fl', k, edge_id), format_var('b', k, edge_id)], [1, -dirty_flows[i][traffic_idx]]])
                        my_rhs.append(0)
                        my_senses = my_senses + 'L'

                        # all out going edges
                        var_names.append(format_var('b', k, edge_id))
                    
                    #C3_2 unsplittable 
                    rows.append([var_names, coefs])
                    my_rhs.append(1)
                    my_senses = my_senses + 'L'
                    
    #C1_3
    for i in range(n_flows):
        var_names = []
        coefs = [1] * n_splits
        for j in range(n_splits):
            var_names.append(format_var('q', i, j))
        rows.append([var_names, coefs])
        my_rhs.append(dirty_flows[i][traffic_idx])
        my_senses = my_senses + 'E'
                    
    colnames = []
    colnames.extend(b)
    colnames.extend(q)
    colnames.extend(fl)
    colnames.append('bound')
    if min_links:
        colnames.extend(used_edges)
#    colnames.extend(used_edges)
    
    var_lb = []
    var_lb.extend(b_lb)
    var_lb.extend(q_lb)
    var_lb.extend(fl_lb)
    var_lb.append(0)
    if min_links:
        var_lb.extend(used_edges_lb)
        
    
    var_ub = []
    var_ub.extend(b_ub)
    var_ub.extend(q_ub)
    var_ub.extend(fl_ub)
    #var_ub.append(channel_width * ncycles)
    var_ub.append(cplex.infinity)
    if min_links:
        var_ub.extend(used_edges_ub)
    
    # optimal goal
    load_obj = [0] * (len(b) + len(q) + len(fl))
    
    
    
    if min_links:
        load_obj.append(10 * len(used_edges))
        load_obj.extend([1] * len(used_edges))
    else:
        load_obj.append(1)
        
    my_prob = new_cplex_solver()
              
    my_prob.objective.set_sense(my_prob.objective.sense.minimize)

    if min_links:
        var_type = b_type + q_type + fl_type + 'I' + used_edges_type
    else:
       var_type = b_type + q_type + fl_type + 'I'
    
    my_prob.variables.add(obj=load_obj, lb=var_lb, ub=var_ub, types=var_type,
                       names=colnames)

    my_prob.linear_constraints.add(lin_expr=rows, senses=my_senses,
                                rhs=my_rhs)

    # m.minimize((power_levels * s).sum())
    # m.minimize(b[0,:].sum())
    print 'Solving the problem ...'
    my_prob.solve()
    #print
    # solution.get_status() returns an integer code
    #print "Solution status = " , my_prob.solution.get_status(), ":",
    # the following line prints the corresponding string
    #print my_prob.solution.status[my_prob.solution.get_status()]
    #print "Solution value  = ", my_prob.solution.get_objective_value()

#    numcols = my_prob.variables.get_num()
#    numrows = my_prob.linear_constraints.get_num()
#
#    slack = my_prob.solution.get_linear_slacks()
    x = my_prob.solution.get_values()
    
    #check(x[0:len(b)], x[len(b):(len(b) + len(q))], x[(len(b) + len(q)):(len(b) + len(q) + len(fl))], dim, ndirs)
    # for j in range(numrows):
    #    print "Row %d:  Slack = %10f" % (j, slack[j])
#    for j in range(numcols):
#        print colnames[j] + " %d:  Value = %d" % (j, x[j])
    
    # calculate wire frequencies and routes
    #wire_delays = calculate_optimal_wire_delays_3(x[(len(b) + len(q)):(len(b) + len(q) + len(fl))], n_splits, dim, ndirs, flows, ncycles)
    #if min_links:
    #    [b_output, q_ouput] = minimize_path_len_fission(x[-1] +  1, flows, dim, ndirs, n_splits)
    #else:
    b_output = x[:len(b)]
    q_ouput = x[len(b):(len(b) + len(q))]
    
    [routes, edge_traffic] = calculate_routes_2(b_output, q_ouput, n_splits, dim, ndirs, flows, ncycles)
    
    wire_delays = calculate_optimal_wire_delays_2(edge_traffic, dim, ndirs, ncycles)
    
    router_delays = calculate_optimal_router_delays(edge_traffic, local_edge_traffic, dim, ndirs, ncycles)
    
    splitted_flows = []
    for i in range(n_flows):
        f = dirty_flows[i]
        true_n_splits = 0
        for j in range(n_splits):
            if q_ouput[i * n_splits + j] > 0:
                true_n_splits = true_n_splits + 1
        k = 0
        for j in range(n_splits):
            if q_ouput[i * n_splits + j] == 0:
                continue
            f_child = copy.deepcopy(f)
            f_child.append(k)
            f_child.append(true_n_splits)
            f_child[traffic_idx] = q_ouput[i * n_splits + j] 
            splitted_flows.append(f_child)
            k = k + 1
            
    return [routes, wire_delays, router_delays, splitted_flows]


    #return name + '_' + str(idx1) + '_' + str(idx2)

def cal_index(i, edge_id, dim , ndirs):
        return i * dim * dim * ndirs + edge_id
    
def minimize_max_load_fission_zero_pop(min_links, ncycles, flows, dim, ndirs, n_splits):
    
    def get_index(name, idx1, idx2):
        if name == 'fl':
            return cal_index(idx1, idx2, dim, ndirs) + fl_base_idx
        elif name == 'b':
            return cal_index(idx1, idx2, dim, ndirs) + b_base_idx
        elif name == 'ue':
            return idx2 + ue_base_idx
        elif name == 'bound':
            return bound_base_idx
        elif name == 'q':
            return i * n_splits + j + q_base_idx
        else:
            raise Exception('invalide argument')
        
    dirty_flows = copy.deepcopy(flows)  # make a copy of the flows in case it is modified
    
    local_edge_traffic = calculate_local_edge_traffic(dirty_flows, dim)
    
    n_flows = len(dirty_flows)
    
    # flows demands
    nsent = copy.deepcopy([f[traffic_idx] for f in dirty_flows])
    
    #b = []
    b_type = ''

    #fl = []
    fl_type = ''
    fl_ub = []
    
    for i in range(n_flows):
        for j in range(n_splits):
            k = i * n_splits + j
            for x in range(dim):
                for y in range(dim):
                    for dir in range(ndirs):
                        edge_id = (x * dim + y) * ndirs + dir
                        #b.append(format_var('b', k, edge_id))
                        b_type = b_type + 'B'
        
                        #fl.append(format_var('fl', k, edge_id))
                        fl_type = fl_type + 'I'
                        fl_ub.append(dirty_flows[i][traffic_idx])
                        
    b_lb = [0] * len(b_type)
    b_ub = [1] * len(b_type)

    fl_lb = [0] * len(fl_type)

    #q = []
    q_type = ''
    q_ub = []
        
    for i in range(n_flows):
        for j in range(n_splits):
            #q.append(format_var('q', i, j))
            q_type = q_type + 'I'
            q_ub.append(dirty_flows[i][traffic_idx])

    q_lb = [0] * len(q_type)
    #q_ub = [cplex.infinity] * len(q)
    
    if min_links:
        #used_edges = []
        used_edges_type = ''
        
        for x in range(dim):
            for y in range(dim):
                for dir in range(ndirs):
                    edge_id = (x * dim + y) * ndirs + dir
                    #used_edges.append(format_var('ue', 0, edge_id))
                    used_edges_type = used_edges_type + 'B'
        
        used_edges_ub = [1] * len(used_edges_type)
        used_edges_lb = [0] * len(used_edges_type)
    
    #rows = []
    my_rhs = []
    my_senses = ''
    
    b_base_idx = 0
    q_base_idx = len(b_lb)
    fl_base_idx = len(q_lb) + q_base_idx
    bound_base_idx = fl_base_idx + len(fl_lb)
    ue_base_idx = bound_base_idx + 1
    
    entries = []
    current_row = 0
    
    for x in range(dim):
        for y in range(dim):
            for dir in range(ndirs):
                edge_id = (x * dim + y) * ndirs + dir
                # capacity constraint for each edge
                #var_names = []
                #coefs = [1] * (n_flows * n_splits)
                
                for i in range(n_flows * n_splits):
                    entries.append([current_row, get_index('fl', i, edge_id), 1])
                    #var_names.append(format_var('fl', i, edge_id))
                
                entries.append([current_row, get_index('bound', 0, 0), -1])
                #var_names.append('bound')
                #coefs.append(-1)
                
                #rows.append([var_names, coefs])
                my_rhs.append(0)
                my_senses = my_senses + 'L'
                current_row = current_row + 1
    
    #bound <= channel load
    #rows.append([['bound'], [1]])
    #my_rhs.append(channel_width * ncycles)
    #my_senses = my_senses + 'L'
    
    if min_links:
        for x in range(dim):
            for y in range(dim):
                for dir in range(ndirs):
                    edge_id = (x * dim + y) * ndirs + dir
                    for i in range(n_flows * n_splits):
                        entries.append([current_row, get_index('b', i, edge_id), 1])
                        entries.append([current_row, get_index('ue', 0, edge_id), -1])
                        #rows.append([[format_var('b', i, edge_id), format_var('ue', 0, edge_id)], [1, -1]])
                        my_rhs.append(0)
                        my_senses = my_senses + 'L'
                        current_row = current_row + 1
                         
    
    # unsplitable constraints and flow conservation
    for x in range(dim):
        for y in range(dim):
            for i in range(n_flows):
                # if this is the source of the flow
                # there must be some out going edge
                if x == dirty_flows[i][srcXIdx] and y == dirty_flows[i][srcYIdx]:
                    edgeSrcId = (x * dim + y) * ndirs
                    
                    for j in range(n_splits):
                        k = i * n_splits + j
                        # C1_1
                        #var_names = []
                        #coefs = [1] * ndirs
                        for dir in range(ndirs):
                            entries.append([current_row, get_index('fl', k, edgeSrcId + dir), 1])
                            #var_names.append(format_var('fl', k, edgeSrcId + dir))
                        
                        entries.append([current_row, get_index('q', i, j), -1])
                        #var_names.append(format_var('q', i, j))
                        #coefs.append(-1)
                        
                        #rows.append([var_names, coefs])
                        my_rhs.append(0)
                        my_senses = my_senses + 'E'
                        current_row = current_row + 1
                        
                        #var_names = []
                        #coefs = [1] * ndirs
                        for dir in range(ndirs):
                            entries.append([current_row, get_index('b', k, edgeSrcId + dir), 1])
                            #var_names.append(format_var('b', k, edgeSrcId + dir))
                            
                        #rows.append([var_names, coefs])
                        my_rhs.append(1)
                        my_senses = my_senses + 'E'
                        current_row = current_row + 1
                        
                        #C4_1
                        # all incoming edges are invalid
                        for dir in range(0, ndirs):
                            e_in_id = incoming_edge_id(x, y, dir, dim, ndirs)
                            
                            if e_in_id >= 0:
#                                rows.append([[format_var('b', k, e_in_id)], [1]])
#                                my_rhs.append(0)
#                                my_senses = my_senses + 'E'
                                
                                #rows.append([[format_var('fl', k, e_in_id)], [1]])
                                entries.append([current_row, get_index('fl', k, e_in_id), 1])
                                my_rhs.append(0)
                                my_senses = my_senses + 'E'
                                current_row = current_row + 1
                    
                    continue
                     
                # if this is the destination for the flowfreq_levels
                # we do not need flow conservation
                # and all outgoing edge is invalid
                if x == dirty_flows[i][dstXIdx] and y == dirty_flows[i][dstYIdx]:
                    edgeSrcId = (x * dim + y) * ndirs
                    for j in range(n_splits):
                        k = i * n_splits + j
                        #C1_2
                        # one incoming edge is true
                        #var_names = []
                        for dir in range(ndirs):
                            e_in_id = incoming_edge_id(x, y, dir, dim, ndirs)
                            
                            if e_in_id >= 0:
                                #var_names.append(format_var('fl', k, e_in_id))
                                entries.append([current_row, get_index('fl', k, e_in_id), 1])
                        
                        #coefs = [1] * len(var_names)
                        #var_names.append(format_var('q', i, j))
                        #coefs.append(-1)
                        entries.append([current_row, get_index('q', i, j), -1])
                        
                        #rows.append([var_names, coefs])
                        my_rhs.append(0)
                        my_senses = my_senses + 'E'
                        current_row = current_row + 1
                        
#                        var_names = []
#                        for dir in range(ndirs):
#                            e_in_id = incoming_edge_id(x, y, dir, dim, ndirs)
#                            
#                            if e_in_id >= 0:
#                                var_names.append(format_var('b', k, e_in_id))
#                        
#                        coefs = [1] * len(var_names)
#                        rows.append([var_names, coefs])
#                        my_rhs.append(1)
#                        my_senses = my_senses + 'E'
                        
                        
                        #C4_2
                        for dir in range(ndirs):
#                            rows.append([[format_var('b', k, edgeSrcId + dir)], [1]])
#                            my_rhs.append(0)
#                            my_senses = my_senses + 'E'
                            
                            #rows.append([[format_var('fl', k, edgeSrcId + dir)], [1]])
                            entries.append([current_row, get_index('fl', k, edgeSrcId + dir), 1])
                            my_rhs.append(0)
                            my_senses = my_senses + 'E'
                            current_row = current_row + 1
                        
                    continue
                
                edgeSrcId = (x * dim + y) * ndirs
                    
                for j in range(n_splits):
                    # this is an intermediate hop
#                    var_names = []
#                    coefs = []
#                    k = i * n_splits + j    
#                    for dir in range(ndirs):
#                        #C2
#                        # flows out
#                        var_names.append(format_var('b', k, edgeSrcId + dir))
#                        coefs.append(1)
#                        
#                        # flows in
#                        e_in_id = incoming_edge_id(x, y, dir, dim, ndirs)
#                        
#                        # if this is a valid incoming edge
#                        if e_in_id >= 0:
#                            # do no go back, 180 u-turn
#                            #if dir < 2:  # avoid duplication
#                            e_back_id = (x * dim + y) * ndirs + (3 - dir)
#                            rows.append([[format_var('b', k, e_in_id), format_var('b', k, e_back_id)], [1, 1]])
#                            my_rhs.append(1)
#                            my_senses = my_senses + 'L'
#                            
#                            var_names.append(format_var('b', k, e_in_id))
#                            coefs.append(-1)
#                        
#    
#                    rows.append([var_names, coefs])
#                    my_rhs.append(0)
#                    my_senses = my_senses + 'E'
                    
                    #var_names = []
                    #coefs = []
                    k = i * n_splits + j    
                    for dir in range(ndirs):
                        #C2
                        # flows out
                        #var_names.append(format_var('fl', k, edgeSrcId + dir))
                        #coefs.append(1)
                        entries.append([current_row, get_index('fl', k, edgeSrcId + dir), 1])
                        
                        # flows in
                        e_in_id = incoming_edge_id(x, y, dir, dim, ndirs)
                        
                        # if this is a valid incoming edge
                        if e_in_id >= 0:
                            #var_names.append(format_var('fl', k, e_in_id))
                            #coefs.append(-1)
                            entries.append([current_row, get_index('fl', k, e_in_id),  -1])
                        
                    #rows.append([var_names, coefs])
                    my_rhs.append(0)
                    my_senses = my_senses + 'E'
                    current_row = current_row + 1
                    
                        # m.constrain(b[i, edgeSrcId:(edgeSrcId+ndirs)].sum() - b[i, e_back_id] == b[i, e_id])
                        
    # edge conditions
    for y in range(dim):
        e_id_w = y * ndirs + west
        e_id_e = ((dim - 1) * dim + y) * ndirs + east
        for i in range(n_flows * n_splits):
            # m.constrain(b[i, e_id_w] == 0)
            # m.constrain(b[i, e_id_e] == 0)

            #rows.append([[format_var('fl', i, e_id_w)], [1]])
            entries.append([current_row, get_index('fl', i, e_id_w), 1])
            my_rhs.append(0)
            my_senses = my_senses + 'E'
            current_row = current_row + 1
            #rows.append([[format_var('fl', i, e_id_e)], [1]])
            entries.append([current_row, get_index('fl', i, e_id_e), 1])
            my_rhs.append(0)
            my_senses = my_senses + 'E'
            current_row = current_row + 1
            
    for x in range(dim):
        e_id_s = x * dim * ndirs + south
        e_id_n = (x * dim + dim - 1) * ndirs + north
        for i in range(n_flows * n_splits):
            # m.constrain(b[i, e_id_n] == 0)
            # m.constrain(b[i, e_id_s] == 0)

            #rows.append([[format_var('fl', i, e_id_n)], [1]])
            entries.append([current_row, get_index('fl', i, e_id_n), 1])
            my_rhs.append(0)
            my_senses = my_senses + 'E'
            current_row = current_row + 1
            #rows.append([[format_var('fl', i, e_id_s)], [1]])
            entries.append([current_row, get_index('fl', i, e_id_s), 1])
            my_rhs.append(0)
            my_senses = my_senses + 'E'
            current_row = current_row + 1

    #C3
    for i in range(n_flows):
        for j in range(n_splits):
            k = i * n_splits + j
            #\forall u
            for x in range(dim):
                for y in range(dim):
                    #var_names = []
                    #coefs = [1] * ndirs
                    for dir in range(ndirs):
                        edge_id = (x * dim + y) * ndirs + dir
                        #C3_1 split traffic bound 
                        #rows.append([[format_var('fl', k, edge_id), format_var('b', k, edge_id)], [1, -dirty_flows[i][traffic_idx]]])
                        entries.append([current_row, get_index('fl', k, edge_id), 1])
                        entries.append([current_row, get_index('b', k, edge_id), -dirty_flows[i][traffic_idx]])
                        my_rhs.append(0)
                        my_senses = my_senses + 'L'
                        current_row = current_row + 1
                    
                    for dir in range(ndirs):
                        edge_id = (x * dim + y) * ndirs + dir
                        # all out going edges
                        #var_names.append(format_var('b', k, edge_id))
                        entries.append([current_row, get_index('b', k, edge_id), 1])
                    
                    #C3_2 unsplittable 
                    #rows.append([var_names, coefs])
                    my_rhs.append(1)
                    my_senses = my_senses + 'L'
                    current_row = current_row + 1
                    
    #C1_3
    for i in range(n_flows):
        #var_names = []
        #coefs = [1] * n_splits
        for j in range(n_splits):
            #var_names.append(format_var('q', i, j))
            entries.append([current_row, get_index('q', i, j), 1])
        #rows.append([var_names, coefs])
        my_rhs.append(dirty_flows[i][traffic_idx])
        my_senses = my_senses + 'E'
        current_row = current_row + 1
    
    assert(current_row == len(my_rhs))
                    
#    colnames = []
#    colnames.extend(b)
#    colnames.extend(q)
#    colnames.extend(fl)
#    colnames.append('bound')
#    if min_links:
#        colnames.extend(used_edges)
#    colnames.extend(used_edges)
    
    var_lb = []
    var_lb.extend(b_lb)
    var_lb.extend(q_lb)
    var_lb.extend(fl_lb)
    var_lb.append(0)
    if min_links:
        var_lb.extend(used_edges_lb)
        
    
    var_ub = []
    var_ub.extend(b_ub)
    var_ub.extend(q_ub)
    var_ub.extend(fl_ub)
    #var_ub.append(channel_width * ncycles)
    var_ub.append(cplex.infinity)
    if min_links:
        var_ub.extend(used_edges_ub)
    
    # optimal goal
    load_obj = [0] * (len(b_type) + len(q_type) + len(fl_type))
    
    
    
    if min_links:
        load_obj.append(10 * len(used_edges))
        load_obj.extend([1] * len(used_edges))
    else:
        load_obj.append(1)
        
    my_prob = new_cplex_solver()
              
    my_prob.objective.set_sense(my_prob.objective.sense.minimize)

    if min_links:
        var_type = b_type + q_type + fl_type + 'I' + used_edges_type
    else:
       var_type = b_type + q_type + fl_type + 'I'
    
    my_prob.variables.add(obj=load_obj, lb=var_lb, ub=var_ub, types=var_type)

    my_prob.linear_constraints.add(rhs = my_rhs, senses = my_senses)
    #my_prob.linear_constraints.add(lin_expr=rows, senses=my_senses, rhs=my_rhs)
    my_prob.linear_constraints.set_coefficients(entries)

    # m.minimize((power_levels * s).sum())
    # m.minimize(b[0,:].sum())
    print 'Solving the problem ...'
    my_prob.solve()
    #print
    # solution.get_status() returns an integer code
    #print "Solution status = " , my_prob.solution.get_status(), ":",
    # the following line prints the corresponding string
    #print my_prob.solution.status[my_prob.solution.get_status()]
    #print "Solution value  = ", my_prob.solution.get_objective_value()

#    numcols = my_prob.variables.get_num()
#    numrows = my_prob.linear_constraints.get_num()
#
#    slack = my_prob.solution.get_linear_slacks()
    x = my_prob.solution.get_values()
    
    #check(x[0:len(b)], x[len(b):(len(b) + len(q))], x[(len(b) + len(q)):(len(b) + len(q) + len(fl))], dim, ndirs)
    # for j in range(numrows):
    #    print "Row %d:  Slack = %10f" % (j, slack[j])
#    for j in range(numcols):((currentX * dim + currentY) * ndirs)
#        print colnames[j] + " %d:  Value = %d" % (j, x[j])
    
    # calculate wire frequencies and routes
    #wire_delays = calculate_optimal_wire_delays_3(x[(len(b) + len(q)):(len(b) + len(q) + len(fl))], n_splits, dim, ndirs, flows, ncycles)
    #if min_links:
    #    [b_output, q_ouput] = minimize_path_len_fission(x[-1] +  1, flows, dim, ndirs, n_splits)
    #else:
    b_output = x[:len(b_type)]
    q_ouput = x[len(b_type):(len(b_type) + len(q_type))]
    
    [routes, edge_traffic] = calculate_routes_2(b_output, q_ouput, n_splits, dim, ndirs, flows, ncycles)
    
    wire_delays = calculate_optimal_wire_delays_2(edge_traffic, dim, ndirs, ncycles)
    
    router_delays = calculate_optimal_router_delays(edge_traffic, local_edge_traffic, dim, ndirs, ncycles)
    
    splitted_flows = []
    for i in range(n_flows):
        f = dirty_flows[i]
        true_n_splits = 0
        for j in range(n_splits):
            if q_ouput[i * n_splits + j] > 0:
                true_n_splits = true_n_splits + 1
        k = 0
        for j in range(n_splits):
            if q_ouput[i * n_splits + j] == 0:
                continue
            f_child = copy.deepcopy(f)
            f_child.append(k)
            f_child.append(true_n_splits)
            f_child[traffic_idx] = q_ouput[i * n_splits + j] 
            splitted_flows.append(f_child)
            k = k + 1
            
    return [routes, wire_delays, router_delays, splitted_flows]

def check(b, q, fl, dim, ndirs):
    for x in range(dim):
        for y in range(dim):
            for dir in range(ndirs):
                edge_id = (x * dim + y) * ndirs + dir
                for i in range(len(q)):
                    if fl[i * dim * dim * ndirs + edge_id] != b[i * dim * dim * ndirs + edge_id] * q[i]:
                        print x, y, dir, i, fl[i * dim * dim * ndirs + edge_id], b[i * dim * dim * ndirs + edge_id], q[i]
                    
def minimize_max_load(min_links, ncycles, flows, dim, ndirs):
    
    debug = False
    
    dirty_flows = copy.deepcopy(flows)  # make a copy of the flows in case it is modified
    
    n_flows = len(dirty_flows)
    
    # flows demands
    nsent = copy.deepcopy([f[traffic_idx] for f in dirty_flows])
    
    b = []
    b_type = ''

    for i in range(n_flows):
        for x in range(dim):
            for y in range(dim):
                for dir in range(ndirs):
                    edge_id = (x * dim + y) * ndirs + dir
                    b.append(format_var('b', i, edge_id))
                    b_type = b_type + 'B'
                    
    b_lb = [0] * len(b)
    b_ub = [1] * len(b)
    
    if min_links:
        used_edges = []
        used_edges_type = ''
         
        for x in range(dim):
            for y in range(dim):
                for dir in range(ndirs):
                    edge_id = (x * dim + y) * ndirs + dir
                    used_edges.append(format_var('used_edges', 0, edge_id))
                    used_edges_type = used_edges_type + 'B'
        
        used_edges_lb = [0] * len(used_edges)
        used_edges_ub = [1] * len(used_edges)
    
    rows = []
    my_rhs = []
    my_senses = ''
    
    for x in range(dim):
        for y in range(dim):
            for dir in range(ndirs):
                edge_id = (x * dim + y) * ndirs + dir
                # capacity constraint for each edge
                # m.constrain((nsent * b[:, edge_id])  <= bound <= channel_width * ncycles]) 
                var_names = []
                coefs = []
                coefs.extend(nsent)
                
                for i in range(n_flows):
                    var_names.append(format_var('b', i, edge_id))
                
                var_names.append('bound')
                coefs.append(-1)
                
                if debug:
                    continue

                rows.append([var_names, coefs])
                my_rhs.append(0)
                my_senses = my_senses + 'L'
    
    #bound <= channel load
#    rows.append([['bound'], [1]])
#    my_rhs.append(channel_width * ncycles)
#    my_senses = my_senses + 'L'
    
    # unsplitable constraints and flow conservation
    for x in range(dim):
        for y in range(dim):
            for i in range(n_flows):
                # if this is the source of the flow
                # there must be some out going edge
                if x == dirty_flows[i][srcXIdx] and y == dirty_flows[i][srcYIdx]:
                    edgeSrcId = (x * dim + y) * ndirs
                    var_names = []
                    coefs = [1] * ndirs
                    for dir in range(ndirs):
                        var_names.append(format_var('b', i, edgeSrcId + dir))
                        
                    rows.append([var_names, coefs])
                    my_rhs.append(1)
                    my_senses = my_senses + 'E'
                    # m.constrain(b[i, edgeSoutrcId:(edgeSrcId+ndirs)].sum() == 1)
                    
                    # all incoming edges are invalid
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
                        # m.constrain(b[i, edgeSrcId + dir] == 0)
                    # m.constrain(b[i, edgeSrcId:(edgeSrcId+ndirs)].sum() == 0)
                    
                    # one incoming edge is true
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
                
                # this is an intermediate hop
                var_names = []
                coefs = []
                edgeSrcId = (x * dim + y) * ndirs
                    
                for dir in range(ndirs):
                    # flows out
                    var_names.append(format_var('b', i, edgeSrcId + dir))
                    coefs.append(1)
                    
                    # flows in
                    e_in_id = incoming_edge_id(x, y, dir, dim, ndirs)
                    
                    # if this is a valid incoming edge
                    if e_in_id >= 0:
                        # do no go back
                        #if dir < 2:  # avoid duplication
                        e_back_id = (x * dim + y) * ndirs + (3 - dir)
                        rows.append([[format_var('b', i, e_in_id), format_var('b', i, e_back_id)], [1, 1]])
                        my_rhs.append(1)
                        my_senses = my_senses + 'L'
                        
                        var_names.append(format_var('b', i, e_in_id))
                        coefs.append(-1)
                    

                rows.append([var_names, coefs])
                my_rhs.append(0)
                my_senses = my_senses + 'E'
                        # m.constrain(b[i, edgeSrcId:(edgeSrcId+ndirs)].sum() - b[i, e_back_id] == b[i, e_id])
                        
    # edge conditions
    for y in range(dim):
        e_id_w = y * ndirs + west
        e_id_e = ((dim - 1) * dim + y) * ndirs + east
        for i in range(n_flows):
            # m.constrain(b[i, e_id_w] == 0)
            # m.constrain(b[i, e_id_e] == 0)

            rows.append([[format_var('b', i, e_id_w)], [1]])
            my_rhs.append(0)
            my_senses = my_senses + 'E'
            rows.append([[format_var('b', i, e_id_e)], [1]])
            my_rhs.append(0)
            my_senses = my_senses + 'E'
            
    for x in range(dim):
        e_id_s = x * dim * ndirs + south
        e_id_n = (x * dim + dim - 1) * ndirs + north
        for i in range(n_flows):
            # m.constrain(b[i, e_id_n] == 0)
            # m.constrain(b[i, e_id_s] == 0)

            rows.append([[format_var('b', i, e_id_n)], [1]])
            my_rhs.append(0)
            my_senses = my_senses + 'E'
            rows.append([[format_var('b', i, e_id_s)], [1]])
            my_rhs.append(0)
            my_senses = my_senses + 'E'
            
    # minimal route
#    for i in range(0, n_flows):
#        fi = dirty_flows[i]
#        hop = abs(fi[srcXIdx] - fi[dstXIdx]) + abs(fi[srcYIdx] - fi[dstYIdx])
#        n_edges = dim * dim * ndirs
#        
#        var_names = []
#        for e in range(n_edges):
#            var_names.append(format_var('b', i, e))
#        coefs = [1] * len(var_names)
#        
#        rows.append([var_names, coefs])
#        my_rhs.append(hop)
#        my_senses = my_senses + 'E'             
#        # m.constrain(b[i,:].sum() <= hop)
#    
#    # pruning the application
#    for i in range(n_flows):
#        f = dirty_flows[i]
#        edges_excluded = edges_not_on_k_paths(f[srcXIdx], f[srcYIdx], f[dstXIdx], f[dstYIdx], 0, dim, ndirs)
#        for e in edges_excluded:
#            rows.append([[format_var('b', i, e)], [1]])
#            my_rhs.append(0)
#            my_senses = my_senses + 'E'
    
    if min_links:
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
    
    colnames = []
    colnames.extend(b)
    colnames.append('bound')
    colnames.extend(used_edges)
    
    var_lb = []
    var_lb.extend(b_lb)
    var_lb.append(0)
    if min_links:
        var_lb.extend(used_edges_lb)
    
    var_ub = []
    var_ub.extend(b_ub)
    #var_ub.append(channel_width * ncycles)
    var_ub.append(cplex.infinity)
    if min_links:
        var_ub.extend(used_edges_ub)
    
    # optimal goal
    load_obj = [0] * len(b)
    
    if min_links:
        load_obj.append(10 * len(used_edges))
        load_obj.extend([1] * len(used_edges))
    else:
        load_obj.append(1)
        
    my_prob = new_cplex_solver()
              
    my_prob.objective.set_sense(my_prob.objective.sense.minimize)

    var_type = b_type + 'C'
    
    my_prob.variables.add(obj=load_obj, lb=var_lb, ub=var_ub, types=var_type,
                       names=colnames)

    my_prob.linear_constraints.add(lin_expr=rows, senses=my_senses,
                                rhs=my_rhs)

    print 'Solving the problem ...'
    my_prob.solve()
    #print
    # solution.get_status() returns an integer code
    #print "Solution status = " , my_prob.solution.get_status(), ":",
    # the following line prints the corresponding string
    #print my_prob.solution.status[my_prob.solution.get_status()]
    #print "Solution value  = ", my_prob.solution.get_objective_value()

#    numcols = my_prob.variables.get_num()
#    numrows = my_prob.linear_constraints.get_num()
#
#    slack = my_prob.solution.get_linear_slacks()
    x = my_prob.solution.get_values()
    

    # for j in range(numrows):
    #    print "Row %d:  Slack = %10f" % (j, slack[j])
#    for j in range(numcols):
#        print colnames[j] + " %d:  Value = %d" % (j, x[j])
    
    # calculate wire frequencies and routes
#    if min_links:
#        b_output = minimize_used_links_1(x[-1], flows, dim, ndirs)
#    else:
    b_output = x[:len(b)]
        
    [routes, edge_traffic] = calculate_routes_2(b_output, nsent, 1, dim, ndirs, flows, ncycles)
    
    wire_delays = calculate_optimal_wire_delays(b_output, dim, ndirs, flows, ncycles)
    
    #routes = calculate_routes(b_output, dim, ndirs, flows, ncycles)
    
    local_edge_traffic = calculate_local_edge_traffic(dirty_flows, dim)
    
    #edge_traffic = [0] * (dim * dim * ndirs)
    
    router_delays = calculate_optimal_router_delays(edge_traffic, local_edge_traffic, dim, ndirs, ncycles)

    return [routes, wire_delays, router_delays]
                        
def optimal_routes_freqs(ncycles, flows, dim, ndirs):
    
    debug = False
    
    dirty_flows = copy.deepcopy(flows)  # make a copy of the flows in case it is modified
    
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
                # for i in range(n_flows):
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
    
    power_levels = [(link.calc_dynamic_energy(channel_width_bits / 2, opt[0]) / (channel_width * ncycles * opt[1]) * opt[1] + link.get_static_power(opt[0])) for opt in wire_config_opts[1:]]
    power_levels.insert(0, 0)
    
    rows = []
    my_rhs = []
    my_senses = ''
    
    for x in range(dim):
        for y in range(dim):
            for dir in range(ndirs):
                edge_id = (x * dim + y) * ndirs + dir
                # capacity constraint for each edge
                # m.constrain((nsent * b[:, edge_id]) * OneGhz <= channel_width * ncycles * edge_freqs[edge_id]) 
                var_names = []
                coefs = []
                coefs.extend([(item * OneGhz) for item in nsent])
                
                for i in range(0, n_flows):
                    var_names.append(format_var('b', i, edge_id))
                    
                var_names.append(format_var('edge_freqs', 0, edge_id))
                coefs.append(-channel_width * ncycles)
                
                if debug:
                    continue

                rows.append([var_names, coefs])
                my_rhs.append(0)
                my_senses = my_senses + 'L'
                    
    
    # unsplitable constraints and flow conservation
    # for i in range(0, n_flows):
    #    for x in range(0, dim):
    #        for y in range(0, dim):
    #            edgeSrcId = (x * dim + y) * ndirs
    #            m.constrain(b[i, edgeSrcId:(edgeSrcId+ndirs)].sum() <= 1)
    
    # unsplitable constraints and flow conservation
    for x in range(dim):
        for y in range(dim):
            for i in range(n_flows):
                # if this is the source of the flow
                # there must be some out going edge
                if x == dirty_flows[i][srcXIdx] and y == dirty_flows[i][srcYIdx]:
                    edgeSrcId = (x * dim + y) * ndirs
                    var_names = []
                    coefs = [1] * ndirs
                    for dir in range(ndirs):
                        var_names.append(format_var('b', i, edgeSrcId + dir))
                        
                    rows.append([var_names, coefs])
                    my_rhs.append(1)
                    my_senses = my_senses + 'E'
                    # m.constrain(b[i, edgeSoutrcId:(edgeSrcId+ndirs)].sum() == 1)
                    
                    # all incoming edges are invalid
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
                        # m.constrain(b[i, edgeSrcId + dir] == 0)
                    # m.constrain(b[i, edgeSrcId:(edgeSrcId+ndirs)].sum() == 0)
                    
                    # one incoming edge is true
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
                
                # this is an intermediate hop
                var_names = []
                coefs = []
                edgeSrcId = (x * dim + y) * ndirs
                    
                for dir in range(ndirs):
                    # flows out
                    var_names.append(format_var('b', i, edgeSrcId + dir))
                    coefs.append(1)
                    
                    # flows in
                    e_in_id = incoming_edge_id(x, y, dir, dim, ndirs)
                    
                    # if this is a valid incoming edge
                    if e_in_id >= 0:
                        # do no go back
#                        if dir < 2:  # avoid duplication
                        e_back_id = (x * dim + y) * ndirs + (3 - dir)
                        rows.append([[format_var('b', i, e_in_id), format_var('b', i, e_back_id)], [1, 1]])
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
                    # flows out
                    var_names.append(format_var('b', i, edgeSrcId + dir))
                    coefs.append(1)
                 
                rows.append([var_names, coefs])
                my_rhs.append(1)
                my_senses = my_senses + 'L'
                        
    # edge conditions
    for y in range(dim):
        e_id_w = y * ndirs + west
        e_id_e = ((dim - 1) * dim + y) * ndirs + east
        for i in range(n_flows):
            # m.constrain(b[i, e_id_w] == 0)
            # m.constrain(b[i, e_id_e] == 0)

            rows.append([[format_var('b', i, e_id_w)], [1]])
            my_rhs.append(0)
            my_senses = my_senses + 'E'
            rows.append([[format_var('b', i, e_id_e)], [1]])
            my_rhs.append(0)
            my_senses = my_senses + 'E'
            
    for x in range(0, dim):
        e_id_s = x * dim * ndirs + south
        e_id_n = (x * dim + dim - 1) * ndirs + north
        for i in range(n_flows):
            # m.constrain(b[i, e_id_n] == 0)
            # m.constrain(b[i, e_id_s] == 0)

            rows.append([[format_var('b', i, e_id_n)], [1]])
            my_rhs.append(0)
            my_senses = my_senses + 'E'
            rows.append([[format_var('b', i, e_id_s)], [1]])
            my_rhs.append(0)
            my_senses = my_senses + 'E'
            
    # minimal route
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
        # m.constrain(b[i,:].sum() <= hop)
    
        
    # single frequency constraints
    for x in range(dim):
        for y in range(dim):
            for dir in range(ndirs):
                edge_id = (x * dim + y) * ndirs + dir
                # each edge is allowed at most one freq
                # m.constrain(s[:, edge_id].sum() <= 1)
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
                
                # coefs = [1] * len(var_names)
                coefs = [-1]
                coefs.extend(copy.deepcopy(freq_levels))
                
                
                if debug:
                    continue
            
                rows.append([var_names, coefs])
                my_rhs.append(0)
                my_senses = my_senses + 'E'
        
                # m.constrain(edge_freqs[edge_id] <= freq_levels * s[:, edge_id])
    # pruning the application
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
    
    # optimal goal
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
        
    my_prob = new_cplex_solver()
              
    my_prob.objective.set_sense(my_prob.objective.sense.minimize)

    var_type = b_type + edge_freqs_type + s_type
    
    my_prob.variables.add(obj=power_obj, lb=var_lb, ub=var_ub, types=var_type,
                       names=colnames)

    my_prob.linear_constraints.add(lin_expr=rows, senses=my_senses,
                                rhs=my_rhs)

    # m.minimize((power_levels * s).sum())
    # m.minimize(b[0,:].sum())
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
    x = my_prob.solution.get_values()
    

    # for j in range(numrows):
    #    print "Row %d:  Slack = %10f" % (j, slack[j])
    for j in range(numcols):
        print colnames[j] + " %d:  Value = %d" % (j, x[j])
    
    # calculate wire frequencies and routes
    wire_delays = convert_to_wire_delays(x[len(b):(len(b) + len(edge_freqs))], dim, ndirs)
    
    routes = calculate_routes(x[0:len(b)], dim, ndirs, flows, ncycles)

    local_edge_traffic = calculate_local_edge_traffic(dirty_flows, dim)
    
    router_delays = calculate_optimal_router_delays(edge_traffic, local_edge_traffic, dim, ndirs, ncycles)

    return [routes, wire_delays, router_delays]



def minimize_used_links(ncycles, flows, dim, ndirs):
    
    debug = False
    
    dirty_flows = copy.deepcopy(flows)  # make a copy of the flows in case it is modified
    
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
                # capacity constraint for each edge
                # m.constrain((nsent * b[:, edge_id])  <= channel_width * ncycles]) 
                var_names = []
                coefs = []
                coefs.extend(nsent)
                
                for i in range(n_flows):
                    var_names.append(format_var('b', i, edge_id))
                    
                if debug:
                    continue

                rows.append([var_names, coefs])
                my_rhs.append(channel_width * ncycles)
                my_senses = my_senses + 'L'
                    
    
    # unsplitable constraints and flow conservation
    # for i in range(0, n_flows):
    #    for x in range(0, dim):
    #        for y in range(0, dim):
    #            edgeSrcId = (x * dim + y) * ndirs
    #            m.constrain(b[i, edgeSrcId:(edgeSrcId+ndirs)].sum() <= 1)
    
    # unsplitable constraints and flow conservation
    for x in range(dim):
        for y in range(dim):
            for i in range(n_flows):
                # if this is the source of the flow
                # there must be some out going edge
                if x == dirty_flows[i][srcXIdx] and y == dirty_flows[i][srcYIdx]:
                    edgeSrcId = (x * dim + y) * ndirs
                    var_names = []
                    coefs = [1] * ndirs
                    for dir in range(ndirs):
                        var_names.append(format_var('b', i, edgeSrcId + dir))
                        
                    rows.append([var_names, coefs])
                    my_rhs.append(1)
                    my_senses = my_senses + 'E'
                    # m.constrain(b[i, edgeSoutrcId:(edgeSrcId+ndirs)].sum() == 1)edge_freqs
                    
                    # all incoming edges are invalid
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
                        # m.constrain(b[i, edgeSrcId + dir] == 0)
                    # m.constrain(b[i, edgeSrcId:(edgeSrcId+ndirs)].sum() == 0)
                    
                    # one incoming edge is true
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
                
                # this is an intermediate hop
                var_names = []
                coefs = []
                edgeSrcId = (x * dim + y) * ndirs
                    
                for dir in range(ndirs):
                    # flows out
                    var_names.append(format_var('b', i, edgeSrcId + dir))
                    coefs.append(1)
                    
                    # flows in
                    e_in_id = incoming_edge_id(x, y, dir, dim, ndirs)
                    
                    # if this is a valid incoming edge
                    if e_in_id >= 0:
                        # do no go back
                        #if dir < 2:  # avoid duplication
                        e_back_id = (x * dim + y) * ndirs + (3 - dir)
                        rows.append([[format_var('b', i, e_in_id), format_var('b', i, e_back_id)], [1, 1]])
                        my_rhs.append(1)
                        my_senses = my_senses + 'L'
                        
                        var_names.append(format_var('b', i, e_in_id))
                        coefs.append(-1)
                    

                rows.append([var_names, coefs])
                my_rhs.append(0)
                my_senses = my_senses + 'E'
                        # m.constrain(b[i, edgeSrcId:(edgeSrcId+ndirs)].sum() - b[i, e_back_id] == b[i, e_id])
                        
    # edge conditions
    for y in range(dim):
        e_id_w = y * ndirs + west
        e_id_e = ((dim - 1) * dim + y) * ndirs + east
        for i in range(n_flows):
            # m.constrain(b[i, e_id_w] == 0)
            # m.constrain(b[i, e_id_e] == 0)

            rows.append([[format_var('b', i, e_id_w)], [1]])
            my_rhs.append(0)
            my_senses = my_senses + 'E'
            rows.append([[format_var('b', i, e_id_e)], [1]])
            my_rhs.append(0)
            my_senses = my_senses + 'E'
            
    for x in range(0, dim):
        e_id_s = x * dim * ndirs + south
        e_id_n = (x * dim + dim - 1) * ndirs + north
        for i in range(n_flows):
            # m.constrain(b[i, e_id_n] == 0)
            # m.constrain(b[i, e_id_s] == 0)

            rows.append([[format_var('b', i, e_id_n)], [1]])
            my_rhs.append(0)
            my_senses = my_senses + 'E'
            rows.append([[format_var('b', i, e_id_s)], [1]])
            my_rhs.append(0)
            my_senses = my_senses + 'E'
            
    # minimal route
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
        # m.constrain(b[i,:].sum() <= hop)
    
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
        
    # pruning the application
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
    
    # optimal goal
    link_obj = []
    
    link_obj = [0] * len(b)
    
    link_obj.extend([1] * len(used_edges))
        
    my_prob = new_cplex_solver()
              
    my_prob.objective.set_sense(my_prob.objective.sense.minimize)

    var_type = b_type + used_edges_type
    
    my_prob.variables.add(obj=link_obj, lb=var_lb, ub=var_ub, types=var_type,
                       names=colnames)

    my_prob.linear_constraints.add(lin_expr=rows, senses=my_senses,
                                rhs=my_rhs)

    # m.minimize((power_levels * s).sum())
    # m.minimize(b[0,:].sum())
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
    x = my_prob.solution.get_values()
    

    # for j in range(numrows):
    #    print "Row %d:  Slack = %10f" % (j, slack[j])
    for j in range(numcols):
        print colnames[j] + " %d:  Value = %d" % (j, x[j])
    
    # calculate wire frequencies and routes
    wire_delays = calculate_optimal_wire_delays(x[0:len(b)], dim, ndirs, flows, ncycles)
    
    routes = calculate_routes(x[0:len(b)], dim, ndirs, flows, ncycles)

    return [routes, wire_delays]

def power_cost(traffic_amount, ncycles):
    
    for opt in wire_config_opts:
        freq = opt[freq_idx]
        if  channel_width * ncycles * freq >= traffic_amount * max_wire_freq:
            if freq == 0:
                return 0
            power = link.calc_dynamic_energy(channel_width_bits / 2, opt[vdd_idx]) * float(traffic_amount * max_wire_freq * 1000000/OneMhz) / (channel_width * ncycles)
            # power = power + link.get_static_power(opt[0])
            return power
            break
    return -1

def utilization(traffic_amount, ncycles):
    return float(traffic_amount) / (channel_width * ncycles)

def xy_routing(ncycles, flows, dim, ndirs):
    
    vnoc_dir = [0] * ndirs
    
    vnoc_dir[north] = 4
    vnoc_dir[south] = 3
    vnoc_dir[east] = 2
    vnoc_dir[west] = 1
    
     # make a copy of the flows in case we advertently modify it
    dirty_flows = copy.deepcopy(flows)
    
    local_edge_traffic = calculate_local_edge_traffic(dirty_flows, dim)
    
    edge_traffic = [0] * (dim * dim * ndirs) 
    # gradually route flows through the network
    # such that each time a new flow is added
    # the power increment is minimal
    routes = []
    for f in dirty_flows:
        currentX = f[srcXIdx]
        currentY = f[srcYIdx]
        
        dstX = f[dstXIdx]
        dstY = f[dstYIdx]
        
        route = ['(' + str(f[srcXIdx]) + ',' + str(f[srcYIdx]) + ')', '(' + str(f[dstXIdx]) + ',' + str(f[dstYIdx]) + ')']
         # vc
        vc = vc_calculate(f)

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

            # update traffic on the edge
            edge_id = (currentX * dim + currentY) * ndirs + dir
            edge_traffic[edge_id] = edge_traffic[edge_id] + f[traffic_idx]
             
            route.append(vnoc_dir[dir])
            
            currentX = nextX
            currentY = nextY  
            
        route.append(' ')
        
        routes.append(route)
        
    wire_delays = calculate_optimal_wire_delays_2(edge_traffic, dim, ndirs, ncycles)
    
    router_delays = calculate_optimal_router_delays(edge_traffic, local_edge_traffic, dim, ndirs, ncycles)
    
    return [routes, wire_delays, router_delays]
    
def sort_flows_by_routing_freedom(flows):
    for f in flows:
        deltaX = abs(f[srcXIdx] - f[dstXIdx])
        deltaY = abs(f[srcYIdx] - f[dstYIdx])
        n_routes = 1
        divisor = 1
        for i in range(1, deltaX + 1):
            n_routes = n_routes * (deltaY + i)
            divisor = divisor * i
        n_routes = n_routes / divisor
        f.insert(0, n_routes)
    flows.sort(key=lambda tup:tup[0])
    
    #for f in flows:
    #    f.pop(0)
    
    #sort by traffic
    n_routes = flows[0][0]
    start_index = 0
    current_index = 0
    for f in flows:
        f_routes = f.pop(0)
        current_index = current_index + 1
        
        if f_routes > n_routes:
            #sort from start_index
            segment = flows[start_index:current_index]
            segment.sort(key=lambda tup:tup[4])
            segment.reverse()
            flows[start_index:current_index] = segment
            start_index = current_index
            n_routes = f_routes
        
    return flows

# testing flow-sorting function
def sort_flows(flows):
#    for f in flows:
#        deltaX = abs(f[srcXIdx] - f[dstXIdx])
#        deltaY = abs(f[srcYIdx] - f[dstYIdx])
#        distance = deltaX + deltaY
#        f.insert(0, distance)
    flows.sort(key=lambda tup:tup[traffic_idx])
    flows.reverse()
#    for f in flows:
#        f.pop(0)
    return flows

def min_freq_vdd(total_traffic, ncycles):
    for opt in wire_config_opts:
        if channel_width * ncycles * opt[freq_idx] * router_speed_scale >= total_traffic * max_wire_freq:
            return opt
    #return [-1, -1]
    return wire_config_opts[-1]

def estimate_router_Vdd_freq_traffic(u, edge_traffic, local_edge_traffic, ncycles, dim, ndirs):
    max_vdd = 0
    max_freq = 0 
    u_x = u / dim
    u_y = u % dim
    total_traffic = 0
    
    traffics = []
    
    for dir in range(ndirs):
        edge_id = u * ndirs + dir
        traffics.append(edge_traffic[edge_id])
        
        in_edge_id = incoming_edge_id(u_x, u_y, dir, dim, ndirs)
        if in_edge_id >= 0:
            traffics.append(edge_traffic[in_edge_id])
    
    traffics.append(local_edge_traffic[u * 2 + out_edge_idx])
    traffics.append(local_edge_traffic[u * 2 + in_edge_idx])
    
    for traffic in traffics:
        [vdd, freq] = min_freq_vdd(traffic, ncycles)
        if max_freq < freq:
            max_freq = freq
            max_vdd =vdd
        total_traffic = total_traffic + traffic
    
    if max_freq != 0:    
        avg_traffic = float(total_traffic)/(ncycles * channel_width * max_freq / OneGhz)/(ndirs + 1)/2 #average traffic per one input port
    else:
        avg_traffic = 0
    return [max_vdd, max_freq, avg_traffic]

def orion_router_estimation(arguments):
    #p = subprocess.Popen('orion2rt ' + arguments, shell=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    #for l in p.stdout.readlines():
        #print l
    #    m = re.search('Power\s*=\s*(\d+.?\d*(E|e)(\+|-)\d*)', l)
    #    if m != None:
    #        rt_power = float(m.group(1))
    return calculate_power(arguments)       
 
def estimate_power_consumption(u, v, edge_id, edge_traffic, added_traffic, local_edge_traffic, ncycles, dim, ndirs):
    #temporaty commit the traffic
    dirty_edge_traffic = copy.deepcopy(edge_traffic)
    dirty_edge_traffic[edge_id] = dirty_edge_traffic[edge_id] + added_traffic
    
    #estimate u freq
    [vdd, freq, avg_traffic] = estimate_router_Vdd_freq_traffic(u, dirty_edge_traffic, local_edge_traffic, ncycles, dim, ndirs)
    
    arguments = str(vdd) + ' ' + str(freq) + ' ' + str(ndirs + 1) + ' ' + str(ndirs + 1) + ' ' + str(flit_size) + ' ' + str(n_vc) + ' ' +str(buffer_size) + ' -' + str(avg_traffic)
    for i in range(3, 10):
        arguments = arguments + ' 0'

    rt_power = orion_router_estimation(arguments)
    
    [vdd, freq, avg_traffic] = estimate_router_Vdd_freq_traffic(v, dirty_edge_traffic, local_edge_traffic, ncycles, dim, ndirs)
    
    arguments = str(vdd) + ' ' + str(freq) + ' ' + str(ndirs + 1) + ' ' + str(ndirs + 1) + ' ' + str(flit_size) + ' ' + str(n_vc) + ' ' +str(buffer_size) + ' -' + str(avg_traffic)
    for i in range(3, 10):
        arguments = arguments + ' 0'
    
    rt_power = rt_power + orion_router_estimation(arguments)
    
    return rt_power

def multipath_routing(ncycles, flows, dim, ndirs):
    for min_n_routes in [4, 3, 2, 1]:
        #make a copy of the flows in case we advertently modify it
        dirty_flows = copy.deepcopy(flows)
        
        # sort the flows by traffic freedom
        for f in dirty_flows:
            deltaX = abs(f[srcXIdx] - f[dstXIdx])
            deltaY = abs(f[srcYIdx] - f[dstYIdx])
            n_routes = 1
            divisor = 1
            for i in range(1, deltaX + 1):
                n_routes = n_routes * (deltaY + i)
                divisor = divisor * i
            n_routes = n_routes / divisor
            f.insert(0, n_routes)
        
        splitted_flows = []
        manipulated_flows = []
        #do not split flow by more than the number of paths it has
        for f in dirty_flows:
            n_routes = f.pop(0)
            
            min_n_routes = min(min_n_routes, int(round(float(f[traffic_idx]) / (packet_bytes * 10))))
            min_n_routes = max(min_n_routes, 1)
    #        if(f[traffic_idx]/min_n_routes < packet_bytes * 5):
    #            min_n_routes = 1
            
            if n_routes > min_n_routes:
                n_routes = min_n_routes
    #        if n_routes > 1:
    #            print f
            #split the flow by at most the number of routes
            traffic_per_route = f[traffic_idx] / n_routes
            traffic_left = f[traffic_idx] % n_routes
            for i in range(n_routes):
                f_child = copy.deepcopy(f)
                f_child.append(i)
                splitted_flows.append(f_child)
                if i < traffic_left:
                    f_child[traffic_idx] = traffic_per_route + 1
                else:
                    f_child[traffic_idx] = traffic_per_route
                f_copied = copy.deepcopy(f_child)
                f_copied.append(n_routes)
                manipulated_flows.append(f_copied)
        try:
            result = dijkstra_routing(False, ncycles, splitted_flows, dim, ndirs)
            result.append(manipulated_flows)
            print 'n split', min_n_routes
            break
        except Exception, exc:
            continue
    return result

def dijkstra_routing(minimal_route, ncycles, flows, dim, ndirs):
    
    vnoc_dir = [0] * ndirs
    
    vnoc_dir[north] = 4 
    vnoc_dir[south] = 3
    vnoc_dir[east] = 2
    vnoc_dir[west] = 1
    
    wire_delays = []
    routes = []
    
    # make a copy of the flows in case we advertently modify it
    dirty_flows = copy.deepcopy(flows)
    
    # sort the flows by traffic freedom
    #dirty_flows = sort_flows_by_routing_freedom(dirty_flows)
    dirty_flows = sort_flows(dirty_flows)
    local_edge_traffic = calculate_local_edge_traffic(dirty_flows, dim)
    
    edge_traffic = [0] * (dim * dim * ndirs) 
    # gradually route flows through the network
    # such that each time a new flow is added
    # the power increment is minimal
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
            
            # reach the source
            if u == dst:
                break
            
            visited.add(u)
            if dist[u] == float('inf'):
                print 'Error: Cannot route'
                break
            u_x = u / dim
            u_y = u % dim
            
            # next vertices
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
                    # forbids 180 deg turns
                    if previous_dir[u] != -1 and previous_dir[u] == 3 - v[2]:
                        continue
                    edge_id = u * ndirs + v[2]
                    # if this link can not afford the additional traffic
                    if power_cost(edge_traffic[edge_id] + f[traffic_idx], ncycles) < 0:
                        continue
                    
                    # cost of increasing traffic on this edge
                    link_power_increase = power_cost(edge_traffic[edge_id] + f[traffic_idx], ncycles) - power_cost(edge_traffic[edge_id], ncycles)
                    # cost of increasing routers' freqs on this edge to meet the traffic demand
                    rt_power_increase = 0 #estimate_power_consumption(u, v_id, edge_id, edge_traffic, f[traffic_idx], local_edge_traffic, ncycles, dim, ndirs) - estimate_power_consumption(u, v_id, edge_id, edge_traffic, 0, local_edge_traffic, ncycles, dim, ndirs) 
                    
                    #link_power_increase = 1.0/(utilization(f[traffic_idx], ncycles) + 1 - utilization(edge_traffic[edge_id], ncycles))
                    
                    alt = dist[u] + link_power_increase + rt_power_increase
                    
                    if alt < dist[v_id] or alt == dist[v_id] and edge_traffic[edge_id] < edge_traffic[previous[v_id] * ndirs + previous_dir[v_id]]:
                        dist[v_id] = alt
                        previous[v_id] = u
                        previous_dir[v_id] = v[2]
        
         # commit the route
        # src and dst information
        if len(f) <= flow_id_idx:
            f.append(0)
        route = [f[-1], '(' + str(f[srcXIdx]) + ',' + str(f[srcYIdx]) + ')', '(' + str(f[dstXIdx]) + ',' + str(f[dstYIdx]) + ')']
        
        # vc
        vc = vc_calculate(f)  

        route.append(vc)
        
        route.append(f[traffic_idx])
        route.append(ncycles)
        route.append('|')
        
        dirs = []
        
        u = dst
        
        while previous_dir[u] != -1:
            edge_id = previous[u] * ndirs + previous_dir[u]
                    
            # commit the traffic
            edge_traffic[edge_id] = edge_traffic[edge_id] + f[traffic_idx]
                    
            dirs.insert(0, vnoc_dir[previous_dir[u]])
            u = previous[u]
        
        assert(u == src)
        
        route.extend(dirs)
        
        route.append(' ')
        routes.append(route)
    
    # new wire delays
    wire_delays = calculate_optimal_wire_delays_2(edge_traffic, dim, ndirs, ncycles)
          
    router_delays = calculate_optimal_router_delays(edge_traffic, local_edge_traffic, dim, ndirs, ncycles)
    return [routes, wire_delays, router_delays]

            
    
def list_to_file(name, ls):
    FILE = open(name, 'w')
    for l in ls:
        line = ''
        for item in l:
            if isinstance(item, str):
                line = line + ' ' + item
            else:
                line = line + ' ' + str(item)
        # line = line.strip()
        print >> FILE, line[1:]
        
    FILE.close()
    
def write_traffic(name, traces):
    # write to files
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
        os.system("make -f Makefile.mk BACKEND=\'--memory 16000M --spacetime --dup 1 --profile --newSimple " + str(dim) + " --i " + str(iterations) + " \' > " + str(commlog))
    
    FILE = open(commlog, 'r')
    flows = []
    for line in FILE.readlines():
        line = line.replace(' ', '')
        m = re.search('Node\((\d+),(\d+)\)->Node\((\d+),(\d+)\):?(\d+)bytes', line)
        if m != None:
            flow = []
            for i in range(1, 6):
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
    
        # store the files
        os.system("mv str.cpp " + cfile)
        os.system("mv stream stream" + str(dim) + 'x' + str(dim))
    
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
            ncycles = int(rt * profiling_cpu_freq / assumed_cpu_freq / (dim * dim) / iterations)  # convert to the numbers of cycles if runned in 1GHz
            break
    
    FILE.close()
    
    return ncycles

def traffic_gen(flows, ncycles):
    traffics = []
    dirty_flows = copy.deepcopy(flows)  # make a copy of the flows in case it is modified
    
    max_traffic = max([f[traffic_idx] for f in flows])
    # for each source node
    for x in range(0, dim):
        for y in range(0, dim):
            # find flows that have this node as the source node
            fs = []
            
            packets = []  # packets from this source
            
            traffics.append([x, y, packets])
            
            for f in dirty_flows:
                if f[srcXIdx] == x and f[srcYIdx] == y:
                    # f.append(0)  #total traffic used for this flow
                    fs.append(f)
                    
            for f in fs:
                dirty_flows.remove(f)
                if(len(f) <= flow_id_idx):
                    f.extend([0,1])
                interval = packet_bytes * ncycles / f[traffic_idx];
                current_time = int(round(float(interval) * f[flow_id_idx]/f[-1]));
                if max_traffic < 5000:
                    traffic_left = f[traffic_idx] * 10
                else:
                    traffic_left = f[traffic_idx]
                    
                while traffic_left >= packet_bytes:
                    packet = copy.deepcopy(f[0:traffic_idx])
                    packet.insert(0, current_time)
                    packet.append(packet_bytes / flit_size)
                    packet.append(f[flow_id_idx])
                    packets.append(packet)
                    
                    current_time = current_time + interval;
                    traffic_left = traffic_left - packet_bytes
                    
                if traffic_left > 0:
                    packet = copy.deepcopy(f[0:traffic_idx])
                    packet.insert(0, current_time)
                    n_flits = int(round(float(traffic_left) / flit_size + 0.5))
                    if n_flits <= 1:
                        n_flits = 2
                    packet.append(n_flits)
                    packet.append(f[flow_id_idx])
                    packets.append(packet)
                    
            packets.sort(key=lambda tup:tup[0])
                            
    return traffics

def logfile_to_power(logfile, wire_delays, dim, ndirs):
    FILE = open(logfile, 'r')
    power = 0
    rt_power = 0
    
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
                        freq = opt[1] * 1000000 / OneMhz
                        vdd = opt[0]
                        p_dyn = link.calc_dynamic_energy(channel_width_bits / 2, vdd) * utilization * freq
                        p_static = link.get_static_power(vdd)
                        power = power + p_dyn  # + p_static
                        break
                    
        m = re.search('Current time:\s*(\d+.?\d*)', line)
        if m != None:
            running_time = m.group(1)  
        m = re.search('Router\s*\((\d+)\s*,\s*(\d+)\)\s+(\d+)\s+(\d+)\s+(\d+)\s+(\d+)\s+(\d+)\s+(\d+)\s+(\d+)\s+(\d+.?\d*)', line)
        if m != None:
            x = int(m.group(1))
            y = int(m.group(2))
            freq = int(round(OneGhz/float(m.group(10))))
            
            for opt in wire_config_opts:
                if abs(opt[1] - freq) < 20:
                    freq = opt[1] * 1000000 / OneMhz
                    vdd = opt[0]
                    
                    #compute router power using orion 2
                    arguments = str(vdd) + ' ' + str(freq) + ' ' + str(ndirs + 1) + ' ' + str(ndirs + 1) + ' ' + str(flit_size) + ' ' + str(n_vc) + ' ' +str(buffer_size) + ' ' + str(float(running_time) * freq/OneGhz)
                    for i in range(3, 10):
                        arguments = arguments + ' ' + m.group(i)
                    #print arguments
                    rt_power = rt_power + orion_router_estimation(arguments)
                    break       
    FILE.close()
    
    #print 'Link energy (per second) ' + str(power) + ' router energy ' + str(rt_power) + ' time ' + running_time + ' total power link energy ' + str(power * float(running_time))
    print 'Watt Link ' + str(power) + ' router ' + str(rt_power) + ' time ' + running_time
    print 'EDP Link ' + str(power * float(running_time)) + ' router ' + str(rt_power * float(running_time))

def max_rate_estimation(dim):
    
    flows = comm_prof(dim)

    # sort the flows by traffic freedom
    #dirty_flows = sort_flows_by_routing_freedom(dirty_flows)
    local_edge_traffic = calculate_local_edge_traffic(flows, dim)
    
    sat = max([f[traffic_idx] for f in flows]) * 2
    
    #find a suitable SAT value
    while True:
        try: 
            dijkstra_routing(True, sat, flows, dim, directions)
            break
        except Exception, exc:
            sat = sat * 2
            #traceback.print_exc()
            
            
    unsat = 0
    ncycles = int(round((sat + unsat)/2))
    
    while(True):
        try:
            for t in local_edge_traffic:
                freq = -1
                for opt in wire_config_opts:
                    if channel_width * ncycles * opt[freq_idx] >= t * max_wire_freq:
                        freq = opt[freq_idx]
                        break
                if freq < 0:
                    raise Exception("unsat local links")
                
            dijkstra_routing(True, ncycles, flows, dim, directions)
            if sat == ncycles: #no improvement
                break
            sat = ncycles
            ncycles = int(round((sat + unsat)/2))
        except Exception, exc:
            #print exc
            if unsat == ncycles: #no improvement
                break
            unsat = ncycles
            ncycles = int(round((sat + unsat)/2))
    return sat
    
        
############################################################################################

#optimize = True
n_vc = 4
buffer_size = 12

default_routing = 'xyrouting'
dj_routing = 'dijkstra'
mp_routing = 'multipath'
of_routing = 'milp'
mml_routing = 'mml'
mml_ml_routing = 'mmlml'
mml_fission_routing = 'mmlfission'
mml_ml_fission_routing = 'mmlmlfission'

is_done = True 

done = []
notready = ['vocoder']

undone = ['channelvocoder', 'fm']
#undone = ['tde']

for dir in os.listdir(path):
    
    if dir in notready or dir in done or not dir in undone:
        continue
    
    if os.path.isfile('./dir'):
        continue
    
#    if dir != 'filterbank':
#        continue
    # run to obtain profiling information first
    os.chdir('./' + dir + '/streamit')
    
    #os.system("pwd")
    
    print 'Profiling :', dir
    
    n_splits = 4
    
    for dim in [4, 6, 8]:
        max_rate = max_rate_estimation(dim)
#        max_rate = int(max_rate / 0.8)
        
        #methods = [default_routing, dj_routing, mml_routing, mml_ml_routing, mp_routing, mml_fission_routing, mml_ml_fission_routing]
        #methods = [default_routing, dj_routing, mp_routing, mml_routing, mml_ml_routing, mml_fission_routing]
        #methods = [default_routing, dj_routing, mp_routing]
        methods = [mml_fission_routing]
        #methods = [dj_routing]
        
        for i in [0,5]: #range(0, 6):
            ncycles = int(round(max_rate * 10 / (10 - i)))
            
            for method in methods:
                #ncycles = time_prof(dim)
                print
                print "Num cycles per iteration :", ncycles, i, dim
                
                flows = comm_prof(dim)
                
                try:
                # generate ILP files
                    if method == mml_fission_routing:
                        print 'Routing = MinMaxLoadFission'
                        [routes, wire_delays, router_delays, flows] = minimize_max_load_fission_zero_pop(False, ncycles, flows, dim, directions, n_splits)
                    elif method == mml_ml_fission_routing:
                        print 'Routing = MinMaxLoadFission-Minlinks'
                        [routes, wire_delays, router_delays, flows] = minimize_max_load_fission_zero_pop(True, ncycles, flows, dim, directions, n_splits)
                        #[routes, wire_delays, router_delays, flows] = minimize_max_load_fission(True, ncycles, flows, dim, directions, n_splits)
                    elif method == mml_routing:
                        print 'Routing = MinMaxLoad'
                        [routes, wire_delays, router_delays] = minimize_max_load(False, ncycles, flows, dim, directions)
                    elif method == mml_ml_routing:
                        print 'Routing = MinMaxLoad-Minlinks'
                        [routes, wire_delays, router_delays] = minimize_max_load(True, ncycles, flows, dim, directions)
                    elif method == of_routing:
                        print 'Routing = Optimal Freq'
                        [routes, wire_delays] = optimal_routes_freqs(ncycles, flows, dim, directions)
                    elif method == mp_routing:
                        # [routes, wire_delays] = minimize_used_links(ncycles, flows, dim, directions)
                        # [routes, wire_delays] = dp_routing(ncycles, flows, dim, directions)
                        print 'Routing = Multipath'
                        [routes, wire_delays, router_delays, flows] = multipath_routing(ncycles, flows, dim, directions)
                    elif method == dj_routing:
                        print 'Routing = DJ'
                        [routes, wire_delays, router_delays] = dijkstra_routing(True, ncycles, flows, dim, directions)
                    elif method == default_routing:
                        print 'Routing = XY'
                        [routes, wire_delays, router_delays] = xy_routing(ncycles, flows, dim, directions)
                    else:
                        raise Exception("Unkown routing method!")
                except Exception, exc:
                    print exc
                    traceback.print_exc()
                    continue 
    
                list_to_file(method + '_router_config.txt', router_delays)
                # generate wire config
                list_to_file(method + '_wire_config.txt', wire_delays)
                
                # generate routing config
                list_to_file(method + '_traffic_config.txt', routes)
                
                # generate traffic
                traffics = traffic_gen(flows, ncycles)
                                        
                write_traffic(dir, traffics)
        
                # invoke simulation
                logfile = dir + str(dim) + 'x' + str(dim) + '_' + str(i) + '_' + method + '_sim.log'
                
                cmd = 'vnoc ./traffics/' + dir + ' cycles: 1000000 noc_size: ' + str(dim) + ' vc_n: ' + str(n_vc) + ' rtcfg: ' + method + '_router_config.txt wirecfg: ' + method + '_wire_config.txt trafficcfg: ' + method + '_traffic_config.txt '
                if method != default_routing:
                    os.system(cmd + ' routing: TABLE > ' + logfile)
                else:
                    os.system(cmd + ' routing: XY  > ' + logfile)
                # collect data
                
                logfile_to_power(logfile, wire_delays, dim, directions)
        
    os.chdir("../../")
        

