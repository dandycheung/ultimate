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
	private final File mToolchainFile;
	private final String mId;

	public WebBackendToolchainJob(final String name, final ICore<RunDefinition> core,
			final IController<RunDefinition> controller, final ServletLogger logger, final File[] input,
			final JSONObject result, final File toolchainFile, final String id) {
		super(name, core, controller, logger, input);
		mResult = result;
		mServletLogger = logger;
		mToolchainFile = toolchainFile;
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
			mServletLogger.log("Error running the Toolchain: " + e.getMessage());
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
			try {
				UltimateResultProcessor.processUltimateResults(mServletLogger,
						mToolchain.getCurrentToolchainData().getServices(), mResult);
				cleanupTempFiles();
				storeResults();
			} catch (final JSONException e) {
				e.printStackTrace();
			}
			return Status.OK_STATUS;
		case Error:
			cleanupTempFiles();
			storeResults();
		default:
			return super.convert(result);
		}
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
			mServletLogger.log("Stored tollchain result to: " + jobResult.getFilePath());
		} catch (final Exception ex) {
			ex.printStackTrace();
		}
	}

	private void cleanupTempFiles() {
		final File logDir = new File(System.getProperty("java.io.tmpdir") + File.separator + "log" + File.separator);
		if (!logDir.exists()) {
			logDir.mkdir();
		}
		mServletLogger.log("Moving input, setting and toolchain file to " + logDir.getAbsoluteFile());
		for (final File file : mInputFiles) {
			file.renameTo(new File(logDir, file.getName()));
		}
		if (mToolchainFile != null) {
			mToolchainFile.renameTo(new File(logDir, mToolchainFile.getName()));
		}
	}

	public String getId() {
		return mId;
	}

	public CountDownLatch cancelToolchain() {
		return mServices.getProgressMonitorService().cancelToolchain();
	}

}
