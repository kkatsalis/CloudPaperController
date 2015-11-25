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

import Controller.Configuration;
import ilog.concert.*;
import ilog.cplex.*;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Hashtable;


public class Scheduler {
    
    Configuration config;
    
    
    
    
    public Scheduler(Configuration config){
        
        this.config=config;
        
    }
    
    public void buildModelByRow(IloModeler    model,
            SchedulerData          data,
            IloNumVar[][][][]   a,
            IloNumVarType type) throws IloException
    {
        
        for (int i = 0; i < data.N; i++)
            for (int j = 0; j < data.P; j++)
                for (int v = 0; v < data.V; v++)
                    for (int s = 0; s < data.S; s++)
                        a[i][j][v][s] = model.numVar(0, data.A[j][v][s], type);
        
        // build y[i]s
        IloNumExpr y_sum = model.numExpr();
        IloNumExpr[][] y =  new IloNumExpr[data.N][data.R];
        double[][] Q =  new double[data.N][data.R];
        
        for (int i=0;i<data.N;i++)
        {
            IloNumExpr ksum = model.numExpr();
            for (int k=0;k<data.R;k++)
            {
                IloNumExpr ssum = model.numExpr();
                
                for (int s=0; s<data.S; s++)
                {
                    IloNumExpr jsum = model.numExpr();
                    for (int j=0; j<data.P; j++)
                    {
                        IloNumExpr vsum = model.numExpr();
                        for (int v=0; v<data.V; v++)
                        {
                            IloNumExpr expr = model.numExpr();
                            expr = model.sum(expr, a[i][j][v][s]);
                            expr = model.sum(expr, data.n[i][j][v][s]);
                            expr = model.diff(expr, data.D[i][j][v][s]);
                            expr = model.prod(expr, data.m[v][k]);
                            vsum = model.sum(vsum, expr);
                        }
                        jsum = model.sum(jsum,vsum);
                    }
                    ssum = model.sum(ssum, jsum);
                }
                y[i][k] = model.diff(ssum, data.p[i][k]);
                Q[i][k] = Math.max(data.PREV_Y[i][k]+data.PREV_Q[i][k],0);
                
                ksum = model.sum(ksum, model.prod(y[i][k], Q[i][k]));
            }
            y_sum = model.sum(y_sum, ksum);
        }
        
        // build pr penalty expression
        
        IloNumExpr pr_expr = model.numExpr();
        for(int j=0;j<data.P;j++)
        {
            IloNumExpr ssum = model.numExpr();
            for(int s=0;s<data.S;s++)
            {
                IloNumExpr vsum = model.numExpr();
                for(int v=0;v<data.V;v++)
                {
                    IloNumExpr expr = model.numExpr();
                    for (int i=0;i<data.N;i++)
                    {
                        expr = model.sum(expr,a[i][j][v][s]);
                        expr = model.sum(expr, data.n[i][j][v][s]-data.D[i][j][v][s]);
                    }
                    expr = model.prod(expr, data.ksi(s,j,v));
                    vsum = model.sum(vsum, expr);
                }
                vsum = model.diff(data.r[j][s],vsum);
                vsum = model.prod(vsum, data.pen[j][s]);
                ssum = model.sum(ssum,vsum);
            }
            pr_expr = model.sum(pr_expr, ssum);
        }
        
        // build fr fairness expression
        
        // build fr fairness expression
        double n_sum = 0;
        
        for(int i=0;i<data.N;i++)
            for(int j=0;j<data.P;j++)
                for(int v=0;v<data.V;v++)
                    for (int s=0;s<data.S;s++)
                        n_sum += data.n[i][j][v][s];
        
        
        IloNumExpr fr_expr = model.numExpr();
        
        for (int k=0;k<data.P;k++)
        {
            IloNumExpr isum = model.numExpr();
            for(int i=0;i<data.N;i++)
            {
                IloNumExpr vsum = model.numExpr();
                for(int v=0;v<data.V;v++)
                {
                    IloNumExpr expr = model.numExpr();
                    for (int s=0;s<data.S;s++)
                    {
                        expr = model.sum(expr,a[i][k][v][s]);
                        expr = model.sum(expr, data.n[i][k][v][s]-data.D[i][k][v][s]);
                    }
                    vsum = model.sum(vsum, expr);
                }
                isum = model.sum(isum, vsum);
            }
            isum = model.prod(isum,1/(n_sum+0.00001));
            isum = model.diff(isum, 1/data.P);
            isum = model.prod(isum, data.phi[k]);
            fr_expr = model.sum(fr_expr, isum);
        }
        
        
        // constraint for sum of a[][][][] variables
        for (int j=0; j<data.P; j++)
            for (int s=0; s<data.S; s++)
                for (int v=0;v<data.V;v++)
                {
                    IloNumExpr isum = model.numExpr();
                    for (int i=0;i<data.N;i++)
                        isum = model.sum(isum, a[i][j][v][s]);
                    
                    model.addLe(isum, data.A[j][v][s]);
                }
        
        
        // constraint for y[i][k]
        for (int i=0;i<data.N;i++)
            for (int k=0;k<data.R;k++)
                model.addLe(y[i][k], 0);
        
        
        
        //start of debugging
        //fr_expr = model.numExpr();
        //y_sum = model.numExpr();
        //end of debugging
        
        // minimization problem
        IloNumExpr problem = model.numExpr();
        
        // PENALTY AND FAIRNESS TAKE EQUAL IMPORTANCE
        //problem = model.sum(problem,model.sum(model.prod(model.sum(pr_expr, fr_expr), data.Omega), y_sum));
        
        // PENALTY MINIMIZATION GETS MORE IMPORTANCE OVER FAIRNESS
        problem = model.sum(problem,model.sum(model.sum(model.prod(pr_expr, data.Omega),fr_expr), y_sum));
        model.addMinimize(problem);
    }
    
    
    public int[][][][] Run(SchedulerData data) throws IOException {
        
        
        BufferedWriter ios = null;
        BufferedWriter nos = null;
        
        int[][][][] activationMatrix=new int[data.N][data.P][data.V][data.S];
        
        try {
            
            // Build model
            IloCplex     cplex = new IloCplex();
            IloNumVar[][][][]  a   = new IloNumVar[data.N][data.P][data.V][data.S];
            
            
            IloNumVarType varType = IloNumVarType.Int;
            
            ios = new BufferedWriter(new FileWriter("a_values",true));
            nos = new BufferedWriter(new FileWriter("n_values",true));
            
            //System.out.println(cplex.toString());
            
            // Solve model
            
            buildModelByRow   (cplex, data, a, varType);
            
            // Solve model
            
            if ( cplex.solve() ) {
                System.out.println();
                System.out.println("Solution status = " + cplex.getStatus());
                System.out.println();
                System.out.println(" cost = " + cplex.getObjValue());
                for (int i = 0; i < data.N; i++) {
                    for (int j=0;j < data.P; j++)
                        for (int v=0;v < data.V; v++)
                            for (int s=0;s < data.S; s++)
                            {
                                System.out.println(" a[" + i + "],["+j+"]["+v+"]["+s+"] = " + cplex.getValue(a[i][j][v][s]));
                                activationMatrix[i][j][v][s] = (int)Math.round(cplex.getValue(a[i][j][v][s]));
                                ios.write(activationMatrix[i][j][v][s]+" ");
                                ios.flush();
                            }
                }
                ios.write("\n");
                ios.flush();
                
                System.out.println();
                
                for (int i = 0; i < data.N; i++) {
                    for (int j=0;j < data.P; j++)
                        for (int v=0;v < data.V; v++)
                        {
                            for (int s=0;s < data.S; s++)
                            {
                                nos.write(data.n[i][j][v][s]+" ");
                                nos.flush();
                            }
                        }
                }
                nos.write("\n");
                nos.flush();
            } else {
                System.out.println("Solution NOT FOUND");
                System.out.println("Solution status = " + cplex.getStatus());
            }
            updateData(data, activationMatrix);
            cplex.end();
            
            
            
        }
        catch (IloException ex) {
            System.out.println("Concert Error: " + ex);
        }
        
        System.out.println("Method Call: Cplex Run Called");
        
        return activationMatrix;
    }
    
    public void updateData(SchedulerData data, int[][][][] a)
    {
        double[][] y =  new double[data.N][data.R];
        double[][] Q =  new double[data.N][data.R];
        
        for (int i=0;i<data.N;i++)
        {
            for (int k=0;k<data.R;k++)
            {
                double ssum = 0;
                
                for (int s=0; s<data.S; s++)
                {
                    double jsum = 0;
                    for (int j=0; j<data.P; j++)
                    {
                        double vsum = 0;
                        for (int v=0; v<data.V; v++)
                        {
                            double expr = 0;
                            expr = (a[i][j][v][s]+ data.n[i][j][v][s] -data.D[i][j][v][s])*data.m[v][k];
                            vsum += expr;
                        }
                        jsum += vsum;
                    }
                    ssum += jsum;
                }
                y[i][k] = ssum -data.p[i][k];
                Q[i][k] = Math.max(data.PREV_Y[i][k]+ data.PREV_Q[i][k],0);
                
                System.out.println("y["+i+"]["+k+"]="+y[i][k]);
                System.out.println("Q["+i+"]["+k+"]="+Q[i][k]);
            }
        }
        data.PREV_Q = Q;
        data.PREV_Y = y;
        
        for (int i = 0; i < data.N; i++)
            for (int j=0;j < data.P; j++)
                for (int v=0;v < data.V; v++)
                    for (int s=0;s < data.S; s++)
                    {
                        data.n[i][j][v][s] = Math.max(a[i][j][v][s] + data.n[i][j][v][s] - data.D[i][j][v][s],0);
                    }
    }
}



