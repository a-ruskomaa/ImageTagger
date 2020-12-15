package imageclient;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Main {

	public static void main(String[] args) {
		if (args.length > 0) {
			ImageClient client = new ImageClient();
			Map<String, File> uploaded = new ConcurrentHashMap<>();
			
			System.setProperty("tika.config", "tika-config.xml");
			
			System.out.printf("=========== Auto-tagging %d files ============%n", args.length);

			for (int i = 0; i < args.length; i++) {
				File file = new File(args[i]);

				if (!file.canRead()) {
					System.err.println("The file " + args[i] + " can not be read!");
					System.exit(-1);
				}

				if (!file.canWrite()) {
					System.err.println("The file " + args[i] + " can not be written to!");
					System.exit(-1);
				}

				if (file.isDirectory()) {
					System.err.println("Target file is a directory, tagging multiple files is currently not supported!");
					System.exit(-1);
				}
				client.upload(file, uploaded);
			}

			if (!uploaded.isEmpty()) {
				System.out.printf("=========== Waiting for results ============%n");
				client.subscribeAsync(uploaded);
			}
		} else {
			System.out.println("No arguments provided");
			System.exit(-1);

		}
	}

}
