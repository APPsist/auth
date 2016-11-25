package de.appsist.service.auth;

/**
 * Exception thrown if a token is invalid.
 * @author simon.schwantzer(at)im-c.de
 */
public class InvalidTokenException extends Exception {
	private static final long serialVersionUID = 1L;
	
	private final String token;
	
	public InvalidTokenException(String token, String message) {
		super(message);
		this.token = token;
	}
	
	public InvalidTokenException(String token, String message, Throwable e) {
		super(message, e);
		this.token = token;
	}
	
	/**
	 * Returns the invalid token.
	 * @return Token string.
	 */
	public String getInvalidToken() {
		return token;
	}
}
