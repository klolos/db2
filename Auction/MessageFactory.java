import java.util.ArrayList;
import java.util.HashMap;


public class MessageFactory
{
    private String remaining;
    
    /**
     * Creates a message object with no properties
     * @param command
     * @return
     */
    public Message createMessage(String command)
    {
        return new Message(command);
    }
    
    /**
     * Creates a message object with the given properties
     * @param command
     * @param properties
     * @return
     */
    public Message createMessage(String command, HashMap<String, String> properties)
    {
        Message message = new Message(command);
        for (String key : properties.keySet())
            message.addProperty(key, properties.get(key));
        
        return message;
    }
        
    public Message createBidItemMessage(int itemId, String description, int startingPrice)
    {
        Message message = createMessage(Constants.bid_item);
        message.addProperty(Constants.item_id, itemId);
        message.addProperty(Constants.description, description);
        message.addProperty(Constants.starting_price, startingPrice);
        return message;
    }
    
    public Message createStopBiddingMessage(int itemId, String winner, int highestBid)
    {
        Message message = createMessage(Constants.stop_bidding);
        message.addProperty(Constants.item_id, itemId);
        message.addProperty(Constants.winner, winner);
        message.addProperty(Constants.highest_bid, highestBid);
        return message;
    }
    
    public Message createStartBiddingMessage(int startingPrice, int itemId)
    {
        Message message = createMessage(Constants.start_bidding);
        message.addProperty(Constants.starting_price, startingPrice);
        message.addProperty(Constants.item_id, itemId);
        return message;
    }
    //TODO chara 5/5
    /**
     * Creates a message for quitting a user and shutting his/her channel down
     * @param username
     */
    
    public Message createQuitMessage(String username)
    {
        Message message = createMessage(Constants.quit);
        message.addProperty(Constants.username, username);
        return message;
    }
    
    //TODO chara 5/5
    /**
     * Creates a message for a control message: i_am_interested
     * @param username
     * @param id
     */
        
    public Message createIAmInterestedMessage(String username, int id)
    {
        Message message = createMessage(Constants.i_am_interested);
        message.addProperty(Constants.username, username);
        message.addProperty(Constants.item_id, id);
        return message;
    }

    //TODO chara 5/5
    /**
     * Creates a message for placing a new bid on behalf of a user    
     * @param username
     * @param amount
     */
    public Message createMyBidMessage(String username, int amount, int itemId)
    {
        Message message = createMessage(Constants.my_bid);
        message.addProperty(Constants.username, username);
        message.addProperty(Constants.amount, amount);
        message.addProperty(Constants.item_id, itemId);
        return message;
    }
    
    public Message createInterestedCountMessage(int count)
    {
        Message message = createMessage(Constants.interested_count);
        message.addProperty(Constants.amount, count);
        return message;
    }
    
    public Message createInfoMessage(String message)
    {
        Message m = createMessage(Constants.info);
        m.addProperty(Constants.message, message);
        return m;
    }
        
    public Message createGotBidMessage(String userName, int amount)
    {
        Message message = createMessage(Constants.got_bid);
        message.addProperty(Constants.username, userName);
        message.addProperty(Constants.amount, amount);
        return message;
    }
    
    public Message createBidOkMessage(String userName, int amount)
    {
        Message message = createMessage(Constants.bid_ok);
        message.addProperty(Constants.username, userName);
        message.addProperty(Constants.amount, amount);
        return message;
    }
    
    public Message createNewHighBidMessage(String userName, int amount)
    {
        Message message = createMessage(Constants.new_high_bid);
        message.addProperty(Constants.username, userName);
        message.addProperty(Constants.amount, amount);
        return message;
    }
        
    /**
     * Creates a message object from its String representation
     * @param rawMessage
     * @return
     */
    public Message fromRawMessage(String rawMessage)
    {
        String command = getCommand(rawMessage);
        HashMap<String, String> props = getProperties(rawMessage);
        
        return createMessage(command, props);
    }
    
    /**
     * Returns a HashMap containing all the arguments and their values found in the message
     * @param message
     * @return
     */
    private HashMap<String, String> getProperties(String message)
    {
        int pos = message.indexOf(": ");
        String props = message.substring(pos + 2);
        HashMap<String, String> properties = new HashMap<String, String>();
        
        for (String prop : props.split(", ")) {
            String[] pair = prop.split(" = ");
            if (pair.length == 2)
                properties.put(pair[0], pair[1].replace("\"", ""));
        }
        return properties;
    }

    /**
     * Returns the command of the given message
     * @param message
     * @return
     */
    private String getCommand(String message)
    {
        int pos = message.indexOf(": ");
        return message.substring(0, pos);
    }

    /**
     * Extracts the complete messages contained in the given data
     * Stores temporarily the remaining data to be retrieved through getRemaining()
     * @param data
     * @return
     */
    public ArrayList<Message> extractMessages(String data)
    {
        ArrayList<String> rawMessages = extractRawMessages(data);
        ArrayList<Message> messages = new ArrayList<Message>();
        for (String m : rawMessages)
            messages.add(fromRawMessage(m));
        return messages;
    }
    
    
    /**
     * Breaks a string of received data into separate messages
     * Stores temporarily the remaining data to be retrieved through getRemaining()
     * @param data
     * @return
     */
    private ArrayList<String> extractRawMessages(String data)
    {
        ArrayList<String> list = new ArrayList<String>();
        int pos;
        while ((pos = data.indexOf('\n')) != -1) {
            String message = data.substring(0, pos);
            data = data.substring(pos+1);
            list.add(message);
        }
        
        this.remaining = data;
        return list;
    }
    
    /**
     * Returns the remaining data left over from the last extractMessages call
     * @return
     */
    public String getRemaining()
    {
        return remaining;
    }
    
}
