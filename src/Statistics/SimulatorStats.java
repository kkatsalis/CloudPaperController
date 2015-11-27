/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Statistics;

/**
 *
 * @author kostas
 */
public class SimulatorStats {
    
    int slot;
    int[] vmsRequested; //per provider
    int[] vmsSatisfied; //per provider
    int[] smallVmsRquested; //per provider
    int[] smallVmsSatisfied; //per provider
    int[] mediumVmsRquested; //per provider
    int[] mediumVmsSatisfied; //per provider
    int[] largeVmsRquested; //per provider
    int[] largeVmsSatisfied; //per provider
    int[] numberOfActiveVMs; //per provider
    double netBenefit;

    public SimulatorStats(int numberOfProviders){
    
        smallVmsRquested=new int[numberOfProviders]; //per provider
        smallVmsSatisfied=new int[numberOfProviders]; //per provider
        mediumVmsRquested=new int[numberOfProviders]; //per provider
        mediumVmsSatisfied=new int[numberOfProviders]; //per provider
        largeVmsRquested=new int[numberOfProviders]; //per provider
        largeVmsSatisfied=new int[numberOfProviders]; //per provider
        numberOfActiveVMs=new int[numberOfProviders]; //per provider
        vmsRequested=new int[numberOfProviders]; //per provider
        vmsSatisfied=new int[numberOfProviders]; //per provider
    
    }
    
    public int getSlot() {
        return slot;
    }

    public void setSlot(int slot) {
        this.slot = slot;
    }

    public int[] getSmallVmsRquested() {
        return smallVmsRquested;
    }

    public int[] getSmallVmsSatisfied() {
        return smallVmsSatisfied;
    }

    public int[] getMediumVmsRquested() {
        return mediumVmsRquested;
    }

    public int[] getMediumVmsSatisfied() {
        return mediumVmsSatisfied;
    }

    public int[] getLargeVmsRquested() {
        return largeVmsRquested;
    }

    public int[] getLargeVmsSatisfied() {
        return largeVmsSatisfied;
    }

    public int[] getNumberOfActiveVMs() {
        return numberOfActiveVMs;
    }

    public double getNetBenefit() {
        return netBenefit;
    }

    public void setNetBenefit(double netBenefit) {
        this.netBenefit = netBenefit;
    }

    public int[] getVmsRequested() {
        return vmsRequested;
    }

    public int[] getVmsSatisfied() {
        return vmsSatisfied;
    }

  
    
}
