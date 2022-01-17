package de.uni_freiburg.informatik.ultimate.web.backend.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;

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
		if (!cleanedId.equals(mJobId)) {
			throw new IllegalArgumentException("Job ID contained illegal characters");
		}
	}

	public void store() throws IOException {
		try (final BufferedWriter writer = new BufferedWriter(new FileWriter(getJsonFile()))) {
			writer.write(mJsonResult.toString());
		}
	}

	public void load() throws JSONException, IOException, IllegalArgumentException {
		try {
			final byte[] encoded = Files.readAllBytes(getJsonFile().toPath());
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

	public File getJsonFile() {
		return FileUtil.getJobResultJsonFile(mJobId);
	}

	public void setJson(final JSONObject jsonObject) {
		mJsonResult = jsonObject;
	}
}
