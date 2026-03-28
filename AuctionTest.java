import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

class AuctionNotFoundException extends Exception{
    AuctionNotFoundException(String message){
        super(message);
    }
}
class InvalidBidException extends Exception{
    InvalidBidException(String message){
        super(message);
    }
}
class AuctionCloseException extends Exception{
    AuctionCloseException(String message){
        super(message);
    }
}
enum AuctionStatus{OPEN,CLOSED}
class Bid{
    String bidId;
    String auctionId;
    String bidderId;
    double amount;
    Bid(String auctionId,String bidderId,double amount){
        this.amount=amount;
        this.auctionId=auctionId;
        this.bidderId=bidderId;
        this.bidId="BID"+ UUID.randomUUID().toString().substring(0,6);
    }
}
class Auction{
    String auctionId;
    String sellerId;
    String itemName;
    double reservePrice;
    double minBidIncrement;
    long endMs;
    CopyOnWriteArrayList<Bid> bids=new CopyOnWriteArrayList<>();;
    Bid highestBid;
    AuctionStatus status;
    ReadWriteLock lock=new ReentrantReadWriteLock();

    public Auction(String sellerId, String itemName, double reservePrice, double minBidIncrement,long durationMs) {
        this.sellerId = sellerId;
        this.itemName = itemName;
        this.reservePrice = reservePrice;
        this.minBidIncrement = minBidIncrement;
        auctionId="AU-"+UUID.randomUUID().toString().substring(0,6);
        this.endMs=System.currentTimeMillis()+durationMs;
    }
    boolean isExpired() {return System.currentTimeMillis()>=this.endMs;}
}
class AuctionService{
    ConcurrentHashMap<String,Auction> auctions=new ConcurrentHashMap<>();
    Auction createAuction(String sellerId,String itemName,double reservedPrice,double minIncrementBid,long durationMs){
        Auction auction=new Auction(sellerId,itemName,reservedPrice,minIncrementBid,durationMs);
        auctions.put(auction.auctionId,auction);
        return auction;
    }
    Bid placeBid(String auctionId,String bidderId,double amount) throws AuctionNotFoundException, InvalidBidException, AuctionCloseException {
        Auction auction=auctions.get(auctionId);
        if(auction==null) throw new AuctionNotFoundException(auctionId);
        auction.lock.writeLock().lock();
        try{
            if(auction.isExpired() && auction.status==AuctionStatus.OPEN) closeAuction(auctionId);
            if(bidderId.equals(auction.sellerId)) throw new InvalidBidException("seller can't bid");
            if(auction.status!=AuctionStatus.OPEN) throw new AuctionNotFoundException(auctionId);
            double minRequired=(auction.highestBid!=null)?auction.highestBid.amount+ auction.minBidIncrement:auction.reservePrice;
            if(amount<minRequired) throw new InvalidBidException("amount less than"+minRequired);
            Bid bid=new Bid(auctionId,bidderId,amount);
            auction.bids.add(bid);
            auction.highestBid=bid;
            return bid;
        }finally {
            auction.lock.writeLock().unlock();
        }
    }
    void closeAuction(String auctionId) throws AuctionCloseException {
        Auction auction=auctions.get(auctionId);
        if(auction==null) throw new AuctionCloseException(auctionId);
        auction.lock.writeLock().lock();
        try {
            auction.status=AuctionStatus.CLOSED;
        }finally {
            auction.lock.writeLock().unlock();
        }
    }
    int closedExpiredAuction()  {
        int closed=0;
        for(Auction a:auctions.values()){
            if(a.status==AuctionStatus.OPEN && a.isExpired()){
                try{
                    closeAuction(a.auctionId);
                    closed++;
                }catch (AuctionCloseException ignored){

                }
            }
        }
        return closed;
    }
    List<Bid> getBidHistory(String auctionId) throws AuctionNotFoundException {
        Auction auction=auctions.get(auctionId);
        if(auction==null) throw new AuctionNotFoundException(auctionId);
        auction.lock.readLock().lock();
        try {
            return new ArrayList<>(auction.bids);
        }finally {
            auction.lock.readLock().unlock();
        }
    }
}

public class AuctionTest {
    static int passed = 0, failed = 0;

    static boolean check(String name, boolean ok) {
        if (ok) { passed++; System.out.println("  ✅ PASS: " + name); }
        else    { failed++; System.out.println("  ❌ FAIL: " + name); }
        return ok;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║       Auction System Test Suite          ║");
        System.out.println("╚══════════════════════════════════════════╝");

        // ── Exceptions ──
        testExceptionClasses();

        // ── Enums ──
        testAuctionStatusEnum();

        // ── Bid ──
        testBidCreation();
        testBidIdUnique();

        // ── Auction ──
        testAuctionCreation();
        testAuctionIdFormat();
        testAuctionIsExpired();
        testAuctionNotExpired();
        testAuctionDefaults();

        // ── AuctionService: createAuction ──
        testCreateAuction();
        testCreateMultipleAuctions();

        // ── AuctionService: placeBid ──
        testPlaceBidSuccess();
        testPlaceBidUpdatesHighest();
        testPlaceBidAddsToBidList();
        testPlaceBidAuctionNotFound();
        testPlaceBidSellerCannotBid();
        testPlaceBidBelowReservePrice();
        testPlaceBidBelowMinIncrement();
        testPlaceBidOnClosedAuction();
        testPlaceBidOnExpiredAuction();
        testPlaceBidMultipleBidders();
        testPlaceBidExactMinRequired();

        // ── AuctionService: closeAuction ──
        testCloseAuctionSuccess();
        testCloseAuctionAlreadyClosed();
        testCloseAuctionNotFound();

        // ── AuctionService: closedExpiredAuction ──
        testClosedExpiredAuctionNoneExpired();
        testClosedExpiredAuctionSomeExpired();
        testClosedExpiredAuctionAlreadyClosed();

        // ── AuctionService: getBidHistory ──
        testGetBidHistoryEmpty();
        testGetBidHistoryWithBids();
        testGetBidHistoryNotFound();
        testGetBidHistoryReturnsCopy();

        // ── End-to-End ──
        testEndToEndFullAuctionFlow();
        testEndToEndNoBidsAuction();
        testEndToEndMultipleAuctions();
        testEndToEndBidWarScenario();

        System.out.println("\n══════════════════════════════════════════");
        System.out.printf("  Results: %d passed, %d failed, %d total%n", passed, failed, passed + failed);
        System.out.println("══════════════════════════════════════════");
        System.exit(failed > 0 ? 1 : 0);
    }

    // ════════════════════════════════════════════════════════════════
    //              Exceptions
    // ════════════════════════════════════════════════════════════════
    static void testExceptionClasses() {
        System.out.println("\n── Exceptions ──");
        AuctionNotFoundException anf = new AuctionNotFoundException("not found");
        check("AuctionNotFoundException message", "not found".equals(anf.getMessage()));
        check("AuctionNotFoundException is Exception", anf instanceof Exception);

        InvalidBidException ib = new InvalidBidException("bad bid");
        check("InvalidBidException message", "bad bid".equals(ib.getMessage()));
        check("InvalidBidException is Exception", ib instanceof Exception);

        AuctionCloseException ac = new AuctionCloseException("close err");
        check("AuctionCloseException message", "close err".equals(ac.getMessage()));
        check("AuctionCloseException is Exception", ac instanceof Exception);

        check("null message works", new AuctionNotFoundException(null).getMessage() == null);
        check("empty message works", "".equals(new InvalidBidException("").getMessage()));
    }

    // ════════════════════════════════════════════════════════════════
    //              AuctionStatus Enum
    // ════════════════════════════════════════════════════════════════
    static void testAuctionStatusEnum() {
        System.out.println("\n── AuctionStatus Enum ──");
        check("OPEN exists", AuctionStatus.valueOf("OPEN") == AuctionStatus.OPEN);
        check("CLOSED exists", AuctionStatus.valueOf("CLOSED") == AuctionStatus.CLOSED);
        check("2 values total", AuctionStatus.values().length == 2);
    }

    // ════════════════════════════════════════════════════════════════
    //              Bid
    // ════════════════════════════════════════════════════════════════
    static void testBidCreation() {
        System.out.println("\n── Bid Creation ──");
        Bid b = new Bid("AU-001", "bidder1", 100.0);
        check("auctionId set", "AU-001".equals(b.auctionId));
        check("bidderId set", "bidder1".equals(b.bidderId));
        check("amount set", b.amount == 100.0);
        check("bidId not null", b.bidId != null);
        check("bidId starts with BID", b.bidId.startsWith("BID"));
        check("bidId has length 9 (BID + 6)", b.bidId.length() == 9);
    }

    static void testBidIdUnique() {
        System.out.println("\n── Bid ID Uniqueness ──");
        Bid b1 = new Bid("AU-001", "bidder1", 50.0);
        Bid b2 = new Bid("AU-001", "bidder2", 60.0);
        Bid b3 = new Bid("AU-002", "bidder1", 70.0);
        check("bid1 != bid2", !b1.bidId.equals(b2.bidId));
        check("bid2 != bid3", !b2.bidId.equals(b3.bidId));
        check("bid1 != bid3", !b1.bidId.equals(b3.bidId));
    }

    // ════════════════════════════════════════════════════════════════
    //              Auction
    // ════════════════════════════════════════════════════════════════
    static void testAuctionCreation() {
        System.out.println("\n── Auction Creation ──");
        Auction a = new Auction("seller1", "Painting", 500.0, 10.0, 60000);
        check("sellerId set", "seller1".equals(a.sellerId));
        check("itemName set", "Painting".equals(a.itemName));
        check("reservePrice set", a.reservePrice == 500.0);
        check("minBidIncrement set", a.minBidIncrement == 10.0);
        check("auctionId not null", a.auctionId != null);
        check("bids list initialized", a.bids != null);
        check("bids list empty", a.bids.isEmpty());
        check("highestBid is null", a.highestBid == null);
        check("endMs in future", a.endMs > System.currentTimeMillis());
        check("lock initialized", a.lock != null);
    }

    static void testAuctionIdFormat() {
        System.out.println("\n── Auction ID Format ──");
        Auction a = new Auction("seller", "Item", 100.0, 5.0, 60000);
        check("auctionId starts with AU-", a.auctionId.startsWith("AU-"));
        check("auctionId length is 9 (AU- + 6)", a.auctionId.length() == 9);

        Auction a2 = new Auction("seller", "Item", 100.0, 5.0, 60000);
        check("two auctions have different IDs", !a.auctionId.equals(a2.auctionId));
    }

    static void testAuctionIsExpired() {
        System.out.println("\n── Auction isExpired (expired) ──");
        Auction a = new Auction("seller", "Item", 100.0, 5.0, 0);
        // durationMs=0 means endMs = now, so isExpired should be true after a tiny delay
        try { Thread.sleep(5); } catch (InterruptedException e) {}
        check("auction with 0ms duration is expired", a.isExpired());
    }

    static void testAuctionNotExpired() {
        System.out.println("\n── Auction isExpired (not expired) ──");
        Auction a = new Auction("seller", "Item", 100.0, 5.0, 60000);
        check("auction with 60s duration is not expired", !a.isExpired());
    }

    static void testAuctionDefaults() {
        System.out.println("\n── Auction Defaults ──");
        Auction a = new Auction("seller", "Widget", 10.0, 1.0, 5000);
        check("status is null initially (not explicitly set)", a.status == null);
        check("highestBid null initially", a.highestBid == null);
        check("bids empty initially", a.bids.size() == 0);
    }

    // ════════════════════════════════════════════════════════════════
    //              AuctionService: createAuction
    // ════════════════════════════════════════════════════════════════
    static void testCreateAuction() {
        System.out.println("\n── AuctionService: createAuction ──");
        AuctionService service = new AuctionService();
        Auction a = service.createAuction("seller1", "Vase", 200.0, 10.0, 60000);
        check("returns auction", a != null);
        check("auction stored in map", service.auctions.containsKey(a.auctionId));
        check("auction in map is same object", service.auctions.get(a.auctionId) == a);
        check("sellerId correct", "seller1".equals(a.sellerId));
        check("itemName correct", "Vase".equals(a.itemName));
        check("reservePrice correct", a.reservePrice == 200.0);
        check("minBidIncrement correct", a.minBidIncrement == 10.0);
    }

    static void testCreateMultipleAuctions() {
        System.out.println("\n── AuctionService: createMultipleAuctions ──");
        AuctionService service = new AuctionService();
        Auction a1 = service.createAuction("s1", "Item1", 100, 5, 60000);
        Auction a2 = service.createAuction("s2", "Item2", 200, 10, 60000);
        Auction a3 = service.createAuction("s1", "Item3", 300, 15, 60000);
        check("3 auctions in map", service.auctions.size() == 3);
        check("all different IDs", !a1.auctionId.equals(a2.auctionId) && !a2.auctionId.equals(a3.auctionId));
        check("a1 accessible", service.auctions.get(a1.auctionId) == a1);
        check("a2 accessible", service.auctions.get(a2.auctionId) == a2);
        check("a3 accessible", service.auctions.get(a3.auctionId) == a3);
    }

    // ════════════════════════════════════════════════════════════════
    //              AuctionService: placeBid
    // ════════════════════════════════════════════════════════════════
    static void testPlaceBidSuccess() {
        System.out.println("\n── placeBid: Success ──");
        AuctionService service = new AuctionService();
        Auction a = service.createAuction("seller1", "Item", 100.0, 10.0, 60000);
        a.status = AuctionStatus.OPEN;
        try {
            Bid bid = service.placeBid(a.auctionId, "bidder1", 100.0);
            check("bid returned", bid != null);
            check("bid auctionId matches", a.auctionId.equals(bid.auctionId));
            check("bid bidderId matches", "bidder1".equals(bid.bidderId));
            check("bid amount correct", bid.amount == 100.0);
            check("bidId not null", bid.bidId != null);
        } catch (Exception e) { check("placeBid success: " + e.getMessage(), false); }
    }

    static void testPlaceBidUpdatesHighest() {
        System.out.println("\n── placeBid: Updates Highest Bid ──");
        AuctionService service = new AuctionService();
        Auction a = service.createAuction("seller1", "Item", 100.0, 10.0, 60000);
        a.status = AuctionStatus.OPEN;
        try {
            Bid b1 = service.placeBid(a.auctionId, "bidder1", 100.0);
            check("highest bid after 1st", a.highestBid == b1);
            check("highest amount is 100", a.highestBid.amount == 100.0);

            Bid b2 = service.placeBid(a.auctionId, "bidder2", 115.0);
            check("highest bid after 2nd", a.highestBid == b2);
            check("highest amount is 115", a.highestBid.amount == 115.0);

            Bid b3 = service.placeBid(a.auctionId, "bidder1", 130.0);
            check("highest bid after 3rd", a.highestBid == b3);
            check("highest amount is 130", a.highestBid.amount == 130.0);
        } catch (Exception e) { check("placeBid highest: " + e.getMessage(), false); }
    }

    static void testPlaceBidAddsToBidList() {
        System.out.println("\n── placeBid: Adds to Bid List ──");
        AuctionService service = new AuctionService();
        Auction a = service.createAuction("seller1", "Item", 50.0, 5.0, 60000);
        a.status = AuctionStatus.OPEN;
        try {
            service.placeBid(a.auctionId, "b1", 50.0);
            check("1 bid in list", a.bids.size() == 1);
            service.placeBid(a.auctionId, "b2", 60.0);
            check("2 bids in list", a.bids.size() == 2);
            service.placeBid(a.auctionId, "b3", 70.0);
            check("3 bids in list", a.bids.size() == 3);
            check("bids in order: 1st=50", a.bids.get(0).amount == 50.0);
            check("bids in order: 2nd=60", a.bids.get(1).amount == 60.0);
            check("bids in order: 3rd=70", a.bids.get(2).amount == 70.0);
        } catch (Exception e) { check("placeBid list: " + e.getMessage(), false); }
    }

    static void testPlaceBidAuctionNotFound() {
        System.out.println("\n── placeBid: Auction Not Found ──");
        AuctionService service = new AuctionService();
        try {
            service.placeBid("AU-NONEXIST", "bidder1", 100.0);
            check("should throw AuctionNotFoundException", false);
        } catch (AuctionNotFoundException e) {
            check("throws AuctionNotFoundException", true);
            check("message contains auction id", e.getMessage().contains("AU-NONEXIST"));
        } catch (Exception e) { check("wrong exception type: " + e.getClass().getSimpleName(), false); }
    }

    static void testPlaceBidSellerCannotBid() {
        System.out.println("\n── placeBid: Seller Cannot Bid ──");
        AuctionService service = new AuctionService();
        Auction a = service.createAuction("seller1", "Item", 100.0, 10.0, 60000);
        a.status = AuctionStatus.OPEN;
        try {
            service.placeBid(a.auctionId, "seller1", 150.0);
            check("seller bidding should throw", false);
        } catch (InvalidBidException e) {
            check("throws InvalidBidException for seller bid", true);
            check("message mentions seller", e.getMessage().contains("seller"));
        } catch (Exception e) { check("wrong exception: " + e.getClass().getSimpleName(), false); }
    }

    static void testPlaceBidBelowReservePrice() {
        System.out.println("\n── placeBid: Below Reserve Price ──");
        AuctionService service = new AuctionService();
        Auction a = service.createAuction("seller1", "Item", 100.0, 10.0, 60000);
        a.status = AuctionStatus.OPEN;
        try {
            service.placeBid(a.auctionId, "bidder1", 50.0);
            check("bid below reserve should throw", false);
        } catch (InvalidBidException e) {
            check("throws InvalidBidException for low bid", true);
        } catch (Exception e) { check("wrong exception: " + e.getClass().getSimpleName(), false); }
    }

    static void testPlaceBidBelowMinIncrement() {
        System.out.println("\n── placeBid: Below Min Increment ──");
        AuctionService service = new AuctionService();
        Auction a = service.createAuction("seller1", "Item", 100.0, 20.0, 60000);
        a.status = AuctionStatus.OPEN;
        try {
            service.placeBid(a.auctionId, "bidder1", 100.0); // first bid at reserve
            service.placeBid(a.auctionId, "bidder2", 110.0); // 110 < 100+20=120
            check("bid below min increment should throw", false);
        } catch (InvalidBidException e) {
            check("throws InvalidBidException for insufficient increment", true);
        } catch (Exception e) { check("wrong exception: " + e.getClass().getSimpleName(), false); }
    }

    static void testPlaceBidOnClosedAuction() {
        System.out.println("\n── placeBid: On Closed Auction ──");
        AuctionService service = new AuctionService();
        Auction a = service.createAuction("seller1", "Item", 100.0, 10.0, 60000);
        a.status = AuctionStatus.CLOSED;
        try {
            service.placeBid(a.auctionId, "bidder1", 200.0);
            check("bid on closed auction should throw", false);
        } catch (AuctionNotFoundException e) {
            check("throws AuctionNotFoundException for closed auction", true);
        } catch (Exception e) { check("wrong exception: " + e.getClass().getSimpleName(), false); }
    }

    static void testPlaceBidOnExpiredAuction() {
        System.out.println("\n── placeBid: On Expired Auction ──");
        AuctionService service = new AuctionService();
        Auction a = service.createAuction("seller1", "Item", 100.0, 10.0, 1); // 1ms duration
        a.status = AuctionStatus.OPEN;
        try { Thread.sleep(10); } catch (InterruptedException e) {}
        try {
            service.placeBid(a.auctionId, "bidder1", 200.0);
            check("bid on expired auction should throw", false);
        } catch (AuctionNotFoundException e) {
            check("expired auction auto-closed and throws", true);
            check("auction status set to CLOSED", a.status == AuctionStatus.CLOSED);
        } catch (AuctionCloseException e) {
            check("expired auction throws close exception", true);
        } catch (Exception e) { check("wrong exception: " + e.getClass().getSimpleName(), false); }
    }

    static void testPlaceBidMultipleBidders() {
        System.out.println("\n── placeBid: Multiple Bidders ──");
        AuctionService service = new AuctionService();
        Auction a = service.createAuction("seller", "Art", 100.0, 10.0, 60000);
        a.status = AuctionStatus.OPEN;
        try {
            Bid b1 = service.placeBid(a.auctionId, "alice", 100.0);
            Bid b2 = service.placeBid(a.auctionId, "bob", 120.0);
            Bid b3 = service.placeBid(a.auctionId, "charlie", 140.0);
            Bid b4 = service.placeBid(a.auctionId, "alice", 160.0);

            check("4 bids placed", a.bids.size() == 4);
            check("highest bid is alice's 2nd", a.highestBid == b4);
            check("highest amount is 160", a.highestBid.amount == 160.0);
            check("alice can bid twice", a.bids.get(0).bidderId.equals("alice") && a.bids.get(3).bidderId.equals("alice"));
        } catch (Exception e) { check("multiple bidders: " + e.getMessage(), false); }
    }

    static void testPlaceBidExactMinRequired() {
        System.out.println("\n── placeBid: Exact Min Required ──");
        AuctionService service = new AuctionService();
        Auction a = service.createAuction("seller", "Item", 100.0, 25.0, 60000);
        a.status = AuctionStatus.OPEN;
        try {
            service.placeBid(a.auctionId, "bidder1", 100.0);   // exactly reserve
            check("bid at exact reserve accepted", a.bids.size() == 1);

            service.placeBid(a.auctionId, "bidder2", 125.0);   // exactly 100+25
            check("bid at exact min increment accepted", a.bids.size() == 2);
            check("highest is 125", a.highestBid.amount == 125.0);
        } catch (Exception e) { check("exact min required: " + e.getMessage(), false); }
    }

    // ════════════════════════════════════════════════════════════════
    //              AuctionService: closeAuction
    // ════════════════════════════════════════════════════════════════
    static void testCloseAuctionSuccess() {
        System.out.println("\n── closeAuction: Success ──");
        AuctionService service = new AuctionService();
        Auction a = service.createAuction("seller", "Item", 100.0, 10.0, 60000);
        a.status = AuctionStatus.OPEN;
        try {
            service.closeAuction(a.auctionId);
            check("auction status is CLOSED", a.status == AuctionStatus.CLOSED);
        } catch (Exception e) { check("closeAuction: " + e.getMessage(), false); }
    }

    static void testCloseAuctionAlreadyClosed() {
        System.out.println("\n── closeAuction: Already Closed ──");
        AuctionService service = new AuctionService();
        Auction a = service.createAuction("seller", "Item", 100.0, 10.0, 60000);
        a.status = AuctionStatus.CLOSED;
        try {
            service.closeAuction(a.auctionId);
            // closeAuction doesn't check if already closed, just sets CLOSED again
            check("closing already-closed auction no-ops without error", a.status == AuctionStatus.CLOSED);
        } catch (Exception e) { check("close already-closed: " + e.getMessage(), false); }
    }

    static void testCloseAuctionNotFound() {
        System.out.println("\n── closeAuction: Not Found ──");
        AuctionService service = new AuctionService();
        try {
            service.closeAuction("AU-NONEXIST");
            check("should throw AuctionCloseException", false);
        } catch (AuctionCloseException e) {
            check("throws AuctionCloseException for missing auction", true);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //              AuctionService: closedExpiredAuction
    // ════════════════════════════════════════════════════════════════
    static void testClosedExpiredAuctionNoneExpired() {
        System.out.println("\n── closedExpiredAuction: None Expired ──");
        AuctionService service = new AuctionService();
        Auction a1 = service.createAuction("s1", "Item1", 100, 5, 60000);
        a1.status = AuctionStatus.OPEN;
        Auction a2 = service.createAuction("s2", "Item2", 200, 10, 60000);
        a2.status = AuctionStatus.OPEN;

        int closed = service.closedExpiredAuction();
        check("0 closed when none expired", closed == 0);
        check("a1 still OPEN", a1.status == AuctionStatus.OPEN);
        check("a2 still OPEN", a2.status == AuctionStatus.OPEN);
    }

    static void testClosedExpiredAuctionSomeExpired() throws Exception {
        System.out.println("\n── closedExpiredAuction: Some Expired ──");
        AuctionService service = new AuctionService();
        Auction a1 = service.createAuction("s1", "Item1", 100, 5, 1);  // expires immediately
        a1.status = AuctionStatus.OPEN;
        Auction a2 = service.createAuction("s2", "Item2", 200, 10, 60000); // long duration
        a2.status = AuctionStatus.OPEN;
        Auction a3 = service.createAuction("s3", "Item3", 300, 15, 1);  // expires immediately
        a3.status = AuctionStatus.OPEN;

        Thread.sleep(10);

        int closed = service.closedExpiredAuction();
        check("2 expired auctions closed", closed == 2);
        check("a1 is CLOSED", a1.status == AuctionStatus.CLOSED);
        check("a2 still OPEN", a2.status == AuctionStatus.OPEN);
        check("a3 is CLOSED", a3.status == AuctionStatus.CLOSED);
    }

    static void testClosedExpiredAuctionAlreadyClosed() throws Exception {
        System.out.println("\n── closedExpiredAuction: Already Closed Ignored ──");
        AuctionService service = new AuctionService();
        Auction a = service.createAuction("s1", "Item", 100, 5, 1);
        a.status = AuctionStatus.CLOSED; // already closed

        Thread.sleep(10);

        int closed = service.closedExpiredAuction();
        check("0 closed (already was CLOSED)", closed == 0);
    }

    // ════════════════════════════════════════════════════════════════
    //              AuctionService: getBidHistory
    // ════════════════════════════════════════════════════════════════
    static void testGetBidHistoryEmpty() {
        System.out.println("\n── getBidHistory: Empty ──");
        AuctionService service = new AuctionService();
        Auction a = service.createAuction("seller", "Item", 100.0, 10.0, 60000);
        try {
            List<Bid> history = service.getBidHistory(a.auctionId);
            check("empty history returns empty list", history != null && history.isEmpty());
        } catch (Exception e) { check("getBidHistory empty: " + e.getMessage(), false); }
    }

    static void testGetBidHistoryWithBids() {
        System.out.println("\n── getBidHistory: With Bids ──");
        AuctionService service = new AuctionService();
        Auction a = service.createAuction("seller", "Item", 50.0, 5.0, 60000);
        a.status = AuctionStatus.OPEN;
        try {
            service.placeBid(a.auctionId, "b1", 50.0);
            service.placeBid(a.auctionId, "b2", 60.0);
            service.placeBid(a.auctionId, "b3", 70.0);

            List<Bid> history = service.getBidHistory(a.auctionId);
            check("history has 3 bids", history.size() == 3);
            check("1st bid amount 50", history.get(0).amount == 50.0);
            check("2nd bid amount 60", history.get(1).amount == 60.0);
            check("3rd bid amount 70", history.get(2).amount == 70.0);
            check("1st bidder is b1", "b1".equals(history.get(0).bidderId));
            check("2nd bidder is b2", "b2".equals(history.get(1).bidderId));
            check("3rd bidder is b3", "b3".equals(history.get(2).bidderId));
        } catch (Exception e) { check("getBidHistory with bids: " + e.getMessage(), false); }
    }

    static void testGetBidHistoryNotFound() {
        System.out.println("\n── getBidHistory: Not Found ──");
        AuctionService service = new AuctionService();
        try {
            service.getBidHistory("AU-NONEXIST");
            check("should throw AuctionNotFoundException", false);
        } catch (AuctionNotFoundException e) {
            check("throws AuctionNotFoundException", true);
        }
    }

    static void testGetBidHistoryReturnsCopy() {
        System.out.println("\n── getBidHistory: Returns Copy ──");
        AuctionService service = new AuctionService();
        Auction a = service.createAuction("seller", "Item", 50.0, 5.0, 60000);
        a.status = AuctionStatus.OPEN;
        try {
            service.placeBid(a.auctionId, "b1", 50.0);
            List<Bid> history1 = service.getBidHistory(a.auctionId);
            List<Bid> history2 = service.getBidHistory(a.auctionId);
            check("returns different list instances (defensive copy)", history1 != history2);
            check("same content", history1.size() == history2.size());

            // modifying returned list doesn't affect internal state
            history1.clear();
            check("clearing copy doesn't affect auction bids", a.bids.size() == 1);
        } catch (Exception e) { check("getBidHistory copy: " + e.getMessage(), false); }
    }

    // ════════════════════════════════════════════════════════════════
    //              End-to-End Scenarios
    // ════════════════════════════════════════════════════════════════
    static void testEndToEndFullAuctionFlow() {
        System.out.println("\n── E2E: Full Auction Flow ──");
        AuctionService service = new AuctionService();
        try {
            // Seller creates auction
            Auction a = service.createAuction("seller1", "Rare Coin", 100.0, 10.0, 60000);
            a.status = AuctionStatus.OPEN;
            check("E2E: auction created", a != null);
            check("E2E: auction is OPEN", a.status == AuctionStatus.OPEN);

            // First bid at reserve price
            Bid b1 = service.placeBid(a.auctionId, "alice", 100.0);
            check("E2E: alice bids 100", b1.amount == 100.0);

            // Second bid with proper increment
            Bid b2 = service.placeBid(a.auctionId, "bob", 115.0);
            check("E2E: bob bids 115", b2.amount == 115.0);

            // Alice outbids
            Bid b3 = service.placeBid(a.auctionId, "alice", 130.0);
            check("E2E: alice outbids with 130", b3.amount == 130.0);

            // Check highest bid
            check("E2E: highest bid is alice's 130", a.highestBid.amount == 130.0);
            check("E2E: highest bidder is alice", "alice".equals(a.highestBid.bidderId));

            // Check bid history
            List<Bid> history = service.getBidHistory(a.auctionId);
            check("E2E: 3 bids in history", history.size() == 3);

            // Close auction
            service.closeAuction(a.auctionId);
            check("E2E: auction closed", a.status == AuctionStatus.CLOSED);

            // Winner is alice with 130
            check("E2E: winner is alice", "alice".equals(a.highestBid.bidderId));
            check("E2E: winning bid 130", a.highestBid.amount == 130.0);

            // Can't bid after close
            try {
                service.placeBid(a.auctionId, "charlie", 200.0);
                check("E2E: bid after close should throw", false);
            } catch (AuctionNotFoundException e) { check("E2E: bid after close throws", true); }

        } catch (Exception e) { check("E2E full flow: " + e.getMessage(), false); }
    }

    static void testEndToEndNoBidsAuction() {
        System.out.println("\n── E2E: Auction with No Bids ──");
        AuctionService service = new AuctionService();
        try {
            Auction a = service.createAuction("seller", "Unsold Item", 1000.0, 50.0, 60000);
            a.status = AuctionStatus.OPEN;

            check("E2E-nb: no bids initially", a.bids.isEmpty());
            check("E2E-nb: no highest bid", a.highestBid == null);

            service.closeAuction(a.auctionId);
            check("E2E-nb: auction closed", a.status == AuctionStatus.CLOSED);
            check("E2E-nb: still no bids", a.bids.isEmpty());
            check("E2E-nb: still no highest bid (no winner)", a.highestBid == null);

            List<Bid> history = service.getBidHistory(a.auctionId);
            check("E2E-nb: empty history", history.isEmpty());
        } catch (Exception e) { check("E2E no bids: " + e.getMessage(), false); }
    }

    static void testEndToEndMultipleAuctions() {
        System.out.println("\n── E2E: Multiple Concurrent Auctions ──");
        AuctionService service = new AuctionService();
        try {
            Auction a1 = service.createAuction("seller1", "Item A", 50.0, 5.0, 60000);
            a1.status = AuctionStatus.OPEN;
            Auction a2 = service.createAuction("seller2", "Item B", 100.0, 10.0, 60000);
            a2.status = AuctionStatus.OPEN;

            // Bid on both auctions
            service.placeBid(a1.auctionId, "alice", 50.0);
            service.placeBid(a2.auctionId, "bob", 100.0);
            service.placeBid(a1.auctionId, "bob", 60.0);
            service.placeBid(a2.auctionId, "alice", 120.0);

            check("E2E-multi: a1 has 2 bids", a1.bids.size() == 2);
            check("E2E-multi: a2 has 2 bids", a2.bids.size() == 2);
            check("E2E-multi: a1 highest is bob@60", a1.highestBid.amount == 60.0 && "bob".equals(a1.highestBid.bidderId));
            check("E2E-multi: a2 highest is alice@120", a2.highestBid.amount == 120.0 && "alice".equals(a2.highestBid.bidderId));

            // Close one, other remains open
            service.closeAuction(a1.auctionId);
            check("E2E-multi: a1 closed", a1.status == AuctionStatus.CLOSED);
            check("E2E-multi: a2 still open", a2.status == AuctionStatus.OPEN);

            // Can still bid on a2
            service.placeBid(a2.auctionId, "charlie", 150.0);
            check("E2E-multi: a2 now has 3 bids", a2.bids.size() == 3);
        } catch (Exception e) { check("E2E multiple auctions: " + e.getMessage(), false); }
    }

    static void testEndToEndBidWarScenario() {
        System.out.println("\n── E2E: Bid War Scenario ──");
        AuctionService service = new AuctionService();
        try {
            Auction a = service.createAuction("auctioneer", "Diamond Ring", 500.0, 25.0, 60000);
            a.status = AuctionStatus.OPEN;

            service.placeBid(a.auctionId, "alice", 500.0);
            service.placeBid(a.auctionId, "bob", 550.0);
            service.placeBid(a.auctionId, "charlie", 600.0);
            service.placeBid(a.auctionId, "alice", 700.0);
            service.placeBid(a.auctionId, "bob", 800.0);
            service.placeBid(a.auctionId, "charlie", 900.0);
            service.placeBid(a.auctionId, "alice", 1000.0);

            check("E2E-war: 7 total bids", a.bids.size() == 7);
            check("E2E-war: highest bid is 1000", a.highestBid.amount == 1000.0);
            check("E2E-war: winner is alice", "alice".equals(a.highestBid.bidderId));

            // bob tries insufficient increment
            try {
                service.placeBid(a.auctionId, "bob", 1010.0); // min is 1025
                check("E2E-war: insufficient increment should fail", false);
            } catch (InvalidBidException e) { check("E2E-war: insufficient increment rejected", true); }

            // bob bids correctly
            service.placeBid(a.auctionId, "bob", 1025.0);
            check("E2E-war: bob takes lead at 1025", a.highestBid.amount == 1025.0);
            check("E2E-war: new leader is bob", "bob".equals(a.highestBid.bidderId));
            check("E2E-war: 8 total bids now", a.bids.size() == 8);

            List<Bid> history = service.getBidHistory(a.auctionId);
            check("E2E-war: history matches bid count", history.size() == 8);
            check("E2E-war: history first bid is 500", history.get(0).amount == 500.0);
            check("E2E-war: history last bid is 1025", history.get(7).amount == 1025.0);

        } catch (Exception e) { check("E2E bid war: " + e.getMessage(), false); }
    }
}
