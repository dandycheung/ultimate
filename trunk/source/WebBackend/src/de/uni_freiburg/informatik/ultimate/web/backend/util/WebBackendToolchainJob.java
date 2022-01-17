package de.uni_freiburg.informatik.ultimate.web.backend.util;

import java.io.File;
import java.util.concurrent.CountDownLatch;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.json.JSONException;
import org.json.JSONObject;

import de.uni_freiburg.informatik.ultimate.core.coreplugin.Activator;
import de.uni_freiburg.informatik.ultimate.core.coreplugin.RcpProgressMonitorWrapper;
import de.uni_freiburg.informatik.ultimate.core.coreplugin.exceptions.ParserInitializationException;
import de.uni_freiburg.informatik.ultimate.core.coreplugin.toolchain.DefaultToolchainJob;
import de.uni_freiburg.informatik.ultimate.core.lib.toolchain.RunDefinition;
import de.uni_freiburg.informatik.ultimate.core.model.IController;
import de.uni_freiburg.informatik.ultimate.core.model.ICore;
import de.uni_freiburg.informatik.ultimate.core.model.IToolchain.ReturnCode;
import de.uni_freiburg.informatik.ultimate.core.model.IToolchainData;
import de.uni_freiburg.informatik.ultimate.core.model.IToolchainProgressMonitor;

public class WebBackendToolchainJob extends DefaultToolchainJob {

	private final JSONObject mResult;
	private final ServletLogger mServletLogger;
	private final String mId;

	public WebBackendToolchainJob(final String name, final ICore<RunDefinition> core,
			final IController<RunDefinition> controller, final ServletLogger logger, final File[] input,
			final JSONObject result, final String id) {
		super(name, core, controller, logger, input);
		mResult = result;
		mServletLogger = logger;
		mId = id;
	}

	@Override
	protected IStatus run(final IProgressMonitor monitor) {
		final IToolchainProgressMonitor tpm = RcpProgressMonitorWrapper.create(monitor);
		tpm.beginTask(getName(), IProgressMonitor.UNKNOWN);

		try {
			setToolchain(mCore.requestToolchain(mInputFiles));
			tpm.worked(1);

			mToolchain.init(tpm);
			tpm.worked(1);

			if (!mToolchain.initializeParsers()) {
				throw new ParserInitializationException();
			}
			tpm.worked(1);

			final IToolchainData<RunDefinition> chain = mToolchain.makeToolSelection(tpm);
			if (chain == null) {
				mServletLogger.fatal("Toolchain selection failed, aborting...");
				return new Status(IStatus.CANCEL, Activator.PLUGIN_ID, IStatus.CANCEL, "Toolchain selection canceled",
						null);
			}
			setServices(chain.getServices());
			tpm.worked(1);

			mToolchain.runParsers();
			tpm.worked(1);

			return convert(mToolchain.processToolchain(tpm));
		} catch (final Throwable e) {
			mServletLogger.error("Error running the Toolchain: " + e.getMessage());
			return handleException(e);
		} finally {
			tpm.done();
			releaseToolchain();
		}
	}

	@Override
	protected IStatus convert(final ReturnCode result) {
		switch (result) {
		case Ok:
		case Cancel:
		case Error:
			try {
				UltimateResultProcessor.processUltimateResults(mServletLogger,
						mToolchain.getCurrentToolchainData().getServices(), mResult);
			} catch (final JSONException ex) {
				mServletLogger.error("Exception during result conversion", ex);
				ex.printStackTrace();
			}
			storeResults();
			break;
		default:
			mServletLogger.error("Unknown return code %s", result);
			break;
		}
		return super.convert(result);
	}

	@Override
	public boolean belongsTo(final Object family) {
		return family == "WebBackendToolchainJob";
	}

	private void storeResults() {
		try {
			final JobResult jobResult = new JobResult(mId);
			mResult.put("status", "done");
			jobResult.setJson(mResult);
			jobResult.store();
			mServletLogger.info("Stored toolchain result to: %s", jobResult.getJsonFile());
		} catch (final Exception ex) {
			mServletLogger.error("Error while storing toolchain job result", ex);
		}
	}

	public String getId() {
		return mId;
	}

	public CountDownLatch cancelToolchain() {
		return mServices.getProgressMonitorService().cancelToolchain();
	}

}
