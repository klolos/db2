import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;

public class DBServer
{
    private Connection conn = null;
    private Auctioneer auctioneer;
    
    private static DBServer callerInstance;
    private static DBServer calleeInstance;
    
    private DBServer() {}
    
    public static DBServer getCallerInstance()
    {
        if (callerInstance == null) {
            callerInstance = new DBServer();
            callerInstance.init("jdbc:mysql://localhost:3306/DB", "DB", "pass");
        }
        
        return callerInstance;
    }
    
    public static DBServer getCalleeInstance()
    {
        if (calleeInstance == null) {
            calleeInstance = new DBServer();
            calleeInstance.init("jdbc:mysql://localhost:3306/DB2", "DB2", "pass");
        }
        
        return calleeInstance;
    }
    
    private void init(String dbURL, String userName, String password) {
        try {
            Class.forName("com.mysql.jdbc.Driver").newInstance();
            conn = DriverManager.getConnection(dbURL, userName, password);
        } catch (Exception e) {
        }
    }
    
    public void initAuctions(ArrayList<Item> items)
    {
        String query = "Delete from items;";
        if (executeUpdate(query) == -1)
            auctioneer.debug("Unable to clear the existing items table in the database");
        
        for (Item i : items) {
            query = "Insert into items values(\"" + 
                    i.getId() + "\", \"" + 
                    i.getStartingPrice() + "\", \"" +
                    i.getDescription() + "\", null, null);";
            if (executeUpdate(query) == -1)
                auctioneer.debug("Insert Query Failed");
        }
    }
    
    public void updateItemPrice(int id, int bid, String bidder)
    {
        String query = "Update items set bid = \""+bid+"\", bidder = \""+bidder+"\" where id = \""+id+"\";";
        if (executeUpdate(query) == -1)
            auctioneer.debug("Bid insertion into the database failed");
    }
    
    public ResultSet execute(String query) 
    {
        auctioneer.debug("Querying: " + query);
        try {
            PreparedStatement stmt = conn.prepareStatement(query);
            return stmt.executeQuery();
        } catch (Exception e) {
            return null;
        }
    }
    
    public int executeUpdate(String query) 
    {
        auctioneer.debug("Querying: " + query);
        try {
            PreparedStatement stmt = conn.prepareStatement(query);
            return stmt.executeUpdate();
        } catch (Exception e) {
            return -1;
        }
    }
    
    public void setAuctioneer(Auctioneer auctioneer)
    {
        this.auctioneer = auctioneer;
    }

    public void updateBid(Item item) 
    {
        String query = "Update items set bid = \""+item.getCurrentBid()+"\", bidder = " + 
                    "\""+item.getCurrentBidder()+"\" where id = \""+item.getId()+"\";";
        if (executeUpdate(query) == -1)
            auctioneer.debug("Bid insertion into the database failed");    
    }

}
