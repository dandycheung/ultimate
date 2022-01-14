package de.uni_freiburg.informatik.ultimate.web.backend.util;

import javax.servlet.http.HttpServletRequest;

public class GetAPIrequest {
	public RessourceType ressourceType;
	public TaskType taskType;
	public String[] urlParts;

	public GetAPIrequest(final HttpServletRequest request) {
		urlParts = getUrlParts(request.getPathInfo());
		setRessource();
		setTask();
	}

	private void setTask() {
		if (urlParts == null || urlParts.length < 3) {
			taskType = TaskType.UNKNOWN;
			return;
		}

		switch (urlParts[2]) {
		case "get":
			taskType = TaskType.GET;
			break;
		case "delete":
			taskType = TaskType.DELETE;
			break;
		default:
			taskType = TaskType.UNKNOWN;
			break;
		}
	}

	private void setRessource() {
		if (urlParts == null || urlParts.length < 1) {
			ressourceType = RessourceType.UNKNOWN;
			return;
		}

		switch (urlParts[1]) {
		case "job":
			ressourceType = RessourceType.JOB;
			break;
		case "version":
			ressourceType = RessourceType.VERSION;
			break;

		default:
			ressourceType = RessourceType.UNKNOWN;
			break;
		}
	}

	private String[] getUrlParts(final String pathInfo) {
		return pathInfo != null ? pathInfo.split("/") : null;
	}
}
