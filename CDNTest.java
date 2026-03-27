import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

class ContentNotFoundException extends Exception{
    ContentNotFoundException(String message){
        super(message);
    }
}
enum CacheResult {MISS,HIT,EXPIRED}
class CacheContent{
    String key;
    byte[] data;
    long createdAt;
    long ttlMs;
    long lastAccessedAt;
    CacheContent(String key,byte[] data,long ttlMs){
        this.key=key;
        this.data=data;
        this.ttlMs=ttlMs;
        this.createdAt=System.currentTimeMillis();
    }
    boolean isExpired(){ return System.currentTimeMillis()-createdAt > ttlMs;}
    void touch(){lastAccessedAt=System.currentTimeMillis();}
    long size() {return data.length;}
}
class EdgeServer{
    String id;
    String region;
    AtomicLong hits=new AtomicLong(0);
    AtomicLong misses=new AtomicLong(0);
    boolean healthy=true;
    long maxBytes;
    long usedBytes;
    LinkedHashMap<String,CacheContent> cache;
    EdgeServer(String id,String region,long maxBytes){
        this.id=id;
        this.region=region;
        this.maxBytes=maxBytes;
        this.usedBytes=0;
        cache=new LinkedHashMap<>(16,0.75f,true);
    }
    synchronized CacheContent get(String key){
        CacheContent c=cache.get(key);
        if(c==null) {misses.incrementAndGet(); return null;}
        if(c.isExpired()) {evict(key); misses.incrementAndGet(); return null;}
        c.touch();
        hits.incrementAndGet();
        return c;
    }
    synchronized void put(String key,CacheContent content){
        if(cache.containsKey(key)) evict(key);
        while(usedBytes+content.size()>maxBytes && !cache.isEmpty()) evict(cache.keySet().iterator().next());
        if(content.size()<=maxBytes) {cache.put(key,content); usedBytes+=content.size();}
    }
    synchronized void evict(String key){
        CacheContent removed=cache.remove(key);
        if(removed!=null) usedBytes-=removed.size();
    }
    synchronized void invalidate(String key){
        evict(key);
    }
    synchronized int evictExpired(){
        List<String> expired=new ArrayList<>();
        for(CacheContent c:cache.values()) {if(c.isExpired()) expired.add(c.key);}
        for(String k:expired) evict(k);
        return expired.size();
    }
    double hitRate(){
        long total=hits.get()+misses.get();
        return total==0?0:(double) hits.get()/total*100.0;
    }
}
class OriginServer{
    ConcurrentHashMap<String,byte[]> store=new ConcurrentHashMap<>();
    AtomicLong fetchCount=new AtomicLong(0);
    void putContent(String key,String content) {store.put(key,content.getBytes());}
    byte[] fetchByte(String key) {
        byte[] data=store.get(key);
        if(data!=null) fetchCount.incrementAndGet();
        return data;
    }
    boolean has(String key) {return store.containsKey(key);}
}
class ConsistentHashRing{
    int VIRTUAL_NODES=50;
    TreeMap<Integer,String> ring=new TreeMap<>();
    Map<String,EdgeServer> servers=new HashMap<>();
    void addServer(EdgeServer server){
        servers.put(server.id,server);
        for(int i=0;i<VIRTUAL_NODES;i++){
            ring.put(hash((server.id+"#"+i)),server.id);
        }
    }
    void removeServer(String serverId){
        servers.remove(serverId);
        for (int i = 0; i < VIRTUAL_NODES; i++) {
            ring.remove(hash((serverId+"#"+i)));
        }
    }
    EdgeServer route(String key){
        if(ring.isEmpty()) return null;
        int n=hash(key);
        Map.Entry<Integer, String> entry= ring.ceilingEntry(n);
        if(entry==null) entry=ring.firstEntry();
        String startId=entry.getValue();
        EdgeServer server=servers.get(startId);
        if(server!=null && server.healthy) return server;
        for(Map.Entry<Integer,String> e:ring.tailMap(entry.getKey(),false).entrySet()){
            EdgeServer s=servers.get(e.getValue());
            if(s!=null && s.healthy) return s;
        }
        for (Map.Entry<Integer, String> e : ring.entrySet()) {
            EdgeServer s = servers.get(e.getValue());
            if (s != null && s.healthy) return s;
        }
        return null;
    }
    int hash(String key) {
        int h = key.hashCode();
        return h == Integer.MIN_VALUE ? 0 : Math.abs(h);
    }
}

// ════════════════════════════════════════════════════════════════════
//                          CDN TEST SUITE
// ════════════════════════════════════════════════════════════════════
public class CDNTest {
    static int passed = 0, failed = 0;

    static boolean check(String name, boolean ok) {
        if (ok) { passed++; System.out.println("  ✅ PASS: " + name); }
        else    { failed++; System.out.println("  ❌ FAIL: " + name); }
        return ok;
    }

    // ── helpers ──
    static CacheContent makeContent(String key, String text, long ttlMs) {
        return new CacheContent(key, text.getBytes(), ttlMs);
    }

    static EdgeServer makeEdge(String id, String region, long maxBytes) {
        return new EdgeServer(id, region, maxBytes);
    }

    // ════════════════════════════════════════════════════════════════
    //                          MAIN
    // ════════════════════════════════════════════════════════════════
    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║         CDN Test Suite                   ║");
        System.out.println("╚══════════════════════════════════════════╝");

        // ── Exception ──
        testContentNotFoundException();

        // ── CacheResult enum ──
        testCacheResultEnum();

        // ── CacheContent ──
        testCacheContentConstruction();
        testCacheContentSize();
        testCacheContentNotExpired();
        testCacheContentExpired();
        testCacheContentTouch();

        // ── EdgeServer ──
        testEdgeServerConstruction();
        testEdgeServerPutAndGet();
        testEdgeServerCacheMiss();
        testEdgeServerHitMissCounters();
        testEdgeServerHitRate();
        testEdgeServerEvictByKey();
        testEdgeServerInvalidate();
        testEdgeServerEvictExpired();
        testEdgeServerLruEviction();
        testEdgeServerOversizedContent();
        testEdgeServerUpdateExistingKey();
        testEdgeServerUsedBytesTracking();
        testEdgeServerHealthFlag();

        // ── OriginServer ──
        testOriginServerPutAndFetch();
        testOriginServerFetchMissing();
        testOriginServerHasKey();
        testOriginServerFetchCount();
        testOriginServerMultipleEntries();
        testOriginServerOverwrite();

        // ── ConsistentHashRing ──
        testHashRingRouteEmpty();
        testHashRingAddAndRoute();
        testHashRingConsistency();
        testHashRingRemoveServer();
        testHashRingSkipsUnhealthy();
        testHashRingAllUnhealthy();
        testHashRingDistribution();
        testHashRingHashFunction();
        testHashRingVirtualNodes();

        // ── Integration / End-to-End ──
        testEndToEndCdnFlow();
        testEndToEndCacheInvalidation();
        testEndToEndFailover();

        System.out.println("\n══════════════════════════════════════════");
        System.out.printf("  Results: %d passed, %d failed, %d total%n", passed, failed, passed + failed);
        System.out.println("══════════════════════════════════════════");
        System.exit(failed > 0 ? 1 : 0);
    }

    // ════════════════════════════════════════════════════════════════
    //              ContentNotFoundException
    // ════════════════════════════════════════════════════════════════
    static void testContentNotFoundException() {
        System.out.println("\n── ContentNotFoundException ──");
        ContentNotFoundException ex = new ContentNotFoundException("not found");
        check("message is set", "not found".equals(ex.getMessage()));
        check("is an Exception", ex instanceof Exception);

        ContentNotFoundException ex2 = new ContentNotFoundException("");
        check("empty message", "".equals(ex2.getMessage()));

        ContentNotFoundException ex3 = new ContentNotFoundException(null);
        check("null message", ex3.getMessage() == null);
    }

    // ════════════════════════════════════════════════════════════════
    //              CacheResult Enum
    // ════════════════════════════════════════════════════════════════
    static void testCacheResultEnum() {
        System.out.println("\n── CacheResult Enum ──");
        check("MISS exists",    CacheResult.valueOf("MISS")    == CacheResult.MISS);
        check("HIT exists",     CacheResult.valueOf("HIT")     == CacheResult.HIT);
        check("EXPIRED exists", CacheResult.valueOf("EXPIRED") == CacheResult.EXPIRED);
        check("3 values",       CacheResult.values().length == 3);
    }

    // ════════════════════════════════════════════════════════════════
    //              CacheContent
    // ════════════════════════════════════════════════════════════════
    static void testCacheContentConstruction() {
        System.out.println("\n── CacheContent Construction ──");
        CacheContent c = makeContent("img.png", "imagedata", 5000);
        check("key is set",        "img.png".equals(c.key));
        check("data is set",       c.data != null && c.data.length > 0);
        check("data content ok",   "imagedata".equals(new String(c.data)));
        check("ttlMs is set",      c.ttlMs == 5000);
        check("createdAt is set",  c.createdAt > 0);
        check("createdAt recent",  System.currentTimeMillis() - c.createdAt < 1000);
    }

    static void testCacheContentSize() {
        System.out.println("\n── CacheContent Size ──");
        CacheContent c1 = makeContent("k", "hello", 1000);
        check("size matches data length", c1.size() == 5);

        CacheContent c2 = makeContent("k", "", 1000);
        check("empty data size is 0", c2.size() == 0);

        CacheContent c3 = makeContent("k", "a".repeat(1024), 1000);
        check("1KB data size", c3.size() == 1024);
    }

    static void testCacheContentNotExpired() {
        System.out.println("\n── CacheContent Not Expired ──");
        CacheContent c = makeContent("k", "data", 60_000); // 60s TTL
        check("not expired immediately", !c.isExpired());
    }

    static void testCacheContentExpired() throws Exception {
        System.out.println("\n── CacheContent Expired ──");
        CacheContent c = makeContent("k", "data", 50); // 50ms TTL
        check("not expired immediately", !c.isExpired());
        Thread.sleep(80);
        check("expired after TTL", c.isExpired());
    }

    static void testCacheContentTouch() throws Exception {
        System.out.println("\n── CacheContent Touch ──");
        CacheContent c = makeContent("k", "data", 60_000);
        long before = c.lastAccessedAt;
        Thread.sleep(10);
        c.touch();
        check("touch updates lastAccessedAt", c.lastAccessedAt > before);
        check("lastAccessedAt is recent", System.currentTimeMillis() - c.lastAccessedAt < 1000);
    }

    // ════════════════════════════════════════════════════════════════
    //              EdgeServer
    // ════════════════════════════════════════════════════════════════
    static void testEdgeServerConstruction() {
        System.out.println("\n── EdgeServer Construction ──");
        EdgeServer e = makeEdge("edge-1", "us-east", 10_000);
        check("id is set",           "edge-1".equals(e.id));
        check("region is set",       "us-east".equals(e.region));
        check("maxBytes is set",     e.maxBytes == 10_000);
        check("usedBytes is 0",      e.usedBytes == 0);
        check("healthy by default",  e.healthy);
        check("hits start at 0",     e.hits.get() == 0);
        check("misses start at 0",   e.misses.get() == 0);
        check("cache is empty",      e.cache.isEmpty());
    }

    static void testEdgeServerPutAndGet() {
        System.out.println("\n── EdgeServer Put & Get ──");
        EdgeServer e = makeEdge("e1", "eu", 10_000);
        CacheContent c = makeContent("logo.png", "pngdata", 60_000);

        e.put("logo.png", c);
        check("cache has entry", e.cache.containsKey("logo.png"));

        CacheContent fetched = e.get("logo.png");
        check("get returns content", fetched != null);
        check("get returns correct data", "pngdata".equals(new String(fetched.data)));
        check("get returns same object", fetched == c);
    }

    static void testEdgeServerCacheMiss() {
        System.out.println("\n── EdgeServer Cache Miss ──");
        EdgeServer e = makeEdge("e1", "eu", 10_000);
        CacheContent result = e.get("nonexistent");
        check("miss returns null", result == null);
        check("miss counter incremented", e.misses.get() == 1);
        check("hit counter unchanged", e.hits.get() == 0);
    }

    static void testEdgeServerHitMissCounters() {
        System.out.println("\n── EdgeServer Hit/Miss Counters ──");
        EdgeServer e = makeEdge("e1", "eu", 10_000);
        e.put("a", makeContent("a", "data", 60_000));

        e.get("a");      // hit
        e.get("a");      // hit
        e.get("b");      // miss
        e.get("c");      // miss
        e.get("a");      // hit

        check("hits = 3",  e.hits.get() == 3);
        check("misses = 2", e.misses.get() == 2);
    }

    static void testEdgeServerHitRate() {
        System.out.println("\n── EdgeServer Hit Rate ──");
        EdgeServer e = makeEdge("e1", "eu", 10_000);

        // no requests = 0%
        check("hit rate 0% when no requests", e.hitRate() == 0.0);

        e.put("a", makeContent("a", "data", 60_000));
        e.get("a"); // hit
        e.get("b"); // miss
        check("hit rate is 50%", Math.abs(e.hitRate() - 50.0) < 0.01);

        e.get("a"); // hit
        // 2 hits, 1 miss = 66.67%
        check("hit rate ~66.67%", Math.abs(e.hitRate() - 66.67) < 0.1);
    }

    static void testEdgeServerEvictByKey() {
        System.out.println("\n── EdgeServer Evict By Key ──");
        EdgeServer e = makeEdge("e1", "eu", 10_000);
        CacheContent c = makeContent("k", "data", 60_000);
        e.put("k", c);

        check("key exists before evict", e.cache.containsKey("k"));
        long bytesBefore = e.usedBytes;

        e.evict("k");
        check("key removed after evict",    !e.cache.containsKey("k"));
        check("usedBytes decreased",        e.usedBytes == bytesBefore - c.size());

        // evict non-existent key (no-op)
        e.evict("nonexistent");
        check("evict non-existent is no-op", e.usedBytes == 0);
    }

    static void testEdgeServerInvalidate() {
        System.out.println("\n── EdgeServer Invalidate ──");
        EdgeServer e = makeEdge("e1", "eu", 10_000);
        e.put("css", makeContent("css", "body{}", 60_000));

        check("key exists before invalidate", e.cache.containsKey("css"));
        e.invalidate("css");
        check("key removed after invalidate", !e.cache.containsKey("css"));
        check("usedBytes back to 0",          e.usedBytes == 0);
    }

    static void testEdgeServerEvictExpired() throws Exception {
        System.out.println("\n── EdgeServer Evict Expired ──");
        EdgeServer e = makeEdge("e1", "eu", 10_000);
        e.put("short",  makeContent("short",  "data1", 50));      // 50ms TTL
        e.put("long",   makeContent("long",   "data2", 60_000));  // 60s TTL
        e.put("short2", makeContent("short2", "data3", 50));      // 50ms TTL

        check("3 items in cache", e.cache.size() == 3);

        Thread.sleep(80); // let short-TTL items expire

        int evicted = e.evictExpired();
        check("evicted 2 expired items", evicted == 2);
        check("long-TTL item survives",  e.cache.containsKey("long"));
        check("short removed",           !e.cache.containsKey("short"));
        check("short2 removed",          !e.cache.containsKey("short2"));
        check("cache size is 1",         e.cache.size() == 1);
    }

    static void testEdgeServerLruEviction() {
        System.out.println("\n── EdgeServer LRU Eviction ──");
        // maxBytes = 20, each item ~5 bytes
        EdgeServer e = makeEdge("e1", "eu", 20);
        e.put("a", makeContent("a", "aaaaa", 60_000));  // 5 bytes, total = 5
        e.put("b", makeContent("b", "bbbbb", 60_000));  // 5 bytes, total = 10
        e.put("c", makeContent("c", "ccccc", 60_000));  // 5 bytes, total = 15
        e.put("d", makeContent("d", "ddddd", 60_000));  // 5 bytes, total = 20

        check("4 items fit in 20 bytes", e.cache.size() == 4);
        check("usedBytes = 20", e.usedBytes == 20);

        // adding 5 more bytes should evict the LRU entry (a)
        e.put("e", makeContent("e", "eeeee", 60_000));
        check("a evicted (LRU)",    !e.cache.containsKey("a"));
        check("e added",             e.cache.containsKey("e"));
        check("usedBytes still 20",  e.usedBytes == 20);
        check("cache size still 4",  e.cache.size() == 4);
    }

    static void testEdgeServerOversizedContent() {
        System.out.println("\n── EdgeServer Oversized Content ──");
        EdgeServer e = makeEdge("e1", "eu", 10); // 10 bytes max
        CacheContent big = makeContent("big", "a".repeat(20), 60_000); // 20 bytes

        e.put("big", big);
        check("oversized content not stored", !e.cache.containsKey("big"));
        check("usedBytes still 0",            e.usedBytes == 0);
    }

    static void testEdgeServerUpdateExistingKey() {
        System.out.println("\n── EdgeServer Update Existing Key ──");
        EdgeServer e = makeEdge("e1", "eu", 10_000);
        e.put("k", makeContent("k", "old", 60_000));  // 3 bytes
        long usedAfterFirst = e.usedBytes;

        e.put("k", makeContent("k", "newvalue", 60_000));  // 8 bytes
        check("key still exists",       e.cache.containsKey("k"));
        check("value updated",          "newvalue".equals(new String(e.get("k").data)));
        check("usedBytes recalculated", e.usedBytes == 8);
    }

    static void testEdgeServerUsedBytesTracking() {
        System.out.println("\n── EdgeServer UsedBytes Tracking ──");
        EdgeServer e = makeEdge("e1", "eu", 10_000);
        check("starts at 0", e.usedBytes == 0);

        e.put("a", makeContent("a", "12345", 60_000)); // 5 bytes
        check("5 after first put", e.usedBytes == 5);

        e.put("b", makeContent("b", "123", 60_000));   // 3 bytes
        check("8 after second put", e.usedBytes == 8);

        e.evict("a");
        check("3 after evicting a", e.usedBytes == 3);

        e.evict("b");
        check("0 after evicting b", e.usedBytes == 0);
    }

    static void testEdgeServerHealthFlag() {
        System.out.println("\n── EdgeServer Health Flag ──");
        EdgeServer e = makeEdge("e1", "eu", 10_000);
        check("healthy by default", e.healthy);

        e.healthy = false;
        check("can be set unhealthy", !e.healthy);

        e.healthy = true;
        check("can be restored", e.healthy);
    }

    // ════════════════════════════════════════════════════════════════
    //              OriginServer
    // ════════════════════════════════════════════════════════════════
    static void testOriginServerPutAndFetch() {
        System.out.println("\n── OriginServer Put & Fetch ──");
        OriginServer o = new OriginServer();
        o.putContent("page.html", "<html>hello</html>");

        byte[] data = o.fetchByte("page.html");
        check("fetch returns data",   data != null);
        check("fetch returns correct", "<html>hello</html>".equals(new String(data)));
    }

    static void testOriginServerFetchMissing() {
        System.out.println("\n── OriginServer Fetch Missing ──");
        OriginServer o = new OriginServer();
        byte[] data = o.fetchByte("nonexistent");
        check("fetch missing returns null", data == null);
    }

    static void testOriginServerHasKey() {
        System.out.println("\n── OriginServer Has Key ──");
        OriginServer o = new OriginServer();
        check("has returns false for missing", !o.has("x"));

        o.putContent("x", "data");
        check("has returns true after put", o.has("x"));
        check("has returns false for other", !o.has("y"));
    }

    static void testOriginServerFetchCount() {
        System.out.println("\n── OriginServer Fetch Count ──");
        OriginServer o = new OriginServer();
        check("fetch count starts at 0", o.fetchCount.get() == 0);

        o.putContent("a", "data");
        o.fetchByte("a");
        check("fetch count is 1 after hit", o.fetchCount.get() == 1);

        o.fetchByte("missing"); // miss, should not increment
        check("fetch count stays 1 after miss", o.fetchCount.get() == 1);

        o.fetchByte("a");
        o.fetchByte("a");
        check("fetch count is 3 after more hits", o.fetchCount.get() == 3);
    }

    static void testOriginServerMultipleEntries() {
        System.out.println("\n── OriginServer Multiple Entries ──");
        OriginServer o = new OriginServer();
        o.putContent("a", "alpha");
        o.putContent("b", "beta");
        o.putContent("c", "gamma");

        check("a correct", "alpha".equals(new String(o.fetchByte("a"))));
        check("b correct", "beta".equals(new String(o.fetchByte("b"))));
        check("c correct", "gamma".equals(new String(o.fetchByte("c"))));
        check("has a", o.has("a"));
        check("has b", o.has("b"));
        check("has c", o.has("c"));
    }

    static void testOriginServerOverwrite() {
        System.out.println("\n── OriginServer Overwrite ──");
        OriginServer o = new OriginServer();
        o.putContent("k", "version1");
        check("initial value", "version1".equals(new String(o.fetchByte("k"))));

        o.putContent("k", "version2");
        check("overwritten value", "version2".equals(new String(o.fetchByte("k"))));
    }

    // ════════════════════════════════════════════════════════════════
    //              ConsistentHashRing
    // ════════════════════════════════════════════════════════════════
    static void testHashRingRouteEmpty() {
        System.out.println("\n── HashRing Route Empty ──");
        ConsistentHashRing ring = new ConsistentHashRing();
        check("route on empty ring returns null", ring.route("any-key") == null);
    }

    static void testHashRingAddAndRoute() {
        System.out.println("\n── HashRing Add & Route ──");
        ConsistentHashRing ring = new ConsistentHashRing();
        EdgeServer e1 = makeEdge("edge-1", "us-east", 10_000);
        ring.addServer(e1);

        // with only 1 server, all keys route to it
        check("route returns edge-1 for key-a", ring.route("key-a") == e1);
        check("route returns edge-1 for key-b", ring.route("key-b") == e1);
        check("route returns edge-1 for key-c", ring.route("key-c") == e1);
        check("server stored in map", ring.servers.containsKey("edge-1"));
        check("ring has virtual nodes", ring.ring.size() == ring.VIRTUAL_NODES);
    }

    static void testHashRingConsistency() {
        System.out.println("\n── HashRing Consistency ──");
        ConsistentHashRing ring = new ConsistentHashRing();
        ring.addServer(makeEdge("e1", "us", 10_000));
        ring.addServer(makeEdge("e2", "eu", 10_000));
        ring.addServer(makeEdge("e3", "ap", 10_000));

        // same key always routes to the same server
        EdgeServer first  = ring.route("my-resource.js");
        EdgeServer second = ring.route("my-resource.js");
        EdgeServer third  = ring.route("my-resource.js");

        check("consistent routing (1st == 2nd)", first == second);
        check("consistent routing (2nd == 3rd)", second == third);
    }

    static void testHashRingRemoveServer() {
        System.out.println("\n── HashRing Remove Server ──");
        ConsistentHashRing ring = new ConsistentHashRing();
        EdgeServer e1 = makeEdge("e1", "us", 10_000);
        EdgeServer e2 = makeEdge("e2", "eu", 10_000);
        ring.addServer(e1);
        ring.addServer(e2);

        int ringBefore = ring.ring.size();
        ring.removeServer("e1");

        check("server removed from map", !ring.servers.containsKey("e1"));
        check("ring shrank", ring.ring.size() < ringBefore);
        check("e2 still in map", ring.servers.containsKey("e2"));

        // all keys now route to e2
        check("routes to e2 after removal", ring.route("any-key") == e2);
    }

    static void testHashRingSkipsUnhealthy() {
        System.out.println("\n── HashRing Skips Unhealthy ──");
        ConsistentHashRing ring = new ConsistentHashRing();
        EdgeServer e1 = makeEdge("e1", "us", 10_000);
        EdgeServer e2 = makeEdge("e2", "eu", 10_000);
        ring.addServer(e1);
        ring.addServer(e2);

        // mark e1 unhealthy
        e1.healthy = false;

        // all routes should go to e2
        boolean allToE2 = true;
        for (int i = 0; i < 100; i++) {
            EdgeServer routed = ring.route("key-" + i);
            if (routed != e2) { allToE2 = false; break; }
        }
        check("all 100 keys route to healthy e2", allToE2);
    }

    static void testHashRingAllUnhealthy() {
        System.out.println("\n── HashRing All Unhealthy ──");
        ConsistentHashRing ring = new ConsistentHashRing();
        EdgeServer e1 = makeEdge("e1", "us", 10_000);
        EdgeServer e2 = makeEdge("e2", "eu", 10_000);
        ring.addServer(e1);
        ring.addServer(e2);

        e1.healthy = false;
        e2.healthy = false;

        check("returns null when all unhealthy", ring.route("key") == null);
    }

    static void testHashRingDistribution() {
        System.out.println("\n── HashRing Distribution ──");
        ConsistentHashRing ring = new ConsistentHashRing();
        EdgeServer e1 = makeEdge("edge-server-us-east-1", "us", 10_000);
        EdgeServer e2 = makeEdge("edge-server-eu-west-2", "eu", 10_000);
        ring.addServer(e1);
        ring.addServer(e2);

        Map<String, Integer> counts = new HashMap<>();
        int total = 3000;
        for (int i = 0; i < total; i++) {
            EdgeServer routed = ring.route("resource-" + i);
            counts.merge(routed.id, 1, Integer::sum);
        }

        // with 2 servers and simple hashCode, verify both get some traffic
        check("both servers got traffic", counts.size() == 2);
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            int count = entry.getValue();
            check(entry.getKey() + " got " + count + " keys (>0)", count > 0);
        }

        // verify all keys were routed
        int sum = counts.values().stream().mapToInt(Integer::intValue).sum();
        check("all " + total + " keys routed", sum == total);

        // verify routing is deterministic (same key always goes to same server)
        boolean deterministic = true;
        for (int i = 0; i < 100; i++) {
            EdgeServer a = ring.route("check-" + i);
            EdgeServer b = ring.route("check-" + i);
            if (a != b) { deterministic = false; break; }
        }
        check("routing is deterministic", deterministic);
    }

    static void testHashRingHashFunction() {
        System.out.println("\n── HashRing Hash Function ──");
        ConsistentHashRing ring = new ConsistentHashRing();

        // deterministic
        check("hash is deterministic", ring.hash("test") == ring.hash("test"));

        // non-negative
        check("hash is non-negative", ring.hash("anything") >= 0);
        check("hash of empty string >= 0", ring.hash("") >= 0);

        // handles edge case Integer.MIN_VALUE
        // The function maps MIN_VALUE to 0
        int h = ring.hash("test");
        check("hash result >= 0", h >= 0);
    }

    static void testHashRingVirtualNodes() {
        System.out.println("\n── HashRing Virtual Nodes ──");
        ConsistentHashRing ring = new ConsistentHashRing();
        check("default virtual nodes = 50", ring.VIRTUAL_NODES == 50);

        EdgeServer e1 = makeEdge("e1", "us", 10_000);
        ring.addServer(e1);
        // some virtual nodes may collide on the same hash, but at most VIRTUAL_NODES entries
        check("ring size <= VIRTUAL_NODES", ring.ring.size() <= ring.VIRTUAL_NODES);
        check("ring size > 0", ring.ring.size() > 0);

        EdgeServer e2 = makeEdge("e2", "eu", 10_000);
        ring.addServer(e2);
        check("ring size grows with 2nd server", ring.ring.size() > ring.VIRTUAL_NODES / 2);
    }

    // ════════════════════════════════════════════════════════════════
    //              End-to-End Integration Tests
    // ════════════════════════════════════════════════════════════════
    static void testEndToEndCdnFlow() {
        System.out.println("\n── E2E: Full CDN Flow ──");
        // Setup: origin + hash ring + edge servers
        OriginServer origin = new OriginServer();
        origin.putContent("index.html", "<html>Home</html>");
        origin.putContent("style.css",  "body{margin:0}");
        origin.putContent("app.js",     "console.log('hi')");

        ConsistentHashRing ring = new ConsistentHashRing();
        EdgeServer e1 = makeEdge("edge-us", "us-east", 10_000);
        EdgeServer e2 = makeEdge("edge-eu", "eu-west", 10_000);
        ring.addServer(e1);
        ring.addServer(e2);

        // Simulate CDN request: route key -> check edge -> miss -> fetch origin -> cache
        String key = "index.html";
        EdgeServer target = ring.route(key);
        check("E2E: routed to an edge server", target != null);

        CacheContent cached = target.get(key);
        check("E2E: cache miss on first request", cached == null);

        // Fetch from origin and cache
        byte[] originData = origin.fetchByte(key);
        check("E2E: origin has content", originData != null);

        CacheContent content = new CacheContent(key, originData, 60_000);
        target.put(key, content);
        check("E2E: content cached in edge", target.cache.containsKey(key));

        // Second request is a cache hit
        CacheContent hit = target.get(key);
        check("E2E: cache hit on second request", hit != null);
        check("E2E: correct data from cache", "<html>Home</html>".equals(new String(hit.data)));
        check("E2E: origin fetch count is 1", origin.fetchCount.get() == 1);
    }

    static void testEndToEndCacheInvalidation() {
        System.out.println("\n── E2E: Cache Invalidation ──");
        OriginServer origin = new OriginServer();
        origin.putContent("data.json", "{\"v\":1}");

        EdgeServer edge = makeEdge("edge-1", "us", 10_000);
        byte[] originData = origin.fetchByte("data.json");
        edge.put("data.json", new CacheContent("data.json", originData, 60_000));

        check("E2E-inv: cached", edge.get("data.json") != null);

        // Origin updates, then invalidate the edge
        origin.putContent("data.json", "{\"v\":2}");
        edge.invalidate("data.json");
        check("E2E-inv: invalidated", edge.get("data.json") == null);

        // Re-fetch from origin
        byte[] newData = origin.fetchByte("data.json");
        edge.put("data.json", new CacheContent("data.json", newData, 60_000));

        CacheContent updated = edge.get("data.json");
        check("E2E-inv: updated data", "{\"v\":2}".equals(new String(updated.data)));
    }

    static void testEndToEndFailover() {
        System.out.println("\n── E2E: Failover ──");
        ConsistentHashRing ring = new ConsistentHashRing();
        EdgeServer primary   = makeEdge("primary",  "us-east", 10_000);
        EdgeServer secondary = makeEdge("secondary","us-west", 10_000);
        ring.addServer(primary);
        ring.addServer(secondary);

        // Cache on both
        CacheContent c = makeContent("img.jpg", "imgdata", 60_000);
        primary.put("img.jpg", c);
        secondary.put("img.jpg", makeContent("img.jpg", "imgdata", 60_000));

        // Normal: route somewhere
        EdgeServer routed = ring.route("img.jpg");
        check("E2E-fail: initial route works", routed != null);

        // Mark the routed server unhealthy
        routed.healthy = false;

        // Re-route should go to the other server
        EdgeServer failover = ring.route("img.jpg");
        check("E2E-fail: failover route works",           failover != null);
        check("E2E-fail: failover is different server",   failover != routed);
        check("E2E-fail: failover server is healthy",     failover.healthy);
        check("E2E-fail: failover has the content",       failover.get("img.jpg") != null);
    }
}
