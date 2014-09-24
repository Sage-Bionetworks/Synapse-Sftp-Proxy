package org.sagebionetworks.web.server.servlet.filter;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

public class SFTPFileMetadata {
	private String host, filename;
	private List<String> path;
	public static final String SFTP_PREFIX = "sftp://";
	
	
	public SFTPFileMetadata(String host, String filename, List<String> path) {
		super();
		this.host = host;
		this.filename = filename;
		this.path = path;
	}
	
	public String getFullUrl() {
		return SFTP_PREFIX + host + "/" + getSourcePath() + filename;
	}
	
	public String getSourcePathWithFilename() {
		return getSourcePath() + filename;
	}
	
	public String getSourcePath() {
		StringBuilder src = new StringBuilder();
		for (String pathElement : path) {
			src.append(pathElement);
			src.append("/");
		}
		return src.toString();
	}

	public static SFTPFileMetadata parseUrl(String url) {
		if (url == null || url.trim().length() == 0) {
			throw new IllegalArgumentException("url must be defined");
		}
		url = url.trim();
		String lowerCaseUrl = url.toLowerCase();
		if (!lowerCaseUrl.startsWith(SFTP_PREFIX)) {
			throw new IllegalArgumentException("url must begin with " + SFTP_PREFIX);
		}
		
		url = url.substring(SFTP_PREFIX.length());
		
		StringTokenizer tokenizer = new StringTokenizer(url, "/");
		//we must have at least the host and filename
		if (tokenizer.countTokens() < 2) {
			throw new IllegalArgumentException("url must contain host and filename");
		}
		String host = tokenizer.nextToken();
		List<String> path = new ArrayList<String>();
		String filename = null;
		while(tokenizer.hasMoreTokens()) {
			//now start adding tokens to the path, until we reach the final token (which is the filename)
			String currentToken = tokenizer.nextToken();
			if (tokenizer.hasMoreTokens())
				path.add(currentToken);
			else
				filename = currentToken;
		}
		
		return new SFTPFileMetadata(host, filename, path);
	}
	
	public String getFilename() {
		return filename;
	}
	
	public String getHost() {
		return host;
	}
	
	public List<String> getPath() {
		return path;
	}
}
