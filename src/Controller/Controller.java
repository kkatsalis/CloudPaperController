/*
* To change this license header, choose License Headers in Project Properties.
* To change this template file, choose Tools | Templates
* and open the template in the editor.
*/
package Controller;

import Cplex.CplexResponse;
import Enumerators.EMachineTypes;
import Enumerators.ESlotDurationMetric;
import Statistics.DBClass;
import Statistics.DBUtilities;
import Utilities.WebUtilities;
import Statistics.VMStats;
import Statistics.HostStats;
import Statistics.NetRateStats;
import Cplex.Scheduler;
import Cplex.SchedulerData;
import Statistics.SimulatorStats;
import Utilities.FakeWebRequestUtilities;
import Utilities.Utilities;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import jsc.distributions.Exponential;
import jsc.distributions.Pareto;
import omlBasePackage.OMLMPFieldDef;
import omlBasePackage.OMLTypes;
import org.json.JSONException;

/**
 *
 * @author kostas
 */
public class Controller {
    
    Configuration _config;
    Slot[] _slots;
    
    Host[] _hosts;
    WebClient[] _clients;
    
    HostStats[] _hostStats;
    List<VMStats> _activeVMStats;
    
    WebUtilities _webUtilities;
    DBUtilities _dbUtilities;
    
    Timer _machineStatsTimer;
    int _numberOfMachineStatsPerSlot=0;
    int _currentInstance=0;
    int vmIDs=0;
    
    SchedulerData _cplexData;
    Scheduler scheduler;
    Provider[] _provider;
    
    
    int[] vmsRequested;
    int[] vmsSatisfied;
    int[] vmsDeleted;
    int[] smallVmsRequested;
    int[] smallVmsSatisfied;
    int[] mediumVmsRequested;
    int[] mediumVmsSatisfied;
    int[] largeVmsRequested;
    int[] largeVmsSatisfied;
    
    int[][][][] vmDeactivationMatrix;
    
    Controller(Host[] hosts,WebClient[] clients, Configuration config, Slot[] slots, DBUtilities dbUtilities,Provider[] _provider) {
        
        this._config=config;
        this._slots=slots;
        this._hosts=hosts;
        this._clients=clients;
        this._webUtilities=new WebUtilities(config);
        this._dbUtilities=dbUtilities;
        this._hostStats=new HostStats[_config.getHostsNumber()];
        this._activeVMStats=new ArrayList<>();
        this._numberOfMachineStatsPerSlot=_config.getNumberOfMachineStatsPerSlot();
        this._cplexData=new SchedulerData(config);
        this._provider =_provider;
        
        initializeStatsArrays();
        
        int N=_config.getHostsNumber();
        int P=_config.getProvidersNumber();
        int V=_config.getVmTypesNumber();
        int S=_config.getServicesNumber();
        
        vmDeactivationMatrix = new int [N][P][V][S]; // D[i][j][v][s]: # of removed VMs of v v for service s of provider j from AP i
        
    }
    
    void Run(int slot) throws IOException {
        
        System.out.println("******* Controller *******");
        System.out.println("-- Slot:"+slot);
        
        if(false)
            startNodesStatsTimer(slot); // for Statistics updates
        
        try {
            
            SimulatorStats simulatorStatistics=new SimulatorStats(_config.getProvidersNumber());
            
            int[][][] vmRequestMatrix=loadVMRequestMatrix(slot);             //requestMatrix[v][s][p]
            
            System.out.println("------------------------------------- LoadRequestMatrix - Slot:" +slot);
            for (int j = 0; j < _config.getProvidersNumber(); j++) {
                for (int k = 0; k < _config.getVmTypesNumber(); k++) {
                    for (int l = 0; l < _config.getServicesNumber(); l++) {
                        System.out.print(vmRequestMatrix[j][k][l]);
                        
                    }
                    System.out.println("");
                    
                }
                
            }
            System.out.println("**********************************");
            
            prepareVmDeactivationMatrix(slot,vmDeactivationMatrix,simulatorStatistics);

            System.out.println("**********************************");

            System.out.println("vmDeactivationMatrix - Slot:" +slot);
            for (int i = 0; i < _hosts.length; i++) {
                for (int j = 0; j < _config.getProvidersNumber(); j++) {
                    for (int k = 0; k < _config.getVmTypesNumber(); k++) {
                        for (int l = 0; l < _config.getServicesNumber(); l++) {
                            System.out.print(vmDeactivationMatrix[i][j][k][l]);
                            
                        }
                         System.out.println("");
                        
                    }
                    
                }
            }
             System.out.println("**********************************");
            
            System.out.println("Method Call: Delete VMs");
            deleteVMs(slot);
            
            System.out.println("Method Call: Find Request Pattern");
            int[][] requestPattern=Utilities.findRequestPattern(_config);
            
             System.out.println("Method Call: Update Cplex parameters");
            _cplexData.updateParameters(requestPattern, vmRequestMatrix, vmDeactivationMatrix);
            
            scheduler=new Scheduler(_config);
            
            System.out.println("Method Call: Cplex Run");
          
            CplexResponse cplexResponse=scheduler.Run(_cplexData);
            int[][][][] activationMatrix=cplexResponse.getActivationMatrix();
            double netBenefit=cplexResponse.getNetBenefit();
            
            updateStatisticsObject(vmRequestMatrix,activationMatrix,netBenefit,simulatorStatistics,slot);
            
             System.out.println("MESSAGE: Activation matrix created!");
            
            
            // int[][][][] activationMatrix =tempScheduler(vmRequestMatrix); // activationMatrix[i][j][v][s]: # of allocated VMs of v v for service s of provider j at AP i
            
            // Create the VMs
            VMRequest request=null;
            LoadVM loadObject;
            Thread thread;
            
            
            
             System.out.println("**********************************");

            System.out.println("ActivationMatrix - Slot:" +slot);
            for (int i = 0; i < _hosts.length; i++) {
                for (int j = 0; j < _config.getProvidersNumber(); j++) {
                    for (int v  = 0; v < _config.getVmTypesNumber(); v++) {
                        for (int s = 0; s < _config.getServicesNumber(); s++) {
                            
                            System.out.print("-"+ activationMatrix[i][j][v][s]+" (i:"+i+" j:"+j+" v:"+v+" s:"+s+")");
                            
                        }
                         System.out.println("");
                        
                    }
                    
                }
            }
             System.out.println("**********************************");
            
            
            
            
            
            
            for (int i = 0; i < _config.getHostsNumber(); i++) {
                List<VMRequest> vm2CreatePerHost=cplexSolution2VMRequets(slot, i,activationMatrix);
                
                System.out.println("MESSAGE: Host:"+i+" Bring up: "+vm2CreatePerHost.size()+"VMs");
                
                for (Iterator iterator = vm2CreatePerHost.iterator(); iterator.hasNext();) {
                    request = (VMRequest) iterator.next();
                    
                    loadObject=new LoadVM(slot,request,i,_hosts[i].getNodeName());
                    thread = new Thread(loadObject);
                    thread.start();
                    Thread.sleep(0);
                    //Thread.sleep(5000);
                    
                }
                
            }
            
          
            
            for (int i = 0; i < _config.getProvidersNumber(); i++) {
                _dbUtilities.updateSimulatorStatistics2DB(slot,simulatorStatistics,i);
            }
  
            
            
        } catch (Exception ex) {
            Logger.getLogger(Controller.class.getName()).log(Level.SEVERE, null, ex);
        }
        
    }
    
    // n[i][j][v][s]: # of allocated VMs of v v for service s of provider j at AP i
    private List<VMRequest> cplexSolution2VMRequets(int slot, int hostID, int[][][][] activationMatrix){
        
        int vms2Load=-1;
        
        List<VMRequest> vmList2Create=new ArrayList<>();
        List<VMRequest> listOfRequestedVMs;
        
        String _vmType="";
        
        for (int p = 0; p < _config.getProvidersNumber(); p++) {
            
            listOfRequestedVMs=_slots[slot].getVmRequests2Activate()[p];
            
            for (int v = 0; v < _config.getVmTypesNumber(); v++) {
                
                if(v==0)
                    _vmType=EMachineTypes.small.toString();
                else if(v==1)
                    _vmType=EMachineTypes.medium.toString();
                else if(v==2)
                    _vmType=EMachineTypes.large.toString();
                
                for (int s = 0; s < _config.getServicesNumber(); s++) {
                    
                    vms2Load=activationMatrix[hostID][p][v][s];
                    
                    for(Iterator iterator = listOfRequestedVMs.iterator(); iterator.hasNext();) {
                        VMRequest nextRequest = (VMRequest)iterator.next();
                        
                        if(nextRequest.getServiceID()==s&&nextRequest.getVmType().equals(_vmType))
                        {
                            while(vms2Load>0){
                                vmList2Create.add(nextRequest);
                                vms2Load--;
                            }
                        }
                        
                    }
                }
            }
            
        }
        
        // Returns A list of VMs to activate for a specific host
        return vmList2Create;
    }
    
    private void deleteVMs(int slot) throws IOException, InterruptedException {
        
        List<Integer> requestID2RemoveThisSlot=new ArrayList<>();
        
        // Step 1: Find RequestIDs to remove
        for (int i = 0; i < _config.getProvidersNumber(); i++) {
            for (int j = 0; j < _slots[slot].getVmRequests2Remove()[i].size(); j++) {
                requestID2RemoveThisSlot.add(_slots[slot].getVmRequests2Remove()[i].get(j).getRequestID());
            }
        }
        
        // Step 2: Find the VMs based on the requestID to remove and Update Host object
        
        for (int i = 0; i < _config.getHostsNumber(); i++) {
            for (int j = 0; j < _hosts[i].getVMs().size(); j++) {
                if(requestID2RemoveThisSlot.contains(_hosts[i].getVMs().get(j).getVmReuestId())){
                    _hosts[i].getVMs().get(j).setSlotDeactivated(slot);
                    _hosts[i].getVMs().get(j).setActive(false);
                }
            }
        }
        
        /* Block To Activate in real system
        String hostName="";
        String vmName="";
        
        Thread thread;
        //Step 3: Delete the VM
        for (int i = 0; i < _config.getHostsNumber(); i++) {
            hostName=_hosts[i].getNodeName();
            
            for (int j = 0; j < _hosts[i].getVMs().size(); j++) {
                
                if(requestID2RemoveThisSlot.contains(_hosts[i].getVMs().get(j).getVmReuestId())&!_hosts[i].getVMs().get(j).isActive()){
                    vmName=_hosts[i].getVMs().get(j).getVmName();
                    
                     
                    
                    DeleteVM deleter=new DeleteVM(vmName,hostName);
                    thread=new Thread(deleter);
                    thread.start();
                    Thread.sleep(5000);
                            
                     
                    
                }
            }
        }*/
        
         System.out.println("Method Call: Delete VMs Called");
    }
    
    
    private int[][][] loadVMRequestMatrix(int slot) {
        
        int[][][] requestMatrix=new int[_config.getProvidersNumber()][_config.getVmTypesNumber()][_config.getServicesNumber()];//: # of allocated VMs of v v for service s of provider j at AP i
        List<VMRequest> listOfRequestedVMs=null;
        int v=-1;
        int s=-1;
        
        for (int p = 0; p < _config.getProvidersNumber(); p++) {
            
            listOfRequestedVMs=_slots[slot].getVmRequests2Activate()[p];
            
            for (VMRequest nextRequest : listOfRequestedVMs) {
                
                if(EMachineTypes.small.toString().equals(nextRequest.getVmType()))
                    v=0;
                else if(EMachineTypes.medium.toString().equals(nextRequest.getVmType()))
                    v=1;
                else if(EMachineTypes.large.toString().equals(nextRequest.getVmType()))
                    v=2;
                
                s=nextRequest.getServiceID();
            
                
                requestMatrix[p][v][s]++;
            }
            
        }
        
        return requestMatrix;
    }
    
    private void prepareVmDeactivationMatrix(int slot,int[][][][] deactivationMatrix,SimulatorStats simulatorStatistics) {
        
        int N=_config.getHostsNumber();
        int P=_config.getProvidersNumber();
        int V=_config.getVmTypesNumber();
        int S=_config.getServicesNumber();
       
        List<VMRequest> vmRequests2RemoveThisSlot=Utilities.findVMequests2RemoveThisSlot(slot,_slots,_config);
        
        for (int i=0;i<N;i++)
            for (int j=0;j<P;j++)
                for (int v=0;v<V;v++)
                    for (int s=0;s<S;s++)
                    {
                        deactivationMatrix[i][j][v][s]=0;
                    }
        
        
        int hostID=-1;
        int providerID=-1;
        int vmTypeID=-1;
        int serviceID=-1;
        
        for (Iterator iterator = vmRequests2RemoveThisSlot.iterator(); iterator.hasNext();) {
            VMRequest next = (VMRequest)iterator.next();
            
            for (int i = 0; i < _hosts.length; i++) {
                for (int j = 0; j < _hosts[i].getVMs().size(); j++) {
                    
                    if(_hosts[i].getVMs().get(j).getVmReuestId()==next.getRequestID()&&_hosts[i].getVMs().get(j).isActive()){
                    
                    hostID=i;
                    providerID=next.getProviderID();
                    serviceID=next.getServiceID();
                    
                    switch (next.getVmType()){
                        case "small":
                            vmTypeID=0;
                            break;
                        case "medium":
                            vmTypeID=1;
                            break;
                        case "large":
                            vmTypeID=2;
                            break;
                    }
                    
                  
                    deactivationMatrix[hostID][providerID][vmTypeID][serviceID]++;
                    simulatorStatistics.getVmsDeleted()[providerID]++;
                    
                }
                
                      
                }
                
            }
            
            
        }
        
     
    }
    
    private int[][][][] tempScheduler(double[][][] vmRequestMatrix) {
        // activationMatrix[i][j][v][s]: # of allocated VMs of v v for service s of provider j at AP i
        //requestMatrix[v][s][p]
        
        int[][][][] activationMatrix=new int[_config.getHostsNumber()][_config.getProvidersNumber()][_config.getVmTypesNumber()][_config.getServicesNumber()];
        
        for (int j = 0; j < _config.getProvidersNumber(); j++) {
            for (int v = 0; v < _config.getVmTypesNumber(); v++) {
                for (int s = 0; s < _config.getServicesNumber(); s++) {
                    activationMatrix[0][j][v][s]=(int)vmRequestMatrix[v][s][j];
                    
                }
            }
            
        }
        
        return activationMatrix;
        
        
        
    }

    private void updateStatisticsObject(int[][][] vmRequestMatrix, int[][][][] activationMatrix,double netBenefit,SimulatorStats simulatorStatistics,int slot) {
        
       
        
        int smallVMsRequestedSlot=0;
        int smallVMsSatisfiedSlot=0;
        int mediumVMsRequestedSlot=0;
        int mediumVMsSatisfiedSlot=0;
        int largeVMsRequestedSlot=0;
        int largeVMsSatisfiedSlot=0;
      
        int numberOfRequests=0;
        int totalNumberOfRequestsRequestedSlot=0;
        int totalNumberOfRequestsSatisfiedSlot=0;
         
        // Update Requested
        for (int p = 0; p < _config.getProvidersNumber(); p++) {
            smallVMsRequestedSlot=0;
            mediumVMsRequestedSlot=0;
            largeVMsRequestedSlot=0;
            totalNumberOfRequestsRequestedSlot=0;
            
            for (int v = 0; v < _config.vmTypesNumber; v++) {
                for (int s = 0; s < _config.getServicesNumber(); s++) {
                    numberOfRequests=vmRequestMatrix[p][v][s];
                    totalNumberOfRequestsRequestedSlot+=vmRequestMatrix[p][v][s];
                    if(v==0)
                        smallVMsRequestedSlot+=numberOfRequests;
                    else if(v==1)
                        mediumVMsRequestedSlot+=numberOfRequests;
                     else if(v==2)
                        largeVMsRequestedSlot+=numberOfRequests;
                }
            }
            simulatorStatistics.getVmsRequestedSlot()[p]=totalNumberOfRequestsRequestedSlot;
            simulatorStatistics.getSmallVmsRequestedSlot()[p]=smallVMsRequestedSlot;
            simulatorStatistics.getMediumVmsRequestedSlot()[p]=mediumVMsRequestedSlot;
            simulatorStatistics.getLargeVmsRequestedSlot()[p]=largeVMsRequestedSlot;
            
            vmsRequested[p]+=totalNumberOfRequestsRequestedSlot;
            smallVmsRequested[p]+=smallVMsRequestedSlot;
            mediumVmsRequested[p]+=mediumVMsRequestedSlot;
            largeVmsRequested[p]+=largeVMsRequestedSlot;
            
            simulatorStatistics.getVmsRequested()[p]=vmsRequested[p];
            simulatorStatistics.getSmallVmsRequested()[p]=smallVmsRequested[p];
            simulatorStatistics.getMediumVmsRequested()[p]=mediumVmsRequested[p];
            simulatorStatistics.getLargeVmsRequested()[p]= largeVmsRequested[p];
            
        }
        
        // Update Satisfied
        
            for (int p = 0; p < _config.getProvidersNumber(); p++) {
                smallVMsSatisfiedSlot=0;
                mediumVMsSatisfiedSlot=0;
                largeVMsSatisfiedSlot=0;
                totalNumberOfRequestsSatisfiedSlot=0;
             
                for (int i = 0; i < _hosts.length; i++) {
                    for (int v = 0; v < _config.vmTypesNumber; v++) {
                        for (int s = 0; s < _config.getServicesNumber(); s++) {
                            numberOfRequests=activationMatrix[i][p][v][s];
                            totalNumberOfRequestsSatisfiedSlot+=numberOfRequests;
                            if(v==0)
                                smallVMsSatisfiedSlot+=numberOfRequests;
                            else if(v==1)
                                mediumVMsSatisfiedSlot+=numberOfRequests;
                             else if(v==2)
                                largeVMsSatisfiedSlot+=numberOfRequests;
                        }
                    }
                    
                }
                
                simulatorStatistics.getVmsSatisfiedSlot()[p]=totalNumberOfRequestsSatisfiedSlot;
                simulatorStatistics.getSmallVmsSatisfiedSlot()[p]=smallVMsSatisfiedSlot;
                simulatorStatistics.getMediumVmsSatisfiedSlot()[p]=mediumVMsSatisfiedSlot;
                simulatorStatistics.getLargeVmsSatisfiedSlot()[p]=largeVMsSatisfiedSlot;

                vmsSatisfied[p]+=totalNumberOfRequestsSatisfiedSlot;
                smallVmsSatisfied[p]+=smallVMsSatisfiedSlot;
                mediumVmsSatisfied[p]+=mediumVMsSatisfiedSlot;
                largeVmsSatisfied[p]+=largeVMsSatisfiedSlot;

                simulatorStatistics.getVmsSatisfied()[p]=vmsSatisfied[p];
                simulatorStatistics.getSmallVmsSatisfied()[p]=smallVmsSatisfied[p];
                simulatorStatistics.getMediumVmsSatisfied()[p]=mediumVmsSatisfied[p];
                simulatorStatistics.getLargeVmsSatisfied()[p]= largeVmsSatisfied[p];

                simulatorStatistics.setNetBenefit(netBenefit);
                simulatorStatistics.setSlot(slot);
        }
    }

    private void initializeStatsArrays() {
        vmsRequested=new int[_config.getProvidersNumber()];
        vmsSatisfied=new int[_config.getProvidersNumber()];
        vmsDeleted=new int[_config.getProvidersNumber()];
        
        smallVmsRequested=new int[_config.getProvidersNumber()];
        smallVmsSatisfied=new int[_config.getProvidersNumber()];
        mediumVmsRequested=new int[_config.getProvidersNumber()];
        mediumVmsSatisfied=new int[_config.getProvidersNumber()];
        largeVmsRequested=new int[_config.getProvidersNumber()];
        largeVmsSatisfied=new int[_config.getProvidersNumber()];
        
        
        for (int i = 0; i < _config.getProvidersNumber(); i++) {
            vmsRequested[i]=0;
            vmsSatisfied[i]=0;
            vmsDeleted[i]=0;
            smallVmsRequested[i]=0;
            smallVmsSatisfied[i]=0;
            mediumVmsRequested[i]=0;
            mediumVmsSatisfied[i]=0;
            largeVmsRequested[i]=0;
            largeVmsSatisfied[i]=0;
        }
 
    }
    
    class DeleteVM implements Runnable{
        
        private Thread _thread;
        private String threadName;
        private String hostName;
        private String vmName;
        
        public boolean deleted=false;
        
        DeleteVM(String hostName,String vmName){
            
            this.hostName=hostName;
            this.vmName=vmName;
            threadName = hostName+"-"+vmName;
            
            System.out.println("Creating " +  threadName );
        }
        public void run() {
            
            try {
                
                System.out.println("Delete Thread: " + threadName + " started");
                
                _webUtilities.deleteVM(hostName, vmName);
                
                // Let the thread sleep for a while.
                Thread.sleep(0);
                
            } catch (Exception e) {
                System.out.println("Thread " +  threadName + " interrupted.");
            }
            
            System.out.println("Delete Thread " +  threadName + " finished.");
            deleted=true;
        }
        
        
        public boolean isDeleted() {
            return deleted;
        }
        
        
        
        
    }
    
    public class LoadVM implements Runnable{
        
        private Thread _thread;
        String threadName;
        String hostName;
        VMRequest request;
        
        public boolean loaded=false;
        int slot;
        int hostID;
        Hashtable hostVMpair;
        
        LoadVM(int slot,VMRequest request,int hostID,String hostName){
            
            this.slot=slot;
            this.hostName=hostName;
            this.hostID=hostID;
            this.request=request;
            this.threadName = hostName+"-"+request.getRequestID();
            this. hostVMpair=new Hashtable();
            
            System.out.println("Creating " +  threadName );
        }
        
        public void run() {
            
            try {
                
                System.out.println("Load VM Thread: " + threadName + " started");
                
                /* Block To Activate in real system
                createVM(slot,request,hostName);
                startVM(request,hostName);
                */        
                createVMobject(slot,request,hostID,hostName);
                
                Thread.sleep(0);
                
            } catch (Exception e) {
                System.out.println("Thread " +  threadName + " interrupted.");
            }
            
            System.out.println("Delete Thread " +  threadName + " finished.");
            loaded=true;
        }
        
        
        
        
        private boolean createVM(int slot,VMRequest request,String nodeName) throws IOException {
            
            Hashtable vmParameters;
            
            boolean vmCreated=false;
            boolean vmCreateCommandSend=false;
            
            
            System.out.println("provider:"+request.providerID+" - activate: "+request.getRequestID());
            
            //Step 1: Add VM on the Physical node
            vmParameters=Utilities.determineVMparameters(request,nodeName);
            vmCreateCommandSend=_webUtilities.createVM(vmParameters);
            
            
            return vmCreateCommandSend;
            
            
        }
        
        private void startVM(VMRequest request, String hostName) throws IOException {
            
            Boolean vmCreated=false;
            Hashtable  vmParameters=Utilities.determineVMparameters(request,hostName);
            int counter=0;
            
            while(!vmCreated&counter<200){
                
                vmCreated=_webUtilities.checkVMListOnHost(hostName,String.valueOf(vmParameters.get("vmName")));
                System.out.println("Bring VM up attempt:"+ counter+"-requestID:"+request.getRequestID());
                
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    // handle the exception...
                    // For example consider calling Thread.currentThread().interrupt(); here.
                }
                counter++;
            }
            
           if(counter==200){
               System.out.println("Failed to load requestID:"+request.getRequestID());
           }
            String vmName=String.valueOf(vmParameters.get("vmName"));
            _webUtilities.startVM(vmName,hostName);
            
        }
        
        private void createVMobject(int slot, VMRequest request, int hostID,String hostName) {
            
            Hashtable  vmParameters=Utilities.determineVMparameters(request,hostName);
            
                _hosts[hostID].getVMs().add(new VM(vmParameters,request,slot,vmIDs,hostID,_hosts[hostID].getNodeName(),_config));
                vmIDs++;
            
        }
        
    }
    
    class StatisticsTimer extends TimerTask {
        
        int slot;
        
        StatisticsTimer(int slot){
            this.slot=slot;
        }
        
        public void run() {
            
            if(_currentInstance<_numberOfMachineStatsPerSlot){
                
                try {
                    
                    _dbUtilities.updateAllHostStatistics(slot,_currentInstance);
                    _dbUtilities.updateAllHostStatistics2DB(slot,_currentInstance);
                    _dbUtilities.updateAllHostStatisticsInterfacesDB(slot,_currentInstance);
                    
                    _dbUtilities.updateActiveVMStatistics(slot,_currentInstance);
                    
                    _currentInstance++;
                    
                } catch (IOException ex) {
                    Logger.getLogger(Controller.class.getName()).log(Level.SEVERE, null, ex);
                } catch (JSONException ex) {
                    Logger.getLogger(Controller.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            else{
                _machineStatsTimer.cancel();
            }
            
        }
        
        
    }
    
    private void startNodesStatsTimer(int slot) {
        
        int statsUpdateInterval=_config.getSlotDuration()/_config.getNumberOfMachineStatsPerSlot();
        
        _machineStatsTimer = new Timer();
        _currentInstance=0;
        
        if(_config.getSlotDurationMetric().equals(ESlotDurationMetric.milliseconds.toString()))
            _machineStatsTimer.scheduleAtFixedRate(new StatisticsTimer(slot),0 ,statsUpdateInterval);
        else if(_config.getSlotDurationMetric().equals(ESlotDurationMetric.seconds.toString()))
            _machineStatsTimer.scheduleAtFixedRate(new StatisticsTimer(slot),0 ,statsUpdateInterval*1000);
        else if(_config.getSlotDurationMetric().equals(ESlotDurationMetric.minutes.toString()))
            _machineStatsTimer.scheduleAtFixedRate(new StatisticsTimer(slot),0 ,60*statsUpdateInterval*1000);
        else if(_config.getSlotDurationMetric().equals(ESlotDurationMetric.hours.toString()))
            _machineStatsTimer.scheduleAtFixedRate(new StatisticsTimer(slot),0 ,3600*statsUpdateInterval*1000);
    }

    public SchedulerData getCplexData() {
        return _cplexData;
    }
    
    
    
    
}
