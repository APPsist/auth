package de.appsist.service.auth;

import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

/**
 * Wrapper for the module configuration.
 * @author simon.schwantzer(at)im-c.de
 */
public class ModuleConfiguration {
	private final JsonObject config;
	
	/**
	 * Creates a wrapper for the given configuration object. 
	 * @param config JSON object containing the module configuration. 
	 * @throws IllegalArgumentException The given configuration is not valid.
	 */
	public ModuleConfiguration(JsonObject config) throws IllegalArgumentException {
		if (config != null) {
			this.config = config;
			validateConfiguration(config);
		} else {
			throw new IllegalArgumentException("Configuration is but may not be null.");
		}
	}
	
	/**
	 * Validate the module configuration.
	 * @param config JSON object to validate.
	 * @throws IllegalArgumentException The JSON object is no valid module configuration. 
	 */
	private void validateConfiguration(JsonObject config) throws IllegalArgumentException {
		JsonObject webserver = config.getObject("webserver");
		if (webserver == null) {
			throw new IllegalArgumentException("Configuration for web server [webserver] is missing.");
		}
		if (getMongoPersistorAddress() == null) {
			throw new IllegalArgumentException("Configuration for MongoDB persistor [mongoPersistorAddress] is missing.");
		}
	}
	
	/**
	 * Returns the JSON object wrapped.
	 * @return JSON object containing the module configuration.
	 */
	public JsonObject asJson() {
		return config;
	}
		
	/**
	 * Returns the list of deployments to be performed.
	 * @return Array with deployment configurations. May be <code>null</code> or empty.
	 */
	public JsonArray getDeployments() {
		return config.getArray("deploys");
	}
	
	/**
	 * Checks if the debug mode is enabled.
	 * @return <code>true</code> of debug mode is enabled, otherwise <code>false</code>.
	 */
	public boolean isDebugModeEnabled() {
		return config.getBoolean("debugMode", false);
	}
	
	/**
	 * Returns the base path for the web server.
	 * @return Web server base path or empty string if not set.
	 */
	public String getWebserverBasePath() {
		String basePath = config.getObject("webserver").getString("basePath");
		return basePath != null ? basePath : "";
	}
	
	/**
	 * Returns the directory containing the static files to be delivered by the web server. 
	 * @return Path for static files or "/" of not set.
	 */
	public String getWebserverStaticDirectory() {
		String path = config.getObject("webserver").getString("statics");
		return path != null ? path : "/";
	}
	
	/**
	 * Returns the port of the web server.
	 * @return Web server port.
	 */
	public int getWebserverPort() {
		return config.getObject("webserver").getInteger("port");
	}
	
	/**
	 * Returns the address of the mongo persistor.
	 * @return
	 */
	public String getMongoPersistorAddress() {
		return config.getString("mongoPersistorAddress");
	}
	
	/**
	 * Returns the hours until a obsolete session is purged.
	 * @return Number of hours. 0 means sessions are not purged.
	 */
	public int getHoursUntilSessionPurged() {
		Integer hours = config.getInteger("hoursUntilSessionPurge");
		return (hours != null) ? hours : 0;
	}
	
	public JsonObject getStatusSignalConfig() {
		return config.getObject("statusSignal");
	}
}
