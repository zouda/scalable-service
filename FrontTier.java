import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

/**
 * 
 * FrontTier VM Instance
 *
 */
public class FrontTier extends UnicastRemoteObject implements FrontTierRMI{
    
    private ServerLib SL;
    private CoordinatorRMI coordinator;
    private int id;
    
//    public int connectMiddleTier(String ip, int port, int middle_id) {
//        try {
//            String url = String.format("//%s:%d/%d", ip, port, middle_id);
//            middleTier = (MiddleTierRMI) Naming.lookup(url);
//            System.err.println("middleTier Connected");
//            return 0;
//        } catch (Exception e) {
//            return -1;
//        }
//    }
    
    /**
     * [RMI Implementation]
     * unregister this FrontTier instance
     */
    public synchronized void unregisterFrontTier() throws RemoteException {
        SL.unregister_frontend();
        try {
            Thread.sleep(1000); 
        } catch (Exception e) {
            
        }
        SL.shutDown();
        UnicastRemoteObject.unexportObject(this, true);
        SL.endVM(id);
    }
    
    /**
     * Front Tier Initialization
     */
    public FrontTier(String ip, int port, ServerLib SL, int id, CoordinatorRMI coordinator) throws Exception {
        super(0);
        System.err.println("Front Tier starts.");
        String url = String.format("//%s:%d/%d", ip, port, id);
        Naming.rebind(url, this);
        
        this.SL = SL;
        this.coordinator = coordinator;
        this.id = id;
    }
    
    /**
     * Main Entry
     */
    public void run() throws Exception{
        SL.register_frontend();
        
        Thread t1 = new Thread(new Runnable() {
            public void run() {
                while (true) {
                    try {
                        Thread.sleep(2000);
                    } catch (Exception e) {
                        
                    }
                    float cpuload = Cloud.getCPUload();
                    //System.err.println(cpuload);
                    if (cpuload > 0.4 && cpuload < 0.9) {                        
                        try {
                            coordinator.addFrontTier();
                            Thread.sleep(5000);
                        } catch (Exception e) {
                            
                        }
                    }
                    if (cpuload < 0.2) {
                        try {
                            coordinator.removeFrontTier();
                            Thread.sleep(5000);
                        } catch (Exception e) {
                            
                        }
                    }
                    
                }
            }
        });
        t1.start();
        // main loop
        
        
        Thread t2 = new Thread(new Runnable() {
            public void run() {
                while (true) {
                    Cloud.FrontEndOps.Request request = SL.getNextRequest();
                    try {
                        coordinator.addRequest(request);
                    } catch (Exception e) {
                        
                    }
                }
            }
        });
        t2.start();
        
        
    }
}
