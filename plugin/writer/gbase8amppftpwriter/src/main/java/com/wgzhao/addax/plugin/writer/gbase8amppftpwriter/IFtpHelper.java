package com.wgzhao.addax.plugin.writer.gbase8amppftpwriter;

import java.io.OutputStream;
import java.util.Set;

public interface IFtpHelper {

    public void loginFtpServer(String host, String username, String password, int port, int timeout, String connectMode, String privateKeyPath);

    public void logoutFtpServer();

    public OutputStream getOutputStream(String filePath);

    public void deleteFiles(Set<String> filesToDelete);

    public Set<String> getAllFilesInDir(String dir, String prefixFileName);
}
