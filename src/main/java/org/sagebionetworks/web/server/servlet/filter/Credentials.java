package org.sagebionetworks.web.server.servlet.filter;

public class Credentials {
	private String username, password;
	public Credentials() {
	}
	public Credentials(String username, String password) {
		super();
		this.username = username;
		this.password = password;
	}
	
	public String getPassword() {
		return password;
	}
	
	public String getUsername() {
		return username;
	}
}
