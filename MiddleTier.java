import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public class MiddleTier extends UnicastRemoteObject implements MiddleTierRMI {
    
    private ServerLib SL;
    private int id;
    private int overloadCounter = 0;
    private int underloadCounter = 0;
    private float overloadThreshold = (float)(0.35);
    private float underloadThreshold = (float)(0.25);
    private int maxAllowableOverload = 10;
    private int maxAllowableUnderload = 10;
    
    private static CoordinatorRMI coordinator;
    private static int defaultCoordinatorId = 1;
    
    /**
     * Middle Tier Initialization
     */
    public MiddleTier(String ip, int port, ServerLib SL, int id) throws Exception {
        // Create new RMI instance for the middle tier
        super(0);
        String url = String.format("//%s:%d/%d", ip, port, id);
        Naming.rebind(url, this);
        
        // Get coordinator instance handler
        try {
            url = String.format("//%s:%d/%d", ip, port, defaultCoordinatorId);
            coordinator = (CoordinatorRMI) Naming.lookup(url);
            System.err.println("Coodinator Connected");
        } catch (Exception e) {
            System.err.println("Coodinator Connection Error");
        }
        
        System.err.println("Middle Tier starts.");
        
        this.SL = SL;
        this.id = id;
    }
    
    /**
     * [RMI Implementation]
     * remote version of processRequest
     */
    public void processRequest(Cloud.FrontEndOps.Request request) throws RemoteException {
        SL.processRequest(request);

        float CPUload = Cloud.getCPUload();
        if (CPUload >= overloadThreshold) {
            overloadCounter++;
            if (overloadCounter >= maxAllowableOverload) {
                overloadCounter = 0;
                coordinator.reportOverloadCPU(id);
            }
        } 
        else if (CPUload < underloadThreshold) {
            underloadCounter++;
            if (underloadCounter >= maxAllowableUnderload) {
                underloadCounter = 0;
                coordinator.reportUnderloadCPU(id);
            }
        } 
    }
}
