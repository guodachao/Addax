package com.wgzhao.addax.plugin.writer.gbase8amppftpwriter;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import com.wgzhao.addax.common.exception.AddaxException;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.Vector;


public class SFtpHelper implements IFtpHelper {
    private static final Logger LOG = LoggerFactory.getLogger(SFtpHelper.class);

    private Session session = null;
    private ChannelSftp channelSftp = null;

    @Override
    public void loginFtpServer(String host, String username, String password, int port, int timeout,
                               String connectMode, String privateKeyPath) {
        JSch jsch = new JSch(); // 创建JSch对象
        try {
            // 当用户配置私钥时，使用私钥方式登录。
            /*
            1、在客户端主机生成秘钥对
            ssh-keygen -t rsa -b 2048
            2、将生成的id_rsa.pub文件拷贝到服务器端ftp用户~/.ssh/目录，并将公钥中的内容追加到authorized_keys文件中
            cat ~/.ssh/id_rsa.pub >> ~/.ssh/authorized_keys
            3、修改配置文件/etc/ssh/sshd_config，开启PublickeyAuthentication yes，并重启服务
            4、验证配置是否正确
            ssh -i /root/.ssh/id_rsa sftp_user1@192.168.103.144
            5、在客户端主机将私钥转换为旧格式，高于openssh7.8版本生成的私钥默认是-----BEGIN OPENSSH PRIVATE KEY-----开头，经过测试
            jsch的最高版本0.1.55也不能正常解析，所以需要将私钥转换为旧格式，以-----BEGIN RSA PRIVATE KEY-----开头，转化命令为
            ssh-keygen -p -m PEM -f /root/.ssh/id_rsa
            后来经过测试，0.1.61（非maven仓库版本）可以正常解析，所以改用0.1.61进行打包。
             */
            if (!StringUtils.isBlank(privateKeyPath)) {
                jsch.addIdentity(privateKeyPath);
            }
            session = jsch.getSession(username, host, port);
            // 根据用户名，主机ip，端口获取一个Session对象
            // 如果服务器连接不上，则抛出异常
            if (session == null) {
                throw AddaxException.asAddaxException(GBase8aMppFtpWriterErrorCode.FAIL_LOGIN,
                        "session is null,无法通过sftp与服务器建立链接，请检查主机名和用户名是否正确.");
            }

            if (!StringUtils.isBlank(password) && StringUtils.isBlank(privateKeyPath)) {
                session.setPassword(password); // 设置密码
            }

            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config); // 为Session对象设置properties
            session.setTimeout(timeout); // 设置timeout时间
            session.connect(); // 通过Session建立链接

            channelSftp = (ChannelSftp) session.openChannel("sftp"); // 打开SFTP通道
            channelSftp.connect(); // 建立SFTP通道的连接
        } catch (JSchException e) {
            if(null != e.getCause()){
                String cause = e.getCause().toString();
                String unknownHostException = "java.net.UnknownHostException: " + host;
                String illegalArgumentException = "java.lang.IllegalArgumentException: port out of range:" + port;
                String wrongPort = "java.net.ConnectException: Connection refused";
                if (unknownHostException.equals(cause)) {
                    String message = String.format("请确认ftp服务器地址是否正确，无法连接到地址为: [%s] 的ftp服务器", host);
                    LOG.error(message);
                    throw AddaxException.asAddaxException(GBase8aMppFtpWriterErrorCode.FAIL_LOGIN, message, e);
                } else if (illegalArgumentException.equals(cause) || wrongPort.equals(cause) ) {
                    String message = String.format("请确认连接ftp服务器端口是否正确，错误的端口: [%s] ", port);
                    LOG.error(message);
                    throw AddaxException.asAddaxException(GBase8aMppFtpWriterErrorCode.FAIL_LOGIN, message, e);
                }
            }else {
                if("Auth fail".equals(e.getMessage())){
                    String message = String.format("与ftp服务器建立连接失败,请检查用户名和密码是否正确: [%s]",
                            "message:host =" + host + ",username = " + username + ",port =" + port);
                    LOG.error(message);
                    throw AddaxException.asAddaxException(GBase8aMppFtpWriterErrorCode.FAIL_LOGIN, message);
                }else{
                    String message = String.format("与ftp服务器建立连接失败 : [%s]",
                            "message:host =" + host + ",username = " + username + ",port =" + port);
                    LOG.error(message);
                    throw AddaxException.asAddaxException(GBase8aMppFtpWriterErrorCode.FAIL_LOGIN, message, e);
                }
            }
        }
    }

    @Override
    public void logoutFtpServer() {
        if (this.channelSftp != null) {
            this.channelSftp.disconnect();
            this.channelSftp = null;
        }
        if (this.session != null) {
            this.session.disconnect();
            this.session = null;
        }
    }

    @Override
    public OutputStream getOutputStream(String filePath) {
        try {
            this.printWorkingDirectory();
            String parentDir = filePath.substring(0,
                    StringUtils.lastIndexOf(filePath, IOUtils.DIR_SEPARATOR_UNIX));
            this.channelSftp.cd(parentDir);

            this.printWorkingDirectory();
            OutputStream writeOutputStream = this.channelSftp.put(filePath,
                    ChannelSftp.APPEND);

            if (null == writeOutputStream) {
                String message = String.format(
                        "打开FTP文件[%s]获取写出流时出错,请确认文件%s有权限创建，有权限写出等", filePath,
                        filePath);
                throw AddaxException.asAddaxException(
                        GBase8aMppFtpWriterErrorCode.OPEN_FILE_ERROR, message);
            }
            return writeOutputStream;
        } catch (SftpException e) {
            String message = String.format(
                    "写出文件[%s] 时出错,请确认文件%s有权限写出, errorMessage:%s", filePath,
                    filePath, e.getMessage());
            LOG.error(message);
            throw AddaxException.asAddaxException(
                    GBase8aMppFtpWriterErrorCode.OPEN_FILE_ERROR, message);
        }
    }

    @Override
    public void deleteFiles(Set<String> filesToDelete) {
        String eachFile = null;
        try {
            this.printWorkingDirectory();
            for (String each : filesToDelete) {
                LOG.info(String.format("delete file [%s].", each));
                eachFile = each;
                this.channelSftp.rm(each);
            }
        } catch (SftpException e) {
            String message = String.format(
                    "删除文件:[%s] 时发生异常,请确认指定文件有删除权限,以及网络交互正常, errorMessage:%s",
                    eachFile, e.getMessage());
            LOG.error(message);
            throw AddaxException.asAddaxException(
                    GBase8aMppFtpWriterErrorCode.COMMAND_FTP_IO_EXCEPTION, message, e);
        }
    }

    @Override
    public Set<String> getAllFilesInDir(String dir, String prefixFileName) {
        Set<String> allFilesWithPointedPrefix = new HashSet<String>();
        try {
            this.printWorkingDirectory();
            @SuppressWarnings("rawtypes")
            Vector allFiles = this.channelSftp.ls(dir);
            LOG.debug(String.format("ls: %s", JSON.toJSONString(allFiles,
                    JSONWriter.Feature.UseSingleQuotes)));
            for (int i = 0; i < allFiles.size(); i++) {
                ChannelSftp.LsEntry le = (ChannelSftp.LsEntry) allFiles.get(i);
                String strName = le.getFilename();
                if (strName.startsWith(prefixFileName)) {
                    allFilesWithPointedPrefix.add(strName);
                }
            }
        } catch (SftpException e) {
            String message = String
                    .format("获取path:[%s] 下文件列表时发生I/O异常,请确认与ftp服务器的连接正常,拥有目录ls权限, errorMessage:%s",
                            dir, e.getMessage());
            LOG.error(message);
            throw AddaxException.asAddaxException(
                    GBase8aMppFtpWriterErrorCode.COMMAND_FTP_IO_EXCEPTION, message, e);
        }
        return allFilesWithPointedPrefix;
    }

    private void printWorkingDirectory() {
        try {
            LOG.info(String.format("current working directory:%s",
                    this.channelSftp.pwd()));
        } catch (Exception e) {
            LOG.warn(String.format("printWorkingDirectory error:%s",
                    e.getMessage()));
        }
    }
}
