package org.sagebionetworks.web.servlet.filter;

import static org.junit.Assert.*;

import org.junit.Test;
import org.sagebionetworks.web.server.servlet.filter.SFTPFileMetadata;

public class SFTPFileMetadataTest {

	
	@Test
	public void testRoundTrip() {
		String host = "jayhodgson.com";
		String baseFilename = "1234-567";
		String url = "sftp://" + host + "/" +baseFilename;
		//no subdirectory
		SFTPFileMetadata metadata = SFTPFileMetadata.parseUrl(url);
		assertEquals(url, metadata.getFullUrl());
		assertEquals(host, metadata.getHost());
		assertTrue(metadata.getPath().isEmpty());
		
		//in subdirectory
		String dir1 = "foo";
		String dir2 = "bar";
		url = "sftp://" + host + "/" + dir1 + "/" + dir2+ "/"+baseFilename;
		metadata = SFTPFileMetadata.parseUrl(url);
		assertEquals(url, metadata.getFullUrl());
		assertEquals(host, metadata.getHost());
		assertEquals(2, metadata.getPath().size());
		assertEquals(dir1, metadata.getPath().get(0));
		assertEquals(dir2, metadata.getPath().get(1));
	}

	@Test (expected=IllegalArgumentException.class) 
	public void testNullUrl() {
		SFTPFileMetadata.parseUrl(null);
	}
	
	@Test (expected=IllegalArgumentException.class) 
	public void testEmptyUrl() {
		SFTPFileMetadata.parseUrl("");
	}
	
	@Test (expected=IllegalArgumentException.class) 
	public void testMissingTokenUrl() {
		SFTPFileMetadata.parseUrl("sftp://basefile.txt");
	}
	
	@Test (expected=IllegalArgumentException.class) 
	public void testMissingPrefixUrl() {
		SFTPFileMetadata.parseUrl("http://xkcd.com/basefile");
	}
}
