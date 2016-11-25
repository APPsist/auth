package de.appsist.service.auth.model;

import java.util.ArrayList;
import java.util.List;

import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

/**
 * Model for a user.
 * @author simon.schwantzer(at)im-c.de
 */
public class User {
	private final JsonObject json;
	
	/**
	 * Creates a new user based on the given JSON object.
	 * @param json JSON object to wrap.
	 */
	public User(JsonObject json) {
		this.json = json;
	}

	/**
	 * Creates a new user object.
	 * @param id ID for the user.
	 * @param firstName First name of the user.
	 * @param lastName Last name of the user.
	 * @param displayName Name to display for the user.
	 * @param mail Email address of the user.
	 */
	public User(String id, String firstName, String lastName, String displayName, String mail) {
		this.json = new JsonObject()
			.putString("id", id)
			.putString("firstName", firstName)
			.putString("lastName", lastName)
			.putString("displayName", displayName)
			.putString("mail", mail)
			.putArray("resources", new JsonArray());
		
	}
	
	/**
	 * Returns the JSON object wrapped by this model.
	 * @return JSON representation of the user model.
	 */
	public JsonObject asJson() {
		return json;
	}
	
	/**
	 * Returns a list of fields to be returned in public requests.
	 * @return Array of field names.
	 */
	public static String[] getPublicFields() {
		return new String[]{"id", "firstName", "lastName", "displayName", "position"};
	}
	
	/**
	 * Returns a list of fields to be returned in restricted requests.
	 * @return Array of field names.
	 */
	public static String[] getRestrictedFields() {
		return new String[]{"id", "firstName", "lastName", "displayName", "position", "mail", "resources", "mobile"};
	}
	
	/**
	 * Returns the user identifier.
	 * @return User identifier.
	 */
	public String getId() {
		return json.getString("id");
	}
	
	/**
	 * Returns the first name of the user.
	 * @return First name.
	 */
	public String getFirstName() {
		return json.getString("firstName");
	}
	
	/**
	 * Sets the first name of the user.
	 * @param firstName First name.
	 */
	public void setFirstName(String firstName) {
		json.putString("firstName", firstName);
	}
	
	/**
	 * Returns the last name of the user.
	 * @return Last name.
	 */
	public String getLastName() {
		return json.getString("lastName");
	}
	
	/**
	 * Sets the last name of the user.
	 * @param lastName Last name.
	 */
	public void setLastName(String lastName) {
		json.putString("lastName", lastName);
	}
	
	/**
	 * Returns the display name of the user.
	 * @return Name to display.
	 */
	public String getDisplayName() {
		return json.getString("displayName");
	}
	
	/**
	 * Sets the display name of the user.
	 * @param displayName Name to display.
	 */
	public void setDisplayName(String displayName) {
		json.putString("displayName", displayName);
	}
	
	/**
	 * Returns the email address of the user.
	 * @return Email address.
	 */
	public String getMail() {
		return json.getString("mail");
	}
	
	/**
	 * Sets the email address of the user.
	 * @param mail Email address.
	 */
	public void setMail(String mail) {
		json.putString("mail", mail);
	}
	
	/**
	 * Returns the position of the user. 
	 * @return Position to display. May be <code>null</code>.
	 */
	public String getPosition() {
		return json.getString("position");
	}
	
	public void setPosition(String position) {
		if (position != null) {
			json.putString("position", position);
		} else {
			json.removeField("position");
		}
	}
	
	/**
	 * Returns the mobile phone number.
	 * @return Mobile phone number. May be <code>null</code>.
	 */
	public String getMobile() {
		return json.getString("mobile");
	}
	
	/**
	 * Sets the mobile phone number.
	 * @param mobile Mobile phone number to set. May be <code>null</code>.
	 */
	public void setMobile(String mobile) {
		if (mobile != null) {
			json.putString("mobile", mobile);
		} else {
			json.removeField("mobile");
		}
	}
	
	/**
	 * Returns the hashed password of the user.
	 * @return Password hash.
	 */
	public String getHash() {
		return json.getString("hash");
	}
	
	/**
	 * Sets the hashed password of the user.
	 * @param hash Password hash.
	 */
	public void setHash(String hash) {
		json.putString("hash", hash);
	}
	
	/**
	 * Returns the PIN of the user.
	 * @return PIN for authentication. May be <code>null</code>.
	 */
	public String getPin() {
		return json.getString("pin");
	}
	
	/**
	 * Sets the PIN for the user.
	 * @param pin PIN for authentication. May be <code>null</code>.
	 */
	public void setPin(String pin) {
		if (pin != null) {
			json.putString("pin", pin);
		} else {
			json.removeField("pin");
		}
	}
	
	/**
	 * Returns the resources the user has access to.
	 * @return List of resource identifiers.
	 */
	public List<String> getResources() {
		List<String> resources = new ArrayList<String>();
		JsonArray resourcesArray = json.getArray("resources");
		if (resources != null) {
			for (Object resource : resourcesArray) {
				resources.add((String) resource);
			}
		}
		return resources;
	}
	
	/**
	 * Sets the list of resources the user has access to.
	 * @param resources List of resource identifiers.
	 */
	public void setResources(List<String> resources) {
		JsonArray resourcesArray = new JsonArray();
		for (String resource : resources) {
			resourcesArray.addString(resource);
		}
		json.putArray("resources", resourcesArray);
	}
	
}
