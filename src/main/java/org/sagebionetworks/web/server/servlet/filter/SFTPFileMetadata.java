package org.sagebionetworks.web.server.servlet.filter;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

public class SFTPFileMetadata {
	public static final int DEFAULT_PORT = 22;
	private String host, filename;
	private List<String> path;
	public static final String SFTP_PREFIX = "sftp://";
	private int port;
	
	public SFTPFileMetadata(String host, String filename, List<String> path) {
		this(host, filename, path, DEFAULT_PORT);
	}
		
	public SFTPFileMetadata(String host, String filename, List<String> path, int port) {
		super();
		this.host = host;
		this.filename = filename;
		this.path = path;
		this.port = port;
	}
	
	public String getFullUrl() {
		return SFTP_PREFIX + host + ":" + port +"/" + getSourcePath() + filename;
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
		int port = DEFAULT_PORT;
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
		//check for port
		if (host.contains(":")) {
			String[] hostTokens = host.split(":");
			if (hostTokens.length != 2) {
				throw new IllegalArgumentException("unrecognized host and port format: " + host);	
			}
			host = hostTokens[0];
			port = Integer.parseInt(hostTokens[1]);
		}
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
		
		return new SFTPFileMetadata(host, filename, path, port);
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
	
	public int getPort() {
		return port;
	}
}
