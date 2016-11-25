package de.appsist.service.auth;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import de.appsist.commons.event.AppsistEvent;
import de.appsist.commons.event.UserOnlineEvent;
import de.appsist.service.auth.UserManager.AccessScope;
import de.appsist.service.auth.model.Session;
import de.appsist.service.auth.model.User;
import de.appsist.service.auth.model.View;

/**
 * Handler for event bus messages
 * @author simon.schwantzer(at)im-c.de
 */
public class EBHandler {
	private static final Logger logger = LoggerFactory.getLogger(EBHandler.class);
	
	private final SessionManager sessionManager;
	private final UserManager userManager;
	private final TokenManager tokenManager;
	private final EventBus eventBus;
	
	public EBHandler(SessionManager sessionManager, UserManager userManager, TokenManager tokenManager, EventBus eventBus) {
		this.sessionManager = sessionManager;
		this.userManager = userManager;
		this.tokenManager = tokenManager;
		this.eventBus = eventBus;
		registerHandler();
	}
	
	private void registerHandler() {
		eventBus.registerHandler(MainVerticle.SERVICE_ID, new Handler<Message<JsonObject>>() {

			@Override
			public void handle(final Message<JsonObject> message) {
				JsonObject body = message.body();
				String action = body.getString("action");
				if (action == null) {
					message.reply(generateErrorResponse("Required field \"action\" is missing."));
					return;
				}
				switch (action) {
				case "createSession":
					handleCreateSession(message);
					break;
				case "storeSession":
					handleStoreSession(message);
					break;
				case "getSession":
					handleGetSession(message);
					break;
				case "deleteSession":
					handleDeleteSession(message);
					break;
				case "registerView":
					handleRegisterView(message);
					break;
				case "removeView":
					handleRemoveView(message);
					break;
				case "storeData":
					handleStoreData(message);
					break;
				case "getData":
					handleGetData(message);
					break;
				case "deleteData" :
					handleDeleteData(message);
					break;
				case "authenticateUser" :
					handleAuthenticateUser(message);
					break;
				case "getUser":
					handleGetUser(message);
					break;
				case "getAllUsers":
					handleGetAllUsers(message);
					break;
				case "getUserStatus":
					handleGetUserStatus(message);
					break;
				case "generateToken":
					handleGenerateToken(message);
					break;
				case "validateToken":
					handleValidateToken(message);
					break;
				case "authorizeResource":
					handleAuthorizeResource(message);
					break;
				default:
					message.fail(400, "Missing action to perform.");
				}
			}
		});
	}
	
	private void handleCreateSession(final Message<JsonObject> message) {
		sessionManager.createSession(new AsyncResultHandler<Session>() {
			@Override
			public void handle(AsyncResult<Session> result) {
				JsonObject response;
				if (result.succeeded()) {
					Session session = result.result();
					response = generateResponse();
					response.putObject("session", session.asJson());
				} else {
					response = generateErrorResponse(result.cause().getMessage());
				}
				message.reply(response);
			}
		});
	}
	
	private void handleStoreSession(final Message<JsonObject> message) {
		JsonObject body = message.body();
		final String token = body.getString("token");
		final Session session;
		try {
			session = new Session(body.getObject("session"));
		} catch (IllegalArgumentException e) {
			message.reply(generateErrorResponse("Invalid session object: " + e.getMessage()));
			return;
		}
		
		String userIdOfSession = session.getUserId();
		if (userIdOfSession != null) {
			// User session, we have to ensure the request is authorized by a valid token.
			try {
				tokenManager.validateToken(token, userIdOfSession);
			} catch (InvalidTokenException e) {
				JsonObject response = generateErrorResponse("Token authentication failed: " + e.getMessage());
				message.reply(response);
				return;
			}
		}
		
		sessionManager.storeSession(session, new AsyncResultHandler<Void>() {
			@Override
			public void handle(AsyncResult<Void> storeSessionRequest) {
				JsonObject response;
				if (storeSessionRequest.succeeded()) {
					response = generateResponse();
				} else {
					response = generateErrorResponse(storeSessionRequest.cause().getMessage());
				}
				message.reply(response);
				if (storeSessionRequest.succeeded() && session.getUserId() != null && session.hasView()) {
					sendOnlineEvent(session.getId(), session.getUserId(), session.getViews().get(0));
				}
			}
		});
	}
	
	private void handleGetSession(final Message<JsonObject> message) {
		JsonObject body = message.body();
		final String userId = body.getString("userId");
		final String token = body.getString("token");
		if (userId != null) {
			try {
				tokenManager.validateToken(token, userId);
				sessionManager.getSessionForUser(userId, new AsyncResultHandler<Session>() {
					
					@Override
					public void handle(AsyncResult<Session> sessionRequest) {
						JsonObject response;
						if (sessionRequest.succeeded()) {
							Session session = sessionRequest.result();
							if (session != null) {
								response = generateResponse();
								response.putObject("session", session.asJson());
							} else {
								response = generateErrorResponse("No session found.");
							}
						} else {
							response = generateErrorResponse(sessionRequest.cause().getMessage());
						}
						message.reply(response);
					}
				});
			} catch (InvalidTokenException e) {
				message.reply(generateErrorResponse(e.getMessage()));
			}
		} else {
			final String sessionId = body.getString("sessionId");
			if (sessionId == null || sessionId.isEmpty()) {
				message.reply(generateErrorResponse("Missing session or user identifier (sessionId, userId)."));
				return;
			}
			retrieveAndValidateSession(sessionId, token, new AsyncResultHandler<Session>() {

				@Override
				public void handle(AsyncResult<Session> sessionRequest) {
					JsonObject response;
					if (sessionRequest.succeeded()) {
						Session session = sessionRequest.result();
						response = generateResponse();
						response.putObject("session", session.asJson());
					} else {
						response = generateErrorResponse(sessionRequest.cause().getMessage());
					}
					message.reply(response);
				}
			});
		}
	}

	private void handleDeleteSession(final Message<JsonObject> message) {
		JsonObject body = message.body();
		final String token = body.getString("token");
		final String sessionId = body.getString("sessionId");
		if (sessionId == null || sessionId.isEmpty()) {
			message.reply(generateErrorResponse("Missing session identifier (sessionId)."));
			return;
		}
		retrieveAndValidateSession(sessionId, token, new AsyncResultHandler<Session>() {
			
			@Override
			public void handle(AsyncResult<Session> sessionRequest) {
				if (sessionRequest.succeeded()) {
					sessionManager.deleteSession(sessionId, new AsyncResultHandler<Integer>() {
						
						@Override
						public void handle(AsyncResult<Integer> sessionDeleteRequest) {
							JsonObject response;
							if (sessionDeleteRequest.succeeded()) {
								if (sessionDeleteRequest.result() > 0) {
									response = generateResponse();
								} else {
									response = generateErrorResponse("Session not found.");
								}
							} else {
								response = generateErrorResponse(sessionDeleteRequest.cause().getMessage());
							}
							message.reply(response);
						}
					});
				} else {
					JsonObject response = generateErrorResponse(sessionRequest.cause().getMessage());
					message.reply(response);
				}
			}
		});
	}
	
	private void handleRegisterView(final Message<JsonObject> message) {
		JsonObject body = message.body();
		final View view;
		try {
			view = new View(body.getObject("view"));
		} catch (IllegalArgumentException e) {
			message.reply(generateErrorResponse("Invalid view: " + e.getMessage()));
			return;
		}
		final String sessionId = body.getString("sessionId");
		if (sessionId == null || sessionId.isEmpty()) {
			message.reply(generateErrorResponse("Missing session identifier (sessionId)."));
			return;
		}
		final String token = body.getString("token");
		retrieveAndValidateSession(sessionId, token, new AsyncResultHandler<Session>() {

			@Override
			public void handle(AsyncResult<Session> sessionRequest) {
				if (sessionRequest.succeeded()) {
					sessionManager.registerView(sessionId, view, new AsyncResultHandler<Session>() {
						
						@Override
						public void handle(AsyncResult<Session> registerViewRequest) {
							JsonObject response;
							Session session = null;
							if (registerViewRequest.succeeded()) {
								response = generateResponse();
								session = registerViewRequest.result();
								response.putObject("session", session.asJson());
							} else {
								response = generateErrorResponse(registerViewRequest.cause().getMessage());
							}
							message.reply(response);
							if (registerViewRequest.succeeded()) {
								sendOnlineEvent(session.getId(), session.getUserId(), view);
							}
						}
					});
				} else {
					JsonObject response = generateErrorResponse(sessionRequest.cause().getMessage());
					message.reply(response);
				}
			}
		});
	}

	private void handleRemoveView(final Message<JsonObject> message) {
		JsonObject body = message.body();
		final String viewId = body.getString("viewId");
		if (viewId == null) {
			message.reply(generateErrorResponse("Missing view (viewId)."));
			return;
		}
		final String sessionId = body.getString("sessionId");
		if (sessionId == null || sessionId.isEmpty()) {
			message.reply(generateErrorResponse("Missing session identifier (sessionId)."));
			return;
		}
		final String token = body.getString("token");
		retrieveAndValidateSession(sessionId, token, new AsyncResultHandler<Session>() {

			@Override
			public void handle(AsyncResult<Session> sessionRequest) {
				if (sessionRequest.succeeded()) {
					sessionManager.removeView(sessionId, viewId, new AsyncResultHandler<Session>() {
						
						@Override
						public void handle(AsyncResult<Session> removeViewRequest) {
							JsonObject response;
							Session session = null;
							if (removeViewRequest.succeeded()) {
								response = generateResponse();
								session = removeViewRequest.result();
								response.putObject("session", session.asJson());
							} else {
								response = generateErrorResponse(removeViewRequest.cause().getMessage());
							}
							message.reply(response);
						}
					});
				} else {
					JsonObject response = generateErrorResponse(sessionRequest.cause().getMessage());
					message.reply(response);
				}
			}
		});
	}
	
	private void handleStoreData(final Message<JsonObject> message) {
		JsonObject body = message.body();
		final String sessionId = body.getString("sessionId");
		if (sessionId == null || sessionId.isEmpty()) {
			message.reply(generateErrorResponse("Missing session identifier (sessionId)."));
			return;
		}
		final String token = body.getString("token");
		final JsonObject data = body.getObject("data");
		if (data == null) {
			message.reply(generateErrorResponse("Missing data to store (data)."));
			return;
		}
		retrieveAndValidateSession(sessionId, token, new AsyncResultHandler<Session>() {

			@Override
			public void handle(AsyncResult<Session> sessionRequest) {
				if (sessionRequest.succeeded()) {
					sessionManager.storeData(sessionId, data, new AsyncResultHandler<Void>() {
						
						@Override
						public void handle(AsyncResult<Void> storeDataRequest) {
							JsonObject response;
							if (storeDataRequest.succeeded()) {
								response = generateResponse();
							} else {
								response = generateErrorResponse(storeDataRequest.cause().getMessage());
							}
							message.reply(response);
						}
					});
				} else {
					JsonObject response = generateErrorResponse(sessionRequest.cause().getMessage());
					message.reply(response);
				}
			}
		});
	}

	private void handleGetData(final Message<JsonObject> message) {
		JsonObject body = message.body();
		final String sessionId = body.getString("sessionId");
		if (sessionId == null || sessionId.isEmpty()) {
			message.reply(generateErrorResponse("Missing session identifier (sessionId)."));
			return;
		}
		final String token = body.getString("token");
		final JsonArray fieldsArray = body.getArray("fields");
		if (fieldsArray == null || fieldsArray.size() == 0) {
			message.reply(generateErrorResponse("Missing fields to return (fields)."));
			return;
		}
		final List<String> fieldNames = new ArrayList<>();
		for (Object field : fieldsArray) {
			fieldNames.add((String) field);
		}
		retrieveAndValidateSession(sessionId, token, new AsyncResultHandler<Session>() {

			@Override
			public void handle(AsyncResult<Session> sessionRequest) {
				if (sessionRequest.succeeded()) {
					sessionManager.getData(sessionId, fieldNames, new AsyncResultHandler<JsonObject>() {
						
						@Override
						public void handle(AsyncResult<JsonObject> getDataRequest) {
							JsonObject response;
							if (getDataRequest.succeeded()) {
								response = generateResponse();
								response.putObject("data", getDataRequest.result());
							} else {
								response = generateErrorResponse(getDataRequest.cause().getMessage());
							}
							message.reply(response);
						}
					});
				} else {
					JsonObject response = generateErrorResponse(sessionRequest.cause().getMessage());
					message.reply(response);
				}
			}
			
		});
	}
	
	private void handleDeleteData(final Message<JsonObject> message) {
		JsonObject body = message.body();
		final String sessionId = body.getString("sessionId");
		if (sessionId == null || sessionId.isEmpty()) {
			message.reply(generateErrorResponse("Missing session identifier (sessionId)."));
			return;
		}
		final String token = body.getString("token");
		final JsonArray fieldsArray = body.getArray("fields");
		if (fieldsArray == null || fieldsArray.size() == 0) {
			message.reply(generateErrorResponse("Missing fields to delete (fields)."));
			return;
		}
		final List<String> fieldNames = new ArrayList<>();
		for (Object field : fieldsArray) {
			fieldNames.add((String) field);
		}
		retrieveAndValidateSession(sessionId, token, new AsyncResultHandler<Session>() {

			@Override
			public void handle(AsyncResult<Session> sessionRequest) {
				if (sessionRequest.succeeded()) {
					sessionManager.deleteData(sessionId, fieldNames, new AsyncResultHandler<Void>() {
						
						@Override
						public void handle(AsyncResult<Void> deleteDataRequest) {
							JsonObject response;
							if (deleteDataRequest.succeeded()) {
								response = generateResponse();
							} else {
								response = generateErrorResponse(deleteDataRequest.cause().getMessage());
							}
							message.reply(response);
						}
					});
				} else {
					JsonObject response = generateErrorResponse(sessionRequest.cause().getMessage());
					message.reply(response);
				}
			}
		});
	}
	
	private void handleGetUser(final Message<JsonObject> message) {
		JsonObject body = message.body();
		final String userId = body.getString("userId");
		if (userId == null || userId.isEmpty()) {
			message.reply(generateErrorResponse("Missing user identifier (userId)."));
			return;
		}
		final String sessionId = body.getString("sessionId");
		final String token = body.getString("token");
		retrieveAndValidateSession(sessionId, token, new AsyncResultHandler<Session>() {

			@Override
			public void handle(AsyncResult<Session> sessionRequest) {
				AccessScope accessScope = sessionRequest.succeeded() ? AccessScope.RESTRICTED : AccessScope.PUBLIC;
				userManager.getUser(userId, accessScope, new AsyncResultHandler<User>() {
					
					@Override
					public void handle(AsyncResult<User> result) {
						JsonObject response;
						if (result.succeeded()) {
							response = generateResponse();
							User user = result.result();
							response.putObject("user", user.asJson());
						} else {
							response = generateErrorResponse(result.cause().getMessage());
						}
						message.reply(response);
					}
				});
			}
		});
	}
	
	private void handleGetAllUsers(final Message<JsonObject> message) {
		userManager.getUsers(new AsyncResultHandler<List<User>>() {
			
			@Override
			public void handle(AsyncResult<List<User>> usersRequest) {
				JsonObject response;
				if (usersRequest.succeeded()) {
					response = generateResponse();
					JsonObject users = new JsonObject();
					for (User user : usersRequest.result()) {
						users.putObject(user.getId(), user.asJson());
					}
					response.putObject("users", users);
				} else {
					response = generateErrorResponse("Failed to retrieve users: " + usersRequest.cause().getMessage());
				}
				message.reply(response);
			}
		});
	}
		
	private void handleAuthenticateUser(final Message<JsonObject> message) {
		JsonObject body = message.body();
		final String userId = body.getString("userId");
		if (userId == null || userId.isEmpty()) {
			message.reply(generateErrorResponse("Missing user identifier (userId)."));
			return;
		}
		
		String type, code;
		if (body.containsField("password")) {
			type = "password";
			code = body.getString("password");
		} else if (body.containsField("pin")) {
			type = "pin";
			code = body.getString("pin");
		} else if (body.containsField("hash")) {
			type = "hash";
			code = body.getString("hash");
		} else {
			message.reply(generateErrorResponse("Invalid authentication method."));
			return;
		}
		userManager.authenticateUser(userId, type, code, new AsyncResultHandler<User>() {
			
			@Override
			public void handle(AsyncResult<User> result) {
				JsonObject response;
				if (result.succeeded()) {
					response = generateResponse();
					User user = result.result();
					response.putObject("user", user.asJson());
					String jwt = tokenManager.generateToken(userId);
					response.putString("token", jwt);
				} else {
					response = generateErrorResponse(result.cause().getMessage());
				}
				message.reply(response);
			}
		});
	}

	private void handleGetUserStatus(final Message<JsonObject> message) {
		JsonObject body = message.body();
		final String userId = body.getString("userId");
		if (userId == null || userId.isEmpty()) {
			message.reply(generateErrorResponse("Missing user identifier (userId)."));
			return;
		}
		sessionManager.getSessionForUser(userId, new AsyncResultHandler<Session>() {
			
			@Override
			public void handle(AsyncResult<Session> result) {
				JsonObject response;
				if (result.succeeded()) {
					response = generateResponse();
					Session session = result.result();
					JsonObject userStatus = new JsonObject();
					if (session != null) {
						boolean isOnline = session.hasView();
						userStatus.putBoolean("isOnline", isOnline);
						userStatus.putString("lastActivity", session.asJson().getString("lastActvitiy"));
						if (isOnline) {
							userStatus.putString("sessionId", session.getId());
							userStatus.putArray("views", session.asJson().getArray("views"));
						}
					} else {
						userStatus.putBoolean("isOnline", false);
					}
					response.putObject("userStatus", userStatus);
				} else {
					response = generateErrorResponse(result.cause().getMessage());
				}
				message.reply(response);
			}
		});
	}
	
	private void handleGenerateToken(final Message<JsonObject> message) {
		JsonObject body = message.body();
		final String userId = body.getString("userId");
		final String serviceId = body.getString("serviceId");
		if (userId != null) {
			String authType, authCode;
			if (body.containsField("password")) {
				authType = "password";
				authCode = body.getString("password");
			} else if (body.containsField("pin")) {
				authType = "pin";
				authCode = body.getString("pin");
			} else if (body.containsField("hash")) {
				authType = "hash";
				authCode = body.getString("hash");
			} else {
				message.reply(generateErrorResponse("Invalid authentication method."));
				return;
			}
			userManager.authenticateUser(userId, authType, authCode, new AsyncResultHandler<User>() {
				
				@Override
				public void handle(AsyncResult<User> result) {
					JsonObject response;
					if (result.succeeded()) {
						String jwt = tokenManager.generateToken(userId);
						response = generateResponse();
						response.putString("subject", userId);
						response.putString("token", jwt);
					} else {
						response = generateErrorResponse(result.cause().getMessage());
					}
					message.reply(response);
				}
			});
		} else if (serviceId != null) {
			String jwt = tokenManager.generateToken(userId);
			JsonObject response = generateResponse();
			response.putString("subject", userId);
			response.putString("token", jwt);
			message.reply(response);
		} else {
			message.reply(generateErrorResponse("Unknown subject: User or system identifier required."));
		}
	}
	
	private void handleValidateToken(final Message<JsonObject> message) {
		JsonObject body = message.body();
		String subject = body.getString("subject");
		if (subject == null || subject.isEmpty()) {
			message.reply(generateErrorResponse("Missing subject of token (subject)."));
			return;
		}
		String jwt = body.getString("token");
		if (jwt == null || jwt.isEmpty()) {
			message.reply(generateErrorResponse("Missing token (token)."));
			return;
		}
		JsonObject response;
		try {
			JsonObject claims = tokenManager.validateToken(jwt, subject);
			response = generateResponse();
			response.putObject("claims", claims);
		} catch (InvalidTokenException e) {
			logger.warn("Invalid token request.", e);
			response = generateErrorResponse("Failed to validate token: " + e.getMessage());
		}
		message.reply(response);
	}
	
	private void handleAuthorizeResource(final Message<JsonObject> message) {
		JsonObject body = message.body();
		final String sessionId = body.getString("sessionId");
		if (sessionId == null || sessionId.isEmpty()) {
			message.reply(generateErrorResponse("Missing session identifier (sessionId)."));
			return;
		}
		final String token = body.getString("token");
		final String resourceId = body.getString("resourceId");
		retrieveAndValidateSession(sessionId, token, new AsyncResultHandler<Session>() {

			@Override
			public void handle(AsyncResult<Session> sessionRequest) {
				if (sessionRequest.succeeded()) {
					Session session = sessionRequest.result();
					userManager.getUser(session.getUserId(), AccessScope.RESTRICTED, new AsyncResultHandler<User>() {
						
						@Override
						public void handle(AsyncResult<User> result) {
							JsonObject response = new JsonObject();
							if (result.succeeded()) {
								User user = result.result();
								if (user.getResources().contains(resourceId)) {
									response.putString("status", "ok");
								} else {
									response.putString("status", "failed");
								}
							} else {
								response.putString("status", "error");
								response.putString("message", result.cause().toString());
							}
							message.reply(response);
						}
					});
				} else {
					JsonObject response = generateErrorResponse(sessionRequest.cause().getMessage());
					message.reply(response);
				}
			}
		});
	}

	
	/**
	 * Validates a session request.
	 * The request succeeds if either the requested session is owned by the subject encoded in the token or if the session is no user session.
	 * The validation automatically fails if the session does not exist.
	 * @param sessionId ID of the session to validate.
	 * @param token JSON web token as used by the {@link TokenManagerImpl}. May be <code>null</code> if the session is no user session.
	 * @param resultHandler Handler to return the session if both the session exists and has been validated. 
	 */
	private void retrieveAndValidateSession(String sessionId, final String token, final AsyncResultHandler<Session> resultHandler) {
		if (sessionId == null) sessionId = "INVALID";
		sessionManager.getSession(sessionId, new AsyncResultHandler<Session>() {
			
			@Override
			public void handle(final AsyncResult<Session> event) {
				final Session session = event.result();
				resultHandler.handle(new AsyncResult<Session>() {
					
					@Override
					public boolean succeeded() {
						if (event.succeeded()) {
							String userId = session.getUserId();
							if (userId != null) {
								try {
									tokenManager.validateToken(token, userId);
									return true;
								} catch (InvalidTokenException e) {
									return false;
								}
							} else {
								return true;
							}
						} else {
							return false;
						}
					}
					
					@Override
					public Session result() {
						return succeeded() ? session : null;
					}
					
					@Override
					public boolean failed() {
						return !succeeded();
					}
					
					@Override
					public Throwable cause() {
						if (event.succeeded() && failed()) {
							return new Throwable("Token authentication failed.");
						} else if (event.failed()) {
							return event.cause();
						} else {
							return null;
						}
					}
				});
			}
		});
	}
	
	private JsonObject generateErrorResponse(String message) {
		JsonObject response = new JsonObject();
		response.putString("status", "error");
		response.putString("message", message);
		return response;
	}
	
	private JsonObject generateResponse() {
		JsonObject response = new JsonObject();
		response.putString("status", "ok");
		return response;
	}
	
	private void sendOnlineEvent(String sessionId, String userId, View view) {
		AppsistEvent event = new UserOnlineEvent(UUID.randomUUID().toString(), sessionId, userId, view.getDeviceId());
		eventBus.publish("appsist:event:" + event.getModelId(), new JsonObject(event.asMap()));
		if (MainVerticle.getConfig().isDebugModeEnabled()) {
			logger.debug("User online event published: " + event);
		}
	}
}
