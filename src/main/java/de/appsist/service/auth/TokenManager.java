package de.appsist.service.auth;

import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.jwk.RsaJwkGenerator;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.lang.JoseException;
import org.vertx.java.core.json.JsonObject;

/**
 * Manager for access tokens.
 * @author simon.schwantzer(at)im-c.de
 */
public class TokenManager {
	private static final String ISSUER = "appsist:service:auth";
	
	private final RsaJsonWebKey rsaJsonWebKey;
	
	/**
	 * Initializes the token manager.
	 * @throws RuntimeException Failed to initialize key for token generation.
	 */
	public TokenManager() throws RuntimeException {
		try {
			rsaJsonWebKey = RsaJwkGenerator.generateJwk(2048);
			rsaJsonWebKey.setKeyId("sas");
		} catch (JoseException e) {
			throw new RuntimeException("Failed to initialize key.", e);
		}
	}
	
	/**
	 * Generates a JSON Web Token for the given subject.
	 * @param subject Service or user identifier to be used as subject for the token.
	 * @return Java web token as string.
	 */
	public String generateToken(String subject) throws RuntimeException {
		JwtClaims claims = new JwtClaims();
		claims.setIssuer(ISSUER);
		claims.setSubject(subject);
		claims.setExpirationTimeMinutesInTheFuture(10);
		claims.setNotBeforeMinutesInThePast(2);
		claims.setIssuedAtToNow();
		claims.setGeneratedJwtId();
		
		JsonWebSignature jws = new JsonWebSignature();
		jws.setPayload(claims.toJson());
		jws.setKey(rsaJsonWebKey.getPrivateKey());
		jws.setKeyIdHeaderValue(rsaJsonWebKey.getKeyId());
		jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);

		String jwt;
		try {
			jwt = jws.getCompactSerialization();
		} catch (JoseException e) {
			throw new RuntimeException("Failed to serialize the token.", e);
		}
		return jwt;
	}
	
	/**
	 * Validates a token fur the given subject.
	 * @param subject ID of the service or user trying to authenticate with the token. 
	 * @param jwt JSON web token to validate.
	 * @return Claims of the token.
	 * @throws InvalidTokenException The token is invalid.
	 */
	public JsonObject validateToken(String jwt, String subject) throws InvalidTokenException {
		if (jwt == null) {
			throw new InvalidTokenException(jwt, "Token is null.");
		}
		JwtConsumer jwtConsumer = new JwtConsumerBuilder()
			.setRequireExpirationTime()
			.setAllowedClockSkewInSeconds(30)
			.setRequireSubject()
			.setExpectedIssuer(ISSUER)
			.setVerificationKey(rsaJsonWebKey.getKey())
			.build();
		JwtClaims jwtClaims;
		try {
			jwtClaims = jwtConsumer.processToClaims(jwt);
			JsonObject claims = new JsonObject(jwtClaims.toJson());
			if (!jwtClaims.getSubject().equals(subject)) {
				throw new InvalidTokenException(jwt, "Invalid subject: \"" + jwtClaims.getSubject() + "\" found but \"" + subject + "\" expected.");
			}
			return claims;
		} catch (InvalidJwtException | MalformedClaimException e) {
			throw new InvalidTokenException(jwt, e.getMessage(), e);
		}
	}
}
