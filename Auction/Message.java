import java.util.HashMap;


public class Message 
{
    private String command;
    private HashMap<String, String> properties;
        
    public Message(String command)
    {
        this.command = command;
        properties = new HashMap<String, String>();
    }
    
    public void addProperty(String name, String value)
    {
        properties.put(name, value);
    }
    
    public void addProperty(String name, int value)
    {
        properties.put(name, String.format("%d", value));
    }
    
    public HashMap<String, String> getProperties()
    {
        return properties;
    }
    
    public String getProperty(String name)
    {
        return properties.get(name);
    }
    
    public String toString()
    {
        String message = command + ": ";
        for (String key : properties.keySet())
            message += key + " = \"" + properties.get(key) + "\", ";

        if (properties.isEmpty())
            return message + "\n";
        else
            return message.substring(0, message.length() - 2) + "\n";
    }
    
    public String getCommand()
    {
        return command;
    }
}

