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
    private String ip;
    private int port;
    
    private static int initialNumInstance = 1;
    private static int defaultCoordinatorId = 1;
    private ConcurrentHashMap<Integer, String> IDTypeMap;
    private ConcurrentHashMap<Integer, Integer> FrontMiddleMap;
    private ConcurrentHashMap<Integer, FrontTierRMI> FrontTierMap;
    private boolean isAddingInstance = false;
    private boolean isRemovingInstance = false;
    private int numInstance;
    
    // state variables used for scale in/out
    private int overloadReportCounter = 0;
    private int underloadReportCounter = 0;
    
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
        FrontTierMap = new ConcurrentHashMap<Integer, FrontTierRMI>();
        this.SL = SL;
        this.ip = ip;
        this.port = port;
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
     * [RMI Implementation]
     * when middle instance has over high CPU load, it reports to coordinator,
     * who will gather reports from all instances and decide whether to scale out
     * by adding new instance.
     * To ensure that only one instance is being added at one moment, we will check the
     * isAddingInstance state every time we want to scale out.
     * @throws RemoteException
     */
    public synchronized void reportOverloadCPU(int VM_id) throws RemoteException {
        overloadReportCounter++;
        if (!isAddingInstance && overloadReportCounter >= numInstance) {
            isAddingInstance = true;
            addInstance();
        }
    }

    /**
     * [RMI Implementation]
     * when both front tier and middle tier completing booting and starting running,
     * the front tier will send a COMPLETE message to coordinator. on receiving this,
     * the coordinator will reset the isAddingInstance state.
     * @param front_id id of newly created running front tier 
     * @throws RemoteException
     */
    public synchronized void completeScaleOut(int front_id) throws RemoteException {
        if (isAddingInstance) {
            isAddingInstance = false;
            overloadReportCounter = 0;
        }
        try {
            String url = String.format("//%s:%d/%d", ip, port, front_id);
            FrontTierRMI frontTier = (FrontTierRMI) Naming.lookup(url);
            System.err.println("Coordinator::FrontTier Connected ID: " + front_id);
            this.FrontTierMap.put(front_id, frontTier);
            numInstance++;
        } catch (Exception e) {
            System.err.println("Coordinator::FrontTier Connection Failure ID: " + front_id);
        }
    }
    
    /**
     * [RMI Implementation]
     * when middle instance has over low CPU load, it reports to coordinator,
     * who will gather reports from all instances and decide whether to scale in
     * by removing old instance.
     * @throws RemoteException
     */
    public synchronized void reportUnderloadCPU(int VM_id) throws RemoteException {
        underloadReportCounter++;
        if (!isRemovingInstance && underloadReportCounter >= numInstance) {
            isRemovingInstance = true;
            removeInstance();
        }
    }
    
    /**
     * After the front tier handle all request remaining in the queue,
     * the coordinator will terminate both the front tier and its 
     * corresponding middle tier.
     * @param front_id the id of the front tier
     * @throws RemoteException
     */
    public void completeScaleIn(int front_id) throws RemoteException {
        int mid_id = FrontMiddleMap.get(front_id);
        FrontMiddleMap.remove(front_id);
        SL.endVM(front_id);
        SL.endVM(mid_id);
        numInstance--;
        underloadReportCounter = 0;
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
     * add new Front Tier and Middle Tier (Scale Out)
     * @return id of newly created front tier
     */
    private int addInstance() {
        int mid_id = startNewMiddleTierInstance();
        int front_id = startNewFrontTierInstance();
        FrontMiddleMap.put(front_id, mid_id);
        return front_id;
    }
    
    /**
     * remove an existing Front-Middle pair (Scale In)
     * on removing the instance, the coordinator will pick an pair and 
     * send UNREGISTER message to the front tier. 
     */
    private void removeInstance() {
        // make sure there is at least 1 front-middle pair
        if (numInstance <= 1)
            return;
        int front_id = 0;
        for (int key: FrontMiddleMap.keySet()) {
            front_id = key;
            break;
        }

        // ask the front tier to unregister
        try {
            FrontTierRMI frontTier = this.FrontTierMap.get(front_id);
            frontTier.unregisterFrontTier();
            completeScaleIn(front_id);
            System.err.println("Coordinator:: FrontTier Unregistered ID:" + front_id);
        } catch (Exception e) {
            System.err.println("Coordinator:: Unregister fails ID:" + front_id);
        }
    }
    
    
    /**
     * Start numInstance new instances, each pair consists of 
     * a front tier and a middle tier instance.
     * @param numInstance
     */
    private void createBeginnerInstance(int num) {
        for (int i = 0; i < num; i++) {
            addInstance();
        }
    }
    
    /**
     * Main Entry
     */
    public void run() {
        createBeginnerInstance(initialNumInstance);
    }
}
