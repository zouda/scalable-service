import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public class MiddleTier extends UnicastRemoteObject implements MiddleTierRMI {
    
    private ServerLib SL;
    
    /**
     * Middle Tier Initialization
     */
    public MiddleTier(String ip, int port, ServerLib SL, int id) throws Exception {
        // create new RMI instance for the middle tier
        super(0);
        String url = String.format("//%s:%d/%d", ip, port, id);
        Naming.rebind(url, this);
        
        System.err.println("Middle Tier starts.");
        
        this.SL = SL;
    }
    
    /**
     * [RMI Implementation]
     * remote version of processRequest
     */
    public void processRequest(Cloud.FrontEndOps.Request request) throws RemoteException {
        SL.processRequest(request);
        System.err.println(Cloud.getCPUload());
    }
}
