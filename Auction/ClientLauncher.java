import java.io.BufferedReader;
import java.util.*;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

 
public class ClientLauncher {
    private static int port;
    private static String host;
    private static SocketChannel channel;
    public static String bidderName;
    
    public static void main(String[] args) throws IOException, InterruptedException 
    {
        if (args.length != 3) {
            System.err.println("Wrong number of parameters.");
            System.err.println("Usage: ClientLauncher <host> <port> <bidderName>");
            System.exit(1);
        }
        
        host = args[0];
        
        try {
            port = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            System.err.println("Argument <port> must be an integer.");
            System.exit(1);
        }
        
            
        channel = SocketChannel.open();
        
        bidderName = args[2] + "@" +host + ":" +port;        
//        channel.configureBlocking(false);
        channel.connect(new InetSocketAddress(host, port));
        
        while (!channel.finishConnect())
            ;
        
        
        
        CharBuffer buffer = CharBuffer.wrap("connect: username = \"" + bidderName + "\"\n");
        while (buffer.hasRemaining()) {
            channel.write(Charset.defaultCharset().encode(buffer));
        }
        

        Client client = new Client(host, port, bidderName, channel);
        new Thread(client.listeningThread).start();
        new Thread(client.commandThread).start();
    }
}
