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
    private MiddleTierRMI middleTier;
    private static CoordinatorRMI coordinator;
    private static int defaultCoordinatorId = 1;
    
    public int connectMiddleTier(String ip, int port, int middle_id) {
        try {
            String url = String.format("//%s:%d/%d", ip, port, middle_id);
            middleTier = (MiddleTierRMI) Naming.lookup(url);
            System.err.println("middleTier Connected");
            return 0;
        } catch (Exception e) {
            return -1;
        }
    }
    
    /**
     * [RMI Implementation]
     * unregister this FrontTier instance
     */
    public void unregisterFrontTier() throws RemoteException {
        SL.unregister_frontend();
        // Handle rest request
        while (SL.getQueueLength() > 0) {
            Cloud.FrontEndOps.Request r = SL.getNextRequest();
            middleTier.processRequest(r);           
        }
    }
    
    /**
     * Front Tier Initialization
     */
    public FrontTier(String ip, int port, ServerLib SL, int id, int middle_id) throws Exception {
        super(0);
        System.err.println("Front Tier starts.");
        String url = String.format("//%s:%d/%d", ip, port, id);
        Naming.rebind(url, this);
        
        // try to connect coordinator
        try {
            url = String.format("//%s:%d/%d", ip, port, defaultCoordinatorId);
            coordinator = (CoordinatorRMI) Naming.lookup(url);
            System.err.println("Coodinator Connected");
        } catch (Exception e) {
            System.err.println("Coodinator Connection Error");
        }
        
        // try to connect middle tier
        int connect = connectMiddleTier(ip, port, middle_id);
        while (connect == -1) {
            // if failure, that means middle tier may be booting
            // retry until middle tier is ready to connect
            connect = connectMiddleTier(ip, port, middle_id);
        }
        this.SL = SL;
        coordinator.completeScaleOut(middle_id);
    }
    
    /**
     * Main Entry
     */
    public void run() throws Exception{
        SL.register_frontend();
        
        // main loop
        while (true) {
            //int len = SL.getQueueLength();
            //System.err.println(Cloud.getCPUload() + " " + len);
            Cloud.FrontEndOps.Request r = SL.getNextRequest();
            middleTier.processRequest(r);
            
        }
    }
}
