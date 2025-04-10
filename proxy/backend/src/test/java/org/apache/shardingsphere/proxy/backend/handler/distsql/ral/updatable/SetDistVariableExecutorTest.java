/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.proxy.backend.handler.distsql.ral.updatable;

import org.apache.shardingsphere.distsql.parser.statement.ral.updatable.SetDistVariableStatement;
import org.apache.shardingsphere.infra.config.mode.ModeConfiguration;
import org.apache.shardingsphere.infra.config.props.ConfigurationPropertyKey;
import org.apache.shardingsphere.infra.config.props.LoggerLevel;
import org.apache.shardingsphere.infra.instance.ComputeNodeInstance;
import org.apache.shardingsphere.infra.instance.InstanceContext;
import org.apache.shardingsphere.infra.instance.metadata.InstanceMetaData;
import org.apache.shardingsphere.infra.instance.workerid.WorkerIdGenerator;
import org.apache.shardingsphere.infra.lock.LockContext;
import org.apache.shardingsphere.infra.metadata.ShardingSphereMetaData;
import org.apache.shardingsphere.infra.util.eventbus.EventBusContext;
import org.apache.shardingsphere.mode.manager.ContextManager;
import org.apache.shardingsphere.mode.manager.standalone.StandaloneModeContextManager;
import org.apache.shardingsphere.mode.metadata.MetaDataContexts;
import org.apache.shardingsphere.mode.metadata.persist.MetaDataPersistService;
import org.apache.shardingsphere.proxy.backend.context.ProxyContext;
import org.apache.shardingsphere.proxy.backend.exception.InvalidValueException;
import org.apache.shardingsphere.proxy.backend.handler.distsql.ral.common.enums.VariableEnum;
import org.apache.shardingsphere.proxy.backend.session.ConnectionSession;
import org.apache.shardingsphere.proxy.backend.session.transaction.TransactionStatus;
import org.apache.shardingsphere.proxy.backend.util.ProxyContextRestorer;
import org.apache.shardingsphere.proxy.backend.util.SystemPropertyUtil;
import org.apache.shardingsphere.transaction.api.TransactionType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.sql.SQLException;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public final class SetDistVariableExecutorTest extends ProxyContextRestorer {
    
    @Mock
    private ConnectionSession connectionSession;
    
    @Test
    public void assertExecuteWithTransactionType() throws SQLException {
        SetDistVariableStatement statement = new SetDistVariableStatement("transaction_type", "local");
        when(connectionSession.getTransactionStatus()).thenReturn(new TransactionStatus(TransactionType.XA));
        SetDistVariableHandler handler = new SetDistVariableHandler();
        handler.init(statement, connectionSession);
        handler.execute();
        assertThat(connectionSession.getTransactionStatus().getTransactionType().name(), is(TransactionType.LOCAL.name()));
    }
    
    @Test
    public void assertExecuteWithAgent() throws SQLException {
        SetDistVariableStatement statement = new SetDistVariableStatement("AGENT_PLUGINS_ENABLED", Boolean.FALSE.toString());
        SetDistVariableHandler handler = new SetDistVariableHandler();
        handler.init(statement, connectionSession);
        handler.execute();
        String actualValue = SystemPropertyUtil.getSystemProperty(VariableEnum.AGENT_PLUGINS_ENABLED.name(), "default");
        assertThat(actualValue, is(Boolean.FALSE.toString()));
    }
    
    @Test
    public void assertExecuteWithConfigurationKey() throws SQLException {
        ContextManager contextManager = mockContextManager();
        SetDistVariableStatement statement = new SetDistVariableStatement("proxy_frontend_flush_threshold", "1024");
        SetDistVariableHandler handler = new SetDistVariableHandler();
        handler.init(statement, connectionSession);
        handler.execute();
        Object actualValue = contextManager.getMetaDataContexts().getMetaData().getProps().getProps().get("proxy-frontend-flush-threshold");
        assertThat(actualValue.toString(), is("1024"));
        assertThat(contextManager.getMetaDataContexts().getMetaData().getProps().getValue(ConfigurationPropertyKey.PROXY_FRONTEND_FLUSH_THRESHOLD), is(1024));
    }
    
    @Test
    public void assertExecuteWithSystemLogLevel() throws SQLException {
        ContextManager contextManager = mockContextManager();
        SetDistVariableStatement statement = new SetDistVariableStatement("system_log_level", "debug");
        SetDistVariableHandler handler = new SetDistVariableHandler();
        handler.init(statement, connectionSession);
        handler.execute();
        Object actualValue = contextManager.getMetaDataContexts().getMetaData().getProps().getProps().get("system-log-level");
        assertThat(actualValue.toString(), is("DEBUG"));
        assertThat(contextManager.getMetaDataContexts().getMetaData().getProps().getValue(ConfigurationPropertyKey.SYSTEM_LOG_LEVEL), is(LoggerLevel.DEBUG));
    }
    
    @Test(expected = InvalidValueException.class)
    public void assertExecuteWithWrongSystemLogLevel() throws SQLException {
        mockContextManager();
        SetDistVariableStatement statement = new SetDistVariableStatement("system_log_level", "invalid");
        SetDistVariableHandler handler = new SetDistVariableHandler();
        handler.init(statement, connectionSession);
        handler.execute();
    }
    
    private ContextManager mockContextManager() {
        StandaloneModeContextManager standaloneModeContextManager = new StandaloneModeContextManager();
        ContextManager contextManager = new ContextManager(new MetaDataContexts(mock(MetaDataPersistService.class), new ShardingSphereMetaData()),
                new InstanceContext(new ComputeNodeInstance(mock(InstanceMetaData.class)), mock(WorkerIdGenerator.class),
                        new ModeConfiguration("Standalone", null), standaloneModeContextManager, mock(LockContext.class), new EventBusContext()));
        standaloneModeContextManager.setContextManagerAware(contextManager);
        ProxyContext.init(contextManager);
        return contextManager;
    }
}
