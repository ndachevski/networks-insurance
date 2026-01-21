import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * RateLimiter.java - Rate limiting to prevent brute force attacks
 */
public class RateLimiter {
    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCKOUT_DURATION = 15 * 60 * 1000; // 15 minutes
    private static final long WINDOW_DURATION = 60 * 1000; // 1 minute
    
    private static class AttemptInfo {
        AtomicInteger attempts = new AtomicInteger(0);
        AtomicLong firstAttempt = new AtomicLong(System.currentTimeMillis());
        AtomicLong lockoutUntil = new AtomicLong(0);
    }
    
    private final Map<String, AttemptInfo> attempts = new ConcurrentHashMap<>();
    
    /**
     * Check if an IP/username is rate limited
     */
    public boolean isRateLimited(String identifier) {
        AttemptInfo info = attempts.get(identifier);
        if (info == null) {
            return false;
        }
        
        long now = System.currentTimeMillis();
        
        // Check if locked out
        if (info.lockoutUntil.get() > now) {
            return true;
        }
        
        // Reset if lockout expired
        if (info.lockoutUntil.get() > 0 && info.lockoutUntil.get() <= now) {
            info.attempts.set(0);
            info.lockoutUntil.set(0);
            info.firstAttempt.set(now);
            return false;
        }
        
        // Reset if window expired
        if (now - info.firstAttempt.get() > WINDOW_DURATION) {
            info.attempts.set(0);
            info.firstAttempt.set(now);
            return false;
        }
        
        return false;
    }
    
    /**
     * Record a failed attempt
     */
    public void recordFailedAttempt(String identifier) {
        AttemptInfo info = attempts.computeIfAbsent(identifier, k -> new AttemptInfo());
        
        long now = System.currentTimeMillis();
        
        // Reset if window expired
        if (now - info.firstAttempt.get() > WINDOW_DURATION) {
            info.attempts.set(0);
            info.firstAttempt.set(now);
        }
        
        int currentAttempts = info.attempts.incrementAndGet();
        
        // Lock out if max attempts reached
        if (currentAttempts >= MAX_ATTEMPTS) {
            info.lockoutUntil.set(now + LOCKOUT_DURATION);
        }
    }
    
    /**
     * Record a successful attempt (reset counter)
     */
    public void recordSuccess(String identifier) {
        attempts.remove(identifier);
    }
    
    /**
     * Get remaining lockout time in seconds
     */
    public long getRemainingLockoutTime(String identifier) {
        AttemptInfo info = attempts.get(identifier);
        if (info == null) {
            return 0;
        }
        
        long remaining = info.lockoutUntil.get() - System.currentTimeMillis();
        return remaining > 0 ? remaining / 1000 : 0;
    }
}
