package de.uni_freiburg.informatik.ultimate.web.backend;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Optional;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.JAXBException;

import org.eclipse.core.runtime.jobs.IJobManager;
import org.eclipse.core.runtime.jobs.Job;
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
import de.uni_freiburg.informatik.ultimate.web.backend.dto.ApiResponse;
import de.uni_freiburg.informatik.ultimate.web.backend.dto.ErrorResponse;
import de.uni_freiburg.informatik.ultimate.web.backend.dto.GenericResponse;
import de.uni_freiburg.informatik.ultimate.web.backend.dto.ToolchainResponse;
import de.uni_freiburg.informatik.ultimate.web.backend.dto.VersionResponse;
import de.uni_freiburg.informatik.ultimate.web.backend.util.GetApiRequest;
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
		final ApiResponse apiResponse = new ApiResponse();

		try {
			switch (apiRequest.getRessourceType()) {
			case VERSION:
				new VersionResponse(getUltimateVersionString()).write(response);
				break;
			case JOB:
				handleJobGetRequest(apiRequest, apiResponse).write(response);
				break;
			default:
				new ErrorResponse("unknown request").write(response);
				break;
			}
		} catch (final IOException e) {
			new ErrorResponse(e.getMessage()).write(response);
			mServletLogger.error("IOException during response", e);
		}
	}

	private ApiResponse handleJobGetRequest(final GetApiRequest apiRequest, final ApiResponse apiResponse)
			throws IOException {
		if (apiRequest.getUrlParts().length < 4) {
			return new ErrorResponse("No JobId provided");
		}

		final String jobId = apiRequest.getUrlParts()[3];
		switch (apiRequest.getTaskType()) {
		case GET:
			final Optional<ToolchainResponse> toolchainResponse = ToolchainResponse.load(mServletLogger, jobId);
			if (toolchainResponse.isEmpty()) {
				return new ErrorResponse("Unknown JobId");
			}
			return toolchainResponse.get();
		case DELETE:
			return cancelToolchainJob(jobId);
		default:
			return new ErrorResponse("Task not supported for ressource " + apiRequest.getRessourceType());
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
		try {
			mServletLogger.debug("Process API POST request.");
			if (internalRequest.getParameterList().containsKey("action")) {
				mServletLogger.debug("Initiate Ultimate run for request: %s", internalRequest.toString());
				scheduleUltimateRun(internalRequest).write(response);
			} else {
				new ErrorResponse("Invalid request: Missing `action` parameter.").write(response);
			}

		} catch (final IOException e) {
			new ErrorResponse(e.getMessage()).write(response);
			mServletLogger.error("IOException during POST", e);
		}
	}

	/**
	 * Initiate ultimate run for the request. Return the results as a json object.
	 *
	 */
	private ApiResponse scheduleUltimateRun(final Request request) {
		try {
			final String action = request.getSingleParameter("action");
			if (!action.equals("execute")) {
				request.getLogger().debug("Don't know how to handle action: %s", action);
				return new ErrorResponse("Invalid request: Unknown `action` parameter ( " + action + ").");
			}

			final ToolchainResponse tcResponse = new ToolchainResponse(request.getRequestId());
			tcResponse.setStatus("creating");
			final UltimateApiController controller = new UltimateApiController(request);
			final int status = controller.init(this);
			mToolchainManager = new ToolchainManager(mLoggingService, mPluginFactory, controller);
			if (status == 0) {
				controller.run();
			}
			mToolchainManager.close();
			return tcResponse;
		} catch (final IllegalArgumentException e) {
			return new ErrorResponse("Invalid request: " + e.getMessage());
		}
	}

	private static ApiResponse cancelToolchainJob(final String jobId) {
		for (final Job job : getPendingToolchainJobs()) {
			final WebBackendToolchainJob tcJob = (WebBackendToolchainJob) job;
			if (tcJob.getId().equals(jobId)) {
				tcJob.cancelToolchain();
				return new GenericResponse(String.format("JobId %s canceled", jobId));
			}
		}
		return new ErrorResponse("Unknown JobId");
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
