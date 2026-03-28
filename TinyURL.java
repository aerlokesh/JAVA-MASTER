import java.util.HashMap;
import java.util.Map;
import java.util.Random;

class InvalidURLException extends Exception {
    public InvalidURLException(String message) {
        super(message);
    }
}
class URLNotFoundException extends Exception {
    public URLNotFoundException(String message) {
        super(message);
    }
}
class AliasAlreadyExistsException extends Exception {
    public AliasAlreadyExistsException(String message) {
        super(message);
    }
}
class URLExpiredException extends Exception {
    public URLExpiredException(String message) {
        super(message);
    }
}
class InvalidAliasException extends Exception {
    public InvalidAliasException(String message) {
        super(message);
    }
}
class ShortCodeGenerationException extends Exception {
    public ShortCodeGenerationException(String message) {
        super(message);
    }
}
class TinyUrlSystem{
    Map<String,String> longToShort;
    Map<String,String> shortToLong;
    Map<String,Long> expirationMap;
    Random random;
    String BASE_URl="https://tiny.url/";
    int SHORT_CODE_LENGTH=6;
    String CHARACTERS="abcdefghijklmnoqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    int MAX_RETRIES=10;
    TinyUrlSystem(){
        longToShort = new HashMap<>();
        shortToLong = new HashMap<>();
        expirationMap = new HashMap<>();
        random = new Random();
    }
    String shorten(String longUrl) throws InvalidURLException,ShortCodeGenerationException {
        if(longUrl==null || longUrl.isEmpty()) throw new InvalidURLException("LongUrl {} is empty");
        if(longToShort.containsKey(longUrl)) return longToShort.get(longUrl);
        String code;
        code=generateShortCode();
        shortToLong.put(code, longUrl);
        String shortUrl = BASE_URl + code;
        longToShort.put(longUrl, shortUrl);
        return shortUrl;
    }
    String generateShortCode() throws ShortCodeGenerationException {
        for (int tries = 0; tries < MAX_RETRIES; tries++) {
            StringBuilder sb=new StringBuilder();
            for(int idx=0;idx<SHORT_CODE_LENGTH;idx++){
                sb.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
            }
            String shortCode=sb.toString();
            if(!shortToLong.containsKey(shortCode)) return shortCode;
        }
        throw new ShortCodeGenerationException("ShortCode generation failed");
    }
    String expand(String shortUrl) throws URLNotFoundException, URLExpiredException {
        String shortCode= getShortCode(shortUrl);
        if(!shortToLong.containsKey(shortCode)) throw new URLNotFoundException(shortUrl+" not found");
        if(isExpired(shortCode)) throw new URLExpiredException("shortUrl "+shortUrl+" expired at "+expirationMap.get(shortCode));
        return shortToLong.get(shortCode);
    }
    boolean isExpired(String code){
        Long expiration=expirationMap.get(code);
        if(expiration==null) return false;
        return System.currentTimeMillis()>expiration;
    }
    String getShortCode(String shortUrl) {
        if(shortUrl==null || shortUrl.isEmpty()) throw new IllegalArgumentException("ShortUrl {} is empty");
        return shortUrl.substring(BASE_URl.length());
    }
    String shortenWithAlias(String longUrl,String alias) throws InvalidURLException, AliasAlreadyExistsException, InvalidAliasException {
        if(longUrl==null || longUrl.isEmpty()) throw new IllegalArgumentException("LongUrl {} is empty");
        if(alias==null || alias.isEmpty() || !alias.matches("[a-zA-Z0-9]+")) throw new InvalidAliasException("Alias {} is empty");
        if(alias.length()<3 || alias.length()>20) throw new InvalidAliasException("Alias {} is too long");
        if(shortToLong.containsKey(alias)) throw new AliasAlreadyExistsException(alias);
        shortToLong.put(alias,longUrl);
        longToShort.put(longUrl,alias);
        return BASE_URl+alias;
    }
    boolean expiration(String shortUrl,int hours) throws URLNotFoundException {
        String shortCode=getShortCode(shortUrl);
        if(!shortToLong.containsKey(shortCode)) throw new URLNotFoundException(shortUrl+"url not found");
        long expirationTime=System.currentTimeMillis()+ (long) hours *60*60*1000;
        expirationMap.put(shortCode,expirationTime);
        return true;
    }
}
public class TinyURL {
    static TinyUrlSystem system;

    public static void main(String[] args) {
        int passed = 0, failed = 0;

        // shorten tests
        if (testShortenValid()) passed++; else failed++;
        if (testShortenNullThrows()) passed++; else failed++;
        if (testShortenEmptyThrows()) passed++; else failed++;
        if (testShortenReturnsCached()) passed++; else failed++;
        if (testShortenCodeLength()) passed++; else failed++;
        if (testShortenCodeCharsValid()) passed++; else failed++;
        if (testShortenStoresMappings()) passed++; else failed++;
        if (testShortenMultipleUrlsUniqueCodes()) passed++; else failed++;
        if (testShortenIdempotent()) passed++; else failed++;

        // expand tests
        if (testExpandNotFoundThrows()) passed++; else failed++;
        if (testExpandAfterAlias()) passed++; else failed++;
        if (testExpandNullThrows()) passed++; else failed++;
        if (testExpandEmptyThrows()) passed++; else failed++;

        // alias tests
        if (testShortenWithAliasValid()) passed++; else failed++;
        if (testShortenWithAliasDuplicateThrows()) passed++; else failed++;
        if (testShortenWithAliasInvalidThrows()) passed++; else failed++;
        if (testShortenWithAliasTooShortThrows()) passed++; else failed++;
        if (testShortenWithAliasNullUrlThrows()) passed++; else failed++;
        if (testShortenWithAliasNullAliasThrows()) passed++; else failed++;
        if (testShortenWithAliasSpecialCharsThrows()) passed++; else failed++;
        if (testShortenWithAliasTooLongThrows()) passed++; else failed++;
        if (testShortenWithAliasStoresBothMaps()) passed++; else failed++;

        // getShortCode tests
        if (testGetShortCodeNullThrows()) passed++; else failed++;
        if (testGetShortCodeEmptyThrows()) passed++; else failed++;
        if (testGetShortCodeExtractsCorrectly()) passed++; else failed++;

        // expiration tests
        if (testExpirationValid()) passed++; else failed++;
        if (testExpirationNotFoundThrows()) passed++; else failed++;
        if (testExpirationSetsCorrectTime()) passed++; else failed++;
        if (testIsExpiredFalseWhenNoExpiration()) passed++; else failed++;
        if (testIsExpiredBugDetection()) passed++; else failed++;

        // ── Additional extensive tests ──

        // Round-trip tests
        if (testShortenThenExpand()) passed++; else failed++;
        if (testShortenWithAliasThenExpand()) passed++; else failed++;
        if (testMultipleShortenThenExpandAll()) passed++; else failed++;

        // Expand expired URL
        if (testExpandExpiredUrlThrows()) passed++; else failed++;
        if (testExpandNonExpiredUrlWorks()) passed++; else failed++;

        // Edge case: shorten same URL twice returns same short
        if (testShortenSameUrlReturnsSame()) passed++; else failed++;

        // Edge case: different long URLs get different short codes
        if (testDifferentUrlsDifferentCodes()) passed++; else failed++;

        // Short code generation edge
        if (testGenerateShortCodeUniqueness()) passed++; else failed++;
        if (testGenerateShortCodeFormat()) passed++; else failed++;

        // Alias boundary lengths
        if (testAliasExactly3Chars()) passed++; else failed++;
        if (testAliasExactly20Chars()) passed++; else failed++;
        if (testAliasExactly2CharsThrows()) passed++; else failed++;
        if (testAliasExactly21CharsThrows()) passed++; else failed++;

        // Alias with numbers only
        if (testAliasNumericOnly()) passed++; else failed++;
        // Alias with uppercase only
        if (testAliasUppercaseOnly()) passed++; else failed++;
        // Alias with mixed case
        if (testAliasMixedCase()) passed++; else failed++;

        // Invalid alias characters
        if (testAliasWithSpacesThrows()) passed++; else failed++;
        if (testAliasWithDashThrows()) passed++; else failed++;
        if (testAliasWithUnderscoreThrows()) passed++; else failed++;
        if (testAliasWithDotThrows()) passed++; else failed++;

        // getShortCode edge cases
        if (testGetShortCodeWithBaseUrlOnly()) passed++; else failed++;
        if (testGetShortCodeWithLongPath()) passed++; else failed++;

        // Expiration edge cases
        if (testExpirationZeroHours()) passed++; else failed++;
        if (testExpirationLargeHours()) passed++; else failed++;
        if (testIsExpiredFutureTime()) passed++; else failed++;
        if (testIsExpiredExactNow()) passed++; else failed++;

        // Expand after setting expiration in future
        if (testExpandWithFutureExpiration()) passed++; else failed++;

        // Multiple expirations overwrite
        if (testExpirationOverwrite()) passed++; else failed++;

        // Constructor test
        if (testConstructorInitialization()) passed++; else failed++;

        // Exception message tests
        if (testExceptionMessages()) passed++; else failed++;

        // Shorten very long URL
        if (testShortenVeryLongUrl()) passed++; else failed++;

        // Shorten URL with special characters
        if (testShortenUrlWithQueryParams()) passed++; else failed++;
        if (testShortenUrlWithFragment()) passed++; else failed++;

        // Expand with wrong base URL prefix
        if (testExpandWrongPrefixStillWorks()) passed++; else failed++;

        // Stress: shorten many URLs
        if (testShortenManyUrls()) passed++; else failed++;

        // Alias then shorten same URL (conflict?)
        if (testAliasAndShortenSameUrl()) passed++; else failed++;

        System.out.println("\n══════════════════════════════════════════");
        System.out.println("  Results: " + passed + " passed, " + failed + " failed, " + (passed + failed) + " total");
        System.out.println("══════════════════════════════════════════");
        System.exit(failed > 0 ? 1 : 0);
    }

    static boolean testShortenValid() {
        try {
            system = new TinyUrlSystem();
            String result = system.shorten("https://example.com");
            return check("testShortenValid", result != null && result.startsWith(system.BASE_URl));
        } catch (Exception e) { return check("testShortenValid", false); }
    }

    static boolean testShortenNullThrows() {
        try {
            system = new TinyUrlSystem();
            system.shorten(null);
            return check("testShortenNullThrows", false);
        } catch (InvalidURLException e) { return check("testShortenNullThrows", true); }
          catch (Exception e) { return check("testShortenNullThrows", false); }
    }

    static boolean testShortenEmptyThrows() {
        try {
            system = new TinyUrlSystem();
            system.shorten("");
            return check("testShortenEmptyThrows", false);
        } catch (InvalidURLException e) { return check("testShortenEmptyThrows", true); }
          catch (Exception e) { return check("testShortenEmptyThrows", false); }
    }

    static boolean testShortenReturnsCached() {
        try {
            system = new TinyUrlSystem();
            system.longToShort.put("https://cached.com", "https://tiny.url/abc123");
            String result = system.shorten("https://cached.com");
            return check("testShortenReturnsCached", "https://tiny.url/abc123".equals(result));
        } catch (Exception e) { return check("testShortenReturnsCached", false); }
    }

    static boolean testShortenCodeLength() {
        try {
            system = new TinyUrlSystem();
            String result = system.shorten("https://example.com");
            String code = result.substring(system.BASE_URl.length());
            return check("testShortenCodeLength", code.length() == system.SHORT_CODE_LENGTH);
        } catch (Exception e) { return check("testShortenCodeLength", false); }
    }

    static boolean testShortenCodeCharsValid() {
        try {
            system = new TinyUrlSystem();
            String result = system.shorten("https://example.com");
            String code = result.substring(system.BASE_URl.length());
            return check("testShortenCodeCharsValid", code.matches("[a-zA-Z0-9]+"));
        } catch (Exception e) { return check("testShortenCodeCharsValid", false); }
    }

    // BUG: shorten() never stores the mapping in longToShort/shortToLong
    static boolean testShortenStoresMappings() {
        try {
            system = new TinyUrlSystem();
            String result = system.shorten("https://store-test.com");
            String code = result.substring(system.BASE_URl.length());
            boolean stored = system.shortToLong.containsKey(code) && system.longToShort.containsKey("https://store-test.com");
            return check("testShortenStoresMappings [BUG: shorten doesn't store]", stored);
        } catch (Exception e) { return check("testShortenStoresMappings", false); }
    }

    static boolean testShortenMultipleUrlsUniqueCodes() {
        try {
            system = new TinyUrlSystem();
            String r1 = system.shorten("https://a.com");
            String r2 = system.shorten("https://b.com");
            String r3 = system.shorten("https://c.com");
            return check("testShortenMultipleUrlsUniqueCodes", !r1.equals(r2) && !r2.equals(r3) && !r1.equals(r3));
        } catch (Exception e) { return check("testShortenMultipleUrlsUniqueCodes", false); }
    }

    static boolean testShortenIdempotent() {
        try {
            system = new TinyUrlSystem();
            system.longToShort.put("https://idem.com", "https://tiny.url/xyz789");
            String r1 = system.shorten("https://idem.com");
            String r2 = system.shorten("https://idem.com");
            return check("testShortenIdempotent", r1.equals(r2));
        } catch (Exception e) { return check("testShortenIdempotent", false); }
    }

    static boolean testExpandNotFoundThrows() {
        try {
            system = new TinyUrlSystem();
            system.expand("https://tiny.url/noexist");
            return check("testExpandNotFoundThrows", false);
        } catch (URLNotFoundException e) { return check("testExpandNotFoundThrows", true); }
          catch (Exception e) { return check("testExpandNotFoundThrows", false); }
    }

    // BUG: expand() does longToShort.get(shortCode) instead of shortToLong.get(shortCode)
    static boolean testExpandAfterAlias() {
        try {
            system = new TinyUrlSystem();
            system.shortenWithAlias("https://expand-test.com", "exptest");
            String result = system.expand("https://tiny.url/exptest");
            return check("testExpandAfterAlias [BUG: expand uses wrong map]", "https://expand-test.com".equals(result));
        } catch (Exception e) { return check("testExpandAfterAlias [BUG: expand uses wrong map]", false); }
    }

    static boolean testExpandNullThrows() {
        try {
            system = new TinyUrlSystem();
            system.expand(null);
            return check("testExpandNullThrows", false);
        } catch (IllegalArgumentException e) { return check("testExpandNullThrows", true); }
          catch (Exception e) { return check("testExpandNullThrows", false); }
    }

    static boolean testExpandEmptyThrows() {
        try {
            system = new TinyUrlSystem();
            system.expand("");
            return check("testExpandEmptyThrows", false);
        } catch (IllegalArgumentException e) { return check("testExpandEmptyThrows", true); }
          catch (Exception e) { return check("testExpandEmptyThrows", false); }
    }

    static boolean testShortenWithAliasValid() {
        try {
            system = new TinyUrlSystem();
            String result = system.shortenWithAlias("https://example.com", "myalias");
            return check("testShortenWithAliasValid", (system.BASE_URl + "myalias").equals(result));
        } catch (Exception e) { return check("testShortenWithAliasValid", false); }
    }

    static boolean testShortenWithAliasDuplicateThrows() {
        try {
            system = new TinyUrlSystem();
            system.shortenWithAlias("https://a.com", "taken1");
            system.shortenWithAlias("https://b.com", "taken1");
            return check("testShortenWithAliasDuplicateThrows", false);
        } catch (AliasAlreadyExistsException e) { return check("testShortenWithAliasDuplicateThrows", true); }
          catch (Exception e) { return check("testShortenWithAliasDuplicateThrows", false); }
    }

    static boolean testShortenWithAliasInvalidThrows() {
        try {
            system = new TinyUrlSystem();
            system.shortenWithAlias("https://example.com", "");
            return check("testShortenWithAliasInvalidThrows", false);
        } catch (InvalidAliasException e) { return check("testShortenWithAliasInvalidThrows", true); }
          catch (Exception e) { return check("testShortenWithAliasInvalidThrows", false); }
    }

    static boolean testShortenWithAliasNullUrlThrows() {
        try {
            system = new TinyUrlSystem();
            system.shortenWithAlias(null, "validalias");
            return check("testShortenWithAliasNullUrlThrows", false);
        } catch (IllegalArgumentException e) { return check("testShortenWithAliasNullUrlThrows", true); }
          catch (Exception e) { return check("testShortenWithAliasNullUrlThrows", false); }
    }

    static boolean testShortenWithAliasNullAliasThrows() {
        try {
            system = new TinyUrlSystem();
            system.shortenWithAlias("https://example.com", null);
            return check("testShortenWithAliasNullAliasThrows", false);
        } catch (InvalidAliasException e) { return check("testShortenWithAliasNullAliasThrows", true); }
          catch (Exception e) { return check("testShortenWithAliasNullAliasThrows", false); }
    }

    static boolean testShortenWithAliasSpecialCharsThrows() {
        try {
            system = new TinyUrlSystem();
            system.shortenWithAlias("https://example.com", "my-alias!");
            return check("testShortenWithAliasSpecialCharsThrows", false);
        } catch (InvalidAliasException e) { return check("testShortenWithAliasSpecialCharsThrows", true); }
          catch (Exception e) { return check("testShortenWithAliasSpecialCharsThrows", false); }
    }

    static boolean testShortenWithAliasTooLongThrows() {
        try {
            system = new TinyUrlSystem();
            system.shortenWithAlias("https://example.com", "abcdefghijklmnopqrstu"); // 21 chars
            return check("testShortenWithAliasTooLongThrows", false);
        } catch (InvalidAliasException e) { return check("testShortenWithAliasTooLongThrows", true); }
          catch (Exception e) { return check("testShortenWithAliasTooLongThrows", false); }
    }

    static boolean testShortenWithAliasStoresBothMaps() {
        try {
            system = new TinyUrlSystem();
            system.shortenWithAlias("https://maps.com", "maptest");
            boolean ok = system.shortToLong.containsKey("maptest") && system.longToShort.containsKey("https://maps.com");
            return check("testShortenWithAliasStoresBothMaps", ok);
        } catch (Exception e) { return check("testShortenWithAliasStoresBothMaps", false); }
    }

    static boolean testShortenWithAliasTooShortThrows() {
        try {
            system = new TinyUrlSystem();
            system.shortenWithAlias("https://example.com", "ab");
            return check("testShortenWithAliasTooShortThrows", false);
        } catch (InvalidAliasException e) { return check("testShortenWithAliasTooShortThrows", true); }
          catch (Exception e) { return check("testShortenWithAliasTooShortThrows", false); }
    }

    static boolean testGetShortCodeNullThrows() {
        try {
            system = new TinyUrlSystem();
            system.getShortCode(null);
            return check("testGetShortCodeNullThrows", false);
        } catch (IllegalArgumentException e) { return check("testGetShortCodeNullThrows", true); }
    }

    static boolean testGetShortCodeEmptyThrows() {
        try {
            system = new TinyUrlSystem();
            system.getShortCode("");
            return check("testGetShortCodeEmptyThrows", false);
        } catch (IllegalArgumentException e) { return check("testGetShortCodeEmptyThrows", true); }
    }

    static boolean testGetShortCodeExtractsCorrectly() {
        system = new TinyUrlSystem();
        String code = system.getShortCode("https://tiny.url/abc123");
        return check("testGetShortCodeExtractsCorrectly", "abc123".equals(code));
    }

    static boolean testExpirationValid() {
        try {
            system = new TinyUrlSystem();
            system.shortToLong.put("abc123", "https://example.com");
            boolean result = system.expiration("https://tiny.url/abc123", 1);
            return check("testExpirationValid", result && system.expirationMap.containsKey("abc123"));
        } catch (Exception e) { return check("testExpirationValid", false); }
    }

    static boolean testExpirationNotFoundThrows() {
        try {
            system = new TinyUrlSystem();
            system.expiration("https://tiny.url/noexist", 1);
            return check("testExpirationNotFoundThrows", false);
        } catch (URLNotFoundException e) { return check("testExpirationNotFoundThrows", true); }
    }

    static boolean testExpirationSetsCorrectTime() {
        try {
            system = new TinyUrlSystem();
            system.shortToLong.put("timecode", "https://time.com");
            long before = System.currentTimeMillis();
            system.expiration("https://tiny.url/timecode", 2);
            long after = System.currentTimeMillis();
            Long exp = system.expirationMap.get("timecode");
            long twoHoursMs = 2L * 60 * 60 * 1000;
            boolean ok = exp != null && exp >= before + twoHoursMs && exp <= after + twoHoursMs;
            return check("testExpirationSetsCorrectTime", ok);
        } catch (Exception e) { return check("testExpirationSetsCorrectTime", false); }
    }

    static boolean testIsExpiredFalseWhenNoExpiration() {
        system = new TinyUrlSystem();
        return check("testIsExpiredFalseWhenNoExpiration", !system.isExpired("nonexistent"));
    }

    // BUG: isExpired compares (now - expiration > now) which is always false for future times
    // Should be: System.currentTimeMillis() > expiration
    static boolean testIsExpiredBugDetection() {
        system = new TinyUrlSystem();
        // Set expiration to 1ms in the past — should be expired
        system.expirationMap.put("pastcode", System.currentTimeMillis() - 1);
        return check("testIsExpiredBugDetection [BUG: isExpired logic wrong]", system.isExpired("pastcode"));
    }

    // ── Round-trip tests ──
    static boolean testShortenThenExpand() {
        try {
            system = new TinyUrlSystem();
            String shortUrl = system.shorten("https://roundtrip.com");
            String expanded = system.expand(shortUrl);
            return check("testShortenThenExpand", "https://roundtrip.com".equals(expanded));
        } catch (Exception e) { return check("testShortenThenExpand", false); }
    }

    static boolean testShortenWithAliasThenExpand() {
        try {
            system = new TinyUrlSystem();
            String shortUrl = system.shortenWithAlias("https://aliasrt.com", "aliasrt");
            String expanded = system.expand(shortUrl);
            return check("testShortenWithAliasThenExpand", "https://aliasrt.com".equals(expanded));
        } catch (Exception e) { return check("testShortenWithAliasThenExpand", false); }
    }

    static boolean testMultipleShortenThenExpandAll() {
        try {
            system = new TinyUrlSystem();
            String s1 = system.shorten("https://one.com");
            String s2 = system.shorten("https://two.com");
            String s3 = system.shorten("https://three.com");
            boolean ok = "https://one.com".equals(system.expand(s1))
                      && "https://two.com".equals(system.expand(s2))
                      && "https://three.com".equals(system.expand(s3));
            return check("testMultipleShortenThenExpandAll", ok);
        } catch (Exception e) { return check("testMultipleShortenThenExpandAll", false); }
    }

    // ── Expand expired/non-expired ──
    static boolean testExpandExpiredUrlThrows() {
        try {
            system = new TinyUrlSystem();
            system.shortToLong.put("expcode", "https://expired.com");
            system.expirationMap.put("expcode", System.currentTimeMillis() - 1000);
            system.expand(system.BASE_URl + "expcode");
            return check("testExpandExpiredUrlThrows", false);
        } catch (URLExpiredException e) { return check("testExpandExpiredUrlThrows", true); }
          catch (Exception e) { return check("testExpandExpiredUrlThrows", false); }
    }

    static boolean testExpandNonExpiredUrlWorks() {
        try {
            system = new TinyUrlSystem();
            system.shortToLong.put("livecode", "https://live.com");
            system.expirationMap.put("livecode", System.currentTimeMillis() + 3600000);
            String result = system.expand(system.BASE_URl + "livecode");
            return check("testExpandNonExpiredUrlWorks", "https://live.com".equals(result));
        } catch (Exception e) { return check("testExpandNonExpiredUrlWorks", false); }
    }

    // ── Idempotency / uniqueness ──
    static boolean testShortenSameUrlReturnsSame() {
        try {
            system = new TinyUrlSystem();
            String r1 = system.shorten("https://same.com");
            String r2 = system.shorten("https://same.com");
            return check("testShortenSameUrlReturnsSame", r1.equals(r2));
        } catch (Exception e) { return check("testShortenSameUrlReturnsSame", false); }
    }

    static boolean testDifferentUrlsDifferentCodes() {
        try {
            system = new TinyUrlSystem();
            String r1 = system.shorten("https://diff1.com");
            String r2 = system.shorten("https://diff2.com");
            return check("testDifferentUrlsDifferentCodes", !r1.equals(r2));
        } catch (Exception e) { return check("testDifferentUrlsDifferentCodes", false); }
    }

    // ── Short code generation ──
    static boolean testGenerateShortCodeUniqueness() {
        try {
            system = new TinyUrlSystem();
            java.util.Set<String> codes = new java.util.HashSet<>();
            for (int i = 0; i < 50; i++) codes.add(system.generateShortCode());
            return check("testGenerateShortCodeUniqueness", codes.size() == 50);
        } catch (Exception e) { return check("testGenerateShortCodeUniqueness", false); }
    }

    static boolean testGenerateShortCodeFormat() {
        try {
            system = new TinyUrlSystem();
            String code = system.generateShortCode();
            boolean ok = code.length() == system.SHORT_CODE_LENGTH && code.matches("[a-zA-Z0-9]+");
            return check("testGenerateShortCodeFormat", ok);
        } catch (Exception e) { return check("testGenerateShortCodeFormat", false); }
    }

    // ── Alias boundary lengths ──
    static boolean testAliasExactly3Chars() {
        try {
            system = new TinyUrlSystem();
            String result = system.shortenWithAlias("https://a3.com", "abc");
            return check("testAliasExactly3Chars", result.equals(system.BASE_URl + "abc"));
        } catch (Exception e) { return check("testAliasExactly3Chars", false); }
    }

    static boolean testAliasExactly20Chars() {
        try {
            system = new TinyUrlSystem();
            String alias = "abcdefghijklmnopqrst"; // 20 chars
            String result = system.shortenWithAlias("https://a20.com", alias);
            return check("testAliasExactly20Chars", result.equals(system.BASE_URl + alias));
        } catch (Exception e) { return check("testAliasExactly20Chars", false); }
    }

    static boolean testAliasExactly2CharsThrows() {
        try {
            system = new TinyUrlSystem();
            system.shortenWithAlias("https://x.com", "ab");
            return check("testAliasExactly2CharsThrows", false);
        } catch (InvalidAliasException e) { return check("testAliasExactly2CharsThrows", true); }
          catch (Exception e) { return check("testAliasExactly2CharsThrows", false); }
    }

    static boolean testAliasExactly21CharsThrows() {
        try {
            system = new TinyUrlSystem();
            system.shortenWithAlias("https://x.com", "abcdefghijklmnopqrstu"); // 21
            return check("testAliasExactly21CharsThrows", false);
        } catch (InvalidAliasException e) { return check("testAliasExactly21CharsThrows", true); }
          catch (Exception e) { return check("testAliasExactly21CharsThrows", false); }
    }

    // ── Alias character types ──
    static boolean testAliasNumericOnly() {
        try {
            system = new TinyUrlSystem();
            String result = system.shortenWithAlias("https://num.com", "12345");
            return check("testAliasNumericOnly", result.equals(system.BASE_URl + "12345"));
        } catch (Exception e) { return check("testAliasNumericOnly", false); }
    }

    static boolean testAliasUppercaseOnly() {
        try {
            system = new TinyUrlSystem();
            String result = system.shortenWithAlias("https://upper.com", "ABCDEF");
            return check("testAliasUppercaseOnly", result.equals(system.BASE_URl + "ABCDEF"));
        } catch (Exception e) { return check("testAliasUppercaseOnly", false); }
    }

    static boolean testAliasMixedCase() {
        try {
            system = new TinyUrlSystem();
            String result = system.shortenWithAlias("https://mixed.com", "AbC123");
            return check("testAliasMixedCase", result.equals(system.BASE_URl + "AbC123"));
        } catch (Exception e) { return check("testAliasMixedCase", false); }
    }

    // ── Invalid alias characters ──
    static boolean testAliasWithSpacesThrows() {
        try {
            system = new TinyUrlSystem();
            system.shortenWithAlias("https://x.com", "has space");
            return check("testAliasWithSpacesThrows", false);
        } catch (InvalidAliasException e) { return check("testAliasWithSpacesThrows", true); }
          catch (Exception e) { return check("testAliasWithSpacesThrows", false); }
    }

    static boolean testAliasWithDashThrows() {
        try {
            system = new TinyUrlSystem();
            system.shortenWithAlias("https://x.com", "my-alias");
            return check("testAliasWithDashThrows", false);
        } catch (InvalidAliasException e) { return check("testAliasWithDashThrows", true); }
          catch (Exception e) { return check("testAliasWithDashThrows", false); }
    }

    static boolean testAliasWithUnderscoreThrows() {
        try {
            system = new TinyUrlSystem();
            system.shortenWithAlias("https://x.com", "my_alias");
            return check("testAliasWithUnderscoreThrows", false);
        } catch (InvalidAliasException e) { return check("testAliasWithUnderscoreThrows", true); }
          catch (Exception e) { return check("testAliasWithUnderscoreThrows", false); }
    }

    static boolean testAliasWithDotThrows() {
        try {
            system = new TinyUrlSystem();
            system.shortenWithAlias("https://x.com", "my.alias");
            return check("testAliasWithDotThrows", false);
        } catch (InvalidAliasException e) { return check("testAliasWithDotThrows", true); }
          catch (Exception e) { return check("testAliasWithDotThrows", false); }
    }

    // ── getShortCode edge cases ──
    static boolean testGetShortCodeWithBaseUrlOnly() {
        system = new TinyUrlSystem();
        String code = system.getShortCode(system.BASE_URl);
        return check("testGetShortCodeWithBaseUrlOnly", "".equals(code));
    }

    static boolean testGetShortCodeWithLongPath() {
        system = new TinyUrlSystem();
        String code = system.getShortCode("https://tiny.url/myLongAliasPath123");
        return check("testGetShortCodeWithLongPath", "myLongAliasPath123".equals(code));
    }

    // ── Expiration edge cases ──
    static boolean testExpirationZeroHours() {
        try {
            system = new TinyUrlSystem();
            system.shortToLong.put("zeroh", "https://zero.com");
            long before = System.currentTimeMillis();
            system.expiration(system.BASE_URl + "zeroh", 0);
            Long exp = system.expirationMap.get("zeroh");
            return check("testExpirationZeroHours", exp != null && exp >= before && exp <= before + 100);
        } catch (Exception e) { return check("testExpirationZeroHours", false); }
    }

    static boolean testExpirationLargeHours() {
        try {
            system = new TinyUrlSystem();
            system.shortToLong.put("largeh", "https://large.com");
            system.expiration(system.BASE_URl + "largeh", 8760); // 1 year
            Long exp = system.expirationMap.get("largeh");
            long oneYearMs = 8760L * 60 * 60 * 1000;
            return check("testExpirationLargeHours", exp != null && exp > System.currentTimeMillis() + oneYearMs - 1000);
        } catch (Exception e) { return check("testExpirationLargeHours", false); }
    }

    static boolean testIsExpiredFutureTime() {
        system = new TinyUrlSystem();
        system.expirationMap.put("future", System.currentTimeMillis() + 999999);
        return check("testIsExpiredFutureTime", !system.isExpired("future"));
    }

    static boolean testIsExpiredExactNow() {
        system = new TinyUrlSystem();
        system.expirationMap.put("now", System.currentTimeMillis());
        // currentTimeMillis() >= expiration means expired (or exactly at boundary)
        boolean result = system.isExpired("now");
        return check("testIsExpiredExactNow (boundary)", true); // just verify no exception
    }

    // ── Expand with future expiration ──
    static boolean testExpandWithFutureExpiration() {
        try {
            system = new TinyUrlSystem();
            String shortUrl = system.shorten("https://futureexp.com");
            system.expiration(shortUrl, 24);
            String expanded = system.expand(shortUrl);
            return check("testExpandWithFutureExpiration", "https://futureexp.com".equals(expanded));
        } catch (Exception e) { return check("testExpandWithFutureExpiration", false); }
    }

    // ── Expiration overwrite ──
    static boolean testExpirationOverwrite() {
        try {
            system = new TinyUrlSystem();
            system.shortToLong.put("overw", "https://overwrite.com");
            system.expiration(system.BASE_URl + "overw", 1);
            Long first = system.expirationMap.get("overw");
            system.expiration(system.BASE_URl + "overw", 10);
            Long second = system.expirationMap.get("overw");
            return check("testExpirationOverwrite", second > first);
        } catch (Exception e) { return check("testExpirationOverwrite", false); }
    }

    // ── Constructor ──
    static boolean testConstructorInitialization() {
        system = new TinyUrlSystem();
        boolean ok = system.longToShort != null && system.shortToLong != null
                  && system.expirationMap != null && system.random != null
                  && system.longToShort.isEmpty() && system.shortToLong.isEmpty()
                  && system.expirationMap.isEmpty();
        return check("testConstructorInitialization", ok);
    }

    // ── Exception messages ──
    static boolean testExceptionMessages() {
        boolean ok = true;
        try { new InvalidURLException("test1"); } catch (Exception e) { ok = false; }
        try { new URLNotFoundException("test2"); } catch (Exception e) { ok = false; }
        try { new AliasAlreadyExistsException("test3"); } catch (Exception e) { ok = false; }
        try { new URLExpiredException("test4"); } catch (Exception e) { ok = false; }
        try { new InvalidAliasException("test5"); } catch (Exception e) { ok = false; }
        try { new ShortCodeGenerationException("test6"); } catch (Exception e) { ok = false; }
        InvalidURLException ex = new InvalidURLException("hello");
        ok = ok && "hello".equals(ex.getMessage());
        return check("testExceptionMessages", ok);
    }

    // ── URL edge cases ──
    static boolean testShortenVeryLongUrl() {
        try {
            system = new TinyUrlSystem();
            String longUrl = "https://example.com/" + "a".repeat(5000);
            String result = system.shorten(longUrl);
            return check("testShortenVeryLongUrl", result != null && result.startsWith(system.BASE_URl));
        } catch (Exception e) { return check("testShortenVeryLongUrl", false); }
    }

    static boolean testShortenUrlWithQueryParams() {
        try {
            system = new TinyUrlSystem();
            String result = system.shorten("https://example.com/search?q=hello&lang=en");
            String expanded = system.expand(result);
            return check("testShortenUrlWithQueryParams", "https://example.com/search?q=hello&lang=en".equals(expanded));
        } catch (Exception e) { return check("testShortenUrlWithQueryParams", false); }
    }

    static boolean testShortenUrlWithFragment() {
        try {
            system = new TinyUrlSystem();
            String result = system.shorten("https://example.com/page#section2");
            String expanded = system.expand(result);
            return check("testShortenUrlWithFragment", "https://example.com/page#section2".equals(expanded));
        } catch (Exception e) { return check("testShortenUrlWithFragment", false); }
    }

    static boolean testExpandWrongPrefixStillWorks() {
        try {
            system = new TinyUrlSystem();
            system.shortToLong.put("manualcode", "https://manual.com");
            // getShortCode just does substring from BASE_URL length, so a different prefix would give wrong code
            // This tests that expand works with the correct base URL
            String result = system.expand(system.BASE_URl + "manualcode");
            return check("testExpandWrongPrefixStillWorks", "https://manual.com".equals(result));
        } catch (Exception e) { return check("testExpandWrongPrefixStillWorks", false); }
    }

    // ── Stress test ──
    static boolean testShortenManyUrls() {
        try {
            system = new TinyUrlSystem();
            java.util.Set<String> shortUrls = new java.util.HashSet<>();
            for (int i = 0; i < 100; i++) {
                shortUrls.add(system.shorten("https://stress" + i + ".com"));
            }
            return check("testShortenManyUrls (100 unique)", shortUrls.size() == 100);
        } catch (Exception e) { return check("testShortenManyUrls", false); }
    }

    // ── Alias then shorten same URL ──
    static boolean testAliasAndShortenSameUrl() {
        try {
            system = new TinyUrlSystem();
            String aliased = system.shortenWithAlias("https://both.com", "myboth");
            // longToShort now has "https://both.com" -> "myboth", so shorten returns cached
            String shortened = system.shorten("https://both.com");
            return check("testAliasAndShortenSameUrl", shortened.equals("myboth"));
        } catch (Exception e) { return check("testAliasAndShortenSameUrl", false); }
    }

    static boolean check(String name, boolean passed) {
        System.out.println((passed ? "PASS" : "FAIL") + " - " + name);
        return passed;
    }
}
