/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Controller;

import Enumerators.EGeneratorType;
import Enumerators.ESlotDurationMetric;
import Statistics.ABStats;
import Statistics.DBClass;
import Statistics.DBUtilities;
import Utilities.Utilities;
import Utilities.WebUtilities;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import jsc.distributions.Exponential;
import jsc.distributions.Pareto;

import java.io.IOException;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.JSONException;
/**
 *
 * @author kostas
 */
public class Simulator {
    
   int slot=0;

    Configuration _config;

    List<String> _hostNames;
    List<String> _clientNames;
    
    Host[] _hosts;
    WebClient[] _webClients;
    Slot[] _slots;

    Controller _controller;

   // How to create requests per Service Domain, one per provider
    String[] _rateGeneratorType; 
    Exponential[] _rateExponentialGenerator;
    Pareto[] _rateParetoGenerator;

    // Lifetime of VM one per provider
    String[] _lifetimeGeneratorType; 
    Exponential[] _lifetimeExponentialGenerator;
    Pareto[] _lifetimeParetoGenerator;

    DBClass _db;
    DBUtilities _dbUtilities;
    Random rand;

    long experimentStart;
    long experimentStop;

    Timer controllerTimer;
    Timer[] _clientsTimer;

    WebUtilities _webUtility;
    
    ServiceRequestsPattern[] _servicePattern; // Per Provider. Each provider has a List of Services
    
        public Simulator(){
           
           this._config=new Configuration();
           this._hostNames=_config.getHostNames();
           this._clientNames=_config.getClientNames();
           this.controllerTimer = new Timer();
           this._clientsTimer=new Timer[_clientNames.size()];
           
          
           this._webUtility=new WebUtilities(_config); 
           this._db=new DBClass();
           this._dbUtilities=new DBUtilities(_hosts, _webUtility,_db);
           this._servicePattern=new ServiceRequestsPattern[_config.getProvidersNumber()];
           
           
           System.out.println("********** System Initialization Phase ****************");
           
           initializeNodesAndSlots(); //creates: Hosts, Clients, Slots
           initializeRateGenerators(); 
           initializeVmLifetimeGenerators();
           initializeRequestPattern();
           addInitialVmEvents();
           
           this._controller=new Controller(_hosts,_webClients,_config,_slots,_dbUtilities); 
           
           System.out.println("********** End of System Initialization Phase **************");
           System.out.println();
           
           if (false)
              this.startClientsRequests();
       }
      

       


    private void initializeRateGenerators()
    {
         _rateGeneratorType=new String[_config.getProvidersNumber()];

        for (int i = 0; i < _config.getProvidersNumber(); i++) {
            _rateGeneratorType[i]=_config.getVmRequestRateConfig()[i].get("vmRate_type").toString();
        }

        // 2. Build the array of generators
        double lamda;
        double location;
        double shape;

        this._rateExponentialGenerator=new Exponential[_config.getProvidersNumber()];
        this._rateParetoGenerator=new Pareto[_config.getProvidersNumber()];

            for (int i = 0; i < _config.getProvidersNumber(); i++) {

                if(_rateGeneratorType[i].equals(EGeneratorType.Exponential.toString())){

                    lamda=((Double)_config.getVmRequestRateConfig()[i].get("mean"));
                    this._rateExponentialGenerator[i]=new Exponential(lamda);
                    this._rateParetoGenerator[i]=null;
                }
                else if(_rateGeneratorType[i].equals(EGeneratorType.Pareto.toString())){

                  _rateExponentialGenerator[i]=null;  
                  location=((Double)_config.getVmRequestRateConfig()[i].get("location"));
                  shape =((Double)_config.getVmRequestRateConfig()[i].get("shape"));

                  this._rateParetoGenerator[i]=new Pareto(location, shape);
                  double mean=_rateParetoGenerator[i].mean();
                  System.out.print(mean);
                }

            }      

    }

    private void initializeVmLifetimeGenerators()
    {
             _lifetimeGeneratorType=new String[_config.getProvidersNumber()];

            for (int i = 0; i < _config.getProvidersNumber(); i++) {
                _lifetimeGeneratorType[i]=_config.getVmRequestRateConfig()[i].get("vmRate_type").toString();
            }

            // 2. Build the array of generators
            double lamda;
            double location;
            double shape;

            this._lifetimeExponentialGenerator=new Exponential[_config.getProvidersNumber()];
            this._lifetimeParetoGenerator=new Pareto[_config.getProvidersNumber()];

                for (int i = 0; i < _config.getProvidersNumber(); i++) {

                    if(_lifetimeGeneratorType[i].equals(EGeneratorType.Exponential.toString())){

                        lamda=((Double)_config.getVmRequestRateConfig()[i].get("mean"));
                        this._lifetimeExponentialGenerator[i]=new Exponential(lamda);
                        this._lifetimeParetoGenerator[i]=null;
                    }
                    else if(_lifetimeGeneratorType[i].equals(EGeneratorType.Pareto.toString())){

                      _lifetimeExponentialGenerator[i]=null;  
                      location=((Double)_config.getVmRequestRateConfig()[i].get("location"));
                      shape =((Double)_config.getVmRequestRateConfig()[i].get("shape"));

                      this._lifetimeParetoGenerator[i]=new Pareto(location, shape);
                      double mean=_lifetimeParetoGenerator[i].mean();
                      System.out.print(mean);
                    }

                }      

        }
       
   
    
    private void initializeNodesAndSlots() {
    
        // Initialize Hosts
        this._hosts=new Host[_config.getHostNames().size()];
        
        for (int i = 0; i < _hosts.length; i++) {
            _hosts[i]=new Host(i,_config,_hostNames.get(i));
        }
        
        System.out.println("Simulator Initialization: Host Objects - OK");
        
        // Initialize Clients
        this._webClients=new WebClient[_config.getClientNames().size()];
         
        for (int i = 0; i < _webClients.length; i++) {
            _webClients[i]=new WebClient(_config,i,0,_clientNames.get(i),_controller);
            
            _clientsTimer[i]=new Timer();
           
        }
        
        System.out.println("Simulator Initialization: Client Objects - OK");
        
        // Initialize Slots
        _slots=new Slot[_config.getNumberOfSlots()];

        for (int i = 0; i < _config.getNumberOfSlots(); i++) {
            _slots[i]=new Slot(i,_config);

        }
        
          System.out.println("Simulator Initialization: Slot Objects - OK");

    }
    
    
    private void startClientsRequests(){
    
     System.out.println("********** Clients Requests Loader ****************");
     for (int i = 0; i < _webClients.length; i++) {
                _clientsTimer[i].schedule(new ExecuteClientRequest(i,0),500); //Start the Client Requests (initial delay 100)
                
     }
     
     System.out.println("****** All Clients Request Generators Loaded ******");
     System.out.println();
    }
        
    private void addInitialVmEvents() {
         
            int runningSlot=0;
            int vmRequests=1;
            //add first VM in slot 0
            CreateNewVMRequest(0,runningSlot,true);
            
            for (int i = 0; i < _config.getProvidersNumber(); i++) {
                
                runningSlot=0;
                
                while(runningSlot<_config.getNumberOfSlots()){
                    
                    runningSlot=CreateNewVMRequest(i,runningSlot,false);
                    vmRequests++;

                }
            }
            
            System.out.println("Simulator Initialization:"+vmRequests+" VM Rquests Added for all Experiment");
            
        }
    
    //Returns the new running slot (this can be also 0)    
    private int CreateNewVMRequest(int providerID,int currentSlot,boolean firstSlot)
    {
        int slot2AddVM=0;
        int slot2RemoveVM=0;
        
        int lifetime=calculateVMLifeTime(providerID);
            
        if(lifetime<1)
                lifetime=1;
        
         // Slot calculation
        int slotDistance;
        
        if(firstSlot)
           slotDistance=0;
        else 
           slotDistance= calculateSlotsAway(providerID);
        
        slot2AddVM=currentSlot+slotDistance;
        slot2RemoveVM=slot2AddVM+lifetime;
        
        if(slot2AddVM<_config.getNumberOfSlots()){
            
            int serviceType=Utilities.determineVMService(providerID,_config);
             
            List<VMRequest> newRequest = new ArrayList<>();
            
            List<String> vms=Utilities.determineVMs(_config,serviceType,_servicePattern[providerID],Utilities.findActiveVMs(slot, _hosts));
        
            if(!vms.isEmpty()){
                
                int index=0;
                
                while(index< vms.size()) {
                        newRequest.add(new VMRequest(_config,providerID,lifetime));
                        newRequest.get(newRequest.size()-1).setService(_config.getServicesNames().get(serviceType));
                        newRequest.get(newRequest.size()-1).setSlotStart(slot2AddVM);
                        newRequest.get(newRequest.size()-1).setVmType(vms.get(index));
                        
                       _slots[slot2AddVM].getVmRequests2Activate()[providerID].add(newRequest.get(newRequest.size()-1));

                     //remove vm during this slot
                    newRequest.get(newRequest.size()-1).setSlotEnd(slot2RemoveVM);
                    
                    if(slot2RemoveVM<_config.getNumberOfSlots())
                        _slots[slot2RemoveVM].getVmRequests2Remove()[providerID].add(newRequest.get(newRequest.size()-1)); 

                    index++;
                    }
                    
            }
              
        
        }
        else
            System.out.println("failed to add request" );
         
        
        
         return slot2AddVM;

    }

    
    private int calculateSlotsAway(int providerID) {
     
    		
       int interArrivalTime=-1;

       double min=0;
       double max=0;
        Double value;
        switch (EGeneratorType.valueOf(this._rateGeneratorType[providerID])){
            case Exponential:
                value=_rateExponentialGenerator[providerID].random();
                interArrivalTime = value.intValue();
                break;
            
            case Pareto:
                value=_rateParetoGenerator[providerID].random();
                interArrivalTime=value.intValue();
                                
                break;
            
            
            case Random: 
               min= Double.valueOf(_config.getVmRequestRateConfig()[providerID].get("min").toString());
               max= Double.valueOf(_config.getVmRequestRateConfig()[providerID].get("max").toString());
                
               interArrivalTime = Utilities.randInt((int)min, (int)max);
                break;
           
           
            default:            
                break;
        }
        

        return interArrivalTime;
        
        }
   
     private int calculateVMLifeTime(int providerID) {
     
    		
        int lifetime=-1;

        double min=0;
        double max=0;
        Double value;
        
        switch (EGeneratorType.valueOf(this._lifetimeGeneratorType[providerID])){
            case Exponential:
                value=_lifetimeExponentialGenerator[providerID].random();
                lifetime = value.intValue();
                break;
            
            case Pareto:
                value=_lifetimeParetoGenerator[providerID].random();
                lifetime=value.intValue();
                                
                break;
            
            
            case Random: 
               min= Double.valueOf(_config.getVmLifeTimeConfig()[providerID].get("min").toString());
               max= Double.valueOf(_config.getVmLifeTimeConfig()[providerID].get("max").toString());
                
               lifetime = Utilities.randInt((int)min, (int)max);
                break;
           
           
            default:            
                break;
        }
        
 
        if(lifetime!=0)
            return lifetime;
        else 
            return 3;
        
    }
        
     
     public final void StartExperiment() {

            int duration=_config.getSlotDuration();

            experimentStart=System.currentTimeMillis();
            
            System.out.println("Simulator started! Time instant: " +experimentStart);
            
            if(_config.getSlotDurationMetric().equals(ESlotDurationMetric.milliseconds.toString()))
               controllerTimer.scheduleAtFixedRate(new RunSlot(),0 ,duration);
            else if(_config.getSlotDurationMetric().equals(ESlotDurationMetric.seconds.toString()))
               controllerTimer.scheduleAtFixedRate(new RunSlot(),0 ,duration*1000);
            else if(_config.getSlotDurationMetric().equals(ESlotDurationMetric.minutes.toString()))
               controllerTimer.scheduleAtFixedRate(new RunSlot(),0 ,60*duration*1000);
            else if(_config.getSlotDurationMetric().equals(ESlotDurationMetric.hours.toString()))
               controllerTimer.scheduleAtFixedRate(new RunSlot(),0 ,3600*duration*1000);
            
        }

    private void initializeRequestPattern() {
     
        
        
        for (int i = 0; i < _config.getProvidersNumber(); i++) {
           
                _servicePattern[i].
                        
        
        
        }
        
    }

            
    
    class ExecuteClientRequest extends TimerTask {

        int clientID;
        int measurement=0;
        
        public ExecuteClientRequest(int clientID,int measurement) {
            this.measurement=measurement;
            this.clientID=clientID;
        }

        
         @Override
         public void run() {
            
            try {
                int duration=_config.getSlotDuration();
                
                if(_config.getSlotDurationMetric().equals(ESlotDurationMetric.seconds.toString()))
                    duration=1000*duration;
                else if(_config.getSlotDurationMetric().equals(ESlotDurationMetric.minutes.toString()))
                    duration=60*duration*1000;
                else if(_config.getSlotDurationMetric().equals(ESlotDurationMetric.hours.toString()))
                    duration=3600*duration*1000;
                
                int delay = (5 + new Random().nextInt(duration));
                
                _clientsTimer[clientID].schedule(new ExecuteClientRequest(clientID,measurement+1), delay);
                
                System.out.println("Client: "+clientID+" slot: "+slot+" delay "+delay);
                
                String clientName=_clientNames.get(clientID);
                String service = Utilities.chooseService2Call(_config,clientID); //Choose Service to Run
                int providerID=_webClients[clientID].getProviderID(); //Find the provider he belongs
                String vmIP=chooseVMforService(service, providerID,clientName);   //Find the hosting VM of the Service
                
                ABStats abStats=_webUtility.retrieveStatsABPerClient(clientName,vmIP); //getFromClient
                _dbUtilities.updateABStatistics2DB(slot,measurement,abStats); //send2DB
                
                updateServiceRequestPattern();
                
                
            } catch (IOException | JSONException ex) {
                Logger.getLogger(Simulator.class.getName()).log(Level.SEVERE, null, ex);
            }
                      
                      
            }

        private void updateServiceRequestPattern() {
            //update this objects
            //_servicePattern
                    
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

       
    }
        
    
    
    //Algorithm: Choose a VM in the hosting Node else choose at Random
        
    private String chooseVMforService(String service, int providerID, String webClient){
        
        String vmIP="";
        String hostApName="";
        Random random=new Random();
                
        
        //Step 1: Find the hosting node
        for (int i = 0; i <_webClients.length; i++) {
            if(_webClients[i].getClientName().equals(webClient))
                hostApName=_webClients[i].getApName();
        }
 
        //Step 2: Find all the VMs that can be used
        
        List<VM> potentialVMs=new ArrayList<>();
     
           for (Host _host : _hosts) {
               for (Iterator iterator = _host.getVMs().iterator(); iterator.hasNext();) {
                   VM nextVM = (VM)iterator.next();
                   
                   if(nextVM.isActive()&nextVM.getProviderID()==providerID&nextVM.getService().equals(service)){
                       potentialVMs.add(nextVM);
                   }
               }
           }
           
           if(0==potentialVMs.size()){
               if("AB".equals(service)){
                  return _config.getCloudVM_AB_IPs().get(random.nextInt(_config.getCloudVM_AB_number()-1));
                }
               else {
                 return _config.getCloudVM_VLC_IPs().get(random.nextInt(_config.getCloudVM_VLC_number()-1));
               }
                        
            }
                        
        //Step 3: Find the local VM
           for (Iterator iterator = potentialVMs.iterator(); iterator.hasNext();) {
               VM nextVM = (VM)iterator.next();
              
               if(hostApName.equals(nextVM.getHostname()))
                    vmIP=nextVM.getIp();
        }
        
        return vmIP;
    }
    
        
    class RunSlot extends TimerTask {

            public void run() {
             
                try {
                    if(slot<_config.getNumberOfSlots()){

                        _controller.Run(slot);

                        slot++; 
                    
                    }else{
                        experimentStop=System.currentTimeMillis();
                        controllerTimer.cancel();
                       _db.getOmlclient().close();
                        System.exit(0);
                    }
                } 
                catch (IOException ex) {
                    Logger.getLogger(Simulator.class.getName()).log(Level.SEVERE, null, ex);
                }
              
            }
    }
}
