package org.sagebionetworks.web.server.servlet;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.sagebionetworks.repo.model.attachment.UploadResult;
import org.sagebionetworks.repo.model.attachment.UploadStatus;
import org.sagebionetworks.schema.adapter.JSONObjectAdapterException;
import org.sagebionetworks.schema.adapter.org.json.EntityFactory;
import org.sagebionetworks.web.server.servlet.filter.BasicAuthFilter;
import org.sagebionetworks.web.server.servlet.filter.Credentials;
import org.sagebionetworks.web.server.servlet.filter.SFTPFileMetadata;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;

public class SftpProxyServlet extends HttpServlet {
	public static final String SFTP_CHANNEL_TYPE = "sftp";
	public static final String SFTP_URL_PARAM = "url";
	private static final long serialVersionUID = 1L;
	
	private JSch jsch = new JSch();
	protected static final ThreadLocal<HttpServletRequest> perThreadRequest = new ThreadLocal<HttpServletRequest>();

	@Override
	protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		SftpProxyServlet.perThreadRequest.set(request);
		super.service(request, response);
	}
	
	@Override
	public void service(ServletRequest request, ServletResponse response) throws ServletException, IOException {
		super.service(request, response);
	}

	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		ServletOutputStream stream = response.getOutputStream();
		SFTPFileMetadata metadata = SFTPFileMetadata.parseUrl(request.getParameter(SFTP_URL_PARAM));
		Session session = null;
		try {
			session = getSession(request, metadata);
			Channel channel = session.openChannel(SFTP_CHANNEL_TYPE);
			channel.connect();
			ChannelSftp sftpChannel = (ChannelSftp) channel;
			sftpChannel.get(metadata.getSourcePath(), stream);
			sftpChannel.exit();
		} catch (SecurityException e) {
			BasicAuthFilter.respondWithChallenge(response, metadata.getHost());
		} catch (JSchException e) {
			throw new ServletException(e);
		} catch (SftpException e) {
			throw new ServletException(e);
		} finally {
			if (session != null)
				session.disconnect();
		}
	}
	
	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		SFTPFileMetadata metadata = SFTPFileMetadata.parseUrl(request.getParameter(SFTP_URL_PARAM));
		try {
			sftpUploadFile(request, metadata, response);
		} catch (SecurityException e) {
			BasicAuthFilter.respondWithChallenge(response, metadata.getHost());
		} catch (FileUploadException e) {
			throw new ServletException(e);
		}
	}
	
	public void sftpUploadFile(HttpServletRequest request, SFTPFileMetadata metadata, HttpServletResponse response) throws FileUploadException, IOException, ServletException {
		ServletFileUpload upload = new ServletFileUpload();
		FileItemIterator iter = upload.getItemIterator(request);
		if (iter.hasNext()) {
			// should be one in this case
			FileItemStream item = iter.next();
			String name = item.getFieldName();
			InputStream stream = item.openStream();
			
			String fileName = item.getName();
			if (fileName.contains("\\")) {
				fileName = fileName.substring(fileName.lastIndexOf("\\") + 1);
			}
			
			Session session = null;
			try {
				session = getSession(request, metadata);
				Channel channel = session.openChannel(SFTP_CHANNEL_TYPE);
				channel.connect();
				ChannelSftp sftpChannel = (ChannelSftp) channel;
				changeToRemoteUploadDirectory(metadata, sftpChannel);
				sftpChannel.put(stream, fileName);
				sftpChannel.exit();
				
				fillResponseWithSuccess(response, metadata.getFullUrl() + "/" + fileName);
			} catch (SecurityException e) {
				throw e;
			} catch (Exception e) {
				fillResponseWithFailure(response, e);
				return;
			} finally {
				if (session != null)
					session.disconnect();
			}
		}
		response.flushBuffer();
	}
	
	public void changeToRemoteUploadDirectory(SFTPFileMetadata metadata, ChannelSftp sftpChannel) throws SftpException {
		//change directory (and make directory if not exist)
		for (String directory : metadata.getPath()) {
			try{
				sftpChannel.cd(directory);
			} catch (SftpException e) {
				//cannot access, try to create and go there
				sftpChannel.mkdir(directory);
				sftpChannel.cd(directory);
			}
		}
	}
	
	public Session getSession(HttpServletRequest request, SFTPFileMetadata metadata) throws SecurityException {
		Session session;
		try {
			Credentials credentials = BasicAuthFilter.getCredentials(request);
			if (credentials == null) {
				throw new IllegalArgumentException("Basic authorization required for SFTP connection.");
			}
			session = jsch.getSession(credentials.getUsername(), metadata.getHost(), metadata.getPort());
			session.setPassword(credentials.getPassword());
			session.setConfig("StrictHostKeyChecking", "no");
			session.connect();
		} catch (Throwable e) {
			throw new SecurityException(e);
		}
		return session;
	}

	/**
	 * For testing purposes
	 * @param jsch
	 */
	public void setJsch(JSch jsch) {
		this.jsch = jsch;
	}

	public static void fillResponseWithSuccess(HttpServletResponse response, String url) throws JSONObjectAdapterException, UnsupportedEncodingException, IOException {
		UploadResult result = new UploadResult();
		result.setMessage(url);
		
		result.setUploadStatus(UploadStatus.SUCCESS);
		String out = EntityFactory.createJSONStringForEntity(result);
		response.setStatus(HttpServletResponse.SC_CREATED);
		response.setCharacterEncoding("UTF-8");
		response.setContentLength(out.length());
		PrintWriter writer = response.getWriter();
		writer.println(out);
	}
	
	public static void fillResponseWithFailure(HttpServletResponse response, Exception e) throws UnsupportedEncodingException, IOException {
		UploadResult result = new UploadResult();
		result.setMessage(e.getMessage());
		result.setUploadStatus(UploadStatus.FAILED);
		String out;
		try {
			out = EntityFactory.createJSONStringForEntity(result);
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			response.setCharacterEncoding("UTF-8");
			response.setContentLength(out.length());
			PrintWriter writer = response.getWriter();
			writer.println(out);
		} catch (JSONObjectAdapterException e1) {
			throw new RuntimeException(e1);
		}
	}
}
