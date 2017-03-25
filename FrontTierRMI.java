import java.rmi.*;  

/**
 * 
 * Front Tier Remote Interface Definition 
 *
 */

public interface FrontTierRMI extends Remote {
    public void unregisterFrontTier() throws RemoteException;
}
