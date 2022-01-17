package de.uni_freiburg.informatik.ultimate.web.backend;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.JAXBException;

import org.eclipse.core.runtime.jobs.IJobManager;
import org.eclipse.core.runtime.jobs.Job;
import org.json.JSONException;
import org.json.JSONObject;
import org.xml.sax.SAXException;

import de.uni_freiburg.informatik.ultimate.core.coreplugin.Activator;
import de.uni_freiburg.informatik.ultimate.core.coreplugin.PluginFactory;
import de.uni_freiburg.informatik.ultimate.core.coreplugin.SettingsManager;
import de.uni_freiburg.informatik.ultimate.core.coreplugin.ToolchainManager;
import de.uni_freiburg.informatik.ultimate.core.coreplugin.UltimateCore;
import de.uni_freiburg.informatik.ultimate.core.coreplugin.services.ToolchainStorage;
import de.uni_freiburg.informatik.ultimate.core.lib.toolchain.RunDefinition;
import de.uni_freiburg.informatik.ultimate.core.lib.toolchain.ToolchainData;
import de.uni_freiburg.informatik.ultimate.core.model.ICore;
import de.uni_freiburg.informatik.ultimate.core.model.IToolchain;
import de.uni_freiburg.informatik.ultimate.core.model.IToolchainData;
import de.uni_freiburg.informatik.ultimate.core.model.IUltimatePlugin;
import de.uni_freiburg.informatik.ultimate.core.model.preferences.IPreferenceInitializer;
import de.uni_freiburg.informatik.ultimate.core.model.preferences.IPreferenceProvider;
import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;
import de.uni_freiburg.informatik.ultimate.core.model.services.ILoggingService;
import de.uni_freiburg.informatik.ultimate.web.backend.util.ApiResponse;
import de.uni_freiburg.informatik.ultimate.web.backend.util.GetApiRequest;
import de.uni_freiburg.informatik.ultimate.web.backend.util.JobResult;
import de.uni_freiburg.informatik.ultimate.web.backend.util.Request;
import de.uni_freiburg.informatik.ultimate.web.backend.util.ServletLogger;
import de.uni_freiburg.informatik.ultimate.web.backend.util.WebBackendToolchainJob;

public class UltimateApiServlet extends HttpServlet implements ICore<RunDefinition>, IUltimatePlugin {

	// TODO: A servlet should not implement ICore

	private static final long serialVersionUID = 1L;
	private static final boolean DEBUG = true;
	private final ServletLogger mServletLogger;
	private ToolchainManager mToolchainManager;
	private final ToolchainStorage mCoreStorage;
	private final SettingsManager mSettingsManager;
	private final PluginFactory mPluginFactory;
	private final ILoggingService mLoggingService;
	private static String sUltimateVersion = new UltimateCore().getUltimateVersionString();

	/**
	 * Constructor.
	 *
	 * @see HttpServlet#HttpServlet()
	 */
	public UltimateApiServlet() {
		mServletLogger = new ServletLogger(this, DEBUG);
		mCoreStorage = new ToolchainStorage();
		mLoggingService = mCoreStorage.getLoggingService();
		final ILogger ultLogger = mLoggingService.getLogger(Activator.PLUGIN_ID);
		mSettingsManager = new SettingsManager(ultLogger);
		mSettingsManager.registerPlugin(this);
		mPluginFactory = new PluginFactory(mSettingsManager, ultLogger);
	}

	/**
	 * Process GET requests
	 */
	@Override
	protected void doGet(final HttpServletRequest request, final HttpServletResponse response) {
		mServletLogger.debug("Connection from %s, GET: %s%s", request.getRemoteAddr(), request.getRequestURL(),
				request.getQueryString() == null ? "" : "?" + request.getQueryString());
		try {
			processAPIGetRequest(request, response);
		} catch (final IOException ex) {
			mServletLogger.error("Exception during GET: ", ex);
		}

	}

	/**
	 * Process POST requests
	 */
	@Override
	protected void doPost(final HttpServletRequest request, final HttpServletResponse response) {
		mServletLogger.debug("Connection from %s, POST: %s", request.getRemoteAddr(), request.getRequestURI());
		final ServletLogger sessionLogger = new ServletLogger(this, DEBUG);
		final Request internalRequest = new Request(request, sessionLogger);
		try {
			processAPIPostRequest(internalRequest, response);
		} catch (final IOException ex) {
			mServletLogger.error("Exception during POST: ", ex);
		}

	}

	private void processAPIGetRequest(final HttpServletRequest request, final HttpServletResponse response)
			throws IOException {
		final GetApiRequest apiRequest = new GetApiRequest(request);
		final ApiResponse apiResponse = new ApiResponse(response);

		try {
			switch (apiRequest.getRessourceType()) {
			case VERSION:
				apiResponse.put("ultimate_version", getUltimateVersionString());
				break;
			case JOB:
				handleJobGetRequest(apiRequest, apiResponse);
				break;
			default:
				apiResponse.setStatusError();
				apiResponse.setMessage("unknown request.");
				break;
			}
			apiResponse.write();
		} catch (final Exception e) {
			apiResponse.invalidRequest(e.getMessage());
			if (DEBUG) {
				mServletLogger.error("Invalid JSON in response", e);
			}
		}
	}

	private static void handleJobGetRequest(final GetApiRequest apiRequest, final ApiResponse apiResponse)
			throws JSONException, IOException {
		if (apiRequest.getUrlParts().length < 4) {
			apiResponse.setStatusError();
			apiResponse.setMessage("No JobId provided.");
			return;
		}

		final String jobId = apiRequest.getUrlParts()[3];

		switch (apiRequest.getTaskType()) {
		case GET:
			final JobResult jobResult = new JobResult(jobId);
			jobResult.load();
			apiResponse.mergeJSON(jobResult.getJson());
			break;
		case DELETE:
			final boolean canceled = cancelToolchainJob(jobId);
			if (!canceled) {
				apiResponse.setStatusError();
			}
			final String message = canceled ? "Job " + jobId + " canceled." : "No unfinished job " + jobId + " found.";
			apiResponse.setMessage(message);
			break;
		default:
			apiResponse.setStatusError();
			apiResponse.setMessage("Task not supported for ressource " + apiRequest.getRessourceType());
			break;
		}
	}

	/**
	 * Handle POST request. Write result to HttpServletResponse via APIResponse.
	 *
	 * @param internalRequest
	 * @param responseWriter
	 */
	private void processAPIPostRequest(final Request internalRequest, final HttpServletResponse response)
			throws IOException {
		final ApiResponse apiResponse = new ApiResponse(response);

		try {
			mServletLogger.debug("Process API POST request.");

			if (internalRequest.getParameterList().containsKey("action")) {
				mServletLogger.debug("Initiate Ultimate run for request: %s", internalRequest.toString());
				apiResponse.mergeJSON(initiateUltimateRun(internalRequest));
			} else {
				apiResponse.setStatusError();
				apiResponse.setMessage("Invalid request: Missing `action` parameter.");
			}
			apiResponse.write();
		} catch (final JSONException e) {
			apiResponse.invalidRequest(e.getMessage());
			if (DEBUG) {
				mServletLogger.error("Invalid JSON in response", e);
			}
		}
	}

	/**
	 * Initiate ultimate run for the request. Return the results as a json object.
	 *
	 * @param internalRequest
	 * @return
	 * @throws JSONException
	 */
	private JSONObject initiateUltimateRun(final Request internalRequest) throws JSONException {
		try {
			final String action = internalRequest.getSingleParameter("action");
			if (!action.equals("execute")) {
				internalRequest.getLogger().debug("Don't know how to handle action: %s", action);
				final JSONObject json = new JSONObject();
				json.put("error", "Invalid request: Unknown `action` parameter ( " + action + ").");
				return json;
			}
			final JSONObject json = new JSONObject();
			json.put("requestId", internalRequest.getRequestId());
			json.put("status", "creating");
			final UltimateApiController controller = new UltimateApiController(internalRequest, json);
			final int status = controller.init(this);
			mToolchainManager = new ToolchainManager(mLoggingService, mPluginFactory, controller);
			if (status == 0) {
				controller.run();
			}
			mToolchainManager.close();
			return json;
		} catch (final IllegalArgumentException e) {
			final JSONObject json = new JSONObject();
			json.put("error", "Invalid request: " + e.getMessage());
			return json;
		}
	}

	private static boolean cancelToolchainJob(final String jobId) {
		final Job[] jobs = getPendingToolchainJobs();
		for (final Job job2 : jobs) {
			final WebBackendToolchainJob job = (WebBackendToolchainJob) job2;
			if (job.getId().equals(jobId)) {
				job.cancelToolchain();
				return true;
			}
		}
		return false;
	}

	/**
	 * Jobs (by family "WebBackendToolchainJob") running or queued.
	 *
	 * @return
	 */
	private static Job[] getPendingToolchainJobs() {
		final IJobManager jobManager = Job.getJobManager();
		return jobManager.find("WebBackendToolchainJob");
	}

	/***************************** ICore Implementation *********************/

	@Override
	public IToolchainData<RunDefinition> createToolchainData(final String filename)
			throws FileNotFoundException, JAXBException, SAXException {
		if (!new File(filename).exists()) {
			throw new FileNotFoundException("The specified toolchain file " + filename + " was not found");
		}

		final ToolchainStorage tcStorage = new ToolchainStorage();
		return new ToolchainData(filename, tcStorage, tcStorage);
	}

	@Override
	public IToolchainData<RunDefinition> createToolchainData() {
		return null;
	}

	@Override
	public IToolchain<RunDefinition> requestToolchain(final File[] inputFiles) {
		return mToolchainManager.requestToolchain(inputFiles);
	}

	@Override
	public void releaseToolchain(final IToolchain<RunDefinition> toolchain) {
		mToolchainManager.releaseToolchain(toolchain);
	}

	@Override
	public void savePreferences(final String absolutePath) {

	}

	@Override
	public void loadPreferences(final String absolutePath, final boolean silent) {

	}

	@Override
	public void resetPreferences(final boolean silent) {

	}

	@Override
	public IUltimatePlugin[] getRegisteredUltimatePlugins() {
		return null;
	}

	@Override
	public String[] getRegisteredUltimatePluginIDs() {
		return null;
	}

	@Override
	public ILoggingService getCoreLoggingService() {
		return null;
	}

	@Override
	public IPreferenceProvider getPreferenceProvider(final String pluginId) {
		return null;
	}

	@Override
	public String getUltimateVersionString() {
		return sUltimateVersion;
	}

	/************************* End ICore Implementation *********************/

	/************************* IUltimatePlugin Implementation *********************/

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

	/************************* End IUltimatePlugin Implementation *********************/

}
