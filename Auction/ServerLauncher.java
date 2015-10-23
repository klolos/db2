import java.io.IOException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;



public class ServerLauncher 
{
    static final int callerPort = 4444;
    static final int calleePort = 5556;
    
    public static void main(String[] args) throws IOException
    {
        String confFile = getConfFile(args);
        if (confFile == null) 
            return;
        
        Lock debugLock    = new ReentrantLock();
        Auctioneer caller = new Auctioneer(callerPort, debugLock);
        Auctioneer callee = new Auctioneer(calleePort, debugLock);
        caller.makeCaller(calleePort);
        
        caller.configure(confFile);
        callee.configure(confFile);
        caller.start();
        callee.start();
        
        try {
            caller.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        try {
            callee.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.exit(0);
    }
    
    private static String getConfFile(String[] args)
    {
        if (args.length != 1) {
            System.out.println("Usage: java ServerLauncher <configuration file>");
            return null;
        } else {
            return args[0];
        }
    }
}
