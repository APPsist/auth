package de.appsist.service.auth;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import de.appsist.service.auth.connector.MongoDBConnector;
import de.appsist.service.auth.model.User;

/**
 * Manager for users.
 * @author simon.schwantzer(at)im-c.de
 */
public class UserManager {
	private static final Logger logger = LoggerFactory.getLogger(UserManager.class);
	
	/**
	 * Enumeration to define a access scope.
	 * @author simon.schwantzer(at)im-c.de
	 */
	public enum AccessScope {
		/**
		 * Publicly visible.
		 */
		PUBLIC,
		/**
		 * Visible to the user only, without authentication information.
		 */
		RESTRICTED,
		/**
		 * All information.
		 */
		CONFIDENTIAL
	}
	
	private final MongoDBConnector mongo;
	
	public UserManager(MongoDBConnector mongoConnector) {
		this.mongo = mongoConnector;
	}
	
	/**
	 * Returns a list of all registered users.
	 * @param resultHandler Handler for the resulting list.
	 */
	public void getUsers(final AsyncResultHandler<List<User>> resultHandler) {
		final JsonObject matcher = new JsonObject();
		final JsonObject keys = new JsonObject()
			.putNumber("_id_", 0);
		mongo.find("users", matcher, keys, new AsyncResultHandler<JsonArray>() {
			
			@Override
			public void handle(final AsyncResult<JsonArray> event) {
				resultHandler.handle(new AsyncResult<List<User>>() {
					
					@Override
					public boolean succeeded() {
						return event.succeeded();
					}
					
					@Override
					public List<User> result() {
						if (succeeded()) {
							List<User> users = new ArrayList<User>();
							for (Object userObject : event.result()) {
								users.add(new User((JsonObject) userObject));
							}
							return users;
						} else {
							return null;
						}
					}
					
					@Override
					public boolean failed() {
						return !succeeded();
					}
					
					@Override
					public Throwable cause() {
						return event.cause();
					}
				});
			}
		});
	}
	
	/**
	 * Authenticates a user
	 * @param userId Id of the user to authenticate.
	 * @param authType Type of the authentication. Options are: password (plain text password), hash (hexadecimal SHA-256 hash string), pin
	 * @param code Password, password hash or PIN.
	 * @param resultHandler Handler to return the user information if the authentication was a success.
	 * @throws IllegalArgumentException Invalid authentication type.
	 */
	public void authenticateUser(String userId, String authType, String code, final AsyncResultHandler<User> resultHandler) throws IllegalArgumentException {
		JsonObject matcher = new JsonObject();
		matcher.putString("id", userId);
		switch (authType) {
		case "password":
			try {
				matcher.putString("hash", hash(code));
			} catch (NoSuchAlgorithmException e) {
				logger.error("Failed to create password hash.", e);
				throw new RuntimeException("Failed to create password hash: " + e.getMessage());
			}
			break;
		case "hash":
			matcher.putString("hash", code);
			break;
		case "pin":
			matcher.putString("pin", code);
			break;
		default:
			throw new IllegalArgumentException("Invalid authentication type. The following types are supported: password, hash, pin");
		}
		JsonObject keys = new JsonObject();
		for (String key : User.getRestrictedFields()) {
			keys.putNumber(key, 1);
		}
		mongo.findOne("users", matcher, keys, new AsyncResultHandler<JsonObject>() {
			
			@Override
			public void handle(final AsyncResult<JsonObject> result) {
				resultHandler.handle(new AsyncResult<User>() {
					
					@Override
					public boolean succeeded() {
						return result.succeeded() && result.result() != null;
					}
					
					@Override
					public User result() {
						return succeeded() ? new User(result.result()) : null;
					}
					
					@Override
					public boolean failed() {
						return result.failed();
					}
					
					@Override
					public Throwable cause() {
						if (result.succeeded() && !this.succeeded()) {
							return new Throwable("No match.");
						} else {
							return result.cause();
						}
					}
				});
			}
		});
	}
	
	/**
	 * Returns user information.
	 * @param userId ID of the user to retrieve information of.
	 * @param scope Access scope of the request.
	 * @param resultHandler Handler to return user model. The model contains only fields permitted by the access scope.
	 */
	public void getUser(final String userId, AccessScope scope, final AsyncResultHandler<User> resultHandler) {
		JsonObject matcher = new JsonObject().putString("id", userId);
		JsonObject keys;
		switch (scope) {
		case CONFIDENTIAL:
			keys = null;
			break;
		case RESTRICTED:
			keys = new JsonObject();
			for (String key : User.getRestrictedFields()) {
				keys.putNumber(key, 1);
			}
			break;
		case PUBLIC:
		default:
			keys = new JsonObject();
			for (String key : User.getPublicFields()) {
				keys.putNumber(key, 1);
			}
			break;
		}
		mongo.findOne("users", matcher, keys, new AsyncResultHandler<JsonObject>() {
			
			@Override
			public void handle(final AsyncResult<JsonObject> result) {
				resultHandler.handle(new AsyncResult<User>() {
					
					@Override
					public boolean succeeded() {
						return result.succeeded() && result.result() != null;
					}
					
					@Override
					public User result() {
						return succeeded() ? new User(result.result()) : null;
					}
					
					@Override
					public boolean failed() {
						return !succeeded();
					}
					
					@Override
					public Throwable cause() {
						if (result.succeeded() && !this.succeeded()) {
							return new Throwable("User " + userId + " not found."); 
						} else {
							return result.cause();
						}
					}
				});
			}
		});
	};
	
	/**
	 * Stores a user. A user will be created if no user with the given ID exists, otherwise the entry will be updated.
	 * @param user User to store.
	 * @param resultHandler Handler to check if the operation succeeded. May be <code>null</code>.
	 */
	public void storeUser(User user, AsyncResultHandler<Void> resultHandler) {
		mongo.update("users", new JsonObject().putString("id", user.getId()), user.asJson(), true, false, resultHandler);
	}
	
	/**
	 * Deletes a user.
	 * @param userId ID of the user to delete.
	 * @param resultHandler Handler to check if the operation succeeded. May be <code>null</code>.
	 */
	public void deleteUser(String userId, final AsyncResultHandler<Void> resultHandler) {
		mongo.delete("users", new JsonObject().putString("id", userId), new AsyncResultHandler<Integer>() {
			
			@Override
			public void handle(final AsyncResult<Integer> result) {
				if (resultHandler != null) resultHandler.handle(new AsyncResult<Void>() {
					
					@Override
					public boolean succeeded() {
						return result.succeeded() && result.result() > 0;
					}
					
					@Override
					public Void result() {
						return null;
					}
					
					@Override
					public boolean failed() {
						return !succeeded();
					}
					
					@Override
					public Throwable cause() {
						return result.cause();
					}
				});
			}
		});
	}
	
	
	/**
	 * Encodes a string as hexadecimal SHA-256 hash.
	 * @param text String to encode.
	 * @return Encoded string.
	 * @throws NoSuchAlgorithmException SHA-256 is not provided by the runtime.
	 */
	public static String hash(String text) throws NoSuchAlgorithmException {
		MessageDigest mDigest = MessageDigest.getInstance("SHA-256");
		byte[] result = mDigest.digest(text.getBytes());
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < result.length; i++) {
			sb.append(Integer.toString((result[i] & 0xff) + 0x100, 16).substring(1));
		}

		return sb.toString();
	}
}
