import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;


public class ConfParser 
{
    private String confFile;
    private int timeLapse;
    private ArrayList<Item> items;
    
    public ConfParser(String confFile)
    {
        this.confFile = confFile;
        items = new ArrayList<Item>();
    }
    
    public void parse() throws IOException
    {
        BufferedReader in = new BufferedReader(new FileReader(confFile));
        
        String line = in.readLine();
        timeLapse = Integer.parseInt(line.trim());
        
        line = in.readLine();
        int numItems = Integer.parseInt(line.trim());
        
        for (int i = 0; i < numItems; i++) {
            line = in.readLine().trim();
            int splitPos = line.indexOf(' ');
            int startingPrice = Integer.parseInt(line.substring(0, splitPos));
            String description = line.substring(splitPos + 1);
            items.add(new Item(startingPrice, description, i+1));
        }
        
        in.close();
    }

    public ArrayList<Item> getItems()
    {
        return items;
    }

    public int getTimeLapse()
    {
        return timeLapse;
    }

}
