package imageclient;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.tika.metadata.Metadata;

import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import com.google.cloud.storage.StorageOptions;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;
import com.google.cloud.pubsub.v1.AckReplyConsumer;

public class ImageClient {
	private final String AUTH_CREDENTIALS = "/imagesorter-292711-25182a333c8f.json";
	private final String BUCKET_NAME = "temp_pics";
	private final String PROJECT_NAME = "imagesorter-292711";
	private final String SUBSCRIPTION_NAME = "label-subscription";
	private final String JPEG_TYPE = "image/jpeg";
	private CredentialsProvider credentialsProvider;
	private Bucket bucket;

	/*
	 * ImageClient handles communication with cloud services
	 */
	public ImageClient() {
		try {
			// Reads the authentication credentials from file system and creates a provider
			InputStream stream = ImageClient.class.getResourceAsStream(AUTH_CREDENTIALS);
			this.credentialsProvider = getCredentialsProvider(stream);

			// Creates the storage object and accesses the storage bucket
			Storage storage = StorageOptions.newBuilder()
					.setCredentials(this.credentialsProvider.getCredentials())
					.build().getService();
			
			this.bucket = storage.get(BUCKET_NAME);

		} catch (IOException e) {
			System.err.println("Auth file not present!");
			System.exit(-2);
			e.printStackTrace();
		}
	}

	/*
	 * Uploads the given file to the storage bucket and adds the file to a map of uploaded files
	 */
	public void upload(File file, Map<String, File> uploaded) {
		try {
			String fileName = file.getName();
			System.out.println("Processing file: " + fileName);
			
			byte[] content = Files.toByteArray(file);
			String fileMimeType = ImageTagger.getMimeType(content);
			
			if (!fileMimeType.equals(JPEG_TYPE)) {
				System.out.println("Unsupported file type!");
				return;
			}

			System.out.print("Uploading file...");
			bucket.create(fileName, content, fileMimeType);
			
			uploaded.put(fileName, file);
			System.out.println("done!");
		} catch (IOException | StorageException e) {
			e.printStackTrace();
		}
	}

	/*
	 * Waits for messages to be available, and calls updateTags with the message data
	 */
	public void subscribeAsync(Map<String, File> uploaded) {
		ProjectSubscriptionName subscriptionName = ProjectSubscriptionName.of(PROJECT_NAME, SUBSCRIPTION_NAME);
		Subscriber subscriber = null;

		// Instantiates an asynchronous message receiver that handles the incoming message
		MessageReceiver receiver = (PubsubMessage message, AckReplyConsumer consumer) -> {
			String filename = message.getAttributesMap().getOrDefault("name", "unknown_file");
			String keywords = message.getAttributesMap().getOrDefault("keywords", "");

			System.out.println("Received results for file: " + filename);
			System.out.println("Detected keywords: " + keywords);
			
			// Access the file object
			File file = uploaded.get(filename);
			if (file != null) {
				updateTags(file, keywords);
				// Remove the file from map, so duplicate messages will not be processed
				uploaded.remove(filename);
			} else {
				System.out.println("...skipping duplicate result!");
			}
	
			consumer.ack();
		};

		try {
			// Build and start the subscriber
			subscriber = Subscriber.newBuilder(subscriptionName, receiver)
					.setCredentialsProvider(this.credentialsProvider).build();
			subscriber.startAsync().awaitRunning();
			
			// Allow the subscriber to run for 20s unless an unrecoverable error occurs.
			subscriber.awaitTerminated(20, TimeUnit.SECONDS);
		} catch (TimeoutException timeoutException) {
			// Shut down the subscriber after 20s. Stop receiving messages.
			System.out.println("Timeout");
			subscriber.stopAsync();
		}
	}
	
	/*
	 * Updates the files metadata with the provided keywords
	 */
	private void updateTags(File file, String value) {
		System.out.print("Updating metadata...");
        Metadata metadata = ImageTagger.extractMetadataFromJpeg(file);
        ImageTagger.updateSubjectMetadata(value, metadata, file);
        System.out.println("done!");
	}


	/*
	 * Creates a CredentialsProvider from the credentials passed as an inputstream
	 */
	private CredentialsProvider getCredentialsProvider(InputStream stream) throws IOException {
		CredentialsProvider credentialsProvider = FixedCredentialsProvider.create(ServiceAccountCredentials
				.fromStream(stream).createScoped(Lists.newArrayList("https://www.googleapis.com/auth/cloud-platform")));

		return credentialsProvider;

	}
}
