#!/usr/bin/python

import math

class TechParameter:
    
    def __init__(self):
        #32 nm technology
        self.m_BufferDriveResistance      = 10.4611e+03
        self.m_BufferInputCapacitance     = 2.4e-15
        self.m_BufferPMOSOffCurrent       = 792.4e-09
        self.m_BufferNMOSOffCurrent       = 405.1e-09
        
        #global
        self.m_WireMinWidth            = 48e-9
        self.m_WireMinSpacing          = 48e-9
        self.m_WireMetalThickness      = 120e-9
        self.m_WireBarrierThickness    = 2.4e-9
        self.m_WireDielectricThickness = 110.4e-9
        self.m_WireDielectricConstant  = 1.9
        
    def get_EnergyFactor(self, vdd):
        return vdd * vdd
    
    def get_BufferDriveResistance(self):
        return self.m_BufferDriveResistance
    
    def get_BufferInputCapacitance(self):
        return self.m_BufferInputCapacitance
    
    def get_BufferNMOSOffCurrent(self):
        return self.m_BufferNMOSOffCurrent
    
    def get_BufferPMOSOffCurrent(self):
        return self.m_BufferPMOSOffCurrent
    
    def get_WireMinWidth(self): 
        return self.m_WireMinWidth
    def get_WireMinSpacing(self): 
        return self.m_WireMinSpacing
    def get_WireBarrierThickness(self): 
        return self.m_WireBarrierThickness
    def get_WireMetalThickness(self): 
        return self.m_WireMetalThickness
    def get_WireDielectricThickness(self): 
        return self.m_WireDielectricThickness
    def get_WireDielectricConstant(self): 
        return self.m_WireDielectricConstant
    
    def get_vdd(self):
        self.m_vdd
    
class Wire:
    MIN_DELAY = 1
    STAGGERED = 2
    
    SWIDTH_SSPACE = 1
    SWIDTH_DSPACE = 2
    DWIDTH_SSPACE = 3
    DWIDTH_DSPACE = 4
    
    def calc_dynamic_energy(self, len, vdd):
        c_g = 2 * self.m_gnd_cap_unit_len * len
        c_c = 2 * self.m_couple_cap_unit_len * len
        cap_wire = c_g + c_c
        
        [k,h] = self.calc_opt_buffering(len)
        
        BufferInputCapacitance = self.m_tech_param_ptr.get_BufferInputCapacitance()
        
        cap_buf = float(k) * BufferInputCapacitance * h
        
        e_factor = self.m_tech_param_ptr.get_EnergyFactor(vdd)
        
        return ((cap_wire+cap_buf)*e_factor)
    
    def __init__(self, buf_scheme, is_shielding, width_spacing_model, tech_param_ptr):
        self.m_buf_scheme = buf_scheme
        
        self.m_is_shielding = is_shielding
        
        self.m_width_spacing_model = width_spacing_model
        
        self.m_tech_param_ptr = tech_param_ptr
        
        self.m_res_unit_len = self.calc_res_unit_len()
        self.m_gnd_cap_unit_len = self.calc_gnd_cap_unit_len()
        self.m_couple_cap_unit_len = self.calc_couple_cap_unit_len()
        
        
        
    
    def calc_opt_buffering(self, len):
        BufferDriveResistance = self.m_tech_param_ptr.get_BufferDriveResistance()
        BufferInputCapacitance = self.m_tech_param_ptr.get_BufferInputCapacitance()
        
        r = self.m_res_unit_len*len
        c_g = 2*self.m_gnd_cap_unit_len*len
        c_c = 2*self.m_couple_cap_unit_len*len
            
        if self.m_buf_scheme == self.MIN_DELAY:
            if self.m_is_shielding:
                k = int(math.sqrt(((0.4*r*c_g)+(0.57*r*c_c))/(0.7*BufferDriveResistance*BufferInputCapacitance)))
                h = math.sqrt(((0.7*BufferDriveResistance*c_g) + (1.4*1.5*BufferDriveResistance*c_c))/(0.7*r*BufferInputCapacitance))
            else:
                k = int(math.sqrt(((0.4*r*c_g)+(0.51*r*c_c))/(0.7*BufferDriveResistance*BufferInputCapacitance)))
                h = math.sqrt(((0.7*BufferDriveResistance*c_g) + (1.4*2.2*BufferDriveResistance*c_c))/(0.7*r*BufferInputCapacitance))
        elif self.m_buf_scheme == self.STAGGERED:
            k = int(sqrt(((0.4*r*c_g)+(0.57*r*c_c))/(0.7*BufferDriveResistance*BufferInputCapacitance)))
            h = sqrt(((0.7*BufferDriveResistance*c_g) + (1.4*1.5*BufferDriveResistance*c_c))/(0.7*r*BufferInputCapacitance))
        
        return [k, h]
    
    def calc_static_power(self,len, vdd):
        [k, h] = self.calc_opt_buffering(len)

        BufferNMOSOffCurrent = self.m_tech_param_ptr.get_BufferNMOSOffCurrent()
        BufferPMOSOffCurrent = self.m_tech_param_ptr.get_BufferPMOSOffCurrent()
        i_static_nmos = BufferNMOSOffCurrent*h*k
        i_static_pmos = BufferPMOSOffCurrent*h*k

        return (vdd*(i_static_pmos+i_static_nmos)/2)
    
    def calc_gnd_cap_unit_len(self):
        c_g = -1.0
        
        WireMinWidth = self.m_tech_param_ptr.get_WireMinWidth()
        WireMinSpacing = self.m_tech_param_ptr.get_WireMinSpacing()
        WireMetalThickness = self.m_tech_param_ptr.get_WireMetalThickness()
        WireDielectricThickness = self.m_tech_param_ptr.get_WireDielectricThickness()
        WireDielectricConstant = self.m_tech_param_ptr.get_WireDielectricConstant()
        
        minSpacingNew = WireMinSpacing
        minWidthNew = WireMinWidthminSpacingNew = WireMinSpacing
        minWidthNew = WireMinWidth
            
        if self.m_width_spacing_model == self.SWIDTH_DSPACE:
            minSpacingNew = 2*WireMinSpacing + WireMinWidth
        elif self.m_width_spacing_model == self.DWIDTH_SSPACE:
            minWidthNew = 2*WireMinWidth
        elif self.m_width_spacing_model == self.DWIDTH_DSPACE:
            minSpacingNew = 2 * WireMinSpacing
            minWidthNew = 2 * WireMinWidth
            
        if self.m_width_spacing_model == self.SWIDTH_DSPACE:
            minSpacingNew = 2*WireMinSpacing + WireMinWidth
        elif self.m_width_spacing_model == self.DWIDTH_SSPACE:
            minWidthNew = 2*WireMinWidth
        elif self.m_width_spacing_model == self.DWIDTH_DSPACE:
            minSpacingNew = 2 * WireMinSpacing
            minWidthNew = 2 * WireMinWidth
            
        A = (minWidthNew/WireDielectricThickness)
        B = 2.04*pow((minSpacingNew/(minSpacingNew + 0.54*WireDielectricThickness)), 1.77)
        C = pow((WireMetalThickness/(WireMetalThickness + 4.53*WireDielectricThickness)), 0.07)
        c_g = WireDielectricConstant*8.85e-12*(A+(B*C));
        
        return c_g
    
    def calc_res_unit_len(self):
        r = -1.0
        
        WireMinWidth = self.m_tech_param_ptr.get_WireMinWidth()
        WireBarrierThickness = self.m_tech_param_ptr.get_WireBarrierThickness()
        WireMetalThickness = self.m_tech_param_ptr.get_WireMetalThickness()
        
        if self.m_width_spacing_model == self.SWIDTH_SSPACE or self.m_width_spacing_model == self.SWIDTH_DSPACE:
            rho = 2.202e-8 + (1.030e-15  / (WireMinWidth - 2*WireBarrierThickness))
            r = ((rho) / ((WireMinWidth - 2*WireBarrierThickness) * (WireMetalThickness - WireBarrierThickness)))
        elif self.m_width_spacing_model == self.DWIDTH_DSPACE or self.m_width_spacing_model == self.DWIDTH_SSPACE:
            rho = 2.202e-8 + (1.030e-15  / (2*WireMinWidth - 2*WireBarrierThickness))
            r = ((rho) / ((2*WireMinWidth - 2*WireBarrierThickness) * (WireMetalThickness - WireBarrierThickness)))
        else:
            r = 1.0
            
        return r
    def calc_couple_cap_unit_len(self):
        c_c = -1
        
        WireMinWidth = self.m_tech_param_ptr.get_WireMinWidth()
        WireMinSpacing = self.m_tech_param_ptr.get_WireMinSpacing()
        WireMetalThickness = self.m_tech_param_ptr.get_WireMetalThickness()
        WireDielectricThickness = self.m_tech_param_ptr.get_WireDielectricThickness()
        WireDielectricConstant = self.m_tech_param_ptr.get_WireDielectricConstant()
        
        minSpacingNew = WireMinSpacing
        minWidthNew = WireMinWidth
            
        if self.m_width_spacing_model == self.SWIDTH_DSPACE:
            minSpacingNew = 2*WireMinSpacing + WireMinWidth
        elif self.m_width_spacing_model == self.DWIDTH_SSPACE:
            minWidthNew = 2*WireMinWidth
        elif self.m_width_spacing_model == self.DWIDTH_DSPACE:
            minSpacingNew = 2 * WireMinSpacing
            minWidthNew = 2 * WireMinWidth
            
        A = 1.14*(WireMetalThickness/minSpacingNew) * math.exp(-4*minSpacingNew/(minSpacingNew + 8.01*WireDielectricThickness))
        B = 2.37*pow((minWidthNew/(minWidthNew + 0.31*minSpacingNew)), 0.28) 
        C = pow((WireDielectricThickness/(WireDielectricThickness + 8.96*minSpacingNew)), 0.76) * math.exp(-2*minSpacingNew/(minSpacingNew + 6*WireDielectricThickness))
        
        c_c = WireDielectricConstant*8.85e-12*(A + (B*C))
        
        return c_c
        
class Link:
    MIN_DELAY = 1
    STAGGERED = 2
    
    SWIDTH_SSPACE = 1
    SWIDTH_DSPACE = 2
    DWIDTH_SSPACE = 3
    DWIDTH_DSPACE = 4
    
    
    def __init__(self, len, width):
        self.m_wire = Wire(self.MIN_DELAY, self.SWIDTH_SSPACE, True, TechParameter())
        
        self.m_len = len
        self.m_line_width = width
        
    def calc_dynamic_energy(self, num_bit_flip, vdd):
        return (num_bit_flip*(self.m_wire.calc_dynamic_energy(self.m_len, vdd)/2))
                
    def get_static_power(self, vdd):
        return (self.m_line_width * self.m_wire.calc_static_power(self.m_len, vdd))
        