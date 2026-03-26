import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class RateLimitExceededException extends Exception{
    RateLimitExceededException(String message){
        super(message);
    }
}
class InvalidConfigurationException extends Exception{
    InvalidConfigurationException(String message){
        super(message);
    }
}
enum RateLimiterType{
    TOKEN_BUCKET,
    FIXED_WINDOW,
    SLIDING_WINDOW
}
enum RequestResult{
    ALLOWED,
    DENIED
}
interface RateLimiter{
    boolean allowRequest(String clientId);
    RateLimiterType getName();
}
class TokenBucketRateLimiter implements RateLimiter{
    int capacity;
    double refillRate;
    ConcurrentHashMap<String,double[]> buckets;
    TokenBucketRateLimiter(int capacity,double refillRate){
        this.capacity=capacity;
        this.refillRate=refillRate;
        buckets=new ConcurrentHashMap<>();
    }
    @Override
    public boolean allowRequest(String clientId) {
        long now=System.currentTimeMillis();
        double[] bucket=buckets.computeIfAbsent(clientId,k->new double[]{capacity,now});
        double elapsed=now-bucket[1];
        bucket[0]=Math.min(capacity,bucket[0]+(now-elapsed)*refillRate);
        if(bucket[0]>1) {bucket[0]-=1; return true;}
        return false;
    }
    @Override
    public RateLimiterType getName() {
        return RateLimiterType.TOKEN_BUCKET;
    }
}
class FixedWindowRateLimiter implements RateLimiter{
    int capacity;
    ConcurrentHashMap<String,long[]> windows;
    long windowMills;
    FixedWindowRateLimiter(int capacity,long windowMills ){
        this.windowMills=windowMills;
        this.capacity=capacity;
        windows=new ConcurrentHashMap<>();
    }
    @Override
    public boolean allowRequest(String clientId) {
        long now=System.currentTimeMillis();
        long[] window=windows.computeIfAbsent(clientId,k->new long[]{now,0});
        long elapsed=now-window[0];
        if(elapsed>windowMills) {window[0]=now; window[1]=0;}
        if(window[1]<capacity) {window[1]++; return true;}
        return false;
    }
    @Override
    public RateLimiterType getName() {
        return RateLimiterType.FIXED_WINDOW;
    }
}
class SlidingWindowRateLimiter implements RateLimiter{
    long windowMillis;
    ConcurrentHashMap<String, Deque<Long>> windows;
    long maxRequests;
    SlidingWindowRateLimiter(long windowMillis,long maxRequests){
        this.windowMillis=windowMillis;
        this.maxRequests=maxRequests;
        windows=new ConcurrentHashMap<>();
    }
    @Override
    public boolean allowRequest(String clientId) {
        long now=System.currentTimeMillis();
        Deque<Long> window=windows.computeIfAbsent(clientId, k->new ArrayDeque<>());
        while (!window.isEmpty() && now-window.peek()>=windowMillis) window.pollFirst();
        if(window.size()<maxRequests){
            window.addLast(now);
            return true;
        }
        return false;
    }

    @Override
    public RateLimiterType getName() {
        return RateLimiterType.SLIDING_WINDOW;
    }
}
class RateLimiterSystem{
    Map<String,RateLimiter> endpointLimiters;
    RateLimiter defaultLimiter;
    RateLimiterSystem(RateLimiter rateLimiter){
        this.defaultLimiter=rateLimiter;
        endpointLimiters=new ConcurrentHashMap<>();
    }
    void registerEndpoint(String endpoint,RateLimiter limiter){
        endpointLimiters.put(endpoint,limiter);
    }
    RequestResult processRequest(String endpoint,String client){
        RateLimiter rateLimiter=endpointLimiters.getOrDefault(endpoint,defaultLimiter);
        return rateLimiter.allowRequest(client)?RequestResult.ALLOWED:RequestResult.DENIED;
    }
}
public class RateLimiterTest {

    static int passed = 0;
    static int failed = 0;

    static void check(String testName, boolean condition) {
        if (condition) {
            System.out.println("  ✅ PASS: " + testName);
            passed++;
        } else {
            System.out.println("  ❌ FAIL: " + testName);
            failed++;
        }
    }

    public static void main(String[] args) throws Exception {

        System.out.println("========================================");
        System.out.println(" RateLimiterSystem  —  Test Suite");
        System.out.println("========================================\n");

        // ── 1. Token Bucket Rate Limiter ──
        System.out.println("── Token Bucket Rate Limiter ──");
        {
            TokenBucketRateLimiter tb = new TokenBucketRateLimiter(3, 1.0);
            check("getName() == TOKEN_BUCKET", tb.getName() == RateLimiterType.TOKEN_BUCKET);
            boolean r1 = tb.allowRequest("userA");
            boolean r2 = tb.allowRequest("userA");
            boolean r3 = tb.allowRequest("userA");
            check("Allow 1st request for userA", r1);
            check("Allow 2nd request for userA", r2);
            check("Allow 3rd request for userA", r3);
            boolean other = tb.allowRequest("userB");
            check("Allow 1st request for different client userB", other);
        }
        System.out.println();

        // ── 2. Fixed Window Rate Limiter ──
        System.out.println("── Fixed Window Rate Limiter ──");
        {
            FixedWindowRateLimiter fw = new FixedWindowRateLimiter(5, 1000);
            check("getName() == FIXED_WINDOW", fw.getName() == RateLimiterType.FIXED_WINDOW);
            boolean allAllowed = true;
            for (int i = 0; i < 5; i++) {
                if (!fw.allowRequest("clientX")) allAllowed = false;
            }
            check("Allow first 5 requests within window", allAllowed);
            check("Deny 6th request within same window", !fw.allowRequest("clientX"));
            check("Allow request from different client clientY", fw.allowRequest("clientY"));
            System.out.println("  ⏳ Waiting 1.1s for window to expire...");
            Thread.sleep(1100);
            check("Allow request after window expires for clientX", fw.allowRequest("clientX"));
        }
        System.out.println();

        // ── 3. Sliding Window Rate Limiter ──
        System.out.println("── Sliding Window Rate Limiter ──");
        {
            SlidingWindowRateLimiter sw = new SlidingWindowRateLimiter(1000, 3);
            check("getName() == SLIDING_WINDOW", sw.getName() == RateLimiterType.SLIDING_WINDOW);
            check("Allow 1st request", sw.allowRequest("user1"));
            check("Allow 2nd request", sw.allowRequest("user1"));
            check("Allow 3rd request", sw.allowRequest("user1"));
            check("Deny 4th request (over limit)", !sw.allowRequest("user1"));
            System.out.println("  ⏳ Waiting 1.1s for sliding window to expire...");
            Thread.sleep(1100);
            check("Allow request after sliding window expires", sw.allowRequest("user1"));
        }
        System.out.println();

        // ── 4. RateLimiterSystem — default limiter ──
        System.out.println("── RateLimiterSystem — Default Limiter ──");
        {
            RateLimiter defaultLimiter = new SlidingWindowRateLimiter(1000, 2);
            RateLimiterSystem system = new RateLimiterSystem(defaultLimiter);
            check("1st request ALLOWED via default limiter",
                    system.processRequest("/api/data", "alice") == RequestResult.ALLOWED);
            check("2nd request ALLOWED via default limiter",
                    system.processRequest("/api/data", "alice") == RequestResult.ALLOWED);
            check("3rd request DENIED via default limiter",
                    system.processRequest("/api/data", "alice") == RequestResult.DENIED);
        }
        System.out.println();

        // ── 5. RateLimiterSystem — endpoint-specific limiter ──
        System.out.println("── RateLimiterSystem — Endpoint-Specific Limiter ──");
        {
            RateLimiterSystem system = new RateLimiterSystem(new SlidingWindowRateLimiter(1000, 10));
            system.registerEndpoint("/api/login", new FixedWindowRateLimiter(1, 1000));
            check("/api/login 1st request ALLOWED",
                    system.processRequest("/api/login", "bob") == RequestResult.ALLOWED);
            check("/api/login 2nd request DENIED (strict limit)",
                    system.processRequest("/api/login", "bob") == RequestResult.DENIED);
            check("/api/items 1st request ALLOWED (default)",
                    system.processRequest("/api/items", "bob") == RequestResult.ALLOWED);
            check("/api/items 2nd request ALLOWED (default)",
                    system.processRequest("/api/items", "bob") == RequestResult.ALLOWED);
        }
        System.out.println();

        // ── 6. RateLimiterSystem — multiple clients ──
        System.out.println("── RateLimiterSystem — Multiple Clients ──");
        {
            RateLimiterSystem system = new RateLimiterSystem(new FixedWindowRateLimiter(2, 2000));
            system.processRequest("/api/resource", "clientA");
            system.processRequest("/api/resource", "clientA");
            check("clientA 3rd request DENIED",
                    system.processRequest("/api/resource", "clientA") == RequestResult.DENIED);
            check("clientB 1st request ALLOWED (independent quota)",
                    system.processRequest("/api/resource", "clientB") == RequestResult.ALLOWED);
        }
        System.out.println();

        // ── Summary ──
        System.out.println("========================================");
        System.out.printf(" Results:  %d passed,  %d failed%n", passed, failed);
        System.out.println("========================================");
        if (failed > 0) System.exit(1);
    }
}
