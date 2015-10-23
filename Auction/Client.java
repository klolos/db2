import java.io.BufferedReader;
import java.util.*;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.io.PrintWriter;

 
public class Client {
    private static int port;
    private static String host;
    private static SocketChannel channel;
    public Runnable listeningThread;
    public Runnable commandThread;
    ClientItem currentItem;
    private static String bidderName;
    private static ArrayList<ClientItem> boughtItems; 
    private MessageFactory messageFactory;
    private static String total;
    private Lock itemLock;
    private PrintWriter writer; 
    
    public Client(String host, int port, String bidderName, SocketChannel channel) {
        this.host = host;
        this.port = port;
        this.bidderName = bidderName;
        this.channel = channel;
        
        try {
            writer = new PrintWriter("logs/" + bidderName + ".log", "UTF-8");
        } catch (FileNotFoundException | UnsupportedEncodingException e1) {
            System.err.println("Could not open the log file.");
        }

        boughtItems = new ArrayList<ClientItem>();
        total = "";
        messageFactory  = new MessageFactory();
        itemLock = new ReentrantLock();
        listeningThread = new Runnable() {
            public void run() {
                try {
                    Client.this.runListeningThread();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
        
        commandThread = new Runnable() {
            public void run() {
                try {
                    Client.this.runCommandThread();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
    }
     
    protected void runCommandThread() throws IOException {
        // TODO Auto-generated method stub
        
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        String input;

        while((input=br.readLine())!=null) {
            debug("User said: " + input);
            if (input.equals("list_high_bid")) {
                itemLock.lock();
                if (currentItem != null)
                    System.out.println("Currently highest bid: " +currentItem.getCurrentPrice());
                else
                    System.out.println("You are not in the interested users' list for currentItem.");
                itemLock.unlock();
            }
            else if (input.equals("list_description")) {
                itemLock.lock();
                if (currentItem != null)
                    System.out.println("Description of item currently auctioned: " +currentItem.getDescription());
                else
                    System.out.println("You are not in the interested users' list for currentItem.");
                itemLock.unlock();
            }
            else if (input.equals("quit")) {
                System.out.println("Bye bye " +bidderName);
                send(channel, messageFactory.createQuitMessage(bidderName));
                channel.close();
                /* after closing the channel, we print the items that the user bought */
                if (boughtItems != null) {
                    Iterator<ClientItem> it = boughtItems.iterator();
                    System.out.println("You bought: " + boughtItems.size() + "items!");
                    while (it.hasNext()) {
                        ClientItem bought = it.next();
                        System.out.println("Item ID = " + bought.getId());
                        System.out.println("Item Description = " + bought.getDescription());
                    }
                }
                else 
                    System.out.println("You bought no items."); 
                writer.close();
                System.exit(0);
            }
            else if (input.equals("i_am_interested")) {
                itemLock.lock();
                if (currentItem != null) 
                    send(channel, messageFactory.createIAmInterestedMessage(bidderName, currentItem.getId()));
                else 
                    System.out.println("There is no item, currently auctioned");
                itemLock.unlock();
            }
            else {
                itemLock.lock();
                String[] temp = input.split(" ");
                if (temp[0].equals("bid") && (temp.length == 2)) {
                    System.out.println("You gave a new bid.");
                    int amount = Integer.parseInt(temp[1]);
                    if (currentItem != null) {
                        if (amount > currentItem.getCurrentPrice()) {
                        //    System.out.println("Your bid got accepted.");
                            send(channel, messageFactory.createMyBidMessage(bidderName, amount, currentItem.getId()));
                        }
                        else {
                            System.out.println("Sorry we cannot accept bids less than "+currentItem.getCurrentPrice());
                        }
                    }
                    else 
                        System.out.println("There is no current item to bid for.");
                    itemLock.unlock();
                }        
                else 
                    System.err.println("Wrong type of input. Please try again.");
            }
        }
        debug("Reached End Of File. Exiting...");
        writer.close();
        System.exit(0);
    }

    
    // Listens on the socket channel and prints out whatever the server delivers.
    protected void runListeningThread() throws IOException {
        
        
        while (true) {
            // see if any message has been received
            ByteBuffer bufferA = ByteBuffer.allocate(256);
            String message = "";
            while ((channel.read(bufferA)) > 0) {
                // flip the buffer to start reading
                bufferA.flip();
                message += Charset.defaultCharset().decode(bufferA);
 
            }
 
            if (message.length() > 0) {
                //System.out.print(message);
                processMessage(message);
                //bufferA.clear();
                //handleMessage(message);
                //message = "";
            }
        }
        
    }
    /**
     * Sends the message to the specified channel
     * @param channel
     * @param message
     */
    protected void send(SocketChannel channel, Message message) 
    {
        CharBuffer buffer = CharBuffer.wrap(message.toString());
        try {
            while (buffer.hasRemaining())
                channel.write(Charset.defaultCharset().encode(buffer));
        } catch (IOException e) {
//            e.printStackTrace();
        }
        buffer.clear();
        debug("Sending message: " + message);
    }

    public void processMessage(String s) {
        //@SuppressWarnings("unchecked")
        
        total += s;
        ArrayList<Message> messages = messageFactory.extractMessages(total);
        String remaining = messageFactory.getRemaining();
        total = remaining; 
        
        for (Message m : messages)
            handleMessage(m);
    }

    // General method for handling the commands that server gives
    private void handleMessage(Message m) {
        String command = m.getCommand();
        HashMap<String, String> props = m.getProperties();
        debug("Got message: " +m);
        switch (command) {
        case Constants.duplicate_name:
            handleDuplicateName();
            break;
        case Constants.auction_complete:
              handleAuctionComplete();
              break;
        case Constants.start_bidding:
              itemLock.lock();
            handleStartBidding(props);
            itemLock.unlock();
            break;
        case Constants.bid_item:
            itemLock.lock();
            handleBidItem(props);
            itemLock.unlock();
            break;
        case Constants.new_high_bid:
            itemLock.lock();
            handleNewHighBid(props);
            itemLock.unlock();
            break;
        case Constants.stop_bidding:
            itemLock.lock();
            handleStopBidding(props);
            itemLock.unlock();
            break;
        case Constants.info:
            handleInfo(props);
            break;
        }
    }        


    // Inform users about stop bidding
    // If client is the winner, inform him & add the item bought into his list! 
    private void handleStopBidding(HashMap<String, String> props) {
        System.out.println("You shall now stop bidding for the item.");
        String winner = props.get(Constants.winner);
        if (bidderName.equals(winner)) {
            System.out.println("Congratulations! The item: " + currentItem.getDescription() + " is yours!"); // TODO check whether we should check the props.get("itemID")
            boughtItems.add(currentItem);
            currentItem = null;
        }
        
    }

    // When you get info via the channel, just print it to the user
    private void handleInfo(HashMap<String, String> props) {
        for (String key : props.keySet())
            if (key.equals("message"))
                System.out.print(props.get(key));
        System.out.println();        
    }

    
    // Inform users about new highest bid & holder
    private void handleNewHighBid(HashMap<String, String> props) {
        System.out.println("New highest bid for current item belongs to " +props.get(Constants.username));
        System.out.println("New price: " + props.get(Constants.amount));
        currentItem.setHolder(props.get(Constants.username));
        currentItem.setCurrentPrice(Integer.parseInt(props.get(Constants.amount)));
    }

    // Inform users about the info of the new item + create the new current Item 
    private void handleBidItem(HashMap<String, String> props) {
        System.out.println("New item out for bidding!");
        System.out.println("Item ID: " + props.get(Constants.item_id));
        System.out.println("Item Description: " + props.get(Constants.description));
        System.out.println("Starting Price: " +props.get(Constants.starting_price));
        int initial = Integer.parseInt(props.get(Constants.starting_price));
        int id      = Integer.parseInt(props.get(Constants.item_id));
        
        // create the new current Item with all of its details
        currentItem = new ClientItem(initial, props.get(Constants.description), id, Constants.no_holder);
    }

    private void handleStartBidding(HashMap<String, String> props) {
        String temp  = props.get("item_id");
        String descr = currentItem.getDescription();
        System.out.println("You may now start bidding for item with description = " + descr + "!");
        //currentItem.id = Integer.parseInt(temp);
    }

    // Inform users about the completion of the auction
    private void handleAuctionComplete() {
        System.out.println("Auction is now completed.");
        System.out.println("Thank you for your participation");
        try {
            channel.close();
        } catch (IOException e) {    
        }
        writer.close();
        System.exit(0);
    }

    private void handleDuplicateName() {
        System.err.println("Username " + bidderName + " already exists.");
        writer.close();
        System.exit(1);        
    }

    private void printMessage(String command, HashMap<String, String> props) {
        System.out.print("Received: " + command + ": ");
        for (String key : props.keySet())
            System.out.print(key + " = \"" + props.get(key) + "\" ");
        System.out.println();        
    }
    
    public void debug(String debugMessage)
    {
        DateFormat dateFormat = new SimpleDateFormat("HH:mm:ss : "); 
        Date date = new Date(); 
        writer.print(dateFormat.format(date));
        if (debugMessage.contains("\n"))
            writer.print(debugMessage);
        else 
            writer.println(debugMessage);
        writer.flush();
    }

}
