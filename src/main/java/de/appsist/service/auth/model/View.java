package de.appsist.service.auth.model;

import org.vertx.java.core.json.JsonObject;

/**
 * Model for a view.
 * @author simon.schwantzer(at)im-c.de
 */
public class View {
	public final JsonObject json;

	/**
	 * Creates a view based on the given JSON object.
	 * @param json JSON object to be wrapped.
	 * @throws IllegalArgumentException The given JSON object does not represent a view model.
	 */
	public View(JsonObject json) throws IllegalArgumentException {
		this.json = json;
		validateJson(json);
	}
	
	private static void validateJson(JsonObject json) throws IllegalArgumentException {
		if (json == null) throw new IllegalArgumentException("Field missing.");
		String id = json.getString("id");
		if (id == null || id.isEmpty()) {
			throw new IllegalArgumentException("Missing identifier (id).");
		}
		String deviceClass = json.getString("deviceClass");
		if (deviceClass == null || deviceClass.isEmpty()) {
			throw new IllegalArgumentException("Missing device class (deviceClass).");
		}
		String deviceId = json.getString("deviceId");
		if (deviceId == null || deviceId.isEmpty()) {
			throw new IllegalArgumentException("Missing device identifier (deviceId).");
		}
	}
	
	/**
	 * Creates a new view.
	 * @param id Unique ID for this view.
	 * @param deviceClass Identifier for the device class.
	 * @param deviceId Identifier for the device.
	 */
	public View(String id, String deviceClass, String deviceId) {
		JsonObject json = new JsonObject();
		json.putString("id", id);
		json.putString("deviceClass", deviceClass);
		json.putString("deviceId", deviceId);
		this.json = json;
	}
	
	/**
	 * Returns the JSON representation of the model.
	 * @return JSON object.
	 */
	public JsonObject asJson() {
		return json;
	}
	
	/**
	 * Returns the unique identifier of the view.
	 * @return UUID.
	 */
	public String getId() {
		return json.getString("id");
	}
	
	/**
	 * Returns the device class of the view.
	 * @return Device class identifier.
	 */
	public String getDeviceClass() {
		return json.getString("deviceClass");
	}
	
	/**
	 * Returns the device id of the view.
	 * @return Device identifier.
	 */
	public String getDeviceId() {
		return json.getString("deviceId");
	}
}
