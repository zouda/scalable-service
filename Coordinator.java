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
    
    private static int initialNumFrontTier = 0;
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
    
    private Queue<Request> requestQueue;
    private List<Integer> FrontTierList;
    private List<Integer> MiddleTierList;
    private int count = 0;
    private int outcount = 0;
    private int sum = 0;
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
        requestQueue = new LinkedList<Request>();
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
    
    public class Request {
        Cloud.FrontEndOps.Request request;
        long timestamp;
        Request(Cloud.FrontEndOps.Request r, long t) {
            request = r;
            timestamp = t;
        }
    }
    
    public void addRequest(Cloud.FrontEndOps.Request request) throws RemoteException {
        Request r = new Request(request, System.currentTimeMillis());
        requestQueue.offer(r);
    }
    
    public synchronized Cloud.FrontEndOps.Request getRequest() throws RemoteException {
        while (requestQueue.size() > 0) {
            Request r = requestQueue.peek();
            if (r.request.isPurchase) {
                if (System.currentTimeMillis() - r.timestamp > 1500) {
                    requestQueue.poll();
                    continue;
                }
            } else {
                if (System.currentTimeMillis() - r.timestamp > 500) {
                    requestQueue.poll();
                    continue;
                }
            }
            break;
        }
        return requestQueue.poll().request;
    }
    
    public void addFrontTier() throws RemoteException {
        if (this.FrontTierList.size() < 2)
            startNewFrontTierInstance();
    }
    
    public void removeFrontTier() throws RemoteException {
        if (this.FrontTierList.size() > 0)
            removeFrontTierInstance();
    }
    
    public void removeMiddleTier() throws RemoteException {
        if (this.MiddleTierList.size() > 1)
            removeMiddleTierInstance();
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
    private synchronized int startNewFrontTierInstance() {
        int id = SL.startVM();
        IDTypeMap.put(id, "FrontTier");
        this.FrontTierList.add(id);
        return id;
    }
    
    /**
     * start a new middle tier instance
     * @return VM id
     */
    private synchronized void startNewMiddleTierInstance() {
        int id = SL.startVM();
        IDTypeMap.put(id, "MiddleTier");
        this.MiddleTierList.add(id);
    }
    
    private void removeFrontTierInstance() {
        int front_id = 0;
        synchronized (this) {
            int size = this.FrontTierList.size();
            if (size <= 1)
                return;
            front_id = this.FrontTierList.get(size-1);
            this.FrontTierList.remove(size-1);
        }
        try {
            String url = String.format("//%s:%d/%d", ip, port, front_id);
            FrontTierRMI frontTier = (FrontTierRMI) Naming.lookup(url);
            frontTier.unregisterFrontTier();
        } catch (Exception e) {
            System.err.println("Coordinator::Remove FrontTier Connection Failure ID: " + front_id);
        }
    }
    
    private void removeMiddleTierInstance() {
        int mid_id = 0;
        synchronized (this) {
            int size = this.MiddleTierList.size();
            if (size <= 1)
                return;
            mid_id = this.MiddleTierList.get(size-1);
            this.MiddleTierList.remove(size-1);
        }
        MiddleTierRMI middleTier = null;
        try {
            String url = String.format("//%s:%d/%d", ip, port, mid_id);
            middleTier = (MiddleTierRMI) Naming.lookup(url);
        } catch (Exception e) {
            System.err.println("Coordinator::Remove MiddleTier Connection Failure ID: " + mid_id);
            return;
        }
        try {
            middleTier.unregisterMiddleTier();
            SL.endVM(mid_id);
        } catch (Exception e) {
            System.err.println("Coordinator::Remove MiddleTier Remove Failure ID: " + mid_id);
        }
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
//                       outcount++;
//                       sum += newStartNum;
//                       if (outcount > 4) {
//                           newStartNum = sum / outcount;
                           startMiddleTier(newStartNum);
                           try {
                               Thread.sleep(5000);
                           } catch (Exception e) {
                           
                           }
                           //outcount = 0;
                       }
                   //}
                   else {
                       count++;
                       if (count > 500) {
                           removeMiddleTierInstance();
                           count = 0;
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

        Thread t1 = new Thread(new Runnable() {
            public void run() {
                while (true) {
                    try {
                        Thread.sleep(2000);
                    } catch (Exception e) {
                        
                    }
                    float cpuload = Cloud.getCPUload();
                    //System.err.println(cpuload);
                    if (cpuload > 0.4 && cpuload < 0.9) {                        
                        try {
                            addFrontTier();
                            Thread.sleep(5000);
                        } catch (Exception e) {
                            
                        }
                    }
                    if (cpuload < 0.2) {
                        try {
                            removeFrontTier();
                            Thread.sleep(5000);
                        } catch (Exception e) {
                            
                        }
                    }
                    
                }
            }
        });
        t1.start();
        
        Thread t2 = new Thread(new Runnable() {
            public void run() {
                try {
                    Thread.sleep(5000);
                } catch (Exception e) {
                    
                }
                SL.register_frontend();
                while (true) {
                    Cloud.FrontEndOps.Request request = SL.getNextRequest();
                    try {
                        addRequest(request);
                    } catch (Exception e) {
                        
                    }
                }
            }
        });
        t2.start();
    }
}
