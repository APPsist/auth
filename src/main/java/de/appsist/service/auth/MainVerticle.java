package de.appsist.service.auth;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.HttpServerResponse;
import org.vertx.java.core.http.RouteMatcher;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.platform.Verticle;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;

import de.appsist.commons.misc.StatusSignalConfiguration;
import de.appsist.commons.misc.StatusSignalSender;
import de.appsist.service.auth.UserManager.AccessScope;
import de.appsist.service.auth.connector.MongoDBConnector;
import de.appsist.service.auth.model.User;

/**
 * Main verticle of the authentication and session service.
 * @author simon.schwantzer(at)im-c.de
 *
 */
public class MainVerticle extends Verticle {
	public static final String SERVICE_ID = "appsist:service:auth";
	private static final Logger logger = LoggerFactory.getLogger(MainVerticle.class);
	
	private static ModuleConfiguration config;
	private RouteMatcher routeMatcher;
	private SessionManager sessionManager;
	private UserManager userManager;
	private TokenManager tokenManager;
	private Map<String, Template> templates = new HashMap<>();

	@Override
	public void start() {
		config = new ModuleConfiguration(container.config());

		JsonArray deploys = config.getDeployments();
		if (deploys != null) for (Object deploy : deploys) {
			JsonObject deployConfig = (JsonObject) deploy;
			container.deployModule(deployConfig.getString("id"), deployConfig.getObject("config"));
		}
		
		MongoDBConnector mongoConnector = new MongoDBConnector(config.getMongoPersistorAddress(), vertx.eventBus());
		sessionManager = new SessionManager(mongoConnector, vertx.eventBus());
		userManager = new UserManager(mongoConnector);
		tokenManager = new TokenManager();
		new EBHandler(sessionManager, userManager, tokenManager, vertx.eventBus());
				
		initializeHTTPRouting();
		vertx.createHttpServer()
			.requestHandler(routeMatcher)
			.listen(config.getWebserverPort());
		
		final int hoursUntilPurge = config.getHoursUntilSessionPurged();
		if (hoursUntilPurge > 0) {
			vertx.setPeriodic(900000, new Handler<Long>() { // one an hour

				@Override
				public void handle(Long event) {
					DateTime purgeBefore = new DateTime().minusHours(hoursUntilPurge);
					sessionManager.purgeOldSessions(purgeBefore, new AsyncResultHandler<Integer>() {
						
						@Override
						public void handle(AsyncResult<Integer> event) {
							if (event.succeeded()) {
								if (event.result() > 0) {
									logger.debug("Purged " + event.result() + " obsolete session(s).");
								}
							} else {
								logger.warn("Failed to purge obsolete sessions: " + event.cause().getMessage());
							}
						}
					});
				}
			});
		};
		
		JsonObject statusSignalObject = config.getStatusSignalConfig();
		StatusSignalConfiguration statusSignalConfig;
		if (statusSignalObject != null) {
		  statusSignalConfig = new StatusSignalConfiguration(statusSignalObject);
		} else {
		  statusSignalConfig = new StatusSignalConfiguration();
		}

		StatusSignalSender statusSignalSender = new StatusSignalSender("auth", vertx, statusSignalConfig);
		statusSignalSender.start();

		logger.debug("APPsist service \"Authentication and Session Service\" has been initialized with the following configuration:\n" + config.asJson().encodePrettily());
	}
	
	@Override
	public void stop() {
		logger.debug("APPsist service \"Authentication and Session Service\" has been stopped.");
	}
	
	/**
	 * Returns the module configuration.
	 * @return Module configuration.
	 */
	public static ModuleConfiguration getConfig() {
		return config;
	}
	
	/**
	 * In this method the HTTP API build using a route matcher.
	 */
	private void initializeHTTPRouting() {
		final String basePath = config.getWebserverBasePath();
		routeMatcher = new BasePathRouteMatcher(basePath);
		final String staticFileDirecotry = config.getWebserverStaticDirectory();
		
		try {
			Handlebars handlebars = new Handlebars();
			templates.put("addUser", handlebars.compile("templates/addUser"));
			templates.put("editUser", handlebars.compile("templates/editUser"));
			templates.put("deleteUser", handlebars.compile("templates/deleteUser"));
			templates.put("listUsers", handlebars.compile("templates/listUsers"));
		} catch (IOException e) {
			logger.fatal("Failed to load templates.", e);
		}
		
		routeMatcher.get("/admin/listUsers", new Handler<HttpServerRequest>() {
			@Override
			public void handle(HttpServerRequest request) {
				final HttpServerResponse response = request.response();
				userManager.getUsers(new AsyncResultHandler<List<User>>() {
					
					@Override
					public void handle(AsyncResult<List<User>> usersRequest) {
						if (usersRequest.succeeded()) {
							JsonArray usersList = new JsonArray();
							for (User user : usersRequest.result()) {
								JsonObject userObject = user.asJson();
								userObject.putString("resources", StringUtils.join(user.getResources(), ","));
								usersList.addObject(userObject);
							}
							JsonObject data = new JsonObject()
								.putString("basePath", basePath)
								.putArray("users", usersList);
							renderResponse(response, "listUsers", data);
						} else {
							response.setStatusCode(500).end(usersRequest.cause().getMessage());
						}
					}
				});
			}
		}).get("/admin/addUser", new Handler<HttpServerRequest>() {
			
			@Override
			public void handle(HttpServerRequest request) {
				final HttpServerResponse response = request.response();
				JsonObject data = new JsonObject()
					.putObject("user", new JsonObject())
					.putString("basePath", basePath);
				renderResponse(response, "addUser", data);
			}
		}).post("/admin/addUser", new Handler<HttpServerRequest>() {
			
			@Override
			public void handle(final HttpServerRequest request) {
				final HttpServerResponse response = request.response();
				final JsonObject data = new JsonObject()
					.putString("basePath", basePath);
				
				request.expectMultiPart(true);
				request.bodyHandler(new Handler<Buffer>() {
					
					@Override
					public void handle(Buffer buffer) {
						User user = new User(
								request.formAttributes().get("mail"),
								request.formAttributes().get("firstName"),
								request.formAttributes().get("lastName"),
								request.formAttributes().get("displayName"),
								request.formAttributes().get("mail"));
						
						String password = request.formAttributes().get("password");
						try {
							user.setHash(UserManager.hash(password));
						} catch (NoSuchAlgorithmException e) {
							response.setStatusCode(500).end("Failed to hash password: " + e.getMessage());
							return;
						}
						
						String pin = request.formAttributes().get("pin");
						if (pin != null && pin.length() > 0) {
							user.setPin(pin);
						}
						
						String position = request.formAttributes().get("position");
						if (position != null && position.length() >  0) {
							user.setPosition(position);
						}
						
						String resourcesString = request.formAttributes().get("resources");
						if (resourcesString != null && resourcesString.length() > 0) {
							String[] resourcesArray = StringUtils.split(resourcesString, ",");
							user.setResources(Arrays.asList(resourcesArray));
						}
												
						// validate
						if (user.getPin() != null && !StringUtils.isNumeric(user.getPin())) {
							data.putObject("user", user.asJson().putString("resources", StringUtils.join(user.getResources(), ",")));
							data.putString("error", "Invalid PIN: Only numeric values are allowed.");
							renderResponse(response, "addUser", data);
							return;
						}
						
						userManager.storeUser(user, new AsyncResultHandler<Void>() {
							@Override
							public void handle(AsyncResult<Void> storeRequest) {
								if (storeRequest.succeeded()) {
									response.headers().add("Location", basePath + "/admin/listUsers");
									response.setStatusCode(303).end();
								} else {
									response.setStatusCode(500).end("Failed to create user: " + storeRequest.cause().getMessage());
								}
							}
						});
					}
				});
			}
		}).get("/admin/deleteUser", new Handler<HttpServerRequest>() {

			@Override
			public void handle(HttpServerRequest request) {
				final HttpServerResponse response = request.response();
				String userId = request.params().get("id");
				userManager.getUser(userId, AccessScope.RESTRICTED, new AsyncResultHandler<User>() {
					
					@Override
					public void handle(AsyncResult<User> userRequest) {
						if (userRequest.succeeded()) {
							JsonObject data = new JsonObject()
								.putObject("user", userRequest.result().asJson())
								.putString("basePath", basePath);
						renderResponse(response, "deleteUser", data);
							
						} else {
							response.setStatusCode(500).end("Failed to retrieve user: " + userRequest.cause().getMessage());
						}
					}
				});
				
			}
		}).post("/admin/deleteUser", new Handler<HttpServerRequest>() {

			@Override
			public void handle(final HttpServerRequest request) {
				final HttpServerResponse response = request.response();
				
				request.expectMultiPart(true);
				request.bodyHandler(new Handler<Buffer>() {

					@Override
					public void handle(Buffer buffer) {
						String userId = request.formAttributes().get("id");
						userManager.deleteUser(userId, new AsyncResultHandler<Void>() {
							
							@Override
							public void handle(AsyncResult<Void> deleteRequest) {
								if (deleteRequest.succeeded()) {
									response.headers().add("Location", basePath + "/admin/listUsers");
									response.setStatusCode(303).end();
								} else {
									response.setStatusCode(500).end("Failed to delete user: " + deleteRequest.cause().getMessage());
								}
							}
						});
					}
				});
			}
		}).get("/admin/editUser", new Handler<HttpServerRequest>() {

			@Override
			public void handle(HttpServerRequest request) {
				final HttpServerResponse response = request.response();
				String userId = request.params().get("id");
				userManager.getUser(userId, AccessScope.CONFIDENTIAL, new AsyncResultHandler<User>() {
					
					@Override
					public void handle(AsyncResult<User> userRequest) {
						if (userRequest.succeeded()) {
							User user = userRequest.result();
							JsonObject userObject = user.asJson();
							userObject.putString("resources", StringUtils.join(user.getResources(), ","));
							JsonObject data = new JsonObject()
								.putObject("user", userObject)
								.putString("basePath", basePath);
							renderResponse(response, "editUser", data);
						} else {
							response.setStatusCode(500).end("Failed to retrieve user: " + userRequest.cause().getMessage());
						}
					}
				});
				
			}
		}).post("/admin/editUser", new Handler<HttpServerRequest>() {
			
			@Override
			public void handle(final HttpServerRequest request) {
				final HttpServerResponse response = request.response();
				final JsonObject data = new JsonObject()
					.putString("basePath", basePath);
				
				request.expectMultiPart(true);
				request.bodyHandler(new Handler<Buffer>() {
					
					@Override
					public void handle(Buffer buffer) {
						String userId = request.formAttributes().get("id");
						userManager.getUser(userId, AccessScope.CONFIDENTIAL, new AsyncResultHandler<User>() {
							
							@Override
							public void handle(AsyncResult<User> userRequest) {
								if (userRequest.succeeded()) {
									User user = userRequest.result();
									user.setFirstName(request.formAttributes().get("firstName"));
									user.setLastName(request.formAttributes().get("lastName"));
									user.setDisplayName(request.formAttributes().get("displayName"));
									user.setMail(request.formAttributes().get("mail"));
									
									String password = request.formAttributes().get("password");
									if (password != null && password.length() > 0) {
										try {
											user.setHash(UserManager.hash(password));
										} catch (NoSuchAlgorithmException e) {
											response.setStatusCode(500).end("Failed to hash password: " + e.getMessage());
											return;
										}
									};
									
									String pin = request.formAttributes().get("pin");
									if (pin != null && pin.length() > 0) {
										user.setPin(pin);
									} else {
										user.setPin(null);
									}
									
									String position = request.formAttributes().get("position");
									if (position != null && position.length() >  0) {
										user.setPosition(position);
									} else {
										user.setPosition(null);
									}
									
									String resourcesString = request.formAttributes().get("resources");
									if (resourcesString != null && resourcesString.length() > 0) {
										String[] resourcesArray = StringUtils.split(resourcesString, ",");
										user.setResources(Arrays.asList(resourcesArray));
									} else {
										user.setResources(new ArrayList<String>());
									}
									
									// validate
									if (user.getPin() != null && !StringUtils.isNumeric(user.getPin())) {
										data.putObject("user", user.asJson().putString("resources", StringUtils.join(user.getResources(), ",")));
										data.putString("error", "Invalid PIN: Only numeric values are allowed.");
										renderResponse(response, "editUser", data);
										return;
									}

									userManager.storeUser(user, new AsyncResultHandler<Void>() {
										@Override
										public void handle(AsyncResult<Void> storeRequest) {
											if (storeRequest.succeeded()) {
												response.headers().add("Location", basePath + "/admin/listUsers");
												response.setStatusCode(303).end();
											} else {
												response.setStatusCode(500).end("Failed to store user: " + storeRequest.cause().getMessage());
											}
										}
									});
								} else {
									response.setStatusCode(500).end("Failed to retrieve user: " + userRequest.cause().getMessage());
								}
							}
						});
					}
				});
			}
		});
		
		
		routeMatcher.getWithRegEx("/.*", new Handler<HttpServerRequest>() {
			
			@Override
			public void handle(HttpServerRequest request) {
				request.response().sendFile(staticFileDirecotry + request.path());
			}
		});
	}
	
	private void renderResponse(HttpServerResponse response, String template, JsonObject data) {
		try {
			String html = templates.get(template).apply(data.toMap());
			response.end(html);
		} catch (IOException e) {
			response.setStatusCode(500).end("Failed to load template: " + e.getMessage());
		}
	}
}
