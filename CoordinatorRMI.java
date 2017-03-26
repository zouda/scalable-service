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
    
//    /**
//     * when middle instance has over high CPU load, it reports to coordinator,
//     * who will gather reports from all instances and decide whether to scale out
//     * by adding new instance.
//     * @throws RemoteException
//     */
//    public void reportOverloadCPU(int VM_id) throws RemoteException;
//    
//    /**
//     * when middle instance has over low CPU load, it reports to coordinator,
//     * who will gather reports from all instances and decide whether to scale in
//     * by removing old instance.
//     * @throws RemoteException
//     */
//    public void reportUnderloadCPU(int VM_id) throws RemoteException;
//    
//    /**
//     * Report to coordinator that the new instance is running now
//     */
//    public void completeScaleOut(int VM_id) throws RemoteException;
    
    public void addRequest(Cloud.FrontEndOps.Request request) throws RemoteException;
    
    public Cloud.FrontEndOps.Request getRequest() throws RemoteException;
    
    public void addFrontTier() throws RemoteException;
    
    public void removeFrontTier() throws RemoteException;
    
    public void removeMiddleTier() throws RemoteException;
}
