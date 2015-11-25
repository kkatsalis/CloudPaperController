/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Controller;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author kostas
 */
public class ServiceRequestsPattern {
    
    int providerID;
    int numberOfServices;
    List<Integer> numberOfRequestsPerService;

    public ServiceRequestsPattern(int numberOfServices) {
        
      this.numberOfServices=numberOfServices;
      this.numberOfRequestsPerService=new ArrayList<>(); //this is an initial estimation
       
    }

    public int getNumberOfServices() {
        return numberOfServices;
    }

    public void setNumberOfServices(int numberOfServices) {
        this.numberOfServices = numberOfServices;
    }

    
    public int getProviderID() {
        return providerID;
    }

    public void setProviderID(int providerID) {
        this.providerID = providerID;
    }

   
    public List<Integer> getNumberOfRequestsPerService() {
        return numberOfRequestsPerService;
    }

    
    
    
    
    
}
