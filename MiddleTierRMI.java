import java.rmi.*;  

/**
 * 
 * Middle Tier Remote Interface Definition 
 *
 */

public interface MiddleTierRMI extends Remote {
    
    public void processRequest(Cloud.FrontEndOps.Request request) throws RemoteException;

    public void unportObeject() throws RemoteException;
}
