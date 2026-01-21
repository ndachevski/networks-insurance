// Students: CSY23102, CSY23052, CSY23031

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ratelimiter prevents brute force login attacks by tracking failed attempts per
 * ip/username combination and temporarily locking out after max attempts. lockout
 * window resets after a successful login
 */
public class RateLimiter {
    private static final int MAX_ATTEMPTS = 5;
    // lockout duration to make brute force impractical
    private static final long LOCKOUT_DURATION = 15 * 60 * 1000;
    // attempt window - failed attempts must occur within this time frame to count
    private static final long WINDOW_DURATION = 60 * 1000;
    
    // immutable holder for per-identifier attempt state
    private static class AttemptInfo {
        // atomic counters avoid concurrent modification issues when tracking attempts
        AtomicInteger attempts = new AtomicInteger(0);
        AtomicLong firstAttempt = new AtomicLong(System.currentTimeMillis());
        // 0 means not locked, otherwise timestamp when lockout expires
        AtomicLong lockoutUntil = new AtomicLong(0);
    }
    
    private final Map<String, AttemptInfo> attempts = new ConcurrentHashMap<>();
    
    /**
     * check if an ip/username is currently rate limited (within lockout period)
     */
    public boolean isRateLimited(String identifier) {
        AttemptInfo info = attempts.get(identifier);
        if (info == null) {
            return false;
        }
        
        long now = System.currentTimeMillis();
        
        // if we're still within the lockout period, keep them locked out
        if (info.lockoutUntil.get() > now) {
            return true;
        }
        
        // if lockout period has expired, reset for next cycle
        if (info.lockoutUntil.get() > 0 && info.lockoutUntil.get() <= now) {
            info.attempts.set(0);
            info.lockoutUntil.set(0);
            info.firstAttempt.set(now);
            return false;
        }
        
        // if attempt window expired, reset counter and allow fresh attempts
        if (now - info.firstAttempt.get() > WINDOW_DURATION) {
            info.attempts.set(0);
            info.firstAttempt.set(now);
            return false;
        }
        
        return false;
    }
    
    /**
     * record a failed login attempt. if max attempts exceeded within the window,
     * lock out the identifier for the lockout duration
     */
    public void recordFailedAttempt(String identifier) {
        AttemptInfo info = attempts.computeIfAbsent(identifier, k -> new AttemptInfo());
        
        long now = System.currentTimeMillis();
        
        // reset attempt counter if the window has expired since first attempt
        if (now - info.firstAttempt.get() > WINDOW_DURATION) {
            info.attempts.set(0);
            info.firstAttempt.set(now);
        }
        
        int currentAttempts = info.attempts.incrementAndGet();
        
        // activate lockout if we've hit the limit
        if (currentAttempts >= MAX_ATTEMPTS) {
            info.lockoutUntil.set(now + LOCKOUT_DURATION);
        }
    }
    
    /**
     * record a successful login - clear all tracking data for this identifier
     * so fresh attempt window starts on next failure
     */
    public void recordSuccess(String identifier) {
        attempts.remove(identifier);
    }
    
    /**
     * get remaining lockout time in seconds, or 0 if not locked out
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
