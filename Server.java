import java.rmi.Naming;

/**
 * Project 3 CheckPoint 2
 * @author Haofeng Zou
 * @created 03/23/2017
 */

public class Server {
    
    private static CoordinatorRMI coordinator;
    
    private static int[] VMnumbers = {3,2,1,1,1,2,2,3,4,4,4,4,4,4,4,4,3,4,5,6,6,6,4,4};

    private static int defaultCoordinatorId = 1;
    
    public static void startNewVMs(ServerLib SL, int hour) throws Exception {
        hour = hour % 24;
        for (int i = 0; i < VMnumbers[hour]; i++) {
            SL.startVM();
        }
    }
    
    /**
     * Main Entry
     */
    public static void main ( String args[] ) throws Exception {
        if (args.length != 3) throw new Exception("Need 3 args: <cloud_ip> <cloud_port> <VM id>");
        String ip = args[0];
        int port = Integer.parseInt(args[1]);
        int id = Integer.parseInt(args[2]);
        ServerLib SL = new ServerLib( ip, port );
        
        // Main Routes
        if (id == 1) {
            Coordinator coordinator = new Coordinator(ip, port, SL);
            coordinator.run();
        } else {
            // get coordinator instance handler
            try {
                String url = String.format("//%s:%d/%d", ip, port, defaultCoordinatorId );
                coordinator = (CoordinatorRMI) Naming.lookup(url);
                System.err.println("Coodinator Connected");
            } catch (Exception e) {
                System.err.println("Coodinator Connection Error");
                e.printStackTrace();
            }
            
            String type = coordinator.getInstanceType(id);
            if (type == null) {
                System.err.println("Server::getInstanceType returns null.");
            }
            if (type.equals("MiddleTier")) {
                MiddleTier middleTier = new MiddleTier(ip, port, SL, id);
            } 
            else {  // Front Tier
                String[] temp = type.split(" ");
                int middle_id = Integer.parseInt(temp[1]);
                FrontTier frontTier = new FrontTier(ip, port, SL, id, middle_id);
                frontTier.run();
            }
        }
    }
}
