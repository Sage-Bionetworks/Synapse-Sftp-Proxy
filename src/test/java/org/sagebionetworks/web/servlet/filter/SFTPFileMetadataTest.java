package org.sagebionetworks.web.servlet.filter;

import static org.junit.Assert.*;

import java.io.UnsupportedEncodingException;

import org.junit.Test;
import org.sagebionetworks.web.server.servlet.filter.SFTPFileMetadata;

public class SFTPFileMetadataTest {

	@Test
	public void testRoundTrip() throws UnsupportedEncodingException {
		String host = "jayhodgson.com";
		String baseFilename = "1234-567";
		int port = 23;
		String url = "sftp://" + host + ":" + port + "/" + baseFilename;
		// no subdirectory
		SFTPFileMetadata metadata = SFTPFileMetadata.parseUrl(url);
		assertEquals(url, metadata.getFullEncodedUrl());
		assertEquals(host, metadata.getHost());
		assertEquals(port, metadata.getPort());
		assertEquals(1, metadata.getPath().size());
		assertEquals(baseFilename, metadata.getPath().get(0));
		
		// in subdirectory
		String dir1 = "foo";
		String dir2 = "bar";
		url = "sftp://" + host + ":"+port+"/" + dir1 + "/" + dir2 + "/" + baseFilename;
		metadata = SFTPFileMetadata.parseUrl(url);
		assertEquals(url, metadata.getFullEncodedUrl());
		assertEquals(host, metadata.getHost());
		assertEquals(port, metadata.getPort());
		assertEquals(3, metadata.getPath().size());
		assertEquals(dir1, metadata.getPath().get(0));
		assertEquals(dir2, metadata.getPath().get(1));
		assertEquals(baseFilename, metadata.getPath().get(2));
	}

	@Test
	public void testDefaultPort() throws UnsupportedEncodingException {
		String host = "jayhodgson.com";
		String baseFilename = "1234-567";
		//do not provide the port
		String url = "sftp://" + host + "/" + baseFilename;
		
		//the expected full url should include the port
		String expectedFullUrl = "sftp://" + host + ":" + SFTPFileMetadata.DEFAULT_PORT + "/" + baseFilename;
		// no subdirectory
		SFTPFileMetadata metadata = SFTPFileMetadata.parseUrl(url);
		assertEquals(expectedFullUrl, metadata.getFullEncodedUrl());
		assertEquals(host, metadata.getHost());
		assertEquals(1, metadata.getPath().size());
		assertEquals(baseFilename, metadata.getPath().get(0));
		assertEquals(SFTPFileMetadata.DEFAULT_PORT, metadata.getPort());
	}
	
	
	@Test
	public void testEncodeDecode() throws UnsupportedEncodingException {
		String host = "jayhodgson.com";
		String originalSourcePath = "/a subdirectory/1234 (567).txt";
		int port = 22;
		String url = "sftp://" + host + ":" + port  + originalSourcePath;
		// no subdirectory
		SFTPFileMetadata metadata = SFTPFileMetadata.parseUrl(url);
		
		//encoded value should contain %20 for space, not +
		String encodedUrl = metadata.getFullEncodedUrl();
		assertTrue(encodedUrl.contains("%20") && !encodedUrl.contains("+"));
		//not equal, but this encoded value is what is saved to the external file entity url
		assertTrue(!url.equals(encodedUrl));
		
		//on download of the sftp file, we need to decode
		SFTPFileMetadata encodedMetadata = SFTPFileMetadata.parseUrl(metadata.getFullEncodedUrl());
		assertEquals(originalSourcePath, encodedMetadata.getDecodedSourcePath());
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void testNullUrl() throws UnsupportedEncodingException {
		SFTPFileMetadata.parseUrl(null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testEmptyUrl() throws UnsupportedEncodingException {
		SFTPFileMetadata.parseUrl("");
	}

	@Test(expected = IllegalArgumentException.class)
	public void testMissingTokenUrl() throws UnsupportedEncodingException {
		SFTPFileMetadata.parseUrl("sftp://basefile.txt");
	}

	@Test(expected = IllegalArgumentException.class)
	public void testMissingPrefixUrl() throws UnsupportedEncodingException {
		SFTPFileMetadata.parseUrl("http://xkcd.com/basefile");
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void testBadPort() throws UnsupportedEncodingException {
		SFTPFileMetadata.parseUrl("sftp://test:invalidport/basefile.txt");
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void testBadPort2() throws UnsupportedEncodingException {
		SFTPFileMetadata.parseUrl("sftp://test:12:34/basefile.txt");
	}
	
	

}
