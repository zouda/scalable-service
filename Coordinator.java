import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
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
    
    private static int initialNumFrontTier = 1;
    private static int initialNumMiddleTier = 2;
    
    private static int defaultCoordinatorId = 1;
    private ConcurrentHashMap<Integer, String> IDTypeMap;

    private boolean isAddingInstance = false;
    private boolean isRemovingInstance = false;
    private int numFrontTier;
    private int numMiddleTier;
    
    // state variables used for scale in/out
    private int overloadReportCounter = 0;
    private int underloadReportCounter = 0;
    
    private Queue<Cloud.FrontEndOps.Request> requestQueue;
    private List<Integer> FrontTierList;
    private List<Integer> MiddleTierList;
    
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
        FrontTierList = new ArrayList<Integer>();
        MiddleTierList = new ArrayList<Integer>();
        requestQueue = new LinkedList<Cloud.FrontEndOps.Request>();
        this.SL = SL;
        this.ip = ip;
        this.port = port;
    }
    
    /**
     * [RMI Implementation]
     * given ID of a VM, return its type
     * @param VM_id
     * @return "FrontTier", 
     *         "MiddleTier", 
     *         "Cache", 
     *         or null if the id not exists.
     */
    public String getInstanceType(int VM_id) throws RemoteException {
        if (!IDTypeMap.containsKey(VM_id))
            return null;
        return IDTypeMap.get(VM_id);
    }
    
    public void addRequest(Cloud.FrontEndOps.Request request) throws RemoteException {
        requestQueue.offer(request);
    }
    
    public synchronized Cloud.FrontEndOps.Request getRequest() throws RemoteException {
        return requestQueue.poll();
    }
    
    public void addFrontTier() throws RemoteException {
        if (this.FrontTierList.size() < 3)
            startNewFrontTierInstance();
    }
    
    public void removeFrontTier() throws RemoteException {
        if (this.FrontTierList.size() > 1)
            removeFrontTierInstance();
    }
    
//    /**
//     * [RMI Implementation]
//     * when middle instance has over high CPU load, it reports to coordinator,
//     * who will gather reports from all instances and decide whether to scale out
//     * by adding new instance.
//     * To ensure that only one instance is being added at one moment, we will check the
//     * isAddingInstance state every time we want to scale out.
//     * @throws RemoteException
//     */
//    public synchronized void reportOverloadCPU(int VM_id) throws RemoteException {
//        overloadReportCounter++;
//        if (!isAddingInstance && overloadReportCounter >= numInstance) {
//            isAddingInstance = true;
//            addInstance();
//        }
//    }
//
//    /**
//     * [RMI Implementation]
//     * when both front tier and middle tier completing booting and starting running,
//     * the front tier will send a COMPLETE message to coordinator. on receiving this,
//     * the coordinator will reset the isAddingInstance state.
//     * @param front_id id of newly created running front tier 
//     * @throws RemoteException
//     */
//    public synchronized void completeScaleOut(int front_id) throws RemoteException {
//        if (isAddingInstance) {
//            isAddingInstance = false;
//            overloadReportCounter = 0;
//        }
//        try {
//            String url = String.format("//%s:%d/%d", ip, port, front_id);
//            FrontTierRMI frontTier = (FrontTierRMI) Naming.lookup(url);
//            System.err.println("Coordinator::FrontTier Connected ID: " + front_id);
//            this.FrontTierMap.put(front_id, frontTier);
//            reportedSet.clear();
//            numInstance++;
//        } catch (Exception e) {
//            System.err.println("Coordinator::FrontTier Connection Failure ID: " + front_id);
//        }
//    }
//    
//    /**
//     * [RMI Implementation]
//     * when middle instance has over low CPU load, it reports to coordinator,
//     * who will gather reports from all instances and decide whether to scale in
//     * by removing old instance.
//     * @throws RemoteException
//     */
//    public synchronized void reportUnderloadCPU(int VM_id) throws RemoteException {
//        underloadReportCounter++;
//        if (!isRemovingInstance && underloadReportCounter >= numInstance) {
//            isRemovingInstance = true;
//            removeInstance();
//        }
//    }
//    
//    /**
//     * After the front tier handle all request remaining in the queue,
//     * the coordinator will terminate both the front tier and its 
//     * corresponding middle tier.
//     * @param front_id the id of the front tier
//     * @throws RemoteException
//     */
//    public void completeScaleIn(int front_id) throws RemoteException {
//        int mid_id = FrontMiddleMap.get(front_id);
//        FrontMiddleMap.remove(front_id);
//        SL.endVM(front_id);
//        SL.endVM(mid_id);
//        numInstance--;
//        underloadReportCounter = 0;
//        isRemovingInstance = false;
//    }
    
    /**
     * Start a new front tier instance
     * @return VM id
     */
    private int startNewFrontTierInstance() {
        int id = SL.startVM();
        IDTypeMap.put(id, "FrontTier");
        this.FrontTierList.add(id);
        return id;
    }
    
    private void removeFrontTierInstance() {
        int front_id = this.FrontTierList.get(0);
        this.FrontTierList.remove(0);
        try {
          String url = String.format("//%s:%d/%d", ip, port, front_id);
          FrontTierRMI frontTier = (FrontTierRMI) Naming.lookup(url);
          frontTier.unregisterFrontTier();
      } catch (Exception e) {
          System.err.println("Coordinator::Remove FrontTier Connection Failure ID: " + front_id);
      }
    }
    
    /**
     * start a new middle tier instance
     * @return VM id
     */
    private int startNewMiddleTierInstance() {
        int id = SL.startVM();
        IDTypeMap.put(id, "MiddleTier");
        this.MiddleTierList.add(id);
        return id;
    }
    
//    /**
//     * add new Front Tier and Middle Tier (Scale Out)
//     * @return id of newly created front tier
//     */
//    private int addInstance() {
//        int mid_id = startNewMiddleTierInstance();
//        int front_id = startNewFrontTierInstance();
//        FrontMiddleMap.put(front_id, mid_id);
//        return front_id;
//    }
//    
//    /**
//     * remove an existing Front-Middle pair (Scale In)
//     * on removing the instance, the coordinator will pick an pair and 
//     * send UNREGISTER message to the front tier. 
//     */
//    private void removeInstance() {
//        // make sure there is at least 1 front-middle pair
//        if (numInstance <= 1)
//            return;
//        int front_id = 0;
//        for (int key: FrontMiddleMap.keySet()) {
//            front_id = key;
//            break;
//        }
//
//        // ask the front tier to unregister
//        try {
//            FrontTierRMI frontTier = this.FrontTierMap.get(front_id);
//            frontTier.unregisterFrontTier();
//            completeScaleIn(front_id);
//            System.err.println("Coordinator:: FrontTier Unregistered ID:" + front_id);
//        } catch (Exception e) {
//            System.err.println("Coordinator:: Unregister fails ID:" + front_id);
//        }
//    }
    
    
    /**
     * Start new instances
     */
    private void createBeginnerInstance() {
        for (int i = 0; i < initialNumFrontTier; i++) {
            startNewFrontTierInstance();
        }
        
        for (int i = 0; i < initialNumMiddleTier; i++) {
            startNewMiddleTierInstance();
        }
    }
    
    /**
     * Start n middles
     */
    private void startMiddleTier(int num) {
        for (int i = 0; i < num; i++) {
            startNewMiddleTierInstance();
        }
    }
    
    /**
     * Main Entry
     */
    public void run() {
        createBeginnerInstance();
        Thread t = new Thread(new Runnable() {
           public void run() {
               while (true) {
                   int queueSize = requestQueue.size();
                   int newStartNum = queueSize - MiddleTierList.size();
                   //System.err.println(queueSize);
                   if (newStartNum > 0) {
                       newStartNum = Math.min(10, newStartNum);
                       startMiddleTier(newStartNum);
                       try {
                           Thread.sleep(5000);
                       } catch (Exception e) {
                           
                       }
                   }
                   try {
                       Thread.sleep(100);
                   } catch (Exception e) {
                       
                   }
               }
           } 
        });
        t.start();
    }
}
