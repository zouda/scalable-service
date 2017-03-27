import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public class MiddleTier extends UnicastRemoteObject implements MiddleTierRMI {
    
    private ServerLib SL;
    private int id;
    private int overlookCounter = 0;
    private CoordinatorRMI coordinator;
    private int overloadCounter = 0;
    private int underloadCounter = 0;
    
    // tuned parameters:
    private float overloadThreshold = (float)(0.38);
    private float underloadThreshold = (float)(0.25);
    private int maxAllowableOverload = 3;
    private int maxAllowableUnderload = 1;
    private int overlookThreshold = 15;
    
    private int getRequestMiss = 0;
    /**
     * Middle Tier Initialization
     */
    public MiddleTier(String ip, int port, ServerLib SL, int id, CoordinatorRMI coordinator) throws Exception {
        // Create new RMI instance for the middle tier
        super(0);
        String url = String.format("//%s:%d/%d", ip, port, id);
        Naming.rebind(url, this);
        
        System.err.println("Middle Tier starts.");
        
        this.SL = SL;
        this.id = id;
        this.coordinator = coordinator;
    }
    
//    private void autoScaling() throws RemoteException {
//        float CPUload = Cloud.getCPUload();
//        System.err.println(CPUload);
//        
////        // overlook first coming unstable loads
////        if (overlookCounter < overlookThreshold) { 
////            overlookCounter++;
////            return;
////        }
//        
//        if (CPUload >= overloadThreshold) {
//            overloadCounter++;
//            if (overloadCounter >= maxAllowableOverload) {
//                overloadCounter = 0;
//                coordinator.reportOverloadCPU(id);
//            }
//        }
//        else {
//            if (overloadCounter > 0)
//                overloadCounter--;
//        }
//        
//        if (CPUload < underloadThreshold) {
//            underloadCounter++;
//            if (underloadCounter >= maxAllowableUnderload) {
//                underloadCounter = 0;
//                coordinator.reportUnderloadCPU(id);
//            }
//        }
//        else {
//            if (underloadCounter > 0)
//                underloadCounter--;
//        }
//    }
    
    /**
     * [RMI Implementation]
     * remote version of processRequest
     */
    public void processRequest(Cloud.FrontEndOps.Request request) throws RemoteException {
        SL.processRequest(request);
        //autoScaling();
    }

    public void unregisterMiddleTier() throws RemoteException {
        SL.shutDown();
        try {
            UnicastRemoteObject.unexportObject(this, true);
        } catch (Exception e) {
            System.err.println("error in unexport");
        }
    }
    
    public void run() {
        Thread t = new Thread(new Runnable() {
            public void run() {
                while (true) {
                    try {
                        Cloud.FrontEndOps.Request request = coordinator.getRequest();
                        if (request != null) {
                            SL.processRequest(request);
                        } 
                    } catch (Exception e) {
              
                    }
                }
            }
        });
        t.start();
    }
}
