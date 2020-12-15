package imageclient;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import org.apache.commons.imaging.ImageReadException;
import org.apache.commons.imaging.ImageWriteException;
import org.apache.commons.imaging.formats.jpeg.xmp.JpegXmpRewriter;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.IPTC;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.jpeg.JpegParser;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.xmp.XMPMetadata;
import org.xml.sax.SAXException;

public class ImageTagger {
	private final static String DEFAULT_TYPE = "application/octet-stream";
	private final static String JPEG_TYPE = "image/jpeg";

	public static String getMimeType(byte[] inputfile) {
		TikaConfig tika;
		try (TikaInputStream stream = TikaInputStream.get(inputfile)) {
			tika = new TikaConfig();
			Metadata metadata = new Metadata();

			MediaType mimetype = tika.getDetector().detect(stream, metadata);
			System.out.println("Detected mime type: " + mimetype.toString());
			return mimetype.toString();
		} catch (TikaException e) {
			e.printStackTrace();
			return DEFAULT_TYPE;
		} catch (IOException e) {
			e.printStackTrace();
			return DEFAULT_TYPE;
		}

	}



	public static Metadata extractMetadataFromJpeg(File file) {
		Metadata metadata = new Metadata();
		try (FileInputStream inputstream = new FileInputStream(file)) {
			
			BodyContentHandler handler = new BodyContentHandler();
			ParseContext pcontext = new ParseContext();

			JpegParser JpegParser = new JpegParser();
			
			// 
			JpegParser.parse(inputstream, handler, metadata, pcontext);

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SAXException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (TikaException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return metadata;
	}

//	public static Metadata updateMetadataToJpeg(String property, String value, Metadata metadata, File inputfile, File outputfile) {
	public static Metadata updateSubjectMetadata(String value, Metadata metadata, File inputfile) {
		try (FileInputStream inputstream = new FileInputStream(inputfile);
				FileOutputStream outputstream = new FileOutputStream("tagged_" + inputfile.getName())) {

			metadata.set(IPTC.KEYWORDS, value);

			XMPMetadata xmpMetadata = null;
			try {
				xmpMetadata = new XMPMetadata(metadata, JPEG_TYPE);
			} catch (TikaException e) {
				e.printStackTrace();
			}

			String xmpXml = xmpMetadata.toString();

			JpegXmpRewriter writer = new JpegXmpRewriter();
			try {
				writer.updateXmpXml(inputstream, outputstream, xmpXml);
			} catch (ImageReadException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (ImageWriteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

//	    	  System.out.println(xmpMetadata.toString());  

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return metadata;
	}
}
