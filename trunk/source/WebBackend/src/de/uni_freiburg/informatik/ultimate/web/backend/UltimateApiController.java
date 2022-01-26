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

import org.xml.sax.SAXException;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import de.uni_freiburg.informatik.ultimate.core.coreplugin.Activator;
import de.uni_freiburg.informatik.ultimate.core.lib.toolchain.RunDefinition;
import de.uni_freiburg.informatik.ultimate.core.model.IController;
import de.uni_freiburg.informatik.ultimate.core.model.ICore;
import de.uni_freiburg.informatik.ultimate.core.model.ISource;
import de.uni_freiburg.informatik.ultimate.core.model.ITool;
import de.uni_freiburg.informatik.ultimate.core.model.IToolchainData;
import de.uni_freiburg.informatik.ultimate.core.model.preferences.IPreferenceInitializer;
import de.uni_freiburg.informatik.ultimate.core.model.results.IResult;
import de.uni_freiburg.informatik.ultimate.core.model.services.IUltimateServiceProvider;
import de.uni_freiburg.informatik.ultimate.util.CoreUtil;
import de.uni_freiburg.informatik.ultimate.web.backend.dto.ToolchainResponse;
import de.uni_freiburg.informatik.ultimate.web.backend.util.FileUtil;
import de.uni_freiburg.informatik.ultimate.web.backend.util.Request;
import de.uni_freiburg.informatik.ultimate.web.backend.util.ServletLogger;
import de.uni_freiburg.informatik.ultimate.web.backend.util.WebBackendToolchainJob;

public class UltimateApiController implements IController<RunDefinition> {

	private final ServletLogger mLogger;
	private static final long TIMEOUT = 15 * 1000;
	private File mInputFile;
	private File mToolchainFile;
	private File mSettingsFile;
	private final Request mRequest;
	private ICore<RunDefinition> mCore;
	public static final boolean DEBUG = true;

	public UltimateApiController(final Request request) {
		mLogger = request.getLogger();
		mRequest = request;
	}

	public void run() {

		final ToolchainResponse tcResponse = new ToolchainResponse(mRequest.getRequestId());
		try {
			final WebBackendToolchainJob job =
					new WebBackendToolchainJob("WebBackendToolchainJob for request " + mRequest.getRequestId(), mCore,
							this, mLogger, new File[] { mInputFile }, mRequest.getRequestId());
			job.schedule();

		} catch (final Exception t) {
			mLogger.error("Failed to run Ultimate", t);
			tcResponse.setErrorMessage("Failed to run Ultimate: " + t.getMessage());
		}
		try {
			tcResponse.store(mLogger);
		} catch (final IOException ex) {
			mLogger.error("Failed to store toolchain response", ex);
		}
	}

	/**
	 * Add user frontend settings to the plugins in the toolchain.
	 *
	 * @param tcData
	 * @return Updated UltimateServiceProvider
	 */
	private IUltimateServiceProvider addUserSettings(final IToolchainData<RunDefinition> tcData) {
		// TODO: Add timeout to the API request and use it.
		// Get the user settings from the request
		final List<Map<String, String>> userSettings;
		try {
			mLogger.info("Apply user settings to run configuration.");
			final String usParam = mRequest.getSingleParameter("user_settings");
			final Map<String, List<Map<String, String>>> parsed =
					new Gson().fromJson(usParam, new TypeToken<Map<String, List<Map<String, String>>>>() {
					}.getType());
			userSettings = parsed.get("user_settings");

		} catch (final JsonParseException e) {
			mLogger.error("Could not fetch user settings: %s", e.getMessage());
			return tcData.getServices();
		}

		// TODO: DD: Check that this works as intended, in particular under multiple clients.
		final IUltimateServiceProvider services = tcData.getServices()
				.registerPreferenceLayer(UltimateApiController.class, mCore.getRegisteredUltimatePluginIDs());

		for (final Map<String, String> userSetting : userSettings) {
			final String pluginId = userSetting.get("plugin_id");
			final String key = userSetting.get("key");

			// Check if the setting is in the white-list.
			if (!Config.USER_SETTINGS_WHITELIST.isPluginKeyWhitelisted(pluginId, key)) {
				mLogger.info("User setting for plugin=%s, key=%s is not in whitelist. Ignoring.", pluginId, key);
				continue;
			}

			// Apply the setting.
			switch (userSetting.get("type")) {
			case "bool":
			case "int":
			case "string":
			case "real":
				services.getPreferenceProvider(pluginId).put(key, userSetting.get("value"));
				break;
			default:
				mLogger.info("User setting type %s is unknown. Ignoring", userSetting.get("type"));
				break;
			}
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
		mLogger.info("Prepare input files for RequestId: %s", mRequest.getRequestId());
		try {
			final String timestamp = CoreUtil.getCurrentDateTimeAsString();
			setInputFile(mRequest);
			setToolchainFile(mRequest);
			mLogger.info("Written temporary files to %s with timestamp %s", mInputFile.getParent(), timestamp);
		} catch (final IOException e) {
			try {
				final ToolchainResponse tcResponse = new ToolchainResponse(mRequest.getRequestId());
				tcResponse.setErrorMessage("Internal server error: IO");
				tcResponse.store(mLogger);
			} catch (final IOException ex) {
				mLogger.fatal("Error during IOException: %s", ex);
			}
			mLogger.fatal("Internal server error: %s", e);
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
	 * @throws IOException
	 */
	private void setInputFile(final Request internalRequest) throws IOException {
		final String code = internalRequest.getSingleParameter("code");
		final String fileExtension = internalRequest.getSingleParameter("code_file_extension");
		mInputFile = writeTemporaryFile(mRequest.getRequestId() + "_input" + fileExtension, code);
	}

	/**
	 * Set temporary settings file as sent by the web-frontend.
	 *
	 * @param internalRequest
	 * @throws IOException
	 */
	private void setToolchainFile(final Request internalRequest) throws IOException {
		final String tcXml = internalRequest.getSingleParameter("ultimate_toolchain_xml");
		mToolchainFile = writeTemporaryFile(mRequest.getRequestId() + "_toolchain.xml", tcXml);
	}

	/**
	 * Creates a file in the default temporary-file.
	 *
	 * @param name
	 *            The name of the file (without file extension).
	 * @param content
	 *            Content to end up in the file.
	 * @return
	 * @throws IOException
	 */
	private static File writeTemporaryFile(final String name, final String content) throws IOException {
		final File file = FileUtil.getTmpDir().toPath().resolve(name).toFile();
		try (final Writer fstream = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
			fstream.write(content);
		}
		return file;
	}
}
