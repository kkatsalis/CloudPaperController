/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Cplex;

/**
 *
 * @author kostas
 */
public class CplexResponse {
    
    int[][][][] activationMatrix;
    double netBenefit;

    public CplexResponse(int[][][][] activationMatrix, double netBenefit) {
        this.activationMatrix = activationMatrix;
        this.netBenefit = netBenefit;
    }

    public int[][][][] getActivationMatrix() {
        return activationMatrix;
    }

    public double getNetBenefit() {
        return netBenefit;
    }
    
    
    
}
