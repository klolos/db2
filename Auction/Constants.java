
public class Constants
{
    // bidder to auctioneer commands
    public static final String connect          = "connect";
    public static final String i_am_interested  = "i_am_interested";
    public static final String my_bid           = "my_bid";
    public static final String quit             = "quit";
    
    // auctioneer to bidder commands
    public static final String start_bidding    = "start_bidding";
    public static final String new_high_bid     = "new_high_bid";
    public static final String stop_bidding     = "stop_bidding";
    public static final String duplicate_name   = "duplicate_name";
    public static final String bid_item         = "bid_item";
    public static final String auction_complete = "auction_complete";
    public static final String info             = "info";
    
    // peer to peer commands
    public static final String ready_to_run     = "ready_to_run";
    public static final String ready_to_end     = "ready_to_end";
    public static final String got_bid          = "got_bid";
    public static final String bid_ok           = "bid_ok";
    public static final String interested_count = "interested_count";

    // message property names 
    public static final String username         = "username";
    public static final String item_id          = "item_id";
    public static final String description      = "description";
    public static final String starting_price   = "starting_price";
    public static final String winner           = "winner";
    public static final String highest_bid      = "highest_bid";
    public static final String message          = "message";
    public static final String amount           = "amount";
    
    // other constants
    public static final String no_holder        = "no_holder";
}
