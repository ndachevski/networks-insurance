import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * SecurityUtils.java - Security utilities for password hashing, validation, and sanitization
 */
public class SecurityUtils {
    private static final int SALT_LENGTH = 16;
    private static final int HASH_ITERATIONS = 10000;
    private static final SecureRandom random = new SecureRandom();
    
    // Input validation patterns
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{3,20}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final int MAX_INPUT_LENGTH = 100;
    
    /**
     * Hash a password using PBKDF2 with salt
     */
    public static String hashPassword(String password) {
        try {
            // Generate salt
            byte[] salt = new byte[SALT_LENGTH];
            random.nextBytes(salt);
            
            // Hash password with salt using PBKDF2
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            byte[] hash = md.digest(password.getBytes(StandardCharsets.UTF_8));
            
            // Apply multiple iterations
            for (int i = 0; i < HASH_ITERATIONS; i++) {
                hash = md.digest(hash);
            }
            
            // Combine salt and hash
            byte[] combined = new byte[salt.length + hash.length];
            System.arraycopy(salt, 0, combined, 0, salt.length);
            System.arraycopy(hash, 0, combined, salt.length, hash.length);
            
            // Return base64 encoded string
            return Base64.getEncoder().encodeToString(combined);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
    
    /**
     * Verify a password against a hash
     */
    public static boolean verifyPassword(String password, String hash) {
        try {
            // Decode the hash
            byte[] combined = Base64.getDecoder().decode(hash);
            
            // Extract salt
            byte[] salt = new byte[SALT_LENGTH];
            System.arraycopy(combined, 0, salt, 0, SALT_LENGTH);
            
            // Hash the provided password with the same salt
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            byte[] passwordHash = md.digest(password.getBytes(StandardCharsets.UTF_8));
            
            // Apply same iterations
            for (int i = 0; i < HASH_ITERATIONS; i++) {
                passwordHash = md.digest(passwordHash);
            }
            
            // Compare with stored hash
            byte[] storedHash = new byte[combined.length - SALT_LENGTH];
            System.arraycopy(combined, SALT_LENGTH, storedHash, 0, storedHash.length);
            
            return MessageDigest.isEqual(passwordHash, storedHash);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Check if a password is plaintext (for migration)
     */
    public static boolean isPlaintext(String passwordHash) {
        // Plaintext passwords are typically short and don't contain base64 characters in this pattern
        // Hashed passwords are longer base64 strings
        return passwordHash.length() < 50 || !passwordHash.matches("^[A-Za-z0-9+/=]+$");
    }
    
    /**
     * Validate and sanitize username
     */
    public static String validateUsername(String username) {
        if (username == null) {
            throw new IllegalArgumentException("Username cannot be null");
        }
        
        username = username.trim();
        
        if (username.isEmpty()) {
            throw new IllegalArgumentException("Username cannot be empty");
        }
        
        if (username.length() > MAX_INPUT_LENGTH) {
            throw new IllegalArgumentException("Username too long (max " + MAX_INPUT_LENGTH + " characters)");
        }
        
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new IllegalArgumentException("Username must be 3-20 characters, alphanumeric and underscores only");
        }
        
        return username;
    }
    
    /**
     * Validate and sanitize password
     */
    public static String validatePassword(String password) {
        if (password == null) {
            throw new IllegalArgumentException("Password cannot be null");
        }
        
        if (password.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters");
        }
        
        if (password.length() > MAX_INPUT_LENGTH) {
            throw new IllegalArgumentException("Password too long (max " + MAX_INPUT_LENGTH + " characters)");
        }
        
        return password;
    }
    
    /**
     * Validate and sanitize email
     */
    public static String validateEmail(String email) {
        if (email == null) {
            throw new IllegalArgumentException("Email cannot be null");
        }
        
        email = email.trim().toLowerCase();
        
        if (email.isEmpty()) {
            throw new IllegalArgumentException("Email cannot be empty");
        }
        
        if (email.length() > MAX_INPUT_LENGTH) {
            throw new IllegalArgumentException("Email too long (max " + MAX_INPUT_LENGTH + " characters)");
        }
        
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("Invalid email format");
        }
        
        return email;
    }
    
    /**
     * Validate and sanitize name
     */
    public static String validateName(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Name cannot be null");
        }
        
        name = name.trim();
        
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Name cannot be empty");
        }
        
        if (name.length() > MAX_INPUT_LENGTH) {
            throw new IllegalArgumentException("Name too long (max " + MAX_INPUT_LENGTH + " characters)");
        }
        
        // Remove potentially dangerous characters
        name = name.replaceAll("[<>\"'&]", "");
        
        return name;
    }
    
    /**
     * Sanitize string to prevent injection attacks
     */
    public static String sanitize(String input) {
        if (input == null) {
            return "";
        }
        
        // Remove control characters and potentially dangerous characters
        return input.replaceAll("[\\x00-\\x1F\\x7F<>\"'&]", "").trim();
    }
}
