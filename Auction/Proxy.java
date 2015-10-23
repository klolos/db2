import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;


public class Proxy
{
    private int port;
    private int peerPort;
    private Auctioneer auctioneer;
    private SocketChannel peerChannel;
    private HashMap<String, SocketChannel> activeChannels;
    private Lock lock;
    private long timeToCheck;
    private Selector selector;
    
    /**
     * Constructor for the proxy server
     * @param port
     * @param auctioneer
     */
    public Proxy(int port, Auctioneer auctioneer)
    {
        this.port       = port;
        this.auctioneer = auctioneer;
        this.lock       = auctioneer.getLock();
        timeToCheck     = 1000;
        activeChannels  = new HashMap<String, SocketChannel>();
    }
    
    /**
     * Returns true if a username is already in use
     * @param username
     * @return
     */
    public boolean isUserActive (String username)
    {
        return activeChannels.containsKey(username);
    }
    
    /**
     * Adds user to the proxy's list of active users
     * @param username
     * @param channel
     */
    public void addUser(String username, SocketChannel channel)
    {
        activeChannels.put(username, channel);
    }
    
    /**
     * Removes user from the proxy's list of active users
     * @param username
     * @param channel
     */
    public void removeUser(String username)
    {
        activeChannels.remove(username);
    }
    
    /**
     * Connects to an Auctioneer peer
     * @param peerPort
     * @throws IOException 
     */
    private void connectToPeer(int peerPort) throws IOException
    {
        auctioneer.debug("Connecting to remote peer");
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
//            e.printStackTrace();
        }
        
        peerChannel = SocketChannel.open();
        peerChannel.configureBlocking(false);
        peerChannel.connect(new InetSocketAddress("localhost", peerPort));

        while (!peerChannel.finishConnect())
            auctioneer.debug("Connecting to peer ...");
        
        auctioneer.debug("Connected to remote peer");
    }
    
    /**
     * Accepts a connection from a remote peer
     * @param channel
     * @throws IOException
     */
    private void acceptPeer(ServerSocketChannel channel) throws IOException
    {
        auctioneer.debug("Now accepting peers!");
        while (peerChannel == null) {
            peerChannel = channel.accept();
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
//                e.printStackTrace();
            }
        }
        peerChannel.configureBlocking(false);
        
        auctioneer.debug("Peer accepted!");
    }
    
    /**
     * Register the given selector to the peer channel
     * @param selector
     * @throws ClosedChannelException
     */
    private void registerPeer(Selector selector) throws ClosedChannelException
    {
        SelectionKey peerKey = peerChannel.register(
                selector, SelectionKey.OP_READ,
                SelectionKey.OP_WRITE);
        
        Map<String, String> clientproperties = new HashMap<String, String>();
        clientproperties.put(Auctioneer.channelType, Auctioneer.clientChannel);
        clientproperties.put(Auctioneer.bytesReadString, "");
        clientproperties.put(Auctioneer.clientName, Auctioneer.peerName);
        peerKey.attach(clientproperties);
        
    }
    
    public void setPeerPort(int peerPort)
    {
        this.peerPort = peerPort;
    }

    /**
     * Broadcast a message to all the connected bidders
     * @param message
     */
    public void broadcast(Message message)
    {
        CharBuffer buffer;
        for (SocketChannel channel : activeChannels.values())
        {
            buffer = CharBuffer.wrap(message.toString());
            try {
                while (buffer.hasRemaining())
                    channel.write(Charset.defaultCharset().encode(buffer));
            } catch (IOException e) {
//                e.printStackTrace();
            }
            buffer.clear();
        }
    }
    
    /**
     * Broadcast a message to all the interested bidders
     * @param message
     */
    public void broadcast(ArrayList<String> users, Message message)
    {
        for (String user : users)
            send(user, message);
    }
    
    /**
     * Send a message to a user
     * @param user
     * @param message
     */
    public void send(String user, Message message)
    {
        if (Auctioneer.peerName.equals(user)) {
            send(peerChannel, message);
        } else {
            SocketChannel channel = activeChannels.get(user);
            send(channel, message);
        }
    }
    
    /**
     * Sends the message to the specified channel
     * @param channel
     * @param message
     */
    public void send(SocketChannel channel, Message message) 
    {
        CharBuffer buffer = CharBuffer.wrap(message.toString());
        try {
            while (buffer.hasRemaining())
                channel.write(Charset.defaultCharset().encode(buffer));
        } catch (IOException e) {
//            e.printStackTrace();
        }
        buffer.clear();
    }
    
    /**
     * Listens for incoming messages and connections
     * @throws IOException 
     */
    public void run() throws IOException
    {
        auctioneer.debug("Auctioneer starting ...");
        
        ServerSocketChannel channel = ServerSocketChannel.open();
        channel.bind(new InetSocketAddress("localhost", port));
        channel.configureBlocking(false);
        selector = Selector.open();
        SelectionKey socketServerSelectionKey = channel.register(selector, SelectionKey.OP_ACCEPT);
        
        Map<String, String> properties = new HashMap<String, String>();
        properties.put(Auctioneer.channelType, Auctioneer.serverChannel);
        socketServerSelectionKey.attach(properties);
        
        if (auctioneer.getType().equals("caller"))
            connectToPeer(peerPort);
        else
            acceptPeer(channel);

        registerPeer(selector);
        
        
        // wait for the selected keys
        for (;;) {
            
            try {
                if (selector.select(timeToCheck) == 0) {
                    if (auctioneer.haveAuctionsEnded()) {
                        shutdown();
                        break;                    
                    }
                    else 
                        continue;
                }
            } catch (IOException e1) {
                auctioneer.debug("Selector throwed exception: " + e1);
                continue;
            }
            
            lock.lock();

            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = selectedKeys.iterator();
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                
                // An incoming connection
                if (((Map<?, ?>) key.attachment()).get(Auctioneer.channelType).equals(Auctioneer.serverChannel)) {
                    ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
                    SocketChannel clientSocketChannel;
                    try {
                        clientSocketChannel = serverSocketChannel.accept();

                        if (clientSocketChannel != null) {
                            // set the client connection to be non blocking
                            clientSocketChannel.configureBlocking(false);
                            SelectionKey clientKey = clientSocketChannel
                                    .register(selector, SelectionKey.OP_READ,
                                            SelectionKey.OP_WRITE);

                            Map<String, String> clientproperties = new HashMap<String, String>();
                            clientproperties.put(Auctioneer.channelType,
                                    Auctioneer.clientChannel);
                            clientproperties
                                    .put(Auctioneer.bytesReadString, "");
                            clientproperties.put(Auctioneer.clientName, null);
                            clientKey.attach(clientproperties);

                            auctioneer.debug("Connection accepted!");
                        }
                    } catch (IOException e) {
                        auctioneer.debug("Accepting client connection exception: " + e);
                        continue;
                    }
                } else {
                    // data is available for read
                    ByteBuffer buffer = ByteBuffer.allocate(256);
                    SocketChannel clientChannel = (SocketChannel) key.channel();

                    int bytesRead = 0;
                    if (key.isReadable()) {
                        try {
                            if ((bytesRead = clientChannel.read(buffer)) > 0) {
                                buffer.flip();
                                String s = Charset.defaultCharset().decode(buffer).toString();
                                //auctioneer.debug("Got message: " + s);
                                auctioneer.processMessage(s, key);
                                buffer.clear();
                            }
                        } catch (IOException e) {
                            try {
                                clientChannel.close();
                            } catch (IOException e1) {
                            }
                            @SuppressWarnings("unchecked")
                            Map<String, String> clientProps = (Map<String, String>) key.attachment();
                            String userName  = clientProps.get(Auctioneer.clientName);
                            if (userName != null) {
                                if (userName.equals(Auctioneer.peerName)) {
                                    auctioneer.debug("Connection with the peer was dropped, exiting...");
                                    shutdown();
                                    break;
                                } else {
                                    activeChannels.remove(userName);
                                    auctioneer.removeUserFromInterested(userName);
                                    auctioneer.debug("User " + userName + " disconnected.");
                                }
                            }
                        }
                        
                        if (bytesRead < 0)
                            try {
                                clientChannel.close();
                            } catch (IOException e) {
                            }
                    }
                }
 
                iterator.remove();
            }
            lock.unlock();
        }
    }

    private void shutdown()
    {
        for (SocketChannel c: activeChannels.values()) {
            try {
                c.close();
            } catch (IOException e) {
//                e.printStackTrace();
            }
        }
        try {
            peerChannel.close();
        } catch (IOException e) {
//            e.printStackTrace();
        }
        try {
            selector.close();
        } catch (IOException e) {
//            e.printStackTrace();
        }
    }

    public void closeConnection(String userName) 
    {
        try {
            activeChannels.remove(userName).close();
            auctioneer.debug("User \"" + userName + "\" quit.");
        } catch (IOException e) {
//            e.printStackTrace();
        }
    }
}
