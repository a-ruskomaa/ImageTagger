package ties4560.task6;

import com.google.cloud.vision.v1.AnnotateImageRequest;
import com.google.cloud.vision.v1.AnnotateImageResponse;
import com.google.cloud.vision.v1.Feature;
import com.google.cloud.vision.v1.Feature.Type;
import com.google.cloud.vision.v1.Image;
import com.google.cloud.vision.v1.ImageAnnotatorClient;
import com.google.cloud.vision.v1.ImageSource;
import com.google.common.collect.ImmutableMap;
import com.google.pubsub.v1.ProjectTopicName;
import com.google.pubsub.v1.PubsubMessage;

import ties4560.task6.ImageAnalysis.GCSEvent;

import com.google.cloud.functions.BackgroundFunction;
import com.google.cloud.functions.Context;
import com.google.cloud.pubsub.v1.Publisher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ImageAnalysis implements BackgroundFunction<GCSEvent> {
	// For some reason setting these with System.getEnv() did not work
	private static final String PROJECT_ID = "imagesorter-292711";
	private static final String TOPIC_NAME = "generated-labels";
	
	private static final Logger logger = Logger.getLogger(ImageAnalysis.class.getName());

	/*
	 * Called when the function triggers
	 */
	@Override
	public void accept(GCSEvent event, Context context) {
		logger.info("Processing file: " + event.name);

		//Set the source for file to be processed
		ImageSource src = ImageSource.newBuilder().setImageUri("gs://" + event.bucket + "/" + event.name).build();
		Image img = Image.newBuilder().setSource(src).build();

		List<String> labels = getLabels(img);
		
		if (labels != null) {
			publishLabels(event.name, labels);
		} else {
			logger.log(Level.SEVERE, "Unable to publish labels");
		}
	}

	/*
	 * Calls the Cloud Vision API with an annotate request, returns the first 3 keywords as a List
	 */
	private List<String> getLabels(Image image) {
		try (ImageAnnotatorClient vision = ImageAnnotatorClient.create()) {

			// Creates the request for label detection. The API requires a list, but since the storage
			// bucket doesn't support batch uploads, the list will have only one request per function call
			List<AnnotateImageRequest> requests = new ArrayList<>();
			Feature feat = Feature.newBuilder().setType(Type.LABEL_DETECTION).build();
			AnnotateImageRequest request = AnnotateImageRequest.newBuilder().addFeatures(feat).setImage(image).build();
			requests.add(request);

			// Performs label detection on the image file
			List<AnnotateImageResponse> responses = vision.batchAnnotateImages(requests).getResponsesList();

			// Iterates through the responses, though in reality there will only be one response
			for (AnnotateImageResponse res : responses) {
				if (res.hasError()) {
					logger.log(Level.SEVERE, "Error getting annotations: " + res.getError().getMessage());
					return null;
				}

				// Return the first 3 generated labels
				return res.getLabelAnnotationsList().stream().limit(3L).map(e -> e.getDescription())
						.collect(Collectors.toList());
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	/*
	 * Publishes the result on a predefined pub/sub topic
	 */
	public static void publishLabels(String name, List<String> labels) {

		Publisher publisher = null;
		try {
			// Create a publisher instance with default settings bound to the topic
			try {
				publisher = Publisher.newBuilder(ProjectTopicName.of(PROJECT_ID, TOPIC_NAME)).build();
			} catch (IOException e) {
		        logger.log(Level.SEVERE, "Error creating the publisher: " + e.getMessage(), e);
				e.printStackTrace();
			}
			
			// The result is transmitted inside the message attributes as key-value pairs
			PubsubMessage pubsubMessage = PubsubMessage.newBuilder().putAllAttributes(ImmutableMap.of("name", name,"keywords", String.join(",", labels))).build();

		    try {
		        publisher.publish(pubsubMessage).get();
		      } catch (InterruptedException | ExecutionException e) {
		        // Log error
		        logger.log(Level.SEVERE, "Error publishing generated labels: " + e.getMessage(), e);
		        return;
		      }
		} finally {
			if (publisher != null) {
				// When finished with the publisher, shutdown to free up resources.
				publisher.shutdown();
				try {
					publisher.awaitTermination(1, TimeUnit.MINUTES);
				} catch (InterruptedException e) {
			        logger.log(Level.SEVERE, "Terminating the publisher interrupted: " + e.getMessage(), e);
					e.printStackTrace();
				}
			}
		}
	}

	/*
	 * This class defines the event properties that the function is interested in
	 */
	public static class GCSEvent {
		String bucket;
		String name;
		String metageneration;
		String selfLink;
	}
}
