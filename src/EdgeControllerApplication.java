/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
import Controller.Simulator;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author kostas
 */
public class EdgeControllerApplication {
    
     public static void main(String[] args) {
     
        Simulator simulator;
        int simulationID;
        int runID;
        String algorithm;
        
        if(args.length>0){
           
            algorithm=args[0].toString();
            simulationID=Integer.valueOf(args[1].toString());
            runID=Integer.valueOf(args[2].toString());
            
            simulator=new Simulator(algorithm,simulationID,runID);
            simulator.StartExperiment();
         
        }else{
        
            simulator=new Simulator();
            simulator.StartExperiment();
        }
     
     }
     
      
}
