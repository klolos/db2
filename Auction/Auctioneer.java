import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


public class Auctioneer extends Thread 
{
    // Constants
    public static final String clientChannel    = "clientChannel";
    public static final String serverChannel    = "serverChannel";
    public static final String channelType      = "channelType";
    public static final String bytesReadString  = "bytesRead";
    public static final String clientName       = "clientName";
    public static final String peerName          = "__peer";
    
    private enum State { OFFLINE, READY_TO_BEGIN, ACCEPTING_INTERESTS, WAITING_INTERESTED_COUNT, ACCEPTING_BIDS, READY_TO_END }
    
    // private fields
    private final int countdownTime = 10;
    private int countDownInterval = 5;
    private int timeLapse;
    private ArrayList<Item> items;
    private String type = "callee";
    private Proxy proxy;
    private Lock lock;
    private Lock debugLock;
    private MessageFactory messageFactory;
    private DBServer dbServer;
    private Item currentItem;
    private State state;
    private State peerState;
    private Timer timer;
    private boolean auctionsEnded;
    
    
    public Auctioneer(int port, Lock debugLock)
    {
        lock           = new ReentrantLock();
        this.debugLock = debugLock;
        proxy          = new Proxy(port, this);
        messageFactory = new MessageFactory();
        auctionsEnded  = false;
                
        peerState = State.OFFLINE;
        state     = State.OFFLINE;
    }
    
    /**
     * Set the port of the callee auctioneer and convert this one into a caller
     * @param peerPort
     */
    public void makeCaller(int peerPort)
    {
        this.type = "caller";
        proxy.setPeerPort(peerPort);
    }

    /**
     * Instantiates a DBServer and parses the configuration file
     * @throws IOException 
     */
    public void configure(String confFile) throws IOException
    {
        if (type.equals("caller"))
            dbServer = DBServer.getCallerInstance();
        else
            dbServer = DBServer.getCalleeInstance();
        dbServer.setAuctioneer(this);
        
        ConfParser confParser = new ConfParser(confFile);
        confParser.parse();
        timeLapse = confParser.getTimeLapse();
        items = confParser.getItems();
        dbServer.initAuctions(items);
    }
    
    /**
     * @throws IOException
     */
    public void run()
    {
        runCountDown(countdownTime);
        

        try {
            proxy.run(); 
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        debug("Shuting Down ...");
    }

    /**
     * Runs a countdown till the auctions start
     * @param seconds
     */
    private void runCountDown(int seconds) 
    {
        final int remaining = seconds - countDownInterval;
        Timer t = new Timer();
        t.schedule(new TimerTask() {
            @Override
            public void run() {
                lock.lock();
                if (remaining <= 0) {
                    beginAuctions();
                } else { 
                    String countdownMessage = "Auctions start in " + remaining + " seconds!";
                    broadcastInfo(countdownMessage);
                    if (type.equals("caller"))
                        debug(countdownMessage);
                    runCountDown(remaining);
                }
                lock.unlock();
            }
        }, countDownInterval * 1000);
    }

    /**
     * Begins the auction process
     * This method is to be called holding the lock
     */
    private void beginAuctions()
    {
        debug("Auctions are beggining!");
        broadcastInfo("Welcome to the auction house!");

        setState(State.READY_TO_BEGIN);
        Message m = messageFactory.createMessage(Constants.ready_to_run);
        proxy.send(Auctioneer.peerName, m);
        
        if (peerState == State.READY_TO_BEGIN) {
            beginNextAuction();
        }
    }

    /**
     * Begins the next auction
     * This method is to be called holding the lock
     */
    private void beginNextAuction()
    {
        if (timer != null)
            timer.cancel();

        if (items.isEmpty()) { // there are no more items to auction
            Message m = messageFactory.createMessage(Constants.auction_complete);
            proxy.broadcast(m);
            debug("Auctions have ended");
            auctionsEnded = true;
            return;
        }
        
        debug("Starting Auction for next item");
        currentItem = items.remove(0); // pop the first item
        int itemId = currentItem.getId();
        String description = currentItem.getDescription();
        int startingPrice = currentItem.getStartingPrice();
        Message m = messageFactory.createBidItemMessage(itemId, description, startingPrice);
        proxy.broadcast(m);
        setState(State.ACCEPTING_INTERESTS);
        peerState = State.ACCEPTING_INTERESTS;

        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                lock.lock();
                interestTimerEnded();
                lock.unlock();
            }
        }, timeLapse * 1000);
    }
    
    protected void interestTimerEnded()
    {
        setState(State.WAITING_INTERESTED_COUNT);
        int count = currentItem.getInterestedUsers().size();
        Message toPeer = messageFactory.createInterestedCountMessage(count);
        proxy.send(Auctioneer.peerName, toPeer);
        debug("interest time ended!");
        if (State.WAITING_INTERESTED_COUNT == peerState) {
            if ((count == 0) && (currentItem.getPeerInterestedCount() == 0)) {
                debug("Item " + currentItem.getId() + " is discarded due to lack of interest");
                beginNextAuction();
            } else {                
                peerState = State.ACCEPTING_BIDS;
                Message m = messageFactory.createStartBiddingMessage(currentItem.getStartingPrice(), currentItem.getId());
                proxy.broadcast(currentItem.getInterestedUsers(), m);
                setBidTimer();
            }        
        }
    }

    private void setBidTimer() 
    {
        setState(State.ACCEPTING_BIDS);
        
        if (timer != null)
            timer.cancel();
        
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                lock.lock();
                bidTimerEnded();
                lock.unlock();
            }
        }, timeLapse * 1000);
    }

    protected void bidTimerEnded()
    {
        debug("bid timer ended");
        setState(State.READY_TO_END);
        
        Message m = messageFactory.createMessage(Constants.ready_to_end);
        proxy.send(Auctioneer.peerName, m);

        // if peer's bidTimer has already ended and there is no tempBid for which we are expecting confirmation
        if ((peerState == State.READY_TO_END) && (currentItem.getTempBid() == currentItem.getCurrentBid())){
            currentItem.incrDiscountRound();
            int discountRound = currentItem.getDiscountRound();
            if (currentItem.getCurrentBidder().equals(Constants.no_holder) && (discountRound < 5)) {
                int newPrice = (currentItem.getStartingPrice() * (10 - discountRound)) / 10;
                currentItem.setCurrentBid(newPrice);
                currentItem.setTempBid(newPrice);
                peerState = State.ACCEPTING_BIDS;
                Message bidAgain = messageFactory.createNewHighBidMessage(Constants.no_holder, newPrice);
                proxy.broadcast(currentItem.getInterestedUsers(), bidAgain);
                debug("sent bid again message");
                setBidTimer();
            } else {                
                anounceWinner();
                peerState = State.ACCEPTING_INTERESTS;
                beginNextAuction();
            }
        }
    }
    
    private void anounceWinner()
    {
        int itemId = currentItem.getId();
        String winner = currentItem.getCurrentBidder();
        int winningBid = currentItem.getCurrentBid();
        Message m = messageFactory.createStopBiddingMessage(itemId, winner, winningBid);
        proxy.broadcast(currentItem.getInterestedUsers(), m);
        dbServer.updateBid(currentItem);
    }

    private void broadcastInfo(String message)
    {
        Message m = messageFactory.createInfoMessage(message);
        proxy.broadcast(m);
    }
    
    public void processMessage(String read, SelectionKey key) 
    {
        @SuppressWarnings("unchecked")
        Map<String, String> clientProps = (Map<String, String>) key.attachment();        
        String total = clientProps.get(Auctioneer.bytesReadString) + read;
        
        ArrayList<Message> messages = messageFactory.extractMessages(total);
        String remaining = messageFactory.getRemaining();
        clientProps.put(Auctioneer.bytesReadString, remaining);
        
        for (Message m : messages)
            handleMessage(m, key);
    }

    /**
     * Takes the actions necessary in response to the given message
     * @param message
     * @param name
     * @param channel
     */
    private void handleMessage(Message message, SelectionKey key)
    {
        String command = message.getCommand();
        debug("Received: " + message);

        @SuppressWarnings("unchecked")
        Map<String, String> clientProps = (Map<String, String>) key.attachment();
        String name  = clientProps.get(Auctioneer.clientName);
        SocketChannel channel = (SocketChannel) key.channel();
    
        switch (command) {
        
        // message from bidder
        case Constants.connect:
            handleConnect(name, message, channel, clientProps);
            break;
        case Constants.i_am_interested: 
            handleInterest(name, message);
            break;
        case Constants.my_bid:
            handleBid(name, message);
            break;
        case Constants.quit:
            handleQuit(name);
            break;
            
        // messages from peer
        case Constants.ready_to_run:
            handleReadyToRun();
            break;
        case Constants.ready_to_end:
            handleReadyToEnd();
            break;
        case Constants.got_bid:
            handleGotBid(message);
            break;
        case Constants.bid_ok:
            handleBidOk(message);
            break;
        case Constants.interested_count:
            handleInterestedCount(message);
            break;
        }
    }
    
    private void handleInterestedCount(Message message) 
    {
        int peerCount = Integer.parseInt(message.getProperty(Constants.amount));
        currentItem.setPeerInterestedCount(peerCount);
        peerState = State.WAITING_INTERESTED_COUNT;
        if (State.WAITING_INTERESTED_COUNT == state) {
            if ((peerCount == 0) && (currentItem.getInterestedUsers().size() == 0)) {
                debug("Item " + currentItem.getId() + " is discarded due to lack of interest");
                beginNextAuction();
            } else {                
                peerState = State.ACCEPTING_BIDS;
                Message m = messageFactory.createStartBiddingMessage(currentItem.getStartingPrice(), currentItem.getId());
                proxy.broadcast(currentItem.getInterestedUsers(), m);
                setBidTimer();
            }        
        }
    }

    private void handleQuit(String userName) 
    {
        proxy.closeConnection(userName);
        removeUserFromInterested(userName);
    }

    private void handleGotBid(Message message) 
    {
        int amount = Integer.parseInt(message.getProperty(Constants.amount));
        String userName = message.getProperty(Constants.username);
        if ((type.equals("caller") && amount > currentItem.getTempBid()) ||   // give priority to the caller bids if amounts are equal 
            (type.equals("callee") && amount >= currentItem.getTempBid())) {
                updateHighestBid(userName, amount);
                Message m = messageFactory.createBidOkMessage(userName, amount);
                proxy.send(Auctioneer.peerName, m);
                for (String name: currentItem.getPendingBids().keySet())
                    handleBid(name, currentItem.getPendingBids().get(name));
        }
    }

    private void handleBidOk(Message message) 
    {
        int amount = Integer.parseInt(message.getProperty(Constants.amount));
        String userName = message.getProperty(Constants.username);
        updateHighestBid(userName, amount);
    }

    private void updateHighestBid(String userName, int amount) 
    {
        if (amount > currentItem.getTempBid()) 
            currentItem.setTempBid(amount);      // temp bid needs to always be >= current bid 
        currentItem.setCurrentBid(amount);
        currentItem.setCurrentBidder(userName);
        
        Message m = messageFactory.createNewHighBidMessage(userName, amount);
        proxy.broadcast(currentItem.getInterestedUsers(), m);
        debug("New highest bid from user " + userName + ", amount = " + amount);
        setBidTimer();
    }


    private void handleBid(String userName, Message message) 
    {
        int amount = Integer.parseInt(message.getProperty(Constants.amount));
        int id = Integer.parseInt(message.getProperty(Constants.item_id));
        
        if (currentItem == null) {  // no item being auctioned at the moment
            Message m = messageFactory.createInfoMessage("No item being auctioned at the moment.");
            proxy.send(userName, m);
            return;
        }
        if (currentItem.getId() != id) {  // wrong item id - should not happen
            Message m = messageFactory.createInfoMessage("Invalid item ID.");
            proxy.send(userName, m);
            return;
        }
        if (!currentItem.getInterestedUsers().contains(userName)) {  // user did not declare interest
            Message m = messageFactory.createInfoMessage("You have not declared interest for this item.");
            proxy.send(userName, m);
            return;
        }
        
        if (currentItem.getTempBid() >= amount)  // there is already a higher bid waiting validation
            return;
        
        if (State.ACCEPTING_BIDS == state) {
            currentItem.setTempBid(amount); // temp bid is a bid that is higher than the current but needs to be validated by our peer
            Message m = messageFactory.createGotBidMessage(userName, amount);
            proxy.send(Auctioneer.peerName, m);
        } else if (State.READY_TO_END == state) {
            currentItem.addPendingBid(userName, message);
            debug("saved pending bid from " + userName + ", amount = " + amount);
        } else {
            Message m = messageFactory.createInfoMessage("Not accepting bids at the moment.");
            proxy.send(userName, m);
        }
    }

    private void handleReadyToEnd()
    {
        debug("Got ready to end message");
        peerState = State.READY_TO_END;
        
        // if our bidTimer has already ended and there is no tempBid for which we are expecting confirmation
        if ((state == State.READY_TO_END) && (currentItem.getTempBid() == currentItem.getCurrentBid())) { 
            currentItem.incrDiscountRound();
            int discountRound = currentItem.getDiscountRound();
            if (currentItem.getCurrentBidder().equals(Constants.no_holder) && (discountRound < 5)) {
                int newPrice = (currentItem.getStartingPrice() * (10 - discountRound)) / 10;
                currentItem.setCurrentBid(newPrice);
                currentItem.setTempBid(newPrice);
                Message bidAgain = messageFactory.createNewHighBidMessage(Constants.no_holder, newPrice);
                proxy.broadcast(currentItem.getInterestedUsers(), bidAgain);
                debug("sent bid again from handleReadyToEnd");
                setBidTimer();
            } else {                
                anounceWinner();
                peerState = State.ACCEPTING_INTERESTS;
                beginNextAuction();
            }
        }
    }
    
    private void handleReadyToRun()
    {
        debug("Got ready to run message");
        peerState = State.READY_TO_BEGIN;
        
        if (state == State.READY_TO_BEGIN)
            beginNextAuction();
    }

    private void handleInterest(String name, Message message)
    {
        if (state == State.ACCEPTING_INTERESTS) {
            int id = Integer.parseInt(message.getProperty(Constants.item_id));
            if (currentItem.getId() == id) {
                currentItem.addUser(name);
                debug("Added user " + name + " to interestedUsers list for the current item");
                Message m = messageFactory.createInfoMessage("You will now receive updates for this item.");
                proxy.send(name, m);
            } else {
                Message m = messageFactory.createInfoMessage("Invalid item ID.");
                proxy.send(name, m);
            }
        } else {
            Message m = messageFactory.createInfoMessage("Not accepting interests at the moment.");
            proxy.send(name, m);
        }
    }

    /**
     * Handle a connect request
     * @param messageProps
     * @param existingName
     * @param channel
     * @param clientProps 
     */
    private void handleConnect(String existingName, Message message, SocketChannel channel, Map<String, String> clientProps) 
    {
        if (existingName != null) // this user has already connected.
            return;
        String username = message.getProperty(Constants.username);
        if (proxy.isUserActive(username)) {
            Message m = messageFactory.createMessage(Constants.duplicate_name);
            proxy.send(channel, m);
            return;
        }
        proxy.addUser(username, channel);
        clientProps.put(Auctioneer.clientName, username);
        Message m = messageFactory.createInfoMessage("You are now connected to the server.");
        proxy.send(username, m);
    }

    @SuppressWarnings("unused")
    private void printConfData()
    {
        debug("Configuration for " + type + ":");
        debug("TimeLapse = " + timeLapse);
        for (Item i : items)
            debug("Item " + i.getId() + ": Starting Price = " + 
                    i.getStartingPrice() + ", Description = \"" + 
                    i.getDescription() + "\"");
    }

    public String getType()
    {
        return type;
    }

    public Lock getLock()
    {
        return lock;
    }
    
    public void debug(String debugMessage)
    {
        // if (type.equals("callee")) System.out.print(");
        debugLock.lock();
        System.out.print(type + ": ");
        DateFormat dateFormat = new SimpleDateFormat("HH:mm:ss : "); 
        Date date = new Date(); 
        System.out.print(dateFormat.format(date));
        if (debugMessage.contains("\n"))
            System.out.print(debugMessage);
        else 
            System.out.println(debugMessage);
        debugLock.unlock();
    }
    
    private void setState(State state)
    {
        if (this.state == state)
            return;

        this.state = state;
        debug("Changing State to " + state);
    }
    
    public boolean haveAuctionsEnded()
    {
        return auctionsEnded;
    }

    public void removeUserFromInterested(String userName) 
    {
        if (currentItem != null)
            currentItem.getInterestedUsers().remove(userName);    
    }
}
