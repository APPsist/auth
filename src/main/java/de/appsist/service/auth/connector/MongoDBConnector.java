package de.appsist.service.auth.connector;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

/**
 * Connector for the mongo persistor.
 * @author simon.schwantzer(at)im-c.de
 */
public class MongoDBConnector {
	private final EventBus eventBus;
	private final String address;

	public MongoDBConnector(String address, EventBus eventBus) {
		this.address = address;
		this.eventBus = eventBus;
	}
	
	/**
	 * Saves a document in the database.
	 * @param collection Name of the MongoDB collection to save the document in.
	 * @param document Document to save.
	 * @param resultHandler Handler to check if the operation succeeded. May be <code>null</code>.
	 */
	public void save(String collection, JsonObject document, final AsyncResultHandler<Void> resultHandler) {
		JsonObject request = new JsonObject();
		request.putString("action", "save");
		request.putString("collection", collection);
		request.putObject("document", document);
		eventBus.send(address, request, new Handler<Message<JsonObject>>() {
			
			@Override
			public void handle(Message<JsonObject> message) {
				final JsonObject body = message.body();
				if (resultHandler != null) resultHandler.handle(new AsyncResult<Void>() {
					
					@Override
					public boolean succeeded() {
						return "ok".equals(body.getString("status"));
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
						return failed() ? new Throwable(body.getString("message")) : null;
					}
				});
			}
		});
	}
	
	/**
	 * Updates an existing document in the database.
	 * @param collection Name of the MongoDB collection.
	 * @param criteria The selection criteria for the update.
	 * @param newObj Updated document.
	 * @param upsert If set to <code>true</code>, creates a new document when no document matches the query criteria.
	 * @param multi If set to <code>true</code>, updates multiple documents that meet the query criteria. If set to <code>false</code>, updates one document.
	 * @param resultHandler Handler to check if the operation succeeded. May be <code>null</code>.
	 */
	public void update(String collection, JsonObject criteria, JsonObject newObj, boolean upsert, boolean multi, final AsyncResultHandler<Void> resultHandler) {
		JsonObject request = new JsonObject();
		request.putString("action", "update");
		request.putString("collection", collection);
		request.putObject("criteria", criteria);
		request.putObject("objNew", newObj);
		request.putBoolean("upsert", upsert);
		request.putBoolean("multi", multi);
		eventBus.send(address, request, new Handler<Message<JsonObject>>() {

			@Override
			public void handle(Message<JsonObject> message) {
				final JsonObject body = message.body();
				if (resultHandler != null) resultHandler.handle(new AsyncResult<Void>() {
					
					@Override
					public boolean succeeded() {
						return "ok".equals(body.getString("status"));
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
						return failed() ? new Throwable(body.getString("message")) : null;
					}
				});
				
			}
			
		});
	}
	
	/**
	 * Requests documents from the database.
	 * @param collection Name of the MongoDB collection.
	 * @param matcher JSON object to match against to find matching documents. This obeys the normal MongoDB matching rules.
	 * @param keys JSON object that contains the fields that should be returned for matched documents. May be <code>null</code>.
	 * @param resultHandler Handler for the request results.
	 */
	public void find(String collection, JsonObject matcher, JsonObject keys, final AsyncResultHandler<JsonArray> resultHandler) {
		JsonObject request = new JsonObject();
		request.putString("action", "find");
		request.putString("collection", collection);
		request.putObject("matcher", matcher);
		if (keys != null) request.putObject("keys", keys);
		eventBus.send(address, request, new Handler<Message<JsonObject>>() {

			@Override
			public void handle(Message<JsonObject> message) {
				final JsonObject body = message.body();
				resultHandler.handle(new AsyncResult<JsonArray>() {
					
					@Override
					public boolean succeeded() {
						return "ok".equals(body.getString("status"));
					}
					
					@Override
					public JsonArray result() {
						return succeeded() ? body.getArray("results") : null;
					}
					
					@Override
					public boolean failed() {
						return !succeeded();
					}
					
					@Override
					public Throwable cause() {
						return failed() ? new Throwable(body.getString("message")) : null;
					}
				});
			}
		});
	}
	
	/**
	 * Requests a single document from the database.
	 * If the query addresses multiple document, the first one is returned.
	 * @param collection Name of the MongoDB collection.
	 * @param matcher JSON object to match against to find matching documents. This obeys the normal MongoDB matching rules.
	 * @param keys JSON object that contains the fields that should be returned for matched documents. May be <code>null</code>.
	 * @param resultHandler Handler for the request result.
	 */
	public void findOne(String collection, JsonObject matcher, JsonObject keys, final AsyncResultHandler<JsonObject> resultHandler) {
		JsonObject request = new JsonObject();
		request.putString("action", "findone");
		request.putString("collection", collection);
		request.putObject("matcher", matcher);
		if (keys != null) request.putObject("keys", keys);
		
		eventBus.send(address, request, new Handler<Message<JsonObject>>() {

			@Override
			public void handle(Message<JsonObject> message) {
				final JsonObject body = message.body();
				resultHandler.handle(new AsyncResult<JsonObject>() {
					
					@Override
					public boolean succeeded() {
						return "ok".equals(body.getString("status"));
					}
					
					@Override
					public JsonObject result() {
						return succeeded() ? body.getObject("result") : null;
					}
					
					@Override
					public boolean failed() {
						return !succeeded();
					}
					
					@Override
					public Throwable cause() {
						return failed() ? new Throwable(body.getString("message")) : null ;
					}
				});
			}
		});
	}
	
	/* INVALID - Bug in persistor.
	 * Locates and updates a document in the database.
	 * @param collection Collection containing the document to update.
	 * @param matcher JSON object to match against to find matching documents. This obeys the normal MongoDB matching rules.
	 * @param update Update to apply. Use the MongoDB update operators.
	 * @param returnNew If <code>true</code>, the new document will be returned, otherwise the old version.
	 * @param upsert If <code>true</code>, the document will be created if it does not exist.
	 * @param resultHandler Handler to return the document to.
	
	public void findAndModify(String collection, JsonObject matcher, JsonObject update, boolean returnNew, boolean upsert, final AsyncResultHandler<JsonObject> resultHandler) {
		JsonObject request = new JsonObject();
		request.putString("action", "find_and_modify");
		request.putString("collection", collection);
		request.putObject("matcher", matcher);
		request.putObject("update", update);
		request.putBoolean("new", returnNew);
		request.putBoolean("upsert", upsert);
		eventBus.send(address, request, new Handler<Message<JsonObject>>() {
			
			@Override
			public void handle(Message<JsonObject> message) {
				final JsonObject body = message.body();
				resultHandler.handle(new AsyncResult<JsonObject>() {
					
					@Override
					public boolean succeeded() {
						return "ok".equals(body.getString("status"));
					}
					
					@Override
					public JsonObject result() {
						return succeeded() ? body.getObject("result") : null;
					}
					
					@Override
					public boolean failed() {
						return !succeeded();
					}
					
					@Override
					public Throwable cause() {
						return failed() ? new Throwable(body.getString("message")) : null;
					}
				});
			}
		});
	}
	*/
	
	/**
	 * Deletes documents from the database.
	 * @param collection Collection to search for the documents.
	 * @param matcher JSON object to match against the documents in the collection.
	 * @param resultHandler Handler to return the number of deleted objects. May be <code>null</code>.
	 */
	public void delete(String collection, JsonObject matcher, final AsyncResultHandler<Integer> resultHandler) {
		JsonObject request = new JsonObject();
		request.putString("action", "delete");
		request.putString("collection", collection);
		request.putObject("matcher", matcher);
		eventBus.send(address, request, new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> message) {
				final JsonObject body = message.body();
				if (resultHandler != null) resultHandler.handle(new AsyncResult<Integer>() {
					
					@Override
					public boolean succeeded() {
						return "ok".equals(body.getString("status"));
					}
					
					@Override
					public Integer result() {
						return succeeded() ? body.getInteger("number") : null;
					}
					
					@Override
					public boolean failed() {
						return !succeeded();
					}
					
					@Override
					public Throwable cause() {
						return failed() ? new Throwable(body.getString("message")) : null;
					}
				});
			}
		});
	}
	
	/**
	 * Counts documents in the database.
	 * @param collection Collection containing the documents.
	 * @param matcher Matcher for documents to count.
	 * @param resultHandler Handler to return the number of matches.
	 */
	public void count(String collection, JsonObject matcher, final AsyncResultHandler<Integer> resultHandler) {
		JsonObject request = new JsonObject();
		request.putString("action", "count");
		request.putString("collection", collection);
		request.putObject("matcher", matcher);
		eventBus.send(address, request, new Handler<Message<JsonObject>>() {

			@Override
			public void handle(Message<JsonObject> message) {
				final JsonObject body = message.body();
				resultHandler.handle(new AsyncResult<Integer>() {
					
					@Override
					public boolean succeeded() {
						return "ok".equals(body.getString("status"));
					}
					
					@Override
					public Integer result() {
						return succeeded() ? body.getInteger("count") : null;
					}
					
					@Override
					public boolean failed() {
						return !succeeded();
					}
					
					@Override
					public Throwable cause() {
						return failed() ? new Throwable(body.getString("message")) : null;
					}
				});
				
			}
		});
	}
}
