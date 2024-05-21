package com.wgzhao.addax.plugin.writer.gaussdbwriter;

import com.wgzhao.addax.common.base.Key;
import com.wgzhao.addax.common.exception.AddaxException;
import com.wgzhao.addax.common.plugin.RecordReceiver;
import com.wgzhao.addax.common.spi.Writer;
import com.wgzhao.addax.common.util.Configuration;
import com.wgzhao.addax.rdbms.util.DBUtilErrorCode;
import com.wgzhao.addax.rdbms.util.DataBaseType;
import com.wgzhao.addax.rdbms.writer.CommonRdbmsWriter;

import java.util.List;

public class GaussDbWriter extends Writer {

    private static final DataBaseType DATABASE_TYPE = DataBaseType.GaussDB;

    public static class Job extends Writer.Job {
        private Configuration originalConfig = null;
        private CommonRdbmsWriter.Job commonRdbmsWriterMaster;

        @Override
        public void init() {
            this.originalConfig = super.getPluginJobConf();

            // warn：not like mysql, GaussDB only support insert mode, don't use
//            String writeMode = this.originalConfig.getString(Key.WRITE_MODE);
//            if (null != writeMode) {
//                throw AddaxException.asAddaxException(DBUtilErrorCode.CONF_ERROR,
//                        String.format("写入模式(writeMode)配置有误. 因为GaussDB不支持配置参数项 writeMode: %s, GaussDB仅使用insert sql 插入数据. 请检查您的配置并作出修改.", writeMode));
//            }

            this.commonRdbmsWriterMaster = new CommonRdbmsWriter.Job(DATABASE_TYPE);
            this.commonRdbmsWriterMaster.init(this.originalConfig);
        }

        @Override
        public void prepare() {
            this.commonRdbmsWriterMaster.prepare(this.originalConfig);
        }

        @Override
        public List<Configuration> split(int mandatoryNumber) {
            return this.commonRdbmsWriterMaster.split(this.originalConfig, mandatoryNumber);
        }

        @Override
        public void post() {
            this.commonRdbmsWriterMaster.post(this.originalConfig);
        }

        @Override
        public void destroy() {
            this.commonRdbmsWriterMaster.destroy(this.originalConfig);
        }

    }

    public static class Task extends Writer.Task {
        private Configuration writerSliceConfig;
        private CommonRdbmsWriter.Task commonRdbmsWriterSlave;

        @Override
        public void init() {
            this.writerSliceConfig = super.getPluginJobConf();
            this.commonRdbmsWriterSlave = new CommonRdbmsWriter.Task(DATABASE_TYPE){
                @Override
                public String calcValueHolder(String columnType) {
                    if ("serial".equalsIgnoreCase(columnType)) {
                        return "?::INT";
                    } else if ("bit".equalsIgnoreCase(columnType)) {
                        return "?::BIT VARYING";
                    } else if ("bigserial".equalsIgnoreCase(columnType)) {
                        return "?::BIGINT";
                    } else if ("xml".equalsIgnoreCase(columnType)) {
                        return "?::XML";
                    } else if ("money".equalsIgnoreCase(columnType)) {
                        return "?::NUMERIC::MONEY";
                    } else if ("bool".equalsIgnoreCase(columnType)) {
                        return "?::BOOLEAN";
                    }
                    return super.calcValueHolder(columnType);
                }
            };
            this.commonRdbmsWriterSlave.init(this.writerSliceConfig);
        }

        @Override
        public void prepare() {
            this.commonRdbmsWriterSlave.prepare(this.writerSliceConfig);
        }

        public void startWrite(RecordReceiver recordReceiver) {
            this.commonRdbmsWriterSlave.startWrite(recordReceiver, this.writerSliceConfig, super.getTaskPluginCollector());
        }

        @Override
        public void post() {
            this.commonRdbmsWriterSlave.post(this.writerSliceConfig);
        }

        @Override
        public void destroy() {
            this.commonRdbmsWriterSlave.destroy(this.writerSliceConfig);
        }

    }

}
