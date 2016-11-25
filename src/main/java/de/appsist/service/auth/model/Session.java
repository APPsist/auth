package de.appsist.service.auth.model;

import java.util.ArrayList;
import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

/**
 * Model for a session.
 * The model is internally represented by a JSON object.
 * @author simon.schwantzer(at)im-c.de
 */
public class Session {
	private final JsonObject json;
	
	/**
	 * Creates a session based on the given JSON object.
	 * @param json JSON object to wrap.
	 * @throws IllegalArgumentException The given JSON object is not a valid session
	 */
	public Session(JsonObject json) throws IllegalArgumentException {
		this.json = json;
		validateJson(json);
	}
	
	private static void validateJson(JsonObject json) throws IllegalArgumentException {
		if (json == null) throw new IllegalArgumentException("Field missing.");
		String id = json.getString("id");
		if (id == null || id.isEmpty()) {
			throw new IllegalArgumentException("Missing identifier (id).");
		}
	}
	
	/**
	 * Returns the JSON object wrapped by this model.
	 * @return JSON representation of the session.
	 */
	public JsonObject asJson() {
		return json;
	}
	
	/**
	 * Creates a new session.
	 * @param id Identifier for the session.
	 */
	public Session(String id) {
		json = new JsonObject();
		json.putString("id", id);
	}
	
	/**
	 * Returns the identifier of the session.
	 * @return Session identifier (UUID).
	 */
	public String getId() {
		return json.getString("id");
	}
	
	/**
	 * Returns the user the session belongs to.
	 * @return User identifier (UUID) or <code>null</code> if the session is no user session.
	 */
	public String getUserId() {
		return json.getString("userId");
	}
	
	/**
	 * Registers a view for the session.
	 * @param view View to register.
	 */
	public void registerView(View view) {
		JsonArray views = json.getArray("views");
		if (views == null) {
			views = new JsonArray();
			json.putArray("views", views);
		}
		views.addObject(view.asJson());
	}
	
	/**
	 * Removes a view for the session.
	 * @param viewId ID of the view which should be removed.
	 */
	public void removeView(String viewId) {
		JsonArray views = json.getArray("views");
		
		if (views != null) {
			JsonArray newViews = new JsonArray();
			for (int i = 0; i < views.size(); i++) {
				View view = new View((JsonObject) views.get(i));
				if (!view.getId().equals(viewId)) {
					newViews.addObject(view.asJson());
				}
			}
			json.putArray("views", newViews);
		}
	}
	
	/**
	 * Checks if at least one view is registered for the session.
	 * @return <code>true</code> if one or more views are registered, otherwise <code>false</code>.
	 */
	public boolean hasView() {
		JsonArray views = json.getArray("views");
		return views != null && views.size() > 0;
	}
	
	public List<View> getViews() {
		List<View> views = new ArrayList<View>();
		JsonArray viewsArray = json.getArray("views");
		if (viewsArray != null) for (Object entry : viewsArray) {
			views.add(new View((JsonObject) entry));
		}
		return views;
	}
	
	/**
	 * Sets the lastActivity field to now.
	 */
	public void update() {
		DateTime now = new DateTime();
		String dateTimeString = ISODateTimeFormat.dateTime().print(now);
		json.putString("lastActivity", dateTimeString);
	}
	
	/**
	 * Returns the last time the session has been updated.  
	 * @return Date time of the last activity. May be <code>null</code>.s
	 */
	public DateTime getLastActivity() {
		String dateTimeString = json.getString("lastActivity");
		return dateTimeString != null ? ISODateTimeFormat.dateTime().parseDateTime(dateTimeString) : null;
	}
}