import java.rmi.*;  

/**
 * 
 * Coordinator Remote Interface Definition 
 *
 */

public interface CoordinatorRMI extends Remote {
    
    /**
     * given ID of a VM, return its type
     * @param VM_id
     * @return "FrontTier", "MiddleTier", "Cache" 
     *         or null if the id not exists.
     */
    public String getInstanceType(int VM_id) throws RemoteException;

}
