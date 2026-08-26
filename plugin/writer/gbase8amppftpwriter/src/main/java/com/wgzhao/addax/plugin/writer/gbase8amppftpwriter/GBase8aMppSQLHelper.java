package com.wgzhao.addax.plugin.writer.gbase8amppftpwriter;

import com.wgzhao.addax.common.exception.AddaxException;
import com.wgzhao.addax.common.spi.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class GBase8aMppSQLHelper {

    private static final Logger LOG = LoggerFactory.getLogger(GBase8aMppSQLHelper.class);

    private String url;
    private String username;
    private String password;

    public GBase8aMppSQLHelper(String url, String username, String password) {
        this.url = url;
        this.username = username;
        this.password = password;
    }

    public boolean executeSQL(String sql, ErrorCode errorCode) {

        boolean result = false;

        Connection conn = null;
        Statement stm = null;

        try {
            Class.forName("com.gbase.jdbc.Driver");

            LOG.debug("GBase 8a mpp url is:"+this.url);
            LOG.debug("GBase 8a mpp username is:"+this.username);
            LOG.debug("GBase 8a mpp password is:"+this.password);

            conn = DriverManager.getConnection(this.url, this.username, this.password);
            stm = conn.createStatement();

            LOG.debug("GBase 8a mpp sql is:"+sql);
            result = stm.execute(sql);
        } catch (Exception e) {
            result = false;
            LOG.error("execute GBase 8a mpp SQL error:"+e.getMessage(), e);
            throw AddaxException.asAddaxException(errorCode,
                    String.format("execute GBase 8a mpp SQL error: %s", e.getMessage()));
        } finally {
            if (stm != null) {
                try {
                    stm.close();
                } catch (SQLException e) {
                    LOG.error("close GBase 8a Mpp statement error:"+e.getMessage(), e);
                    throw AddaxException.asAddaxException(errorCode,
                            String.format("close GBase 8a Mpp statement error: %s", e.getMessage()));
                }
            }
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    LOG.error("close GBase 8a Mpp connection error:"+e.getMessage(), e);
                    throw AddaxException.asAddaxException(errorCode,
                            String.format("close GBase 8a Mpp connection error: %s", e.getMessage()));
                }
            }
        }

        return result;
    }
}
