/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Controller;

/**
 *
 * @author kostas
 */
public class VMRequest {
    
    int providerID;
    int requestID;
    static int id=100;
    int slotStart;
    int slotEnd;
    
    String vmType;
    String service;
    int lifetime;     //in Slots
   
  
    Configuration config;
    
    public VMRequest(Configuration config, int providerID, int lifetime) {
        
        this.config=config;
        this.providerID = providerID;
        this.lifetime=lifetime;
        id++;
        this.requestID=id;
        
    
    }

    
    
    
    public int getProviderID() {
        return providerID;
    }

    public int getRequestID() {
        return requestID;
    }

      
    

    public String getVmType() {
        return vmType;
    }

    public void setVmType(String vmType) {
        this.vmType = vmType;
        
    }

    public int getSlotStart() {
        return slotStart;
    }

    public void setSlotStart(int slotStart) {
        this.slotStart = slotStart;
    }

    public int getSlotEnd() {
        return slotEnd;
    }

    public void setSlotEnd(int slotEnd) {
        this.slotEnd = slotEnd;
    }

   

    public String getServiceType() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public int getLifetime() {
        return lifetime;
    }

    
    
    
   

    

    
    
    

}
