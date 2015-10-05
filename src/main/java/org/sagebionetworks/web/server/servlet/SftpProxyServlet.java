package org.sagebionetworks.web.server.servlet;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.List;

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
	protected void doGet(HttpServletRequest req, HttpServletResponse response) throws ServletException, IOException {
		respondWithHtml(response, GET_RESPONSE);
	}
	
	public void respondWithHtml(HttpServletResponse response, String message) throws IOException {
		response.setStatus(HttpServletResponse.SC_OK);
		response.setContentType("text/html");
		response.setCharacterEncoding("UTF-8");
		String html = String.format(HTML_RESPONSE, message);
		byte[] outBytes = html.getBytes("UTF-8");
		response.getOutputStream().write(outBytes);
	}
	
	public void doPost(HttpServletRequest request, HttpServletResponse response, SFTPFileMetadata metadata, ServletFileUpload upload) throws ServletException, IOException, FileUploadException, JSchException, SftpException, JSONObjectAdapterException {
		//gather input values from the request
		FileItemIterator iter = upload.getItemIterator(request);
		
		String username = null;
		String password = null;
		boolean uploading = false;
		while (iter.hasNext()) {
			FileItemStream item = iter.next();
			if (item.isFormField()) {
				String fieldValue = getStringItem(item);
				if (item.getFieldName().equals("username")) {
					username = fieldValue;
				} else if (item.getFieldName().equals("password")) {
					password = fieldValue;
				}
			} else {
				uploading = true;
				//the file
				Session session = getSession(username, password, metadata);
				InputStream stream = item.openStream();
				String fileName = item.getName();
				String url = sftpUploadFile(session, metadata, fileName, stream);
				fillResponseWithSuccess(response, url);
			}
		}
		if (!uploading) {
			try {
				//download!
				Session session = getSession(username, password, metadata);
				response.setContentType("application/octet-stream");
				List<String> path = metadata.getPath();
				String fileName = URLDecoder.decode(path.get(path.size()-1), "UTF-8");
				response.setHeader("Content-disposition","attachment; filename=\""+fileName+"\"");
				
				ServletOutputStream stream = response.getOutputStream();
				sftpDownloadFile(session, metadata, stream);
			} catch (SecurityException se) {
				fillResponseWithFailure(response, se);
			} catch (Exception e) {
				fillResponseWithFailure(response, e);
			}
		}
	
	}
	
	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		try{
			ServletFileUpload upload = new ServletFileUpload();
			SFTPFileMetadata metadata = SFTPFileMetadata.parseUrl(request.getParameter(SFTP_URL_PARAM));
			doPost(request, response, metadata, upload);
		} catch (Exception e) {
			fillResponseWithFailure(response, e);
		}
	}
	
	public String getStringItem(FileItemStream item) throws IOException {
		InputStream stream = null;
		try {
			stream = item.openStream();
			byte[] str = new byte[stream.available()];
			stream.read(str);
			return new String(str, "UTF8");
		} finally {
			if (stream != null)
				stream.close();
		}
	}
	
	public void sftpDownloadFile(Session session, SFTPFileMetadata metadata, OutputStream stream) throws IOException, JSchException, SftpException {
		try {
			Channel channel = session.openChannel(SFTP_CHANNEL_TYPE);
			channel.connect();
			ChannelSftp sftpChannel = (ChannelSftp) channel;
			String decodedSourcePath = metadata.getDecodedSourcePath();
			sftpChannel.get(decodedSourcePath, stream);
			sftpChannel.exit();
		} finally {
			if (session != null)
				session.disconnect();
		}
	}
	
	public String sftpUploadFile(Session session, SFTPFileMetadata metadata, String fileName, InputStream stream) throws FileUploadException, IOException, ServletException, JSchException, SftpException {
		if (fileName.contains("\\")) {
			fileName = fileName.substring(fileName.lastIndexOf("\\") + 1);
		}
		
		try {
			Channel channel = session.openChannel(SFTP_CHANNEL_TYPE);
			channel.connect();
			ChannelSftp sftpChannel = (ChannelSftp) channel;
			changeToRemoteUploadDirectory(metadata, sftpChannel);
			sftpChannel.put(stream, fileName);
			sftpChannel.exit();
			//http://stackoverflow.com/questions/4737841/urlencoder-not-able-to-translate-space-character
			return metadata.getFullEncodedUrl() + "/" + URLEncoder.encode(fileName, "UTF-8").replace("+", "%20");
		} catch (SecurityException e) {
			throw e;
		} finally {
			if (session != null)
				session.disconnect();
		}
	}
	
	public void changeToRemoteUploadDirectory(SFTPFileMetadata metadata, ChannelSftp sftpChannel) throws SftpException {
		//change directory to root before trying to cd
		sftpChannel.cd("/");
		//do not change directories if we are already 
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
	
	public Session getSession(String username, String password, SFTPFileMetadata metadata) throws SecurityException {
		if (username == null || password == null) {
			throw new IllegalArgumentException("Authorization is required to establish the SFTP connection.");
		}

		Session session;
		try {
			session = jsch.getSession(username, metadata.getHost(), metadata.getPort());
			session.setPassword(password);
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
		String uploadResultJson = EntityFactory.createJSONStringForEntity(result);
		response.setStatus(HttpServletResponse.SC_CREATED);
		response.setContentType("text/html");
		response.setCharacterEncoding("UTF-8");
		String out = getPostMessageResponsePage(uploadResultJson);
		byte[] outBytes = out.getBytes("UTF-8");
		response.getOutputStream().write(outBytes);
	}
	
	public static void fillResponseWithFailure(HttpServletResponse response, Exception e) throws UnsupportedEncodingException, IOException {
		UploadResult result = new UploadResult();
		result.setMessage(e.getMessage());
		result.setUploadStatus(UploadStatus.FAILED);
		try {
			String uploadResultJson = EntityFactory.createJSONStringForEntity(result);
			//status code OK, content contains the error
			response.setContentType("text/html");
			response.setCharacterEncoding("UTF-8");
			String out = getPostMessageResponsePage(uploadResultJson);
			byte[] outBytes = out.getBytes("UTF-8");
			response.getOutputStream().write(outBytes);
		} catch (JSONObjectAdapterException e1) {
			throw new RuntimeException(e1);
		}
	}
	
	public static String getPostMessageResponsePage(String uploadResultJson) {
		return String.format(RESPONSE_HTML, uploadResultJson);
	}
	
	public static final String RESPONSE_HTML = "<!doctype html>\n" + 
			"<html>\n" + 
			"  <head>\n" + 
			"    <title>posting response message back to parent</title>\n" + 
			"    <script>\n" + 
			"      function load() {\n" + 
			"        window.parent.postMessage('%s', '*');\n" + 
			"      }\n" + 
			"      window.onload = load;\n" + 
			"    </script>\n" + 
			"  </head>\n" + 
			"  <body>\n" + 
			"  </body>\n" + 
			"</html>";
	
	public static final String GET_RESPONSE = "<h1>The Synapse SFTP proxy is running</h1>";
	public static final String HTML_RESPONSE =
			"<html>\n" + 
			"  <body>\n" +
			"	%s\n"+
			"  </body>\n" + 
			"</html>";
}
