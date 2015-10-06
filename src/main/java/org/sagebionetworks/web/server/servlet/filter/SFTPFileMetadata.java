package org.sagebionetworks.web.server.servlet.filter;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

public class SFTPFileMetadata {
	public static final int DEFAULT_PORT = 22;
	private String host;
	private List<String> path;
	public static final String SFTP_PREFIX = "sftp://";
	private int port;
	private String filenameOverride;
	
	public SFTPFileMetadata(String host, List<String> path) {
		this(host, path, DEFAULT_PORT);
	}
		
	public SFTPFileMetadata(String host, List<String> path, int port) {
		super();
		this.host = host;
		this.path = path;
		this.port = port;
	}
	
	public String getFullEncodedUrl() throws UnsupportedEncodingException {
		return SFTP_PREFIX + host + ":" + port + getEncodedSourcePath();
	}
	
	public String getDecodedSourcePath() throws UnsupportedEncodingException {
		StringBuilder src = new StringBuilder();
		for (String pathElement : path) {
			src.append("/");
			src.append(URLDecoder.decode(pathElement, "UTF-8"));
		}
		return src.toString();
	}
	
	public String getEncodedSourcePath() throws UnsupportedEncodingException {
		StringBuilder src = new StringBuilder();
		for (String pathElement : path) {
			src.append("/");
			src.append(URLEncoder.encode(pathElement, "UTF-8").replace("+", "%20"));
		}
		return src.toString();
	}


	public static SFTPFileMetadata parseUrl(String url) throws UnsupportedEncodingException {
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
		while(tokenizer.hasMoreTokens()) {
			//now start adding tokens to the path, until we reach the final token (which is the filename)
			String currentToken = tokenizer.nextToken();
			//because we are passing this url in a query param, we need to decode this to get to the true path value
			path.add(URLDecoder.decode(currentToken, "UTF-8"));
		}
		
		return new SFTPFileMetadata(host, path, port);
	}
	public String getFilenameOverride() {
		return filenameOverride;
	}
	public void setFilenameOverride(String filenameOverride) {
		this.filenameOverride = filenameOverride;
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
