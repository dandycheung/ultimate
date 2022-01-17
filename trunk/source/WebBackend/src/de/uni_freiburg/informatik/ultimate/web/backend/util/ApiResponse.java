package de.uni_freiburg.informatik.ultimate.web.backend.util;

import java.io.IOException;

import javax.servlet.http.HttpServletResponse;

import org.json.JSONException;
import org.json.JSONObject;

enum ApiResponseStatus {
	SUCCESS, ERROR
}

public class ApiResponse {
	private final JSONObject mJson;
	private final HttpServletResponse mResponse;

	public ApiResponse(final HttpServletResponse response) {
		mResponse = response;
		mJson = new JSONObject();
		setStatus(ApiResponseStatus.SUCCESS);
	}

	public void write() throws JSONException, IOException {
		mResponse.setContentType("application/json");
		mResponse.setCharacterEncoding("UTF-8");
		mJson.write(mResponse.getWriter());
	}

	private void setStatus(final ApiResponseStatus status) {
		try {
			mJson.put("status", status.name());
		} catch (final JSONException ex) {
			// key is constant and not null, cannot happen
		}
	}

	public void setMessage(final String message) {
		try {
			mJson.put("msg", message);
		} catch (final JSONException ex) {
			// key is constant and not null, cannot happen
		}
	}

	public void put(final String key, final String value) throws JSONException {
		mJson.put(key, value);
	}

	public void invalidRequest(final String message) throws IOException {
		setStatus(ApiResponseStatus.ERROR);
		setMessage("Invalid request: " + message);
		try {
			write();
		} catch (final JSONException ex) {
			throw new RuntimeException(ex);
		}
	}

	public void setStatusError() {
		setStatus(ApiResponseStatus.ERROR);
	}

	public void setStatusSuccess() {
		setStatus(ApiResponseStatus.SUCCESS);
	}

	/**
	 * Merges given JSON into the response.
	 *
	 * @param json
	 * @throws JSONException
	 */
	public void mergeJSON(final JSONObject json) throws JSONException {
		for (final String key : JSONObject.getNames(json)) {
			mJson.put(key, json.get(key));
		}
	}
}
