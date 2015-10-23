import java.util.ArrayList;
import java.util.HashMap;


public class Item
{
    private int id;
    private int startingPrice;
    private String description;
    private ArrayList<String> interestedUsers;
    private String currentBidder;
    private int currentBid;
    private int tempBid;
    private HashMap<String, Message> pendingBids;
    private int peerInterestedCount;
    private int discountRound;
    
    public Item(int startingPrice, String description, int id)
    {
        this.id              = id;
        this.startingPrice   = startingPrice;
        this.description     = description;
        this.interestedUsers = new ArrayList<String>();
        this.pendingBids     = new HashMap<String, Message>();
        this.currentBidder   = Constants.no_holder;
        this.currentBid      = startingPrice; 
        this.tempBid         = startingPrice;
    }
    
    public void addUser(String username)
    {
        this.interestedUsers.add(username);
    }
    
    public int getId() 
    {
        return id;
    }

    public int getStartingPrice() {
        return startingPrice;
    }

    public String getDescription() {
        return description;
    }

    public ArrayList<String> getInterestedUsers()
    {
        return interestedUsers;
    }

    public String getCurrentBidder()
    {
        return currentBidder;
    }
    
    public int getCurrentBid()
    {
        return currentBid;
    }
    
    public void setCurrentBidder(String currentBidder)
    {
        this.currentBidder = currentBidder;
    }
    
    public void setCurrentBid(int currentBid)
    {
        this.currentBid = currentBid;
    }

    public HashMap<String, Message> getPendingBids() 
    {
        return pendingBids;
    }

    public void addPendingBid(String userName, Message pendingBid) 
    {
        pendingBids.put(userName, pendingBid);
    }
    
    public void clearPendingBids()
    {
        pendingBids.clear();
    }

    public int getTempBid() 
    {
        return tempBid;
    }

    public void setTempBid(int tempBid) 
    {
        this.tempBid = tempBid;
    }

    public int getPeerInterestedCount() 
    {
        return peerInterestedCount;
    }

    public void setPeerInterestedCount(int peerInterestedCount) 
    {
        this.peerInterestedCount = peerInterestedCount;
    }

    public int getDiscountRound() 
    {
        return discountRound;
    }

    public void incrDiscountRound() 
    {
        this.discountRound++;
    }
}


