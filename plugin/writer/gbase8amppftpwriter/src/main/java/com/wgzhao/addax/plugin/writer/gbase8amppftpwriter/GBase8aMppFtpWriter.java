package com.wgzhao.addax.plugin.writer.gbase8amppftpwriter;

import com.wgzhao.addax.common.exception.AddaxException;
import com.wgzhao.addax.common.plugin.RecordReceiver;
import com.wgzhao.addax.common.spi.Writer;
import com.wgzhao.addax.common.util.Configuration;
import com.wgzhao.addax.storage.reader.StorageReaderErrorCode;
import com.wgzhao.addax.storage.writer.StorageWriterUtil;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.OutputStream;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class GBase8aMppFtpWriter extends Writer {
    public static class Job extends Writer.Job {
        private static final Logger LOG = LoggerFactory.getLogger(Job.class);

        private Configuration originConfig = null;

        private Set<String> allFileExists = null;

        // ftp链接参数
        private String protocol;
        private String host;
        private int port;
        private String username;
        private String password;
        private String privateKeyPath;
        private int timeout;
        private String connectPattern;

        private IFtpHelper ftpHelper = null;

        @Override
        public void init() {
            this.originConfig = this.getPluginJobConf();
            this.validateParameter();

            if ("sftp".equals(protocol)) {
                //sftp协议
                this.ftpHelper = new SFtpHelper();
            } else if ("ftp".equals(protocol)) {
                // ftp 协议
                this.ftpHelper = new StandardFtpHelper();
            }
            ftpHelper.loginFtpServer(host, username, password, port, timeout, connectPattern, privateKeyPath);
        }

        private void validateParameter() {
            this.protocol = this.originConfig.getNecessaryValue(Key.PROTOCOL,
                    GBase8aMppFtpWriterErrorCode.REQUIRED_VALUE);
            boolean ptrotocolTag = "ftp".equals(this.protocol) || "sftp".equals(this.protocol);
            if (!ptrotocolTag) {
                throw AddaxException.asAddaxException(GBase8aMppFtpWriterErrorCode.ILLEGAL_VALUE,
                        String.format("仅支持 ftp和sftp 传输协议 , 不支持您配置的传输协议: [%s]", protocol));
            }
            this.host = this.originConfig.getNecessaryValue(Key.HOST,
                    GBase8aMppFtpWriterErrorCode.REQUIRED_VALUE);
            this.port = Integer.valueOf(this.originConfig.getNecessaryValue(Key.PORT,
                    GBase8aMppFtpWriterErrorCode.REQUIRED_VALUE));
            this.username = this.originConfig.getNecessaryValue(Key.USERNAME,
                    GBase8aMppFtpWriterErrorCode.REQUIRED_VALUE);
            this.password = this.originConfig.getString(Key.PASSWORD);
            this.privateKeyPath = this.originConfig.getString(Key.PRIVATE_KEY_PATH);
            boolean passwordTag = StringUtils.isBlank(this.password) && StringUtils.isBlank(this.privateKeyPath);
            if (passwordTag) { // 如果密码为空，则需要配置私钥路径，通过免密方式登录。
                throw AddaxException.asAddaxException(GBase8aMppFtpWriterErrorCode.ILLEGAL_VALUE,
                        String.format("密码和私钥路径不能同时为空，当密码为空，则需要配置私钥路径，通过免密方式登录。"));
            }

            this.timeout = Integer.valueOf(this.originConfig.getNecessaryValue(Key.TIMEOUT,
                    GBase8aMppFtpWriterErrorCode.REQUIRED_VALUE));

            this.connectPattern = this.originConfig.getNecessaryValue(Key.CONNECT_PATTERN,
                    GBase8aMppFtpWriterErrorCode.REQUIRED_VALUE);
            boolean connectPatternTag = "PORT".equals(connectPattern) || "PASV".equals(connectPattern);
            if (!connectPatternTag) {
                throw AddaxException.asAddaxException(GBase8aMppFtpWriterErrorCode.ILLEGAL_VALUE,
                        String.format("不支持您配置的ftp传输模式: [%s]", connectPattern));
            }else{
                this.originConfig.set(Key.CONNECT_PATTERN, connectPattern);
            }

            String path = this.originConfig.getNecessaryValue(Key.PATH,
                    GBase8aMppFtpWriterErrorCode.REQUIRED_VALUE);
            if (StringUtils.isBlank(path)) {
                throw AddaxException.asAddaxException(
                        GBase8aMppFtpWriterErrorCode.REQUIRED_VALUE,
                        "您需要指定通过load data in file命令导入文件所在的ftp路径");
            }


            String fileName = this.originConfig.getNecessaryValue(Key.FILE_NAME,
                    GBase8aMppFtpWriterErrorCode.REQUIRED_VALUE);
            if (StringUtils.isBlank(fileName)) {
                throw AddaxException.asAddaxException(
                        GBase8aMppFtpWriterErrorCode.REQUIRED_VALUE,
                        "您需要指定通过load data in file命令导入文件的文件名");
            }

            String encoding = this.originConfig
                    .getString(
                            com.wgzhao.addax.common.base.Key.ENCODING,
                            com.wgzhao.addax.common.base.Constant.DEFAULT_ENCODING);
            if (StringUtils.isBlank(encoding)) {
                this.originConfig
                        .set(com.wgzhao.addax.common.base.Key.ENCODING,
                                com.wgzhao.addax.common.base.Constant.DEFAULT_ENCODING);
            } else {
                try {
                    encoding = encoding.trim();
                    this.originConfig
                            .set(com.wgzhao.addax.common.base.Key.ENCODING,
                                    encoding);
                    Charsets.toCharset(encoding);
                } catch (UnsupportedCharsetException uce) {
                    throw AddaxException.asAddaxException(
                            GBase8aMppFtpWriterErrorCode.ILLEGAL_VALUE,
                            String.format("不支持您配置的编码格式 : [%s]", encoding), uce);
                } catch (Exception e) {
                    throw AddaxException.asAddaxException(
                            GBase8aMppFtpWriterErrorCode.CONFIG_INVALID_EXCEPTION,
                            String.format("编码配置异常, 请联系我们: %s", e.getMessage()),
                            e);
                }
            }

            // 此参数在StorageWriterUtil中会用到，并不是必须配置，不配置情况下，默认为text格式，可以支持text或csv格式。
            String fileFormatStr = this.originConfig.getNecessaryValue(Key.FILE_FORMAT,
                    GBase8aMppFtpWriterErrorCode.REQUIRED_VALUE);
            if (StringUtils.isBlank(fileFormatStr)) {
                // 为满足后续判断，此处设置text格式，保持与底层处理逻辑一致。
                fileFormatStr = "text";
            }

            String delimiterInStr = this.originConfig
                    .getString(com.wgzhao.addax.common.base.Key.FIELD_DELIMITER);
            if (null != delimiterInStr && 1 != delimiterInStr.length() && "csv".equals(fileFormatStr)) {
                throw AddaxException.asAddaxException(
                        StorageReaderErrorCode.ILLEGAL_VALUE,
                        String.format("当file_format=csv时，仅支持单字符切分, 您配置的切分为 : [%s]",
                                delimiterInStr));
            }

            String nullFormatStr = this.originConfig.getNecessaryValue(Key.NULL_FORMAT,
                    GBase8aMppFtpWriterErrorCode.REQUIRED_VALUE);
            if (StringUtils.isBlank(nullFormatStr)) {
                throw AddaxException.asAddaxException(
                        GBase8aMppFtpWriterErrorCode.REQUIRED_VALUE,
                        "您需要指定null format");
            }

            String dateFormatStr = this.originConfig.getNecessaryValue(Key.DATE_FORMAT,
                    GBase8aMppFtpWriterErrorCode.REQUIRED_VALUE);
            if (StringUtils.isBlank(dateFormatStr)) {
                throw AddaxException.asAddaxException(
                        GBase8aMppFtpWriterErrorCode.REQUIRED_VALUE,
                        "您需要指定日志格式");
            }

            String loadDataInFileSQLString = this.originConfig.getNecessaryValue(Key.LOAD_DATA_IN_FILE_SQL,
                    GBase8aMppFtpWriterErrorCode.REQUIRED_VALUE);
            if (StringUtils.isBlank(loadDataInFileSQLString)) {
                throw AddaxException.asAddaxException(
                        GBase8aMppFtpWriterErrorCode.REQUIRED_VALUE,
                        "您需要指定load data in file命令");
            }

            String jdbcUrlString = this.originConfig.getNecessaryValue(Key.JDBC_URL,
                    GBase8aMppFtpWriterErrorCode.REQUIRED_VALUE);
            if (StringUtils.isBlank(jdbcUrlString)) {
                throw AddaxException.asAddaxException(
                        GBase8aMppFtpWriterErrorCode.REQUIRED_VALUE,
                        "您需要指定jdbc url");
            }

            String jdbcUsernameString = this.originConfig.getNecessaryValue(Key.JDBC_USERNAME,
                    GBase8aMppFtpWriterErrorCode.REQUIRED_VALUE);
            if (StringUtils.isBlank(jdbcUsernameString)) {
                throw AddaxException.asAddaxException(
                        GBase8aMppFtpWriterErrorCode.REQUIRED_VALUE,
                        "您需要指定jdbc username");
            }

            String jdbcPasswordString = this.originConfig.getNecessaryValue(Key.JDBC_PASSWORD,
                    GBase8aMppFtpWriterErrorCode.REQUIRED_VALUE);
            if (StringUtils.isBlank(jdbcPasswordString)) {
                throw AddaxException.asAddaxException(
                        GBase8aMppFtpWriterErrorCode.REQUIRED_VALUE,
                        "您需要指定jdbc password");
            }
        }

        @Override
        public void prepare() {
            String path = this.originConfig.getString(Key.PATH);
            String fileName = this.originConfig.getString(Key.FILE_NAME);
            Set<String> allFileExists = this.ftpHelper.getAllFilesInDir(path,
                    fileName);
            this.allFileExists = allFileExists;

            LOG.info(String.format(
                    "在写入数据前，开始清理 [%s] 下面以 [%s] 开头的内容", path, fileName));
            Set<String> fullFileNameToDelete = new HashSet<String>();
            for (String each : allFileExists) {
                fullFileNameToDelete.add(StorageWriterUtil
                        .buildFilePath(path, each, null).replace('\\', '/'));
            }
            LOG.info(String.format(
                    "在写入数据前，删除目录path:[%s] 下指定前缀fileName:[%s] 文件列表如下: [%s]", path,
                    fileName,
                    StringUtils.join(fullFileNameToDelete.iterator(), ", ")));

            this.ftpHelper.deleteFiles(fullFileNameToDelete);
        }

        @Override
        public void post() {
            // 执行写入导出文件到8aMpp
            LOG.info("start execute load data in file sql...");
            String jdbcUrl = this.originConfig.getString(Key.JDBC_URL);
            String jdbcUsername = this.originConfig.getString(Key.JDBC_USERNAME);
            String jdbcPassword = this.originConfig.getString(Key.JDBC_PASSWORD);
            String loadDataInFileSQL = this.originConfig.getString(Key.LOAD_DATA_IN_FILE_SQL);
            LOG.info("load data sql is" + loadDataInFileSQL);
            String preSql = this.originConfig.getString(Key.PRE_SQL);
            LOG.info("pre sql is" + preSql);
            String postSql = this.originConfig.getString(Key.POST_SQL);
            LOG.info("post sql is" + postSql);

            GBase8aMppSQLHelper sqlHelper = new GBase8aMppSQLHelper(jdbcUrl, jdbcUsername, jdbcPassword);
            if (StringUtils.isNotBlank(preSql)) {
                // 执行预处理sql
                sqlHelper.executeSQL(preSql, GBase8aMppFtpWriterErrorCode.EXECUTE_SQL_EXCEPTION);
                LOG.info("end execute pre sql...");
            }
            // 执行导入文件到8aMpp
            sqlHelper.executeSQL(loadDataInFileSQL, GBase8aMppFtpWriterErrorCode.EXECUTE_SQL_EXCEPTION);
            LOG.info("end execute load data in file sql...");
            if (StringUtils.isNotBlank(postSql)) {
                // 执行后处理sql
                sqlHelper.executeSQL(postSql, GBase8aMppFtpWriterErrorCode.EXECUTE_SQL_EXCEPTION);
                LOG.info("end execute post sql...");
            }

            String path = this.originConfig.getString(Key.PATH);
            String fileName = this.originConfig.getString(Key.FILE_NAME);
            Set<String> allFileExists = this.ftpHelper.getAllFilesInDir(path,
                    fileName);
            this.allFileExists = allFileExists;

            LOG.info(String.format(
                    "在写入数据后，开始清理 [%s] 下面以 [%s] 开头的内容", path, fileName));
            Set<String> fullFileNameToDelete = new HashSet<String>();
            for (String each : allFileExists) {
                fullFileNameToDelete.add(StorageWriterUtil
                        .buildFilePath(path, each, null).replace('\\', '/'));
            }
            LOG.info(String.format(
                    "在写入数据后，删除目录path:[%s] 下指定前缀fileName:[%s] 文件列表如下: [%s]", path,
                    fileName,
                    StringUtils.join(fullFileNameToDelete.iterator(), ", ")));

            this.ftpHelper.deleteFiles(fullFileNameToDelete);
        }

        @Override
        public void destroy() {
            try {
                this.ftpHelper.logoutFtpServer();
            } catch (Exception e) {
                String message = String.format(
                        "关闭与ftp服务器连接失败, host:%s, username:%s, port:%s, errorMessage:%s",
                        host, username, port, e.getMessage());
                LOG.error(message, e);
            }
        }

        @Override
        public List<Configuration> split(int adviceNumber) {
            LOG.info("split adviceNumber: "+adviceNumber);
            return StorageWriterUtil.split(this.originConfig,
                    this.allFileExists, adviceNumber);
        }
    }

    public static class Task extends Writer.Task {
        private static final Logger LOG = LoggerFactory.getLogger(Task.class);

        private Configuration originConfig;

        private String path;
        private String fileName;

        private String protocol;
        private String host;
        private int port;
        private String username;
        private String password;
        private String privateKeyPath;
        private int timeout;
        private String connectPattern;

        private IFtpHelper ftpHelper = null;

        @Override
        public void init() {
            this.originConfig = this.getPluginJobConf();

            this.path = this.originConfig.getString(Key.PATH);
            this.fileName = this.originConfig.getString(Key.FILE_NAME);

            this.protocol = originConfig.getString(Key.PROTOCOL);
            this.host = originConfig.getString(Key.HOST);
            this.port = originConfig.getInt(Key.PORT);
            this.username = originConfig.getString(Key.USERNAME);
            this.password = originConfig.getString(Key.PASSWORD);
            this.privateKeyPath = originConfig.getString(Key.PRIVATE_KEY_PATH);
            this.timeout = originConfig.getInt(Key.TIMEOUT);
            this.connectPattern = originConfig.getString(Key.CONNECT_PATTERN);

            if ("sftp".equals(protocol)) {
                //sftp协议
                this.ftpHelper = new SFtpHelper();
            } else if ("ftp".equals(protocol)) {
                // ftp 协议
                this.ftpHelper = new StandardFtpHelper();
            }
            ftpHelper.loginFtpServer(host, username, password, port, timeout, connectPattern, privateKeyPath);
        }

        @Override
        public void prepare() {

        }

        @Override
        public void startWrite(RecordReceiver lineReceiver) {

            // 执行写数据到文件
            LOG.info("start write data to load data in file ...");
            String fileFullPath = StorageWriterUtil.buildFilePath(
                    this.path, this.fileName, null).replace('\\', '/');
            LOG.info(String.format("write to file : [%s]", fileFullPath));

            OutputStream outputStream = null;
            try {
                outputStream = this.ftpHelper.getOutputStream(fileFullPath);
                StorageWriterUtil.writeToStream(lineReceiver,
                        outputStream, this.originConfig, this.fileName,
                        this.getTaskPluginCollector());
            } catch (Exception e) {
                throw AddaxException.asAddaxException(
                        GBase8aMppFtpWriterErrorCode.WRITE_FILE_IO_ERROR,
                        String.format("无法创建待写文件 : [%s]", this.fileName), e);
            } finally {
                IOUtils.closeQuietly(outputStream);
            }
            LOG.info("end write data to load data in file ...");
        }

        @Override
        public void post() {

        }

        @Override
        public void destroy() {
            String host = originConfig.getString(Key.HOST);
            int port = originConfig.getInt(Key.PORT);
            String username = originConfig.getString(Key.USERNAME);

            try {
                this.ftpHelper.logoutFtpServer();
            } catch (Exception e) {
                String message = String
                        .format("关闭与ftp服务器连接失败, host:%s, username:%s, port:%s, errorMessage:%s",
                                host, username, port, e.getMessage());
                LOG.error(message, e);
            }
        }
    }
}
