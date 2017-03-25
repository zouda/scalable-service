import java.rmi.Naming;

/**
 * 
 * FrontTier VM Instance
 *
 */
public class FrontTier {
    
    private ServerLib SL;
    private MiddleTierRMI middleTier;
    
    public int connectMiddleTier(String ip, int port, int middle_id) {
        try {
            String url = String.format("//%s:%d/%d", ip, port, middle_id);
            middleTier = (MiddleTierRMI) Naming.lookup(url);
            System.err.println("middleTier Connected");
            return 0;
        } catch (Exception e) {
//            System.err.println("middleTier Connection Fails. Retrying in 0.5s..");
//            try {
//                Thread.sleep(100);
//            } catch (Exception sleepException) {
//                sleepException.printStackTrace();
//            }
            return -1;
        }
    }
    
    public FrontTier(String ip, int port, ServerLib SL, int middle_id) {
        System.err.println("Front Tier starts.");
        this.SL = SL;
        
        // try to connect middle tier
        int connect = connectMiddleTier(ip, port, middle_id);
        while (connect == -1) {
            // if failure, that means middle tier may be booting
            // retry until middle tier is ready to connect
            connect = connectMiddleTier(ip, port, middle_id);
        }
        
    }
    
    public void run() throws Exception{
        SL.register_frontend();
        
        // main loop
        while (true) {
            int len = SL.getQueueLength();
            System.err.println(Cloud.getCPUload() + " " + len);
            Cloud.FrontEndOps.Request r = SL.getNextRequest();
            middleTier.processRequest(r);
            
        }
    }
}
