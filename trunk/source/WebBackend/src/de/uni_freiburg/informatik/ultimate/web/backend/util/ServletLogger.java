package de.uni_freiburg.informatik.ultimate.web.backend.util;

import javax.servlet.http.HttpServlet;

import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;
import de.uni_freiburg.informatik.ultimate.util.CoreUtil;

public class ServletLogger implements ILogger {

	private final HttpServlet mServlet;
	private final boolean mDebug;

	public ServletLogger(final HttpServlet servlet, final String id, final boolean debug) {
		mServlet = servlet;
		mDebug = debug;
	}

	@Override
	public boolean isDebugEnabled() {
		return mDebug;
	}

	public void logDebug(final String message) {
		if (!mDebug || message == null) {
			return;
		}
		final String stampedMsg = "[" + CoreUtil.getCurrentDateTimeAsString() + "][DEBUG] " + message;
		mServlet.log(stampedMsg);
		System.out.println(stampedMsg);
	}

	public void log(final String message) {
		if (message == null) {
			return;
		}
		final String stampedMsg = "[" + CoreUtil.getCurrentDateTimeAsString() + "] " + message;
		mServlet.log(stampedMsg);
		System.out.println(stampedMsg);
	}

	public void logException(final String message, final Throwable t) {
		if (message == null) {
			return;
		}
		final String stampedMsg = "[" + CoreUtil.getCurrentDateTimeAsString() + "] " + message;
		mServlet.log(stampedMsg, t);
		System.out.println(stampedMsg + " " + t.toString());
	}

	@Override
	public void fatal(final Object msg, final Throwable t) {
		// TODO Auto-generated method stub

	}

	@Override
	public void error(final Object msg, final Throwable t) {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean isLogLevelEnabled(final LogLevel level) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void log(final LogLevel level, final String message) {
		// TODO Auto-generated method stub

	}

	@Override
	public void setLevel(final LogLevel level) {
		// TODO Auto-generated method stub

	}
}
