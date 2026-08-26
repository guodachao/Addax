package com.wgzhao.addax.plugin.writer.gbase8amppftpwriter;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Set;

public class StandardFtpHelper implements IFtpHelper {
    private static final Logger LOG = LoggerFactory
            .getLogger(StandardFtpHelper.class);
    FTPClient ftpClient = null;

    @Override
    public void loginFtpServer(String host, String username, String password,
            int port, int timeout, String connectMode, String privateKeyPath) {
        this.ftpClient = new FTPClient();
        try {
            this.ftpClient.setControlEncoding("UTF-8");
            // 不需要写死ftp server的OS TYPE,FTPClient getSystemType()方法会自动识别
            // this.ftpClient.configure(new FTPClientConfig(FTPClientConfig.SYST_UNIX));
            this.ftpClient.setDefaultTimeout(timeout);
            this.ftpClient.setConnectTimeout(timeout);
            this.ftpClient.setDataTimeout(timeout);

            // 连接登录
            this.ftpClient.connect(host, port);
            this.ftpClient.login(username, password);

            this.ftpClient.enterRemotePassiveMode();
            this.ftpClient.enterLocalPassiveMode();
            int reply = this.ftpClient.getReplyCode();
            if (!FTPReply.isPositiveCompletion(reply)) {
                this.ftpClient.disconnect();
                String message = String
                        .format("与ftp服务器建立连接失败,host:%s, port:%s, username:%s, replyCode:%s",
                                host, port, username, reply);
                LOG.error(message);
                throw com.wgzhao.addax.common.exception.AddaxException.asAddaxException(
                        GBase8aMppFtpWriterErrorCode.FAIL_LOGIN, message);
            }
        } catch (UnknownHostException e) {
            String message = String.format(
                    "请确认ftp服务器地址是否正确，无法连接到地址为: [%s] 的ftp服务器, errorMessage:%s",
                    host, e.getMessage());
            LOG.error(message);
            throw com.wgzhao.addax.common.exception.AddaxException.asAddaxException(
                    GBase8aMppFtpWriterErrorCode.FAIL_LOGIN, message, e);
        } catch (IllegalArgumentException e) {
            String message = String.format(
                    "请确认连接ftp服务器端口是否正确，错误的端口: [%s], errorMessage:%s", port,
                    e.getMessage());
            LOG.error(message);
            throw com.wgzhao.addax.common.exception.AddaxException.asAddaxException(
                    GBase8aMppFtpWriterErrorCode.FAIL_LOGIN, message, e);
        } catch (Exception e) {
            String message = String
                    .format("与ftp服务器建立连接失败,host:%s, port:%s, username:%s, errorMessage:%s",
                            host, port, username, e.getMessage());
            LOG.error(message);
            throw com.wgzhao.addax.common.exception.AddaxException.asAddaxException(
                    GBase8aMppFtpWriterErrorCode.FAIL_LOGIN, message, e);
        }

    }

    @Override
    public void logoutFtpServer() {
        if (this.ftpClient.isConnected()) {
            try {
                this.ftpClient.logout();
            } catch (IOException e) {
                String message = String.format(
                        "与ftp服务器断开连接失败, errorMessage:%s", e.getMessage());
                LOG.error(message);
                throw com.wgzhao.addax.common.exception.AddaxException.asAddaxException(
                        GBase8aMppFtpWriterErrorCode.FAIL_DISCONNECT, message, e);
            } finally {
                if (this.ftpClient.isConnected()) {
                    try {
                        this.ftpClient.disconnect();
                    } catch (IOException e) {
                        String message = String.format(
                                "与ftp服务器断开连接失败, errorMessage:%s",
                                e.getMessage());
                        LOG.error(message);
                        throw com.wgzhao.addax.common.exception.AddaxException.asAddaxException(
                                GBase8aMppFtpWriterErrorCode.FAIL_DISCONNECT, message, e);
                    }
                }
                this.ftpClient = null;
            }
        }
    }

    @Override
    public OutputStream getOutputStream(String filePath) {
        try {
            this.printWorkingDirectory();
            String parentDir = filePath.substring(0,
                    StringUtils.lastIndexOf(filePath, IOUtils.DIR_SEPARATOR_UNIX));
            this.ftpClient.changeWorkingDirectory(parentDir);
            this.printWorkingDirectory();
            OutputStream writeOutputStream = this.ftpClient
                    .appendFileStream(filePath);
            String message = String.format(
                    "打开FTP文件[%s]获取写出流时出错,请确认文件%s有权限创建，有权限写出等", filePath,
                    filePath);
            if (null == writeOutputStream) {
                throw com.wgzhao.addax.common.exception.AddaxException.asAddaxException(
                        GBase8aMppFtpWriterErrorCode.OPEN_FILE_ERROR, message);
            }

            return writeOutputStream;
        } catch (IOException e) {
            String message = String.format(
                    "写出文件 : [%s] 时出错,请确认文件:[%s]存在且配置的用户有权限写, errorMessage:%s",
                    filePath, filePath, e.getMessage());
            LOG.error(message);
            throw com.wgzhao.addax.common.exception.AddaxException.asAddaxException(
                    GBase8aMppFtpWriterErrorCode.OPEN_FILE_ERROR, message);
        }
    }

    @Override
    public void deleteFiles(Set<String> filesToDelete) {
        String eachFile = null;
        boolean deleteOk = false;
        try {
            this.printWorkingDirectory();
            for (String each : filesToDelete) {
                LOG.info(String.format("delete file [%s].", each));
                eachFile = each;
                deleteOk = this.ftpClient.deleteFile(each);
                if (!deleteOk) {
                    String message = String.format(
                            "删除文件:[%s] 时失败,请确认指定文件有删除权限", eachFile);
                    throw com.wgzhao.addax.common.exception.AddaxException.asAddaxException(
                            GBase8aMppFtpWriterErrorCode.COMMAND_FTP_IO_EXCEPTION,
                            message);
                }
            }
        } catch (IOException e) {
            String message = String.format(
                    "删除文件:[%s] 时发生异常,请确认指定文件有删除权限,以及网络交互正常, errorMessage:%s",
                    eachFile, e.getMessage());
            LOG.error(message);
            throw com.wgzhao.addax.common.exception.AddaxException.asAddaxException(
                    GBase8aMppFtpWriterErrorCode.COMMAND_FTP_IO_EXCEPTION, message, e);
        }
    }

    @Override
    public Set<String> getAllFilesInDir(String dir, String prefixFileName) {
        Set<String> allFilesWithPointedPrefix = new HashSet<String>();
        try {
            boolean isDirExist = this.ftpClient.changeWorkingDirectory(dir);
            if (!isDirExist) {
                throw com.wgzhao.addax.common.exception.AddaxException.asAddaxException(
                        GBase8aMppFtpWriterErrorCode.COMMAND_FTP_IO_EXCEPTION,
                        String.format("进入目录[%s]失败", dir));
            }
            this.printWorkingDirectory();
            FTPFile[] fs = this.ftpClient.listFiles(dir);
            // LOG.debug(JSON.toJSONString(this.ftpClient.listNames(dir)));
            LOG.debug(String.format("ls: %s",
                    JSON.toJSONString(fs, JSONWriter.Feature.UseSingleQuotes)));
            for (FTPFile ff : fs) {
                String strName = ff.getName();
                if (strName.startsWith(prefixFileName)) {
                    allFilesWithPointedPrefix.add(strName);
                }
            }
        } catch (IOException e) {
            String message = String
                    .format("获取path:[%s] 下文件列表时发生I/O异常,请确认与ftp服务器的连接正常,拥有目录ls权限, errorMessage:%s",
                            dir, e.getMessage());
            LOG.error(message);
            throw com.wgzhao.addax.common.exception.AddaxException.asAddaxException(
                    GBase8aMppFtpWriterErrorCode.COMMAND_FTP_IO_EXCEPTION, message, e);
        }
        return allFilesWithPointedPrefix;
    }

    private void printWorkingDirectory() {
        try {
            LOG.info(String.format("current working directory:%s",
                    this.ftpClient.printWorkingDirectory()));
        } catch (Exception e) {
            LOG.warn(String.format("printWorkingDirectory error:%s",
                    e.getMessage()));
        }
    }
}
