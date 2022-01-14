package de.uni_freiburg.informatik.ultimate.web.backend;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jetty.util.log.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class UserSettingsWhitelist {

	private JSONObject mJSONWhitelist;

	public UserSettingsWhitelist(final String filePath) {
		initFromFile(filePath);
	}

	/**
	 * Check if "pluginId" is available in the whitelist.
	 *
	 * @param pluginId
	 * @return
	 */
	public boolean isPluginIdCovered(final String pluginId) {
		try {
			mJSONWhitelist.getJSONArray(pluginId);
		} catch (final JSONException e) {
			return false;
		}
		return true;
	}

	/**
	 * Check if "pluginId" has white-listed "key".
	 *
	 * @param pluginId
	 * @param key
	 * @return
	 */
	public boolean isPluginKeyWhitelisted(final String pluginId, final String key) {
		try {
			final JSONArray pluginKeys = getPluginKeys(pluginId);
			for (int i = 0; i < pluginKeys.length(); i++) {
				final String pluginKey = (String) pluginKeys.get(i);
				if (pluginKey.equals(key)) {
					return true;
				}
			}
		} catch (final JSONException e) {
			// skip malformed things
		}
		return false;

	}

	private JSONArray getPluginKeys(final String pluginId) throws JSONException {
		return mJSONWhitelist.getJSONArray(pluginId);
	}

	private void initFromFile(final String filePath) {
		final Path file = Paths.get(filePath);
		if (Files.notExists(file)) {
			mJSONWhitelist = new JSONObject();
			Log.getRootLogger().warn(String.format(
					"Could not load user settings whitelist from %s because the file or path does not exist", file));
		} else {
			try (Stream<String> lines = Files.lines(Paths.get(filePath))) {
				final String jsonString = lines.collect(Collectors.joining());
				mJSONWhitelist = new JSONObject(jsonString);
				Log.getRootLogger().info("Loaded user settings whitelist");
			} catch (JSONException | IOException e) {
				Log.getRootLogger().warn(
						String.format("Could not load user settings whitelist from %s: %s", filePath, e.getMessage()));
				mJSONWhitelist = new JSONObject();
			}
		}
	}
}
