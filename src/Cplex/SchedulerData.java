/*
* To change this license header, choose License Headers in Project Properties.
* To change this template file, choose Tools | Templates
* and open the template in the editor.
*/
package Cplex;

import Controller.Configuration;

/**
 *
 * @author kostas
 */
public class SchedulerData {
    
    public int N; // # of APs
    public int P; // # of service providers
    public int S; // # of services
    public int V; // # of VM types
    public int[][] r; // requests per service provider and service
    public int R; // # of physical resources per physical machine
    public double[] phi; // fairness weight per provider
    public double[][] m; // m[vmtype][res]: amount of each res res for each VM vmtype vmtype
    public double[][] p; // p[host][res]: capacity of each res res at each AP host
    public double[][] pen; // pen[j][s] penalty for not satisfying locally a request for service s of provider j
    public int[][][] A; // A[j][vmtype][s]: # of new requests for VMs of vmtype vmtype for service s of provider j
    public int[][][][] D; // D[host][j][vmtype][s]: # of removed VMs of vmtype vmtype for service s of provider j from AP host
    public int[][][][] n; // n[host][j][vmtype][s]: # of allocated VMs of vmtype vmtype for service s of provider j at AP host
    
    public double[][] PREV_Q;
    public double[][] PREV_Y;
    public double Omega = 100;
    
    Configuration config;
    
    public SchedulerData(Configuration config)
    {
        this.config=config;
        this.S = config.getServicesNumber();
        this.P = config.getProvidersNumber();
        this.V = config.getVmTypesNumber();
        this.N = config.getHostsNumber();
        this.R = config.getMachineResourcesNumber();
        
        
        this.Omega=config.getOmega();
        
        initializeArrays();
    }
    
    public void updateParameters(int[][] r, int[][][] A, int[][][][] D){
        
        this.r = r;
        this.A = A;
        this.D = D;
        
        System.out.println("Method Call: Update Cplex Parameters Called");
        
    }
    
    private void initializeArrays(){
        
        // 1 - Fairness Weight
        phi = new double[P]; // fairness weight per provider
        for (int j=0;j<P;j++)
            phi[j] = config.getPhiWeight()[j];
        
        // 2 - amount of each res res for each VM vmtype vmtype
        // R0:cpu, R1:memory, R3:storage,R4:bandwidth, v0:small,v1:medium,v2:large
        m = new double[V][R];
        for (int vmtype=0;vmtype<V;vmtype++)
            for (int res=0;res<R;res++){
                if(res==0)
                    m[vmtype][res] = config.getCpu_VM()[vmtype];
                else if(res==1)
                    m[vmtype][res] = config.getMemory_VM()[vmtype];
                else if(res==2)
                    m[vmtype][res] = config.getStorage_VM()[vmtype];
                else if(res==3)
                    m[vmtype][res] = config.getBandwidth_VM()[vmtype];
                
            }
        // 3- p[host][res]: capacity of each res res at each AP host
        p = new double[N][R];
        
        for (int host=0;host<N;host++)
            for (int res=0;res<R;res++){
                
                if(res==0)
                    p[host][res] = config.getCpu_host();
                else if(res==1)
                    p[host][res] = config.getMemory_host();
                else if(res==2)
                    p[host][res] = config.getStorage_host();
                else if(res==3)
                    p[host][res] = config.getBandwidth_host();
                
            }
        
        // 4- pen[j][s] penalty for not satisfying locally a request for service s of provider j
        pen = new double[P][S];
        for (int j=0;j<P;j++)
            for (int s=0;s<S;s++)
                pen[j][s] = config.getPenalty()[j][s];
        
        // 5- Initialize Prev_Q and Prev_Y
        PREV_Q = new double[N][R];
        PREV_Y = new double[N][R];
        
        for (int i=0;i<N;i++)
            for (int k=0;k<R;k++)
            {
                PREV_Q[i][k] = 0;
                PREV_Y[i][k] = 0;
            }
        
        n=new int[N][P][V][S];
        
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < P; j++) {
                for (int v = 0; v < V; v++) {
                    for (int s = 0; s < S; s++) {
                        n[i][j][v][s]=0;
                    }
                    
                }
            }
        }
    }
    
    
    
  public double ksi(int s, int j, int v)
	{
		if (v == 0 && s == 0)
			return 5000*(j+1);
		else if (v == 0 && s == 1)
			return 500*(j+1);
		else if (v == 1 && s == 0)
			return 10000*(j+1);
		else if (v == 1 && s == 1)
			return 1000*(j+1);
		else if (v == 2 && s == 0)
			return 20000*(j+1);
		else if (v == 2 && s == 1)
			return 5000*(j+1);
		else
			return 100*(v+1);
	}
    
    public double[] f(int r, int s, int j, int V)
    {
        double[] unitVector = new double[V];
        
        if (s == 0)
        {
            unitVector[2] = r/20000;
            unitVector[1] = (r%20000)/10000;
            unitVector[0] = ((r%20000)%10000)/5000;
            if (((r%20000)%10000)%5000 != 0)
                unitVector[0] += 1;
        } else if (s == 1)
        {
            unitVector[2] = r/5000;
            unitVector[1] = (r%5000)/1000;
            unitVector[0] = ((r%5000)%1000)/500;
            if (((r%5000)%1000)%500 != 0)
                unitVector[0] += 1;
        }
        
        for (int i = 0; i < V; i++) {
            unitVector[i]=5;
            
        }
        return unitVector;
    }
    
    
    
    
}

