public class ClientItem
{
    private int id;
    private int startingPrice;
    private String description;
    private String holder;
    
    private int currentPrice;
    
    public ClientItem(int startingPrice, String description, int id, String holder)
    {
        this.id = id;
        this.startingPrice = startingPrice;
        this.description = description;
        this.holder = holder;
        this.currentPrice = startingPrice;    
    }
        
    public int getId() 
    {
        return id;
    }
    
    public void setId(int id) 
    {
        this.id = id;
    }

    public int getStartingPrice() {
        return startingPrice;
    }
    
    public void setStartingPrice(int starting_price) {
        this.startingPrice = starting_price;
    }

    public String getDescription() {
        return description;
    }    
    
    public void setDescription(String descr) {
        this.description = descr;
    }
    
    public String getHolder() {
        return holder;
    }
    
    public void setHolder(String hold) {
        this.holder = hold;
    }
    
    public int getCurrentPrice() {
        return currentPrice;
    }
    
    public void setCurrentPrice(int curr_price) {
        this.currentPrice = curr_price;
    }
}
