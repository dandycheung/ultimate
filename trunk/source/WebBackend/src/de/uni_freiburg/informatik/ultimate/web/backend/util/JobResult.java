package de.uni_freiburg.informatik.ultimate.web.backend.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.json.JSONException;
import org.json.JSONObject;

public class JobResult {
	private final String mJobId;
	private JSONObject mJsonResult = new JSONObject();

	public JobResult(final String jobId) {
		mJobId = jobId;
		validateJobId();
	}

	private void validateJobId() {
		final String cleanedId = mJobId.replaceAll("\\W+", "");
		if (cleanedId != mJobId) {
			throw new IllegalArgumentException();
		}
	}

	String getFilePath() {
		StringBuilder filepath = new StringBuilder().append(System.getProperty("java.io.tmpdir")).append(File.separator)
				.append("log").append(File.separator);
		filepath.append(mJobId).append(".result.json");
		return filepath.toString();
	}

	public void store() throws IOException {
		final BufferedWriter writer = new BufferedWriter(new FileWriter(getFilePath()));
		writer.write(mJsonResult.toString());
		writer.close();
	}

	public void load() throws JSONException, IOException, IllegalArgumentException {
		try {
			final byte[] encoded = Files.readAllBytes(Paths.get(getFilePath()));
			final String resultString = new String(encoded);
			mJsonResult = new JSONObject(resultString);
		} catch (final IOException e) {
			mJsonResult = new JSONObject();
			mJsonResult.put("error", "Job not found.");
			throw new IllegalArgumentException("Job id not found.");
		}
	}

	public JSONObject getJson() {
		return mJsonResult;
	}

	public void setJson(final JSONObject jsonObject) {
		mJsonResult = jsonObject;
	}
}
