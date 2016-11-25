package de.appsist.service.auth.integration;

import static org.vertx.testtools.VertxAssert.assertEquals;
import static org.vertx.testtools.VertxAssert.assertNotNull;
import static org.vertx.testtools.VertxAssert.assertTrue;
import static org.vertx.testtools.VertxAssert.testComplete;

import java.util.UUID;

import org.junit.Ignore;
import org.junit.Test;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.testtools.TestVerticle;

import de.appsist.service.auth.model.Session;
import de.appsist.service.auth.model.User;
import de.appsist.service.auth.model.View;

public class ApiTest extends TestVerticle {
	private Session testSession;
	private JsonObject testData;
	private View testView;
	private String testToken;
	private Logger log;
	
	@Override
	public void start() {
		initialize();
		
		JsonObject config = new JsonObject(vertx.fileSystem().readFileSync("config.json").toString());
		log = container.logger();
		container.deployModule(System.getProperty("vertx.modulename"), config, new AsyncResultHandler<String>() {
			@Override
			public void handle(AsyncResult<String> asyncResult) {
				// Deployment is asynchronous and this this handler will
				// be called when it's complete (or failed)
				assertTrue(asyncResult.succeeded());
				assertNotNull("deploymentID should not be null",
						asyncResult.result());
				// If deployed correctly then start the tests!
				
				// Create test token.
				JsonObject request = new JsonObject();
				request.putString("action", "generateToken");
				request.putString("userId", "alice.tester@example.com");
				request.putString("hash", "c3ab8ff13720e8ad9047dd39466b3c8974e592c2fa383d4a3960714caef0c4f2");
				vertx.eventBus().send("appsist:service:auth", request, new Handler<Message<JsonObject>>() {
					@Override
					public void handle(Message<JsonObject> event) {
						JsonObject body = event.body();
						if ("ok".equals(body.getString("status"))) {
							testToken = body.getString("token");
							log.info("Created test token: " + testToken);
							
							// Create a session to test.
							JsonObject request = new JsonObject();
							request.putString("action", "storeSession");
							testSession = new Session(UUID.randomUUID().toString());
							testSession.asJson().putString("userId", "alice.tester@example.com");
							testData = new JsonObject().putString("foo", "bar").putBoolean("foobar", true);
							testSession.asJson().putObject("data", testData);
							testView = new View(UUID.randomUUID().toString(), "tablet", "nvidiashield01");
							testSession.registerView(testView);
							request.putObject("session", testSession.asJson());
							request.putString("token", testToken);
							vertx.eventBus().send("appsist:service:auth", request, new Handler<Message<JsonObject>>() {
								@Override
								public void handle(Message<JsonObject> event) {
									JsonObject body = event.body();
									if ("ok".equals(body.getString("status"))) {
										log.info("Created test session: " + testSession.asJson().encode());
										startTests();
									} else {
										log.error("Failed to create test session: " + body.getString("message"));
										return;
									}
								}
							});
							
						} else {
							log.error("Failed to create test token: " + body.getString("message"));
						}
					}
				});
				
				
				/*
				JsonObject request = new JsonObject();
				request.putString("action", "storeSession");
				testSession = new Session(UUID.randomUUID().toString());
				testSession.asJson().putString("userId", "alice.tester@example.com");
				testData = new JsonObject().putString("foo", "bar").putBoolean("foobar", true);
				testSession.asJson().putObject("data", testData);
				testView = new View(UUID.randomUUID().toString(), "tablet", "nvidiashield01");
				testSession.registerView(testView);
				request.putObject("session", testSession.asJson());
				vertx.eventBus().send("appsist:service:auth", request, new Handler<Message<JsonObject>>() {
					@Override
					public void handle(Message<JsonObject> event) {
						JsonObject body = event.body();
						if ("ok".equals(body.getString("status"))) {
							log.info("Created test session: " + testSession.asJson().encode());
						} else {
							log.error("Failed to create test session: " + body.getString("message"));
							return;
						}
						JsonObject request = new JsonObject();
						request.putString("action", "generateToken");
						request.putString("userId", "alice.tester@example.com");
						request.putString("hash", "c3ab8ff13720e8ad9047dd39466b3c8974e592c2fa383d4a3960714caef0c4f2");
						vertx.eventBus().send("appsist:service:auth", request, new Handler<Message<JsonObject>>() {
							@Override
							public void handle(Message<JsonObject> event) {
								JsonObject body = event.body();
								if ("ok".equals(body.getString("status"))) {
									testToken = body.getString("token");
									log.info("Created test token: " + testToken);
									startTests();
								} else {
									log.error("Failed to create test token: " + body.getString("message"));
								}
							}
						});
					}
				});*/
			}
		});
	}
	
	@Test
	@Ignore
	public void createSessionTest() {
		JsonObject request = new JsonObject();
		request.putString("action", "createSession");
		vertx.eventBus().send("appsist:service:auth", request, new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> event) {
				JsonObject body = event.body();
				assertEquals("ok", body.getString("status"));
				assertNotNull("session", body.getObject("session"));
				log.info("Created session: " + body.encode());
				testComplete();
			}
		});
	}

	@Test
	@Ignore
	public void getSessionTest() {
		JsonObject request = new JsonObject();
		request.putString("action", "getSession");
		request.putString("sessionId", testSession.getId());
		vertx.eventBus().send("appsist:service:auth", request, new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> event) {
				JsonObject body = event.body();
				assertEquals("ok", body.getString("status"));
				JsonObject session = body.getObject("session");
				assertEquals(testSession.getId(), session.getString("id"));
				log.info("Received session: " + body.encode());
				testComplete();
			}
		});
	}

	@Test
	@Ignore
	public void storeSessionTest() {
		JsonObject request = new JsonObject();
		request.putString("action", "storeSession");
		request.putObject("session", testSession.asJson().copy().putObject("data", new JsonObject().putString("foo", "bar")));
		vertx.eventBus().send("appsist:service:auth", request, new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> event) {
				JsonObject body = event.body();
				assertEquals("ok", body.getString("status"));
				vertx.eventBus().send("appsist:service:auth", new JsonObject().putString("action", "getSession").putString("sessionId", testSession.getId()), new Handler<Message<JsonObject>>() {
					@Override
					public void handle(Message<JsonObject> event) {
						JsonObject body = event.body();
						assertEquals("ok", body.getString("status"));
						JsonObject session = body.getObject("session");
						assertEquals("bar", session.getObject("data").getString("foo"));
						log.info("Received session: " + body.encode());
						testComplete();
					}
				});
			}
		});
	}
	
	@Test
	@Ignore
	public void deleteSessionTest() {
		JsonObject request = new JsonObject();
		request.putString("action", "deleteSession");
		request.putString("sessionId", testSession.getId());
		vertx.eventBus().send("appsist:service:auth", request, new Handler<Message<JsonObject>>() {
			
			@Override
			public void handle(Message<JsonObject> event) {
				JsonObject body = event.body();
				assertEquals("Failed to delete test session: " + body.getString("message"), "ok", body.getString("status"));
				testComplete();
			}
		});
	}
	
	@Test
	@Ignore
	public void storeDataTest() {
		JsonObject request = new JsonObject();
		request.putString("action", "storeData");
		request.putString("sessionId", testSession.getId());
		final JsonObject data = new JsonObject().putNumber("bar", 1);
		request.putObject("data", data);
		vertx.eventBus().send("appsist:service:auth", request, new Handler<Message<JsonObject>>() {
			
			@Override
			public void handle(Message<JsonObject> event) {
				JsonObject body = event.body();
				assertEquals("Failed to store data in the session: " + body.getString("message"), "ok", body.getString("status"));
				testComplete();
			}
		});
	}
	
	@Test
	@Ignore
	public void getDataTest() {
		JsonObject request = new JsonObject();
		request.putString("action", "getData");
		request.putString("sessionId", testSession.getId());
		request.putArray("fields", new JsonArray().addString("foo"));
		vertx.eventBus().send("appsist:service:auth", request, new Handler<Message<JsonObject>>() {
	
			@Override
			public void handle(Message<JsonObject> event) {
				JsonObject body = event.body();
				JsonObject receivedData = body.getObject("data");
				assertEquals("Failed to retrieve data from the session: " + body.getString("message"), "ok", body.getString("status"));
				assertEquals("Wrong data received.", testData.getString("foo"), receivedData.getString("foo"));
				testComplete();				
			}
		});
	}
	
	@Test
	@Ignore
	public void deleteDataTest() {
		JsonObject request = new JsonObject();
		request.putString("action", "deleteData");
		request.putString("sessionId", testSession.getId());
		request.putArray("fields", new JsonArray().addString("foo"));
		vertx.eventBus().send("appsist:service:auth", request, new Handler<Message<JsonObject>>() {
	
			@Override
			public void handle(Message<JsonObject> event) {
				JsonObject body = event.body();
				assertEquals("Failed to delete data from the session: " + body.getString("message"), "ok", body.getString("status"));
				testComplete();				
			}
		});
	}
	
	@Test
	@Ignore
	public void registerViewTest() {
		JsonObject request = new JsonObject();
		request.putString("action", "registerView");
		request.putString("sessionId", testSession.getId());
		final JsonObject testView = new JsonObject();
		testView.putString("id", UUID.randomUUID().toString());
		testView.putString("deviceClass", "watch");
		testView.putString("deviceId", "pebbleTime01");
		request.putObject("view", testView);
		vertx.eventBus().send("appsist:service:auth", request, new Handler<Message<JsonObject>>() {
	
			@Override
			public void handle(Message<JsonObject> event) {
				JsonObject body = event.body();
				assertEquals("Failed to register view for session: " + body.getString("message"), "ok", body.getString("status"));
				JsonObject session = body.getObject("session");
				boolean found = false;
				for (Object viewObject : session.getArray("views")) {
					JsonObject view = (JsonObject) viewObject;
					if (view.getString("id").equals(testView.getString("id"))) {
						found = true;
						break;
					}
				}
				assertTrue("Test view not found in response.", found);
				testComplete();				
			}
		});
	}
	
	@Test
	@Ignore
	public void removeViewTest() {
		JsonObject request = new JsonObject();
		request.putString("action", "removeView");
		request.putString("sessionId", testSession.getId());
		request.putString("viewId", testView.getId());
		vertx.eventBus().send("appsist:service:auth", request, new Handler<Message<JsonObject>>() {

			@Override
			public void handle(Message<JsonObject> event) {
				JsonObject body = event.body();
				assertEquals("Failed to remove view for session: " + body.getString("message"), "ok", body.getString("status"));
				Session session = new Session(body.getObject("session"));
				assertNotNull("No session received.", session);
				assertEquals("No views expected.", false, session.hasView());
				testComplete();
			}
		});
	}
	
	@Test
	@Ignore
	public void generateTokenWithPasswordTest() {
		JsonObject request = new JsonObject();
		request.putString("action", "generateToken");
		request.putString("userId", "alice@example.com");
		request.putString("password", "foobar");
		vertx.eventBus().send("appsist:service:auth", request, new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> event) {
				JsonObject body = event.body();
				assertEquals("ok", body.getString("status"));
				assertNotNull(body.getString("token"));
				assertEquals("alice@example.com", body.getString("subject"));
				log.info("Received response: " + body.encode());
				testComplete();
			}
		});
	}
	
	@Test
	@Ignore
	public void generateTokenWithPinTest() {
		JsonObject request = new JsonObject();
		request.putString("action", "generateToken");
		request.putString("userId", "alice@example.com");
		request.putString("pin", "12345");
		vertx.eventBus().send("appsist:service:auth", request, new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> event) {
				JsonObject body = event.body();
				assertEquals("ok", body.getString("status"));
				assertNotNull(body.getString("token"));
				assertEquals("alice@example.com", body.getString("subject"));
				log.info("Received response: " + body.encode());
				testComplete();
			}
		});
	}
	
	@Test
	@Ignore
	public void generateTokenWithHashTest() {
		JsonObject request = new JsonObject();
		request.putString("action", "generateToken");
		request.putString("userId", "alice@example.com");
		request.putString("hash", "c3ab8ff13720e8ad9047dd39466b3c8974e592c2fa383d4a3960714caef0c4f2");
		vertx.eventBus().send("appsist:service:auth", request, new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> event) {
				JsonObject body = event.body();
				assertEquals("ok", body.getString("status"));
				assertNotNull(body.getString("token"));
				assertEquals("alice@example.com", body.getString("subject"));
				log.info("Received response: " + body.encode());
				testComplete();
			}
		});
	}

	@Test
	@Ignore
	public void validateTokenTest() {
		JsonObject request = new JsonObject();
		request.putString("action", "validateToken");
		request.putString("subject", "alice@example.com");
		request.putString("token", testToken);
		vertx.eventBus().send("appsist:service:auth", request, new Handler<Message<JsonObject>>() {

			@Override
			public void handle(Message<JsonObject> event) {
				JsonObject body = event.body();
				assertEquals("ok", body.getString("status"));
				log.info("Received response: " + body.encode());
				testComplete();
			}
			
		});
	}
	
	@Test
	@Ignore
	public void authenticateUserWithPasswordTest() {
		JsonObject request = new JsonObject();
		request.putString("action", "authenticateUser");
		request.putString("userId", "alice.tester@example.com");
		request.putString("password", "foobar");
		vertx.eventBus().send("appsist:service:auth", request, new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> event) {
				JsonObject body = event.body();
				assertEquals("Invalid status of response.", "ok", body.getString("status"));
				assertNotNull("Missing user object.", body.getObject("user"));
				new User(body.getObject("user"));
				testComplete();
			}
		});
		
	}
	
	@Test
	@Ignore
	public void authenticateUserWithPINTest() {
		JsonObject request = new JsonObject();
		request.putString("action", "authenticateUser");
		request.putString("userId", "alice.tester@example.com");
		request.putString("pin", "12345");
		vertx.eventBus().send("appsist:service:auth", request, new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> event) {
				JsonObject body = event.body();
				assertEquals("Invalid status of response.", "ok", body.getString("status"));
				assertNotNull("Missing user object.", body.getObject("user"));
				new User(body.getObject("user"));
				testComplete();
			}
		});
	}
	
	@Test
	@Ignore
	public void authenticateUserWithHashTest() {
		JsonObject request = new JsonObject();
		request.putString("action", "authenticateUser");
		request.putString("userId", "alice.tester@example.com");
		request.putString("hash", "c3ab8ff13720e8ad9047dd39466b3c8974e592c2fa383d4a3960714caef0c4f2");
		vertx.eventBus().send("appsist:service:auth", request, new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> event) {
				JsonObject body = event.body();
				assertEquals("Invalid status of response.", "ok", body.getString("status"));
				assertNotNull("Missing user object.", body.getObject("user"));
				new User(body.getObject("user"));
				testComplete();
			}
		});
	}
	
	@Test
	@Ignore
	public void authenticateUserWithWrongPasswordTest() {
		JsonObject request = new JsonObject();
		request.putString("action", "authenticateUser");
		request.putString("userId", "alice.tester@example.com");
		request.putString("password", "bar");
		vertx.eventBus().send("appsist:service:auth", request, new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> event) {
				JsonObject body = event.body();
				assertEquals("Invalid status of response.", "error", body.getString("status"));
				testComplete();
			}
		});
	}
	
	@Test
	@Ignore
	public void authenticateUserWithWrongPINTest() {
		JsonObject request = new JsonObject();
		request.putString("action", "authenticateUser");
		request.putString("userId", "alice.tester@example.com");
		request.putString("ping", "54321");
		vertx.eventBus().send("appsist:service:auth", request, new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> event) {
				JsonObject body = event.body();
				assertEquals("Invalid status of response.", "error", body.getString("status"));
				testComplete();
			}
		});
	}
	
	@Test
	@Ignore
	public void authenticateUserWithWrongHashTest() {
		JsonObject request = new JsonObject();
		request.putString("action", "authenticateUser");
		request.putString("userId", "alice.tester@example.com");
		request.putString("hash", "d3ab8ff13720e8ad9047dd39466b3c8974e592c2fa383d4a3960714caef0c4f3");
		vertx.eventBus().send("appsist:service:auth", request, new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> event) {
				JsonObject body = event.body();
				assertEquals("Invalid status of response.", "error", body.getString("status"));
				testComplete();
			}
		});
	}
	
	@Test
	@Ignore
	public void getUserTest() {
		JsonObject request = new JsonObject();
		request.putString("action", "getUser");
		request.putString("userId", "alice.tester@example.com");
		vertx.eventBus().send("appsist:service:auth", request, new Handler<Message<JsonObject>>() {

			@Override
			public void handle(Message<JsonObject> event) {
				JsonObject body = event.body();
				assertEquals("Invalid status of response.", "ok", body.getString("status"));
				User user = new User(body.getObject("user"));
				assertEquals("Invalid user name.", "Alice Tester", user.getDisplayName());
				testComplete();
			}
		});
	}
	
	@Test
	@Ignore
	public void getUserStatusTest() {
		JsonObject request = new JsonObject();
		request.putString("action", "getUserStatus");
		request.putString("userId", "alice.tester@example.com");
		vertx.eventBus().send("appsist:service:auth", request, new Handler<Message<JsonObject>>() {

			@Override
			public void handle(Message<JsonObject> event) {
				JsonObject body = event.body();
				assertEquals("Invalid status of response.", "ok", body.getString("status"));
				testComplete();
			}
		});
	}
	
	@Test
	@Ignore
	public void authorizeResourceTest() {
		JsonObject request = new JsonObject();
		request.putString("action", "authorizeResource");
		request.putString("sessionId", testSession.getId());
		request.putString("token", testToken);
		request.putString("resourceId", "resourceA");
		vertx.eventBus().send("appsist:service:auth", request, new Handler<Message<JsonObject>>() {

			@Override
			public void handle(Message<JsonObject> event) {
				JsonObject body = event.body();
				assertEquals("Invalid status of response.", "ok", body.getString("status"));
				testComplete();
			}
			
		});
	}
}
