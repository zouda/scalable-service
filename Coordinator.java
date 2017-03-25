import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 
 * Coordinator VM Instance
 * 
 * Schedule adding/removing new/old vm instance
 * to meet requirement of current loads
 *
 */
public class Coordinator extends UnicastRemoteObject implements CoordinatorRMI {
    
    private ServerLib SL;
    private static int initialNumInstance = 2;
    private static int defaultCoordinatorId = 1;
    private ConcurrentHashMap<Integer, String> IDTypeMap;
    private ConcurrentHashMap<Integer, Integer> FrontMiddleMap;
    
    /**
     * Coordinator Initialization
     */
    public Coordinator(String ip, int port, ServerLib SL) throws Exception {
        // create new RMI instance for the coordinator
        super(0);
        String url = String.format("//%s:%d/%d", ip, port, defaultCoordinatorId);
        Naming.rebind(url, this);
        
        // initialize variables and data structure
        IDTypeMap = new ConcurrentHashMap<Integer, String>();
        FrontMiddleMap = new ConcurrentHashMap<Integer, Integer>();
        this.SL = SL;
    }
    
    /**
     * [RMI Implementation]
     * given ID of a VM, return its type
     * @param VM_id
     * @return "FrontTier" with corresponding middle tier id, 
     *         "MiddleTier", 
     *         "Cache", 
     *         or null if the id not exists.
     */
    public synchronized String getInstanceType(int VM_id) throws RemoteException {
        if (!IDTypeMap.containsKey(VM_id))
            return null;
        String type =  IDTypeMap.get(VM_id);
        if (type.equals("FrontTier")) {
            if (!FrontMiddleMap.containsKey(VM_id)) {
                System.err.println("Coordinator::Middle Tier not exists. ID:"+VM_id);
            }
            type += " " + FrontMiddleMap.get(VM_id);
        }
        return type;
    }
    
    /**
     * Start a new front tier instance
     * @return VM id
     */
    private int startNewFrontTierInstance() {
        int id = SL.startVM();
        IDTypeMap.put(id, "FrontTier");
        return id;
    }
    
    /**
     * start a new middle tier instance
     * @return VM id
     */
    private int startNewMiddleTierInstance() {
        int id = SL.startVM();
        IDTypeMap.put(id, "MiddleTier");
        return id;
    }
    
    /**
     * Start 2*numInstance new instances, each pair consists of 
     * a front tier and a middle tier instance.
     * @param numInstance
     */
    public void createBeginnerInstance(int numInstance) {
        for (int i = 0; i < numInstance; i++) {
            int midID = startNewMiddleTierInstance();
            int frontID = startNewFrontTierInstance();
            FrontMiddleMap.put(frontID, midID);
        }
    }
    
    /**
     * add new Front Tier and Middle Tier
     */
    public void addInstance() {
        int midID = startNewMiddleTierInstance();
        int frontID = startNewFrontTierInstance();
        FrontMiddleMap.put(frontID, midID);
    }
    
    /**
     * Main Entry
     */
    public void run() {
        createBeginnerInstance(initialNumInstance);
    }
}
