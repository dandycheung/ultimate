package de.uni_freiburg.informatik.ultimate.web.backend;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.xml.bind.JAXBException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xml.sax.SAXException;

import de.uni_freiburg.informatik.ultimate.core.coreplugin.Activator;
import de.uni_freiburg.informatik.ultimate.core.lib.toolchain.RunDefinition;
import de.uni_freiburg.informatik.ultimate.core.model.IController;
import de.uni_freiburg.informatik.ultimate.core.model.ICore;
import de.uni_freiburg.informatik.ultimate.core.model.ISource;
import de.uni_freiburg.informatik.ultimate.core.model.ITool;
import de.uni_freiburg.informatik.ultimate.core.model.IToolchainData;
import de.uni_freiburg.informatik.ultimate.core.model.IUltimatePlugin;
import de.uni_freiburg.informatik.ultimate.core.model.preferences.IPreferenceInitializer;
import de.uni_freiburg.informatik.ultimate.core.model.results.IResult;
import de.uni_freiburg.informatik.ultimate.core.model.services.IUltimateServiceProvider;
import de.uni_freiburg.informatik.ultimate.util.CoreUtil;
import de.uni_freiburg.informatik.ultimate.web.backend.util.JobResult;
import de.uni_freiburg.informatik.ultimate.web.backend.util.Request;
import de.uni_freiburg.informatik.ultimate.web.backend.util.ServletLogger;
import de.uni_freiburg.informatik.ultimate.web.backend.util.WebBackendToolchainJob;

public class UltimateAPIController implements IUltimatePlugin, IController<RunDefinition> {

	private final ServletLogger mLogger;
	private static final long TIMEOUT = 15 * 1000;
	private File mInputFile;
	private File mToolchainFile;
	private File mSettingsFile;
	private final Request mRequest;
	private final JSONObject mResult;
	private ICore<RunDefinition> mCore;
	public static final boolean DEBUG = true;

	public UltimateAPIController(final Request request, final JSONObject result) {
		mLogger = request.getLogger();
		mRequest = request;
		mResult = result;
	}

	public void run() {
		// TODO: Allow timeout to be set in the API request and use it.
		try {
			final WebBackendToolchainJob job = new WebBackendToolchainJob(
					"WebBackendToolchainJob for request " + mRequest.getRequestId(), mCore, this, mLogger,
					new File[] { mInputFile }, mResult, mToolchainFile, mRequest.getRequestId());
			mResult.put("requestId", mRequest.getRequestId());
			job.schedule();
			mResult.put("status", "scheduled");
			final JobResult jobResult = new JobResult(mRequest.getRequestId());
			jobResult.setJson(mResult);
			jobResult.store();
		} catch (final Throwable t) {
			mLogger.log("Failed to run Ultimate.");
			try {
				mResult.put("error", "Failed to run ULTIMATE: " + t.getMessage());
			} catch (final JSONException e) {
				if (DEBUG) {
					e.printStackTrace();
				}
			}
		}
	}

	/**
	 * Add user frontend settings to the plugins in the toolchain.
	 *
	 * @param tcData
	 * @return Updated UltimateServiceProvider
	 */
	private IUltimateServiceProvider addUserSettings(final IToolchainData<RunDefinition> tcData) {
		final IUltimateServiceProvider services = tcData.getServices();

		/*
		 * // Debug: traverse the toolchain to log its content. RunDefinition tcRD = tcData.getRootElement();
		 * ToolchainListType tc = tcRD.getToolchain(); List<Object> tcPluginOrSubchain = tc.getPluginOrSubchain(); for
		 * (Object pluginOrSubchain : tcPluginOrSubchain) { mLogger.log(pluginOrSubchain.toString()); }
		 */

		// Get the user settings from the request
		try {
			mLogger.log("Apply user settings to run configuration.");
			final JSONObject jsonParameter = new JSONObject(mRequest.getSingleParameter("user_settings"));
			final JSONArray userSettings = jsonParameter.getJSONArray("user_settings");

			for (int i = 0; i < userSettings.length(); i++) {
				final JSONObject userSetting = userSettings.getJSONObject(i);
				final String pluginId = userSetting.getString("plugin_id");
				final String key = userSetting.getString("key");

				// Check if the setting is in the white-list.
				if (!Config.USER_SETTINGS_WHITELIST.isPluginKeyWhitelisted(pluginId, key)) {
					mLogger.log(
							"User setting for plugin=" + pluginId + " key=" + key + " is not in whitelist. Ignoring.");
					continue;
				}

				// Apply the setting.
				switch (userSetting.getString("type")) {
				case "bool":
					services.getPreferenceProvider(pluginId).put(key, userSetting.getBoolean("value"));
					break;
				case "int":
					services.getPreferenceProvider(pluginId).put(key, userSetting.getInt("value"));
					break;
				case "string":
					services.getPreferenceProvider(pluginId).put(key, userSetting.getString("value"));
					break;
				case "real":
					services.getPreferenceProvider(pluginId).put(key, userSetting.getLong("value"));
					break;
				default:
					mLogger.log("User setting type " + userSetting.getString("type") + " is unknown. Ignoring");
				}
			}
		} catch (final JSONException e) {
			mLogger.log("Could not fetch user settings: " + e.getMessage());
		}

		return services;
	}

	/******************* Ultimate Plugin Implementation *****************/

	@Override
	public String getPluginName() {
		return Activator.PLUGIN_NAME;
	}

	@Override
	public String getPluginID() {
		return Activator.PLUGIN_ID;
	}

	@Override
	public IPreferenceInitializer getPreferences() {
		return null;
	}

	/**************** End Ultimate Plugin Implementation *****************/

	/**************** IController Implementation *****************/

	@Override
	public int init(final ICore<RunDefinition> core) {
		if (core == null) {
			return -1;
		}

		mCore = core;

		// Prepare {input, toolchain, settings} as temporary files.
		mLogger.log("Prepare input files for RequestId: " + mRequest.getRequestId());
		try {
			final String timestamp = CoreUtil.getCurrentDateTimeAsString();
			setInputFile(mRequest, timestamp);
			setToolchainFile(mRequest, timestamp);
			mLogger.log("Written temporary files to " + mInputFile.getParent() + " with timestamp " + timestamp);
		} catch (final IOException e) {
			try {
				mResult.put("error", "Internal server error: IO");
			} catch (final JSONException eJson) {
				if (DEBUG) {
					eJson.printStackTrace();
				}
			}
			mLogger.log("Internal server error: " + e.getClass().getSimpleName());
			mLogger.logDebug(e.toString());

			if (DEBUG) {
				e.printStackTrace();
			}
			return -1;
		}

		core.resetPreferences(false);

		return 0;
	}

	@Override
	public ISource selectParser(final Collection<ISource> parser) {
		return null;
	}

	@Override
	public IToolchainData<RunDefinition> selectTools(final List<ITool> tools) {
		try {
			return mCore.createToolchainData(mToolchainFile.getAbsolutePath());
		} catch (FileNotFoundException | JAXBException | SAXException e) {
			mLogger.error("Exception during tool selection: " + e.getClass().getSimpleName() + ": " + e.getMessage());
			return null;
		}
	}

	@Override
	public List<String> selectModel(final List<String> modelNames) {
		return null;
	}

	@Override
	public IToolchainData<RunDefinition> prerun(final IToolchainData<RunDefinition> tcData) {
		return tcData.replaceServices(addUserSettings(tcData));
	}

	@Override
	public void displayToolchainResults(final IToolchainData<RunDefinition> toolchain,
			final Map<String, List<IResult>> results) {

	}

	@Override
	public void displayException(final IToolchainData<RunDefinition> toolchain, final String description,
			final Throwable ex) {

	}

	/**************** End IController Implementation *****************/

	/**
	 * Set the temporary ultimate input file. As set by the web-frontend user in the editor.
	 *
	 * @param internalRequest
	 * @param timestamp
	 * @throws IOException
	 */
	private void setInputFile(final Request internalRequest, final String timestamp) throws IOException {
		final String code = internalRequest.getSingleParameter("code");
		final String fileExtension = internalRequest.getSingleParameter("code_file_extension");
		mInputFile = writeTemporaryFile(timestamp + "_input", code, fileExtension);
	}

	/**
	 * Set temporary settings file as sent by the web-frontend.
	 *
	 * @param internalRequest
	 * @param timestamp
	 * @throws IOException
	 */
	private void setToolchainFile(final Request internalRequest, final String timestamp) throws IOException {
		final String ultimate_toolchain_xml = internalRequest.getSingleParameter("ultimate_toolchain_xml");
		mToolchainFile = writeTemporaryFile(timestamp + "_toolchain", ultimate_toolchain_xml, ".xml");
	}

	/**
	 * Creates a file in the default temporary-file.
	 *
	 * @param name
	 *            The name of the file (without file extension).
	 * @param content
	 *            Content to end up in the file.
	 * @param fileExtension
	 *            File extension to be used in the file path.
	 * @return
	 * @throws IOException
	 */
	private static File writeTemporaryFile(final String name, final String content, final String fileExtension)
			throws IOException {
		final File codeFile = File.createTempFile(name, fileExtension);
		try (final Writer fstream = new OutputStreamWriter(new FileOutputStream(codeFile), StandardCharsets.UTF_8)) {
			fstream.write(content);
		}
		return codeFile;
	}
}
