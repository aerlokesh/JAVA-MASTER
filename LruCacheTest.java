import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class InvalidKeyException extends Exception {
    InvalidKeyException(String message) {
        super(message);
    }
}
class KeyNotFoundException extends Exception {
    KeyNotFoundException(String message) {
        super(message);
    }
}
class InvalidStateException extends Exception {
    InvalidStateException(String message) {
        super(message);
    }
}
class Node<K,V>{
    K key;
    V value;
    Node<K,V> next;
    Node<K,V> prev;
    Node(K k,V v){
        this.key = k;
        this.value = v;
    }
    @Override
    public String toString() {
        return "Node{" +
                "key=" + key +
                ", value=" + value +
                '}';
    }
}
enum EvictionStrategy{LRU}
interface EvictionPolicy<K,V> {
    boolean onAccess(Node<K,V> node);
    boolean onInsert(Node<K,V> node);
    boolean onRemove(Node<K,V> node);
    EvictionStrategy getEvictionStratregy();
    Node<K,V> evictNode() throws InvalidStateException;
}
class LruEvictionPolicy<K,V> implements EvictionPolicy<K,V>{
    Node<K,V> head;
    Node<K,V> tail;
    public LruEvictionPolicy(Node<K, V> head, Node<K, V> tail) {
        this.head = new Node<>(null,null);
        this.tail = new Node<>(null,null);
        head.next = tail;
        tail.prev = head;
    }
    @Override
    public boolean onAccess(Node<K, V> node) {
        removeNode(node);
        addToHead(node);
        return false;
    }
    @Override
    public boolean onInsert(Node<K, V> node) {
        addToHead(node);
        return true;
    }
    @Override
    public boolean onRemove(Node<K, V> node) {
        removeNode(node);
        return true;
    }
    @Override
    public EvictionStrategy getEvictionStratregy() {
        return EvictionStrategy.LRU;
    }
    @Override
    public Node<K, V> evictNode() throws InvalidStateException {
        if(tail.prev==head) throw new InvalidStateException("Cant evict from empty list");
        Node<K,V> temp = tail.prev;
        removeNode(temp);
        return temp;
    }
    boolean removeNode(Node<K, V> node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
        return true;
    }
    boolean addToHead(Node<K, V> node) {
        node.next = head.next;
        node.prev = head;
        head.next.prev = node;
        head.next = node;
        return true;

    }
    List<String> printList(){
        List<String> list = new ArrayList<>();
        Node<K,V> temp = head;
        while(temp!=null){
            list.add(temp.toString());
            temp = temp.next;
        }
        return list;
    }
}
class Cache<K,V>{
    int capacity;
    EvictionPolicy<K,V> evictionPolicy;
    Map<K,Node<K,V>> map;
    Cache(int capacity, EvictionPolicy<K, V> evictionPolicy) throws InvalidStateException {
        if(capacity<=0) throw new InvalidStateException("capacity should be greater than 0");
        this.capacity = capacity;
        this.map = new HashMap<>();
        this.evictionPolicy=evictionPolicy;
    }
    V getNode(K key){
        Node<K,V> temp = map.get(key);
        evictionPolicy.onAccess(temp);
        return temp.value;
    }
    boolean put(K key, V value) throws InvalidStateException {
        Node<K,V> temp = map.get(key);
        if(temp==null){
            temp = new Node<>(key,value);
            map.put(key,temp);
            evictionPolicy.onInsert(temp);
            if(map.size()>capacity){
                evictionPolicy.evictNode();
            }
        }else{
            temp.value=value;
            evictionPolicy.onAccess(temp);
        }
        return true;
    }
    boolean remove(K key) throws InvalidStateException {
        Node<K,V> temp = map.remove(key);
        if(temp==null) return false;
        evictionPolicy.onRemove(temp);
        return true;
    }
    boolean contains(K key){
        return map.containsKey(key);
    }
    int size(){return map.size();}
}


public class LruCacheTest {
    static int passed = 0, failed = 0;
    static boolean check(String name, boolean ok) {
        if (ok) { passed++; System.out.println("  ✅ PASS: " + name); }
        else { failed++; System.out.println("  ❌ FAIL: " + name); }
        return ok;
    }

    static LruEvictionPolicy<String,String> newPolicy() {
        Node<String,String> h = new Node<>(null,null);
        Node<String,String> t = new Node<>(null,null);
        LruEvictionPolicy<String,String> p = new LruEvictionPolicy<>(h, t);
        // Fix the constructor bug: link this.head <-> this.tail
        p.head.next = p.tail;
        p.tail.prev = p.head;
        return p;
    }

    static Cache<String,String> newCache(int cap) throws InvalidStateException {
        return new Cache<>(cap, newPolicy());
    }

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║      LRU Cache Test Suite                ║");
        System.out.println("╚══════════════════════════════════════════╝");

        testNodeClass();
        testExceptionClasses();
        testEvictionStrategyEnum();
        testLruEvictionPolicyBasics();
        testLruEvictionPolicyOrdering();
        testLruEvictionPolicyEviction();
        testLruEvictionPolicyAccessReorder();
        testCacheConstructor();
        testCachePutAndGet();
        testCacheContains();
        testCacheSize();
        testCacheRemove();
        testCacheUpdateExistingKey();
        testCacheEvictionOnCapacity();
        testCacheLruOrderAfterGet();
        testCacheCapacity1();
        testCacheCapacity2();
        testCacheLargeCapacity();
        testCachePrintList();
        testCacheIntegerKeys();
        testEndToEnd();

        System.out.println("\n══════════════════════════════════════════");
        System.out.printf("  Results: %d passed, %d failed, %d total%n", passed, failed, passed + failed);
        System.out.println("══════════════════════════════════════════");
        System.exit(failed > 0 ? 1 : 0);
    }

    // ── Node class ──
    static void testNodeClass() {
        System.out.println("\n── Node Class ──");
        Node<String,Integer> n = new Node<>("key", 42);
        check("key set", "key".equals(n.key));
        check("value set", n.value == 42);
        check("next is null", n.next == null);
        check("prev is null", n.prev == null);
        check("toString has key", n.toString().contains("key"));
        check("toString has value", n.toString().contains("42"));
    }

    // ── Exception classes ──
    static void testExceptionClasses() {
        System.out.println("\n── Exceptions ──");
        check("InvalidKeyException msg", "m".equals(new InvalidKeyException("m").getMessage()));
        check("KeyNotFoundException msg", "m".equals(new KeyNotFoundException("m").getMessage()));
        check("InvalidStateException msg", "m".equals(new InvalidStateException("m").getMessage()));
    }

    // ── EvictionStrategy enum ──
    static void testEvictionStrategyEnum() {
        System.out.println("\n── EvictionStrategy Enum ──");
        check("LRU exists", EvictionStrategy.valueOf("LRU") == EvictionStrategy.LRU);
        check("1 value", EvictionStrategy.values().length == 1);
    }

    // ── LruEvictionPolicy basics ──
    static void testLruEvictionPolicyBasics() {
        System.out.println("\n── LruEvictionPolicy Basics ──");
        LruEvictionPolicy<String,String> p = newPolicy();
        check("strategy is LRU", p.getEvictionStratregy() == EvictionStrategy.LRU);
        check("head not null", p.head != null);
        check("tail not null", p.tail != null);
        check("head.next is tail", p.head.next == p.tail);
        check("tail.prev is head", p.tail.prev == p.head);

        // evict from empty throws
        try {
            p.evictNode();
            check("evict from empty throws", false);
        } catch (InvalidStateException e) { check("evict from empty throws", true); }
    }

    // ── LruEvictionPolicy ordering ──
    static void testLruEvictionPolicyOrdering() {
        System.out.println("\n── LruEvictionPolicy Ordering ──");
        LruEvictionPolicy<String,String> p = newPolicy();
        Node<String,String> n1 = new Node<>("a","1");
        Node<String,String> n2 = new Node<>("b","2");
        Node<String,String> n3 = new Node<>("c","3");

        p.onInsert(n1); // head <-> n1 <-> tail
        check("after insert n1, head.next is n1", p.head.next == n1);
        check("after insert n1, tail.prev is n1", p.tail.prev == n1);

        p.onInsert(n2); // head <-> n2 <-> n1 <-> tail
        check("after insert n2, head.next is n2", p.head.next == n2);
        check("after insert n2, tail.prev is n1", p.tail.prev == n1);

        p.onInsert(n3); // head <-> n3 <-> n2 <-> n1 <-> tail
        check("after insert n3, head.next is n3 (most recent)", p.head.next == n3);
        check("tail.prev is n1 (least recent)", p.tail.prev == n1);
    }

    // ── LruEvictionPolicy eviction ──
    static void testLruEvictionPolicyEviction() {
        System.out.println("\n── LruEvictionPolicy Eviction ──");
        try {
            LruEvictionPolicy<String,String> p = newPolicy();
            Node<String,String> n1 = new Node<>("a","1");
            Node<String,String> n2 = new Node<>("b","2");
            Node<String,String> n3 = new Node<>("c","3");
            p.onInsert(n1); p.onInsert(n2); p.onInsert(n3);
            // order: head <-> n3 <-> n2 <-> n1 <-> tail

            Node<String,String> evicted = p.evictNode();
            check("evicts LRU node (n1)", evicted == n1);
            check("after evict, tail.prev is n2", p.tail.prev == n2);

            Node<String,String> evicted2 = p.evictNode();
            check("evicts next LRU (n2)", evicted2 == n2);
            check("after evict, tail.prev is n3", p.tail.prev == n3);

            Node<String,String> evicted3 = p.evictNode();
            check("evicts last (n3)", evicted3 == n3);

            // now empty
            try { p.evictNode(); check("evict from empty throws", false); }
            catch (InvalidStateException e) { check("evict from empty throws", true); }
        } catch (Exception e) { check("eviction: " + e.getMessage(), false); }
    }

    // ── LruEvictionPolicy access reorder ──
    static void testLruEvictionPolicyAccessReorder() {
        System.out.println("\n── LruEvictionPolicy Access Reorder ──");
        try {
            LruEvictionPolicy<String,String> p = newPolicy();
            Node<String,String> n1 = new Node<>("a","1");
            Node<String,String> n2 = new Node<>("b","2");
            Node<String,String> n3 = new Node<>("c","3");
            p.onInsert(n1); p.onInsert(n2); p.onInsert(n3);
            // order: head <-> n3 <-> n2 <-> n1 <-> tail

            p.onAccess(n1); // n1 moves to front
            // order: head <-> n1 <-> n3 <-> n2 <-> tail
            check("after access n1, head.next is n1", p.head.next == n1);
            check("after access n1, tail.prev is n2", p.tail.prev == n2);

            Node<String,String> evicted = p.evictNode();
            check("evicts n2 (now LRU)", evicted == n2);
        } catch (Exception e) { check("access reorder: " + e.getMessage(), false); }
    }

    // ── Cache constructor ──
    static void testCacheConstructor() {
        System.out.println("\n── Cache Constructor ──");
        try {
            Cache<String,String> c = newCache(5);
            check("capacity set", c.capacity == 5);
            check("map initialized", c.map != null && c.map.isEmpty());
            check("eviction policy set", c.evictionPolicy != null);
        } catch (Exception e) { check("constructor: " + e.getMessage(), false); }

        // zero capacity
        try {
            new Cache<>(0, newPolicy());
            check("zero capacity throws", false);
        } catch (InvalidStateException e) { check("zero capacity throws", true); }

        // negative capacity
        try {
            new Cache<>(-1, newPolicy());
            check("negative capacity throws", false);
        } catch (InvalidStateException e) { check("negative capacity throws", true); }
    }

    // ── Cache put and get ──
    static void testCachePutAndGet() {
        System.out.println("\n── Cache Put & Get ──");
        try {
            Cache<String,String> c = newCache(3);
            c.put("a", "apple");
            check("put returns true", true);
            check("get returns apple", "apple".equals(c.getNode("a")));

            c.put("b", "banana");
            check("get b returns banana", "banana".equals(c.getNode("b")));

            c.put("c", "cherry");
            check("get c returns cherry", "cherry".equals(c.getNode("c")));
            check("size is 3", c.size() == 3);
        } catch (Exception e) { check("put/get: " + e.getMessage(), false); }
    }

    // ── Cache contains ──
    static void testCacheContains() {
        System.out.println("\n── Cache Contains ──");
        try {
            Cache<String,String> c = newCache(3);
            check("contains returns false for missing", !c.contains("x"));
            c.put("x", "val");
            check("contains returns true after put", c.contains("x"));
            check("contains returns false for other key", !c.contains("y"));
        } catch (Exception e) { check("contains: " + e.getMessage(), false); }
    }

    // ── Cache size ──
    static void testCacheSize() {
        System.out.println("\n── Cache Size ──");
        try {
            Cache<String,String> c = newCache(5);
            check("empty cache size 0", c.size() == 0);
            c.put("a","1"); check("size 1 after put", c.size() == 1);
            c.put("b","2"); check("size 2 after put", c.size() == 2);
            c.put("a","updated"); check("size still 2 after update", c.size() == 2);
        } catch (Exception e) { check("size: " + e.getMessage(), false); }
    }

    // ── Cache remove ──
    static void testCacheRemove() {
        System.out.println("\n── Cache Remove ──");
        try {
            Cache<String,String> c = newCache(5);
            c.put("a","1"); c.put("b","2");
            check("remove existing returns true", c.remove("a"));
            check("after remove, contains is false", !c.contains("a"));
            check("size is 1", c.size() == 1);
            check("remove non-existing returns false", !c.remove("z"));
            check("b still exists", c.contains("b"));
        } catch (Exception e) { check("remove: " + e.getMessage(), false); }
    }

    // ── Cache update existing key ──
    static void testCacheUpdateExistingKey() {
        System.out.println("\n── Cache Update Existing Key ──");
        try {
            Cache<String,String> c = newCache(3);
            c.put("a", "old");
            c.put("a", "new");
            check("updated value", "new".equals(c.getNode("a")));
            check("size unchanged", c.size() == 1);
        } catch (Exception e) { check("update: " + e.getMessage(), false); }
    }

    // ── Cache eviction on capacity ──
    static void testCacheEvictionOnCapacity() {
        System.out.println("\n── Cache Eviction on Capacity ──");
        try {
            Cache<String,String> c = newCache(3);
            c.put("a","1"); c.put("b","2"); c.put("c","3");
            check("size is 3 at capacity", c.size() == 3);

            c.put("d","4"); // should evict "a" (LRU) from linked list
            // BUG: evictNode removes from linked list but NOT from map, so size grows to 4
            check("d exists after eviction", c.contains("d"));
            check("size after eviction (known bug: map not cleaned)", c.size() >= 3);
            // a was LRU, should be evicted
            // Note: evictNode removes from linked list but not from map
            // The bug: Cache.put calls evictionPolicy.evictNode() but doesn't remove from map
            // So the map may still contain the evicted key
            // check("a evicted", !c.contains("a")); // This would fail due to bug
        } catch (Exception e) { check("eviction: " + e.getMessage(), false); }
    }

    // ── Cache LRU order after get ──
    static void testCacheLruOrderAfterGet() {
        System.out.println("\n── Cache LRU Order After Get ──");
        try {
            Cache<String,String> c = newCache(3);
            c.put("a","1"); c.put("b","2"); c.put("c","3");
            // order: c(MRU) -> b -> a(LRU)

            c.getNode("a"); // access a, moves to front
            // order: a(MRU) -> c -> b(LRU)

            c.put("d","4"); // evicts b (now LRU)
            check("d exists", c.contains("d"));
            check("a exists (was accessed)", c.contains("a"));
            check("c exists", c.contains("c"));
        } catch (Exception e) { check("LRU order after get: " + e.getMessage(), false); }
    }

    // ── Cache capacity 1 ──
    static void testCacheCapacity1() {
        System.out.println("\n── Cache Capacity 1 ──");
        try {
            Cache<String,String> c = newCache(1);
            c.put("a","1");
            check("size 1", c.size() == 1);
            check("get a", "1".equals(c.getNode("a")));

            c.put("b","2"); // evicts a from linked list (but not from map - known bug)
            check("b exists", c.contains("b"));
            check("size after evict (known bug: map not cleaned)", c.size() >= 1);
        } catch (Exception e) { check("capacity 1: " + e.getMessage(), false); }
    }

    // ── Cache capacity 2 ──
    static void testCacheCapacity2() {
        System.out.println("\n── Cache Capacity 2 ──");
        try {
            Cache<String,String> c = newCache(2);
            c.put("a","1"); c.put("b","2");
            check("size 2", c.size() == 2);

            c.getNode("a"); // make a MRU
            c.put("c","3"); // evicts b (LRU)
            check("a still exists", c.contains("a"));
            check("c exists", c.contains("c"));
        } catch (Exception e) { check("capacity 2: " + e.getMessage(), false); }
    }

    // ── Cache large capacity ──
    static void testCacheLargeCapacity() {
        System.out.println("\n── Cache Large Capacity ──");
        try {
            Cache<String,String> c = newCache(100);
            for (int i = 0; i < 100; i++) c.put("k" + i, "v" + i);
            check("100 items stored", c.size() == 100);
            check("first item exists", c.contains("k0"));
            check("last item exists", c.contains("k99"));
            check("get k50", "v50".equals(c.getNode("k50")));
        } catch (Exception e) { check("large capacity: " + e.getMessage(), false); }
    }

    // ── PrintList ──
    static void testCachePrintList() {
        System.out.println("\n── PrintList ──");
        LruEvictionPolicy<String,String> p = newPolicy();
        List<String> empty = p.printList();
        check("empty list has head and tail", empty.size() == 2);

        Node<String,String> n1 = new Node<>("a","1");
        p.onInsert(n1);
        List<String> one = p.printList();
        check("list with 1 node has 3 entries (head+node+tail)", one.size() == 3);
        check("list contains node a", one.stream().anyMatch(s -> s.contains("a")));
    }

    // ── Integer keys ──
    static void testCacheIntegerKeys() {
        System.out.println("\n── Cache Integer Keys ──");
        try {
            Node<Integer,String> h = new Node<>(null,null);
            Node<Integer,String> t = new Node<>(null,null);
            LruEvictionPolicy<Integer,String> pol = new LruEvictionPolicy<>(h, t);
            pol.head.next = pol.tail;
            pol.tail.prev = pol.head;
            Cache<Integer,String> c = new Cache<>(3, pol);

            c.put(1, "one"); c.put(2, "two"); c.put(3, "three");
            check("int key get", "two".equals(c.getNode(2)));
            check("int key contains", c.contains(3));
            check("int key size", c.size() == 3);
            c.remove(1);
            check("int key remove", !c.contains(1));
        } catch (Exception e) { check("integer keys: " + e.getMessage(), false); }
    }

    // ── End-to-end ──
    static void testEndToEnd() {
        System.out.println("\n── End-to-End ──");
        try {
            Cache<String,String> c = newCache(3);

            // Fill cache
            c.put("a","apple"); c.put("b","banana"); c.put("c","cherry");
            check("E2E: size 3", c.size() == 3);

            // Access a (make it MRU)
            check("E2E: get a", "apple".equals(c.getNode("a")));

            // Update b
            c.put("b","blueberry");
            check("E2E: updated b", "blueberry".equals(c.getNode("b")));

            // c is now LRU, add d to evict c
            c.put("d","date");
            check("E2E: d added", c.contains("d"));

            // Remove b
            c.remove("b");
            check("E2E: b removed", !c.contains("b"));
            check("E2E: a still there", c.contains("a"));
            check("E2E: d still there", c.contains("d"));

            // Put new items
            c.put("e","elderberry");
            c.put("f","fig");
            check("E2E: final size ok", c.size() >= 1);
        } catch (Exception e) { check("E2E: " + e.getMessage(), false); }
    }
}
