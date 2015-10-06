package org.sagebionetworks.web.servlet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.*;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.sagebionetworks.schema.adapter.JSONObjectAdapterException;
import org.sagebionetworks.web.server.servlet.SftpProxyServlet;
import org.sagebionetworks.web.server.servlet.filter.SFTPFileMetadata;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;

public class SftpProxyServletTest {
	HttpServletRequest mockRequest;
	HttpServletResponse mockResponse;
	String username, password;
	SftpProxyServlet servlet;
	JSch mockJsch;
	Session mockSession;
	ChannelSftp mockChannel;
	
	@Before
	public void setUp() throws UnsupportedEncodingException, JSchException, SftpException {
		mockJsch = mock(JSch.class);
		mockSession = mock(Session.class);
		when(mockJsch.getSession(anyString(), anyString(), anyInt())).thenReturn(mockSession);
		mockChannel = mock(ChannelSftp.class);
		when(mockSession.openChannel(anyString())).thenReturn(mockChannel);
		servlet = new SftpProxyServlet();
		servlet.setJsch(mockJsch);
		
		username = "bob";
		password = "password1";
		mockRequest = mock(HttpServletRequest.class);
		mockResponse = mock(HttpServletResponse.class);
	}

	@Test
	public void testChangeToRemoteUploadDirectory() throws SftpException {
		List<String> path = new ArrayList<String>();
		String dir1 = "d1";
		String dir2 = "d2";
		path.add(dir1);
		path.add(dir2);
		SFTPFileMetadata metadata = new SFTPFileMetadata("sagebase.org", path);
		servlet.changeToRemoteUploadDirectory(metadata, mockChannel);
		
		//and verify the values
		ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
		verify(mockChannel, times(3)).cd(captor.capture());
		List<String> values = captor.getAllValues();
		assertEquals("/", values.get(0));
		assertEquals(dir1, values.get(1));
		assertEquals(dir2, values.get(2));
	}
	
	@Test
	public void testChangeToRemoteUploadDirectoryMkdir() throws SftpException {
		List<String> path = new ArrayList<String>();
		String dir1 = "d1";
		path.add(dir1);
		SFTPFileMetadata metadata = new SFTPFileMetadata("sagebase.org", path);
		//initially throw an exception, then do nothing (do not throw an exception on the "cd" command)
		Mockito.doNothing().doThrow(new SftpException(1, "fake exception when changing directory")).doNothing().when(mockChannel).cd(anyString());
		servlet.changeToRemoteUploadDirectory(metadata, mockChannel);
		
		verify(mockChannel).mkdir(dir1);
	}
	

	@Test
	public void testChangeToRemoteUploadDirectoryEmpty() throws SftpException {
		List<String> path = new ArrayList<String>();
		SFTPFileMetadata metadata = new SFTPFileMetadata("sagebase.org", path);
		servlet.changeToRemoteUploadDirectory(metadata, mockChannel);
		
		verify(mockChannel, times(1)).cd(anyString()); //only change to root
		verify(mockChannel, never()).mkdir(anyString());
	}
	
	@Test
	public void testRoundTrip() throws ServletException, IOException, FileUploadException, JSchException, SftpException, JSONObjectAdapterException {
		//only run this test if a sftp test server was set on the command line (with credentials)
		String endpoint = System.getProperty("sftp-test-host");
		String path= System.getProperty("sftp-test-path");
		String username= System.getProperty("sftp-test-user");
		String password= System.getProperty("sftp-test-password");
		if (endpoint == null || username == null || password == null || path == null) {
			System.out.println("Skipped round trip sftp test due to missing connection properties (sftp-test-host, sftp-test-path, sftp-test-user, and sftp-test-password)");
			return;
		}
		
		//*****UPLOAD*****
		//for this test, we need to use the real JSch library
		servlet.setJsch(new JSch());
		String swcTestFileName = "swc-test-file.txt";
		//put it in the root
		List<String> pathList = new ArrayList<String>();
		for (String pathElement : path.split("/")) {
			pathList.add(pathElement);
		}
		
		SFTPFileMetadata metadata = new SFTPFileMetadata(endpoint, pathList);
		
		FileItemStream usernameItem = mock(FileItemStream.class);
		when(usernameItem.isFormField()).thenReturn(true);
		when(usernameItem.getFieldName()).thenReturn("username");
		InputStream userNameStream = IOUtils.toInputStream(username);
		when(usernameItem.openStream()).thenReturn(userNameStream);
		FileItemStream passwordItem = mock(FileItemStream.class);
		when(passwordItem.isFormField()).thenReturn(true);
		when(passwordItem.getFieldName()).thenReturn("password");
		InputStream passwordStream = IOUtils.toInputStream(password);
		when(passwordItem.openStream()).thenReturn(passwordStream);
		FileItemStream fileItem = mock(FileItemStream.class);
		when(fileItem.isFormField()).thenReturn(false);
		
		//unique file contents for this test (to be verified when we download)
		String fileContents = Long.toString(new Date().getTime());
		when(fileItem.getName()).thenReturn(swcTestFileName);
		when(fileItem.openStream()).thenReturn(IOUtils.toInputStream(fileContents));
		
		FileItemIterator items = mock(FileItemIterator.class);
		when(items.hasNext()).thenReturn(true, true, true, false);
		when(items.next()).thenReturn(usernameItem, passwordItem, fileItem);
		
		ServletFileUpload mockUpload = mock(ServletFileUpload.class);
		when(mockUpload.getItemIterator(any(HttpServletRequest.class))).thenReturn(items);
		
		ByteArrayServletOutputStream baos = new ByteArrayServletOutputStream();
		when(mockResponse.getOutputStream()).thenReturn(baos);
		
		servlet.doPost(mockRequest, mockResponse, metadata, mockUpload);
		
		//get the response
		String responseString = baos.asString();
		//parse out the response url
		assertTrue("ERROR - unexpected response string: " + responseString, responseString != null && responseString.contains("SUCCESS"));
		
		//****DOWNLOAD*****
		//no file item, but still return the credentials
		when(items.hasNext()).thenReturn(true, true, false);
		when(items.next()).thenReturn(usernameItem, passwordItem);
		
		baos = new ByteArrayServletOutputStream();
		when(mockResponse.getOutputStream()).thenReturn(baos);
		userNameStream.close();
		passwordStream.close();
		userNameStream = IOUtils.toInputStream(username);
		when(usernameItem.openStream()).thenReturn(userNameStream);
		passwordStream = IOUtils.toInputStream(password);
		when(passwordItem.openStream()).thenReturn(passwordStream);
		
		pathList.add(swcTestFileName);
		String filenameOverride = "overridden.txt";
		metadata.setFilenameOverride(filenameOverride);
		servlet.doPost(mockRequest, mockResponse, metadata, mockUpload);
		baos.flush();
		responseString = baos.asString();
		assertEquals(fileContents, responseString);
		verify(mockResponse).setHeader(eq("Content-disposition"), contains(filenameOverride));
		userNameStream.close();
		passwordStream.close();
	}
}
