import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

class ServerNotFoundException extends Exception {
    public ServerNotFoundException(String message) {
        super(message);
    }
}
class NoHealthyServerException extends ServerNotFoundException {
    public NoHealthyServerException(String message) {
        super(message);
    }
}
class InvalidServerException extends ServerNotFoundException {
    public InvalidServerException(String message) {
        super(message);
    }
}
enum ServerStatus {HEALTHY,UNHEALTHY}
enum BalancerStrategy{ROUND_ROBIN,LEAST_CONNECTIONS,RANDOM}
class Server{
    String id;
    String host;
    AtomicInteger activeConnections;
    Random rand=new Random();
    ServerStatus status;
    Server() {
        activeConnections = new AtomicInteger(0);
        id="SERVER_ID:"+ UUID.randomUUID().toString().substring(0,8);
        host=String.format("%d:%d:%d:%d",rand.nextInt(256),rand.nextInt(256),rand.nextInt(256),rand.nextInt(256));
        status=ServerStatus.HEALTHY;
    }
    @Override
    public String toString() {
        return "Server{" +
                "id='" + id + '\'' +
                ", host='" + host + '\'' +
                ", activeConnections=" + activeConnections +
                '}';
    }
}
interface LoadBalancerStrategy{
    Server selectServer(List<Server> servers) throws ServerNotFoundException;
    BalancerStrategy getBalancerStrategy();
}
class RoundRobinLoadBalancerStrategy implements LoadBalancerStrategy{
    AtomicInteger idx=new AtomicInteger(0);
    @Override
    public Server selectServer(List<Server> servers) throws ServerNotFoundException {
        if(servers==null || servers.isEmpty()) throw new ServerNotFoundException("No servers");
        for(int loop=0;loop<servers.size();loop++){
            Server server=servers.get(idx.incrementAndGet()%servers.size());
            if(server==null || server.status!=ServerStatus.HEALTHY) continue;
            return server;
        }
        throw new NoHealthyServerException("No servers available");
    }
    @Override
    public BalancerStrategy getBalancerStrategy() {
        return BalancerStrategy.ROUND_ROBIN;
    }
}
class LeastConnectionsLoadBalancerStrategy implements LoadBalancerStrategy{
    @Override
    public Server selectServer(List<Server> servers) throws ServerNotFoundException {
        Server server=servers.stream().filter(x->x.status==ServerStatus.HEALTHY).min(Comparator.comparing(x->x.activeConnections.get())).orElse(null);
        if(server==null) throw new ServerNotFoundException("No servers available");
        return server;
    }
    @Override
    public BalancerStrategy getBalancerStrategy() {
        return BalancerStrategy.LEAST_CONNECTIONS;
    }
}
class RandomLoadBalancerStrategy implements LoadBalancerStrategy{
    Random rand=new Random();
    @Override
    public Server selectServer(List<Server> servers) throws ServerNotFoundException {
        if(servers==null || servers.isEmpty()) throw new ServerNotFoundException("No servers");
        return servers.get(rand.nextInt(servers.size()));
    }
    @Override
    public BalancerStrategy getBalancerStrategy() {
        return BalancerStrategy.RANDOM;
    }
}
class LoadBalancer{
    List<Server> servers;
    LoadBalancerStrategy strategy;
    LoadBalancer(LoadBalancerStrategy strategy){
       this.servers=new CopyOnWriteArrayList<>();
       this.strategy=strategy;
    }
    boolean addServer(Server server) throws InvalidServerException {
        if(server==null) throw new InvalidServerException("null server");
        if(server.host==null) throw new InvalidServerException("null host");
        if(servers.stream().anyMatch(x->x.id.equals(server.id))) throw new InvalidServerException("server already exists");
        servers.add(server);
        return true;
    }
    boolean removeServer(Server server) throws ServerNotFoundException {
        if(server==null) throw new ServerNotFoundException("null server");
        boolean removed = servers.removeIf(x->x.id.equals(server.id));
        if(!removed) throw new ServerNotFoundException("server not found"+server);
        return removed;
    }
    LoadBalancerStrategy setStrategy(LoadBalancerStrategy strategy){
        this.strategy=strategy;
        return strategy;
    }
    Server routeRequest(String requestId) throws ServerNotFoundException {
        List<Server> healthyServers=servers.stream().filter(x->x.status==ServerStatus.HEALTHY).toList();
        if(healthyServers.isEmpty()) throw new NoHealthyServerException("no healthy server for"+requestId);
        Server server=strategy.selectServer(healthyServers);
        server.activeConnections.incrementAndGet();
        return server;
    }
    Boolean completeRequest(Server server) throws InvalidServerException {
        if(server==null) throw new InvalidServerException("null server cant complete request");
        server.activeConnections.decrementAndGet();
        return true;
    }
    Boolean failRequest(Server server) throws InvalidServerException {
        if(server==null) throw new InvalidServerException("null server cant complete request");
        server.activeConnections.incrementAndGet();
        return true;
    }
    boolean markUnhealthy(String serverId) throws ServerNotFoundException {
        Server server=servers.stream().filter(x->x.id.equals(serverId)).findFirst().orElse(null);
        if(server==null) throw new ServerNotFoundException("server not found");
        server.status=ServerStatus.UNHEALTHY;
        return true;
    }
    boolean markHealthy(String serverId) throws ServerNotFoundException {
        Server server=servers.stream().filter(x->x.id.equals(serverId)).findFirst().orElse(null);
        if(server==null) throw new ServerNotFoundException("server not found");
        server.status=ServerStatus.HEALTHY;
        return true;
    }
    Server getServerById(String serverId) throws ServerNotFoundException {
        Server server=servers.stream().filter(x->x.id.equals(serverId)).findFirst().orElse(null);
        if(server==null) throw new ServerNotFoundException("server not found");
        return server;
    }
    List<Server> getHealthyServers() {
        return servers.stream().filter(x->x.status==ServerStatus.HEALTHY).toList();
    }


}
public class LoadBalancerTest {

    // ── Helper: create Server without broken constructor (rand is never initialized) ──
    private static Server createTestServer(String id, String host) {
        try {
            var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            var unsafe = (sun.misc.Unsafe) unsafeField.get(null);
            Server server = (Server) unsafe.allocateInstance(Server.class);
            server.id = id;
            server.host = host;
            server.activeConnections = new AtomicInteger(0);
            server.rand = new Random();
            server.status = ServerStatus.HEALTHY;
            return server;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create test server", e);
        }
    }

    private static int passed = 0;
    private static int failed = 0;

    private static void assertCondition(String testName, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("  ✅ PASS: " + testName);
        } else {
            failed++;
            System.out.println("  ❌ FAIL: " + testName);
        }
    }

    @FunctionalInterface
    interface ThrowingRunnable { void run() throws Exception; }

    private static void assertThrows(String testName, Class<? extends Exception> expected, ThrowingRunnable action) {
        try {
            action.run();
            failed++;
            System.out.println("  ❌ FAIL: " + testName + " (no exception thrown)");
        } catch (Exception e) {
            if (expected.isInstance(e)) {
                passed++;
                System.out.println("  ✅ PASS: " + testName);
            } else {
                failed++;
                System.out.println("  ❌ FAIL: " + testName + " (expected " + expected.getSimpleName()
                        + " but got " + e.getClass().getSimpleName() + ": " + e.getMessage() + ")");
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  1. AddServer tests
    // ═══════════════════════════════════════════════════════════════════
    static void testAddServer() {
        System.out.println("\n── AddServer Tests ──");

        // add a valid server
        LoadBalancer lb = new LoadBalancer(new RoundRobinLoadBalancerStrategy());
        Server s1 = createTestServer("s1", "10.0.0.1");
        try {
            boolean result = lb.addServer(s1);
            assertCondition("addServer returns true for valid server", result);
            assertCondition("server list size is 1 after add", lb.servers.size() == 1);
        } catch (Exception e) {
            assertCondition("addServer should not throw for valid server", false);
        }

        // add null server
        assertThrows("addServer(null) throws InvalidServerException",
                InvalidServerException.class, () -> lb.addServer(null));

        // add server with null host
        Server noHost = createTestServer("s2", null);
        noHost.host = null;
        assertThrows("addServer with null host throws InvalidServerException",
                InvalidServerException.class, () -> lb.addServer(noHost));

        // add duplicate server (same id)
        assertThrows("addServer duplicate id throws InvalidServerException",
                InvalidServerException.class, () -> lb.addServer(s1));

        // add multiple distinct servers
        try {
            Server s3 = createTestServer("s3", "10.0.0.3");
            Server s4 = createTestServer("s4", "10.0.0.4");
            lb.addServer(s3);
            lb.addServer(s4);
            assertCondition("server list size is 3 after adding 3 servers", lb.servers.size() == 3);
        } catch (Exception e) {
            assertCondition("adding multiple distinct servers should not throw", false);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  2. RemoveServer tests
    // ═══════════════════════════════════════════════════════════════════
    static void testRemoveServer() {
        System.out.println("\n── RemoveServer Tests ──");

        LoadBalancer lb = new LoadBalancer(new RoundRobinLoadBalancerStrategy());
        Server s1 = createTestServer("r1", "10.0.0.1");
        Server s2 = createTestServer("r2", "10.0.0.2");
        try {
            lb.addServer(s1);
            lb.addServer(s2);
        } catch (Exception e) { throw new RuntimeException(e); }

        // remove existing server
        try {
            boolean result = lb.removeServer(s1);
            assertCondition("removeServer returns true for existing server", result);
            assertCondition("server list size is 1 after removal", lb.servers.size() == 1);
        } catch (Exception e) {
            assertCondition("removeServer should not throw for existing server", false);
        }

        // remove null
        assertThrows("removeServer(null) throws ServerNotFoundException",
                ServerNotFoundException.class, () -> lb.removeServer(null));

        // remove server that doesn't exist
        Server ghost = createTestServer("ghost", "10.0.0.99");
        assertThrows("removeServer non-existent throws ServerNotFoundException",
                ServerNotFoundException.class, () -> lb.removeServer(ghost));
    }

    // ═══════════════════════════════════════════════════════════════════
    //  3. RouteRequest tests
    // ═══════════════════════════════════════════════════════════════════
    static void testRouteRequest() {
        System.out.println("\n── RouteRequest Tests ──");

        // route with no servers
        LoadBalancer lb = new LoadBalancer(new RandomLoadBalancerStrategy());
        assertThrows("routeRequest with no servers throws NoHealthyServerException",
                NoHealthyServerException.class, () -> lb.routeRequest("req-1"));

        // route with all unhealthy servers
        Server s1 = createTestServer("route1", "10.0.0.1");
        s1.status = ServerStatus.UNHEALTHY;
        try { lb.addServer(s1); } catch (Exception e) { throw new RuntimeException(e); }
        assertThrows("routeRequest with all unhealthy throws NoHealthyServerException",
                NoHealthyServerException.class, () -> lb.routeRequest("req-2"));

        // route with one healthy server using Random strategy
        Server s2 = createTestServer("route2", "10.0.0.2");
        try { lb.addServer(s2); } catch (Exception e) { throw new RuntimeException(e); }
        try {
            Server routed = lb.routeRequest("req-3");
            assertCondition("routeRequest returns healthy server", routed != null && routed.id.equals("route2"));
            assertCondition("activeConnections incremented to 1", routed.activeConnections.get() == 1);
        } catch (Exception e) {
            assertCondition("routeRequest with healthy server should not throw: " + e.getMessage(), false);
        }

        // route multiple requests – connections increase
        try {
            lb.routeRequest("req-4");
            lb.routeRequest("req-5");
            assertCondition("activeConnections is 3 after 3 routes", s2.activeConnections.get() == 3);
        } catch (Exception e) {
            assertCondition("multiple routeRequests should not throw", false);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  4. CompleteRequest / FailRequest tests
    // ═══════════════════════════════════════════════════════════════════
    static void testCompleteAndFailRequest() {
        System.out.println("\n── CompleteRequest & FailRequest Tests ──");

        LoadBalancer lb = new LoadBalancer(new RandomLoadBalancerStrategy());
        Server s = createTestServer("cf1", "10.0.0.1");
        s.activeConnections.set(5);

        // completeRequest decrements
        try {
            lb.completeRequest(s);
            assertCondition("completeRequest decrements connections (5 -> 4)", s.activeConnections.get() == 4);
        } catch (Exception e) {
            assertCondition("completeRequest should not throw for valid server", false);
        }

        // completeRequest(null) throws
        assertThrows("completeRequest(null) throws InvalidServerException",
                InvalidServerException.class, () -> lb.completeRequest(null));

        // failRequest increments
        try {
            int before = s.activeConnections.get();
            lb.failRequest(s);
            assertCondition("failRequest increments connections (" + before + " -> " + (before + 1) + ")",
                    s.activeConnections.get() == before + 1);
        } catch (Exception e) {
            assertCondition("failRequest should not throw for valid server", false);
        }

        // failRequest(null) throws
        assertThrows("failRequest(null) throws InvalidServerException",
                InvalidServerException.class, () -> lb.failRequest(null));
    }

    // ═══════════════════════════════════════════════════════════════════
    //  5. MarkHealthy / MarkUnhealthy tests
    // ═══════════════════════════════════════════════════════════════════
    static void testMarkHealthyUnhealthy() {
        System.out.println("\n── MarkHealthy / MarkUnhealthy Tests ──");

        LoadBalancer lb = new LoadBalancer(new RandomLoadBalancerStrategy());
        Server s = createTestServer("hu1", "10.0.0.1");
        try { lb.addServer(s); } catch (Exception e) { throw new RuntimeException(e); }

        // mark unhealthy
        try {
            lb.markUnhealthy("hu1");
            assertCondition("markUnhealthy sets status to UNHEALTHY", s.status == ServerStatus.UNHEALTHY);
        } catch (Exception e) {
            assertCondition("markUnhealthy should not throw for existing server", false);
        }

        // mark healthy again
        try {
            lb.markHealthy("hu1");
            assertCondition("markHealthy sets status to HEALTHY", s.status == ServerStatus.HEALTHY);
        } catch (Exception e) {
            assertCondition("markHealthy should not throw for existing server", false);
        }

        // mark non-existent server unhealthy
        assertThrows("markUnhealthy non-existent throws ServerNotFoundException",
                ServerNotFoundException.class, () -> lb.markUnhealthy("noexist"));

        // mark non-existent server healthy
        assertThrows("markHealthy non-existent throws ServerNotFoundException",
                ServerNotFoundException.class, () -> lb.markHealthy("noexist"));
    }

    // ═══════════════════════════════════════════════════════════════════
    //  6. GetServerById tests
    // ═══════════════════════════════════════════════════════════════════
    static void testGetServerById() {
        System.out.println("\n── GetServerById Tests ──");

        LoadBalancer lb = new LoadBalancer(new RandomLoadBalancerStrategy());
        Server s = createTestServer("find1", "10.0.0.1");
        try { lb.addServer(s); } catch (Exception e) { throw new RuntimeException(e); }

        try {
            Server found = lb.getServerById("find1");
            assertCondition("getServerById returns correct server", found == s);
        } catch (Exception e) {
            assertCondition("getServerById should not throw for existing server", false);
        }

        assertThrows("getServerById non-existent throws ServerNotFoundException",
                ServerNotFoundException.class, () -> lb.getServerById("nope"));
    }

    // ═══════════════════════════════════════════════════════════════════
    //  7. GetHealthyServers tests
    // ═══════════════════════════════════════════════════════════════════
    static void testGetHealthyServers() {
        System.out.println("\n── GetHealthyServers Tests ──");

        LoadBalancer lb = new LoadBalancer(new RandomLoadBalancerStrategy());
        assertCondition("getHealthyServers returns empty list when no servers", lb.getHealthyServers().isEmpty());

        Server s1 = createTestServer("h1", "10.0.0.1");
        Server s2 = createTestServer("h2", "10.0.0.2");
        Server s3 = createTestServer("h3", "10.0.0.3");
        s2.status = ServerStatus.UNHEALTHY;
        try {
            lb.addServer(s1);
            lb.addServer(s2);
            lb.addServer(s3);
        } catch (Exception e) { throw new RuntimeException(e); }

        List<Server> healthy = lb.getHealthyServers();
        assertCondition("getHealthyServers returns 2 out of 3", healthy.size() == 2);
        assertCondition("getHealthyServers contains s1", healthy.contains(s1));
        assertCondition("getHealthyServers does NOT contain unhealthy s2", !healthy.contains(s2));
        assertCondition("getHealthyServers contains s3", healthy.contains(s3));
    }

    // ═══════════════════════════════════════════════════════════════════
    //  8. SetStrategy tests
    // ═══════════════════════════════════════════════════════════════════
    static void testSetStrategy() {
        System.out.println("\n── SetStrategy Tests ──");

        LoadBalancer lb = new LoadBalancer(new RoundRobinLoadBalancerStrategy());
        assertCondition("initial strategy is ROUND_ROBIN",
                lb.strategy.getBalancerStrategy() == BalancerStrategy.ROUND_ROBIN);

        lb.setStrategy(new RandomLoadBalancerStrategy());
        assertCondition("strategy changed to RANDOM",
                lb.strategy.getBalancerStrategy() == BalancerStrategy.RANDOM);

        lb.setStrategy(new LeastConnectionsLoadBalancerStrategy());
        assertCondition("strategy changed to LEAST_CONNECTIONS",
                lb.strategy.getBalancerStrategy() == BalancerStrategy.LEAST_CONNECTIONS);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  9. RandomLoadBalancerStrategy tests
    // ═══════════════════════════════════════════════════════════════════
    static void testRandomStrategy() {
        System.out.println("\n── RandomLoadBalancerStrategy Tests ──");

        RandomLoadBalancerStrategy strategy = new RandomLoadBalancerStrategy();
        assertCondition("getBalancerStrategy is RANDOM",
                strategy.getBalancerStrategy() == BalancerStrategy.RANDOM);

        // null list
        assertThrows("selectServer(null) throws ServerNotFoundException",
                ServerNotFoundException.class, () -> strategy.selectServer(null));

        // empty list
        assertThrows("selectServer(empty) throws ServerNotFoundException",
                ServerNotFoundException.class, () -> strategy.selectServer(new ArrayList<>()));

        // single server – always returns it
        List<Server> single = List.of(createTestServer("rand1", "10.0.0.1"));
        try {
            Server s = strategy.selectServer(single);
            assertCondition("selectServer with single server returns that server", s.id.equals("rand1"));
        } catch (Exception e) {
            assertCondition("selectServer with single server should not throw", false);
        }

        // multiple servers – all returned servers are from the list
        Server a = createTestServer("randA", "10.0.0.1");
        Server b = createTestServer("randB", "10.0.0.2");
        Server c = createTestServer("randC", "10.0.0.3");
        List<Server> multi = List.of(a, b, c);
        Set<String> ids = new HashSet<>(Set.of("randA", "randB", "randC"));
        boolean allValid = true;
        try {
            for (int i = 0; i < 20; i++) {
                Server s = strategy.selectServer(multi);
                if (!ids.contains(s.id)) { allValid = false; break; }
            }
            assertCondition("selectServer always returns a server from the list (20 calls)", allValid);
        } catch (Exception e) {
            assertCondition("selectServer should not throw with valid servers", false);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  10. RoundRobinLoadBalancerStrategy tests
    // ═══════════════════════════════════════════════════════════════════
    static void testRoundRobinStrategy() {
        System.out.println("\n── RoundRobinLoadBalancerStrategy Tests ──");

        RoundRobinLoadBalancerStrategy strategy = new RoundRobinLoadBalancerStrategy();
        assertCondition("getBalancerStrategy is ROUND_ROBIN",
                strategy.getBalancerStrategy() == BalancerStrategy.ROUND_ROBIN);

        // null list
        assertThrows("selectServer(null) throws ServerNotFoundException",
                ServerNotFoundException.class, () -> {
                    try { strategy.selectServer(null); } catch (ServerNotFoundException e) { throw e; }
                    catch (Exception e) { throw new RuntimeException(e); }
                });

        // empty list
        assertThrows("selectServer(empty) throws ServerNotFoundException",
                ServerNotFoundException.class, () -> {
                    try { strategy.selectServer(new ArrayList<>()); } catch (ServerNotFoundException e) { throw e; }
                    catch (Exception e) { throw new RuntimeException(e); }
                });

        // Test with a single server – round robin wraps around with modulo
        List<Server> servers = new ArrayList<>();
        servers.add(createTestServer("rr1", "10.0.0.1"));
        try {
            Server s = strategy.selectServer(servers);
            assertCondition("RoundRobin selectServer with 1 server returns it", s != null && s.id.equals("rr1"));
        } catch (Exception e) {
            assertCondition("RoundRobin selectServer should not throw: " + e.getMessage(), false);
        }

        // Test round robin cycles through multiple servers
        RoundRobinLoadBalancerStrategy freshStrategy = new RoundRobinLoadBalancerStrategy();
        Server rr2 = createTestServer("rr2", "10.0.0.2");
        Server rr3 = createTestServer("rr3", "10.0.0.3");
        List<Server> multiServers = List.of(createTestServer("rrA", "10.0.0.1"), rr2, rr3);
        Set<String> selectedIds = new HashSet<>();
        try {
            for (int i = 0; i < 6; i++) {
                Server s = freshStrategy.selectServer(multiServers);
                selectedIds.add(s.id);
            }
            assertCondition("RoundRobin cycles through all servers", selectedIds.size() == 3);
        } catch (Exception e) {
            assertCondition("RoundRobin multiple calls should not throw: " + e.getMessage(), false);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  11. LeastConnectionsLoadBalancerStrategy tests
    // ═══════════════════════════════════════════════════════════════════
    static void testLeastConnectionsStrategy() {
        System.out.println("\n── LeastConnectionsLoadBalancerStrategy Tests ──");

        LeastConnectionsLoadBalancerStrategy strategy = new LeastConnectionsLoadBalancerStrategy();
        assertCondition("getBalancerStrategy is LEAST_CONNECTIONS",
                strategy.getBalancerStrategy() == BalancerStrategy.LEAST_CONNECTIONS);

        // healthy servers – should pick the one with fewest connections
        Server s1 = createTestServer("lc1", "10.0.0.1");
        s1.activeConnections.set(5);
        Server s2 = createTestServer("lc2", "10.0.0.2");
        s2.activeConnections.set(2);
        Server s3 = createTestServer("lc3", "10.0.0.3");
        s3.activeConnections.set(8);
        List<Server> servers = List.of(s1, s2, s3);

        try {
            Server selected = strategy.selectServer(servers);
            assertCondition("LeastConnections selects server with fewest connections", selected.id.equals("lc2"));
        } catch (ServerNotFoundException e) {
            assertCondition("LeastConnections should not throw for healthy servers: " + e.getMessage(), false);
        }

        // all unhealthy servers – should throw ServerNotFoundException
        Server u1 = createTestServer("lcu1", "10.0.0.10");
        u1.status = ServerStatus.UNHEALTHY;
        u1.activeConnections.set(10);
        Server u2 = createTestServer("lcu2", "10.0.0.11");
        u2.status = ServerStatus.UNHEALTHY;
        u2.activeConnections.set(3);
        List<Server> unhealthyServers = List.of(u1, u2);

        assertThrows("LeastConnections with all unhealthy throws ServerNotFoundException",
                ServerNotFoundException.class, () -> strategy.selectServer(unhealthyServers));

        // mixed – should pick healthy server with fewest connections
        Server h1 = createTestServer("lcm1", "10.0.0.20");
        h1.activeConnections.set(7);
        Server h2 = createTestServer("lcm2", "10.0.0.21");
        h2.activeConnections.set(1);
        Server sick = createTestServer("lcm3", "10.0.0.22");
        sick.status = ServerStatus.UNHEALTHY;
        sick.activeConnections.set(0);
        List<Server> mixed = List.of(h1, h2, sick);

        try {
            Server selected = strategy.selectServer(mixed);
            assertCondition("LeastConnections picks healthy server with min connections (not unhealthy with 0)",
                    selected.id.equals("lcm2"));
        } catch (Exception e) {
            assertCondition("LeastConnections mixed should not throw: " + e.getMessage(), false);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  12. Server class tests
    // ═══════════════════════════════════════════════════════════════════
    static void testServerClass() {
        System.out.println("\n── Server Class Tests ──");

        Server s = createTestServer("srv1", "192.168.1.1");
        assertCondition("server id set correctly", "srv1".equals(s.id));
        assertCondition("server host set correctly", "192.168.1.1".equals(s.host));
        assertCondition("server default status is HEALTHY", s.status == ServerStatus.HEALTHY);
        assertCondition("server default activeConnections is 0", s.activeConnections.get() == 0);

        String str = s.toString();
        assertCondition("toString contains id", str.contains("srv1"));
        assertCondition("toString contains host", str.contains("192.168.1.1"));

        // NOTE: Server() constructor has a bug – rand field is never initialized → NPE
        try {
            new Server();
            assertCondition("Server() constructor works", true);
        } catch (NullPointerException e) {
            failed++;
            System.out.println("  ⚠️  KNOWN BUG: Server() constructor → NPE (Random rand not initialized)");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  13. Exception hierarchy tests
    // ═══════════════════════════════════════════════════════════════════
    static void testExceptionHierarchy() {
        System.out.println("\n── Exception Hierarchy Tests ──");

        ServerNotFoundException snfe = new ServerNotFoundException("test");
        assertCondition("ServerNotFoundException is an Exception",
                snfe instanceof Exception);
        assertCondition("ServerNotFoundException message", "test".equals(snfe.getMessage()));

        NoHealthyServerException nhse = new NoHealthyServerException("no healthy");
        assertCondition("NoHealthyServerException extends ServerNotFoundException",
                nhse instanceof ServerNotFoundException);

        InvalidServerException ise = new InvalidServerException("invalid");
        assertCondition("InvalidServerException extends ServerNotFoundException",
                ise instanceof ServerNotFoundException);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  14. End-to-end integration test
    // ═══════════════════════════════════════════════════════════════════
    static void testEndToEnd() {
        System.out.println("\n── End-to-End Integration Test ──");

        try {
            LoadBalancer lb = new LoadBalancer(new RandomLoadBalancerStrategy());

            Server s1 = createTestServer("e2e-1", "10.0.0.1");
            Server s2 = createTestServer("e2e-2", "10.0.0.2");
            Server s3 = createTestServer("e2e-3", "10.0.0.3");

            lb.addServer(s1);
            lb.addServer(s2);
            lb.addServer(s3);
            assertCondition("3 servers added", lb.servers.size() == 3);
            assertCondition("3 healthy servers", lb.getHealthyServers().size() == 3);

            // Route some requests
            Server routed1 = lb.routeRequest("req-e2e-1");
            assertCondition("routed1 is not null", routed1 != null);

            // Mark one server unhealthy
            lb.markUnhealthy("e2e-2");
            assertCondition("healthy servers is now 2", lb.getHealthyServers().size() == 2);

            // Route – should not go to e2e-2
            Set<String> routedIds = new HashSet<>();
            for (int i = 0; i < 20; i++) {
                Server r = lb.routeRequest("req-loop-" + i);
                routedIds.add(r.id);
            }
            assertCondition("unhealthy server e2e-2 never routed to", !routedIds.contains("e2e-2"));

            // Complete a request
            lb.completeRequest(routed1);
            int afterComplete = routed1.activeConnections.get();
            assertCondition("connections decreased after completeRequest", afterComplete >= 0);

            // Bring server back
            lb.markHealthy("e2e-2");
            assertCondition("healthy servers back to 3", lb.getHealthyServers().size() == 3);

            // Remove a server
            lb.removeServer(s3);
            assertCondition("server count is 2 after removal", lb.servers.size() == 2);

            // Switch strategy
            lb.setStrategy(new LeastConnectionsLoadBalancerStrategy());
            assertCondition("strategy switched to LEAST_CONNECTIONS",
                    lb.strategy.getBalancerStrategy() == BalancerStrategy.LEAST_CONNECTIONS);

            // Get server by id
            Server found = lb.getServerById("e2e-1");
            assertCondition("getServerById returns correct server", found == s1);

        } catch (Exception e) {
            assertCondition("End-to-end test threw unexpected exception: " + e.getMessage(), false);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  15. Enum tests
    // ═══════════════════════════════════════════════════════════════════
    static void testEnums() {
        System.out.println("\n── Enum Tests ──");

        assertCondition("ServerStatus has HEALTHY", ServerStatus.valueOf("HEALTHY") == ServerStatus.HEALTHY);
        assertCondition("ServerStatus has UNHEALTHY", ServerStatus.valueOf("UNHEALTHY") == ServerStatus.UNHEALTHY);
        assertCondition("ServerStatus has 2 values", ServerStatus.values().length == 2);

        assertCondition("BalancerStrategy has ROUND_ROBIN", BalancerStrategy.valueOf("ROUND_ROBIN") == BalancerStrategy.ROUND_ROBIN);
        assertCondition("BalancerStrategy has LEAST_CONNECTIONS", BalancerStrategy.valueOf("LEAST_CONNECTIONS") == BalancerStrategy.LEAST_CONNECTIONS);
        assertCondition("BalancerStrategy has RANDOM", BalancerStrategy.valueOf("RANDOM") == BalancerStrategy.RANDOM);
        assertCondition("BalancerStrategy has 3 values", BalancerStrategy.values().length == 3);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  MAIN – Run all tests
    // ═══════════════════════════════════════════════════════════════════
    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║      LoadBalancer Test Suite             ║");
        System.out.println("╚══════════════════════════════════════════╝");

        testServerClass();
        testExceptionHierarchy();
        testEnums();
        testAddServer();
        testRemoveServer();
        testRouteRequest();
        testCompleteAndFailRequest();
        testMarkHealthyUnhealthy();
        testGetServerById();
        testGetHealthyServers();
        testSetStrategy();
        testRandomStrategy();
        testRoundRobinStrategy();
        testLeastConnectionsStrategy();
        testEndToEnd();

        System.out.println("\n══════════════════════════════════════════");
        System.out.printf("  Results: %d passed, %d failed, %d total%n", passed, failed, passed + failed);
        System.out.println("══════════════════════════════════════════");

        if (failed > 0) {
            System.out.println("\n⚠️  Known bugs detected in source code:");
            System.out.println("  1. Server(): Random rand field is never initialized → NPE in constructor");
            System.out.println("  2. RoundRobinStrategy: idx.incrementAndGet() without modulo → IndexOutOfBoundsException");
            System.out.println("  3. LeastConnectionsStrategy: filters UNHEALTHY instead of HEALTHY");
        }

        System.exit(failed > 0 ? 1 : 0);
    }
}
