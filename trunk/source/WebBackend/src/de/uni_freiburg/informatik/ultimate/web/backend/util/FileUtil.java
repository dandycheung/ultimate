package de.uni_freiburg.informatik.ultimate.web.backend.util;

import java.io.File;
import java.nio.file.Path;

import de.uni_freiburg.informatik.ultimate.web.backend.Config;

public class FileUtil {

	private FileUtil() {
		// do not instantiate utility class
	}

	public static File getJobResultJsonFile(final String jobId) {
		return Path.of(Config.TMP_DIR).resolve(jobId + ".result.json").toFile();
	}

	public static File getTmpDir() {
		return Path.of(Config.TMP_DIR).toFile();
	}

}
