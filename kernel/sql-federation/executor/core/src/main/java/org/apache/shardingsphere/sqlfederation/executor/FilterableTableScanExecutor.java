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

package org.apache.shardingsphere.sqlfederation.executor;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.calcite.adapter.java.JavaTypeFactory;
import org.apache.calcite.config.CalciteConnectionConfig;
import org.apache.calcite.config.CalciteConnectionConfigImpl;
import org.apache.calcite.jdbc.JavaTypeFactoryImpl;
import org.apache.calcite.linq4j.AbstractEnumerable;
import org.apache.calcite.linq4j.Enumerable;
import org.apache.calcite.linq4j.Enumerator;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.prepare.CalciteCatalogReader;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.RelFactories;
import org.apache.calcite.rel.rel2sql.RelToSqlConverter;
import org.apache.calcite.rex.RexBuilder;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.SqlDialect;
import org.apache.calcite.sql.util.SqlString;
import org.apache.calcite.tools.RelBuilder;
import org.apache.shardingsphere.infra.binder.QueryContext;
import org.apache.shardingsphere.infra.binder.SQLStatementContextFactory;
import org.apache.shardingsphere.infra.binder.statement.SQLStatementContext;
import org.apache.shardingsphere.infra.context.ConnectionContext;
import org.apache.shardingsphere.infra.context.kernel.KernelProcessor;
import org.apache.shardingsphere.infra.database.type.DatabaseType;
import org.apache.shardingsphere.infra.database.type.DatabaseTypeEngine;
import org.apache.shardingsphere.infra.executor.kernel.model.ExecutionGroup;
import org.apache.shardingsphere.infra.executor.kernel.model.ExecutionGroupContext;
import org.apache.shardingsphere.infra.executor.kernel.model.ExecutionGroupReportContext;
import org.apache.shardingsphere.infra.executor.sql.context.ExecutionContext;
import org.apache.shardingsphere.infra.executor.sql.execute.engine.driver.jdbc.JDBCExecutionUnit;
import org.apache.shardingsphere.infra.executor.sql.execute.engine.driver.jdbc.JDBCExecutor;
import org.apache.shardingsphere.infra.executor.sql.execute.engine.driver.jdbc.JDBCExecutorCallback;
import org.apache.shardingsphere.infra.executor.sql.execute.result.ExecuteResult;
import org.apache.shardingsphere.infra.executor.sql.execute.result.query.QueryResult;
import org.apache.shardingsphere.infra.executor.sql.execute.result.query.QueryResultMetaData;
import org.apache.shardingsphere.infra.executor.sql.execute.result.query.impl.driver.jdbc.type.memory.JDBCMemoryQueryResult;
import org.apache.shardingsphere.infra.executor.sql.execute.result.query.impl.driver.jdbc.type.stream.JDBCStreamQueryResult;
import org.apache.shardingsphere.infra.executor.sql.prepare.driver.DriverExecutionPrepareEngine;
import org.apache.shardingsphere.infra.executor.sql.process.ExecuteProcessEngine;
import org.apache.shardingsphere.infra.merge.MergeEngine;
import org.apache.shardingsphere.infra.merge.result.MergedResult;
import org.apache.shardingsphere.infra.metadata.ShardingSphereMetaData;
import org.apache.shardingsphere.infra.metadata.data.ShardingSphereData;
import org.apache.shardingsphere.infra.metadata.data.ShardingSphereSchemaData;
import org.apache.shardingsphere.infra.metadata.data.ShardingSphereTableData;
import org.apache.shardingsphere.infra.metadata.database.ShardingSphereDatabase;
import org.apache.shardingsphere.infra.metadata.database.rule.ShardingSphereRuleMetaData;
import org.apache.shardingsphere.infra.metadata.database.schema.decorator.model.ShardingSphereTable;
import org.apache.shardingsphere.infra.parser.sql.SQLStatementParserEngine;
import org.apache.shardingsphere.infra.util.exception.external.sql.type.wrapper.SQLWrapperException;
import org.apache.shardingsphere.sql.parser.sql.common.statement.SQLStatement;
import org.apache.shardingsphere.sqlfederation.SQLDialectFactory;
import org.apache.shardingsphere.sqlfederation.optimizer.context.OptimizerContext;
import org.apache.shardingsphere.sqlfederation.optimizer.executor.FilterableScanNodeExecutorContext;
import org.apache.shardingsphere.sqlfederation.optimizer.executor.ScanNodeExecutorContext;
import org.apache.shardingsphere.sqlfederation.optimizer.executor.TableScanExecutor;
import org.apache.shardingsphere.sqlfederation.optimizer.metadata.filter.FilterableSchema;
import org.apache.shardingsphere.sqlfederation.optimizer.util.SQLFederationPlannerUtil;
import org.apache.shardingsphere.sqlfederation.row.EmptyRowEnumerator;
import org.apache.shardingsphere.sqlfederation.row.MemoryEnumerator;
import org.apache.shardingsphere.sqlfederation.row.SQLFederationRowEnumerator;
import org.apache.shardingsphere.sqlfederation.spi.SQLFederationExecutorContext;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Filterable table scan executor.
 */
@RequiredArgsConstructor
public final class FilterableTableScanExecutor implements TableScanExecutor {
    
    private static final JavaTypeFactory JAVA_TYPE_FACTORY = new JavaTypeFactoryImpl();
    
    private final DriverExecutionPrepareEngine<JDBCExecutionUnit, Connection> prepareEngine;
    
    private final JDBCExecutor jdbcExecutor;
    
    private final JDBCExecutorCallback<? extends ExecuteResult> callback;
    
    private final OptimizerContext optimizerContext;
    
    private final ShardingSphereRuleMetaData globalRuleMetaData;
    
    private final TableScanExecutorContext executorContext;
    
    private final ShardingSphereData data;
    
    @Override
    public Enumerable<Object> executeScalar(final ShardingSphereTable table, final ScanNodeExecutorContext scanContext) {
        return new AbstractEnumerable<Object>() {
            
            @Override
            public Enumerator<Object> enumerator() {
                return new EmptyRowEnumerator<>();
            }
        };
    }
    
    @Override
    public Enumerable<Object[]> execute(final ShardingSphereTable table, final ScanNodeExecutorContext scanContext) {
        String databaseName = executorContext.getDatabaseName().toLowerCase();
        String schemaName = executorContext.getSchemaName().toLowerCase();
        DatabaseType databaseType = DatabaseTypeEngine.getTrunkDatabaseType(optimizerContext.getParserContext(databaseName).getDatabaseType().getType());
        if (databaseType.getSystemSchemas().contains(schemaName)) {
            return executeByShardingSphereData(databaseName, schemaName, table);
        }
        SqlString sqlString = createSQLString(table, (FilterableScanNodeExecutorContext) scanContext, SQLDialectFactory.getSQLDialect(databaseType));
        SQLFederationExecutorContext federationContext = executorContext.getFederationContext();
        QueryContext queryContext = createQueryContext(federationContext.getMetaData(), sqlString, databaseType);
        ShardingSphereDatabase database = federationContext.getMetaData().getDatabase(databaseName);
        // TODO need to get session context
        ExecutionContext context = new KernelProcessor().generateExecutionContext(queryContext, database, globalRuleMetaData, executorContext.getProps(), new ConnectionContext());
        if (federationContext.isPreview()) {
            federationContext.getExecutionUnits().addAll(context.getExecutionUnits());
            return createEmptyEnumerable();
        }
        return execute(databaseType, queryContext, database, context);
    }
    
    private AbstractEnumerable<Object[]> execute(final DatabaseType databaseType, final QueryContext queryContext, final ShardingSphereDatabase database, final ExecutionContext context) {
        ExecuteProcessEngine executeProcessEngine = new ExecuteProcessEngine();
        try {
            ExecutionGroupContext<JDBCExecutionUnit> executionGroupContext =
                    prepareEngine.prepare(context.getRouteContext(), context.getExecutionUnits(), new ExecutionGroupReportContext(database.getName()));
            setParameters(executionGroupContext.getInputGroups());
            executeProcessEngine.initializeExecution(executionGroupContext, context.getQueryContext());
            List<QueryResult> queryResults = execute(executionGroupContext, databaseType);
            // TODO need to get session context
            MergeEngine mergeEngine = new MergeEngine(database, executorContext.getProps(), new ConnectionContext());
            MergedResult mergedResult = mergeEngine.merge(queryResults, queryContext.getSqlStatementContext());
            Collection<Statement> statements = getStatements(executionGroupContext.getInputGroups());
            return createEnumerable(mergedResult, queryResults.get(0).getMetaData(), statements);
        } catch (final SQLException ex) {
            throw new SQLWrapperException(ex);
        } finally {
            executeProcessEngine.cleanExecution();
        }
    }
    
    private List<QueryResult> execute(final ExecutionGroupContext<JDBCExecutionUnit> executionGroupContext, final DatabaseType databaseType) throws SQLException {
        Collection<QueryResult> queryResults = jdbcExecutor.execute(executionGroupContext, callback).stream().map(each -> (QueryResult) each).collect(Collectors.toList());
        List<QueryResult> result = new LinkedList<>();
        for (QueryResult each : queryResults) {
            QueryResult queryResult = each instanceof JDBCStreamQueryResult
                    ? new JDBCMemoryQueryResult(((JDBCStreamQueryResult) each).getResultSet(), databaseType)
                    : each;
            result.add(queryResult);
        }
        return result;
    }
    
    private Enumerable<Object[]> executeByShardingSphereData(final String databaseName, final String schemaName, final ShardingSphereTable table) {
        Optional<ShardingSphereTableData> tableData = Optional.ofNullable(data.getDatabaseData().get(databaseName)).map(optional -> optional.getSchemaData().get(schemaName))
                .map(ShardingSphereSchemaData::getTableData).map(shardingSphereData -> shardingSphereData.get(table.getName()));
        return tableData.map(this::createMemoryEnumerator).orElseGet(this::createEmptyEnumerable);
    }
    
    private Enumerable<Object[]> createMemoryEnumerator(final ShardingSphereTableData tableData) {
        return new AbstractEnumerable<Object[]>() {
            
            @Override
            public Enumerator<Object[]> enumerator() {
                return new MemoryEnumerator<>(tableData.getRows());
            }
        };
    }
    
    private Collection<Statement> getStatements(final Collection<ExecutionGroup<JDBCExecutionUnit>> inputGroups) {
        Collection<Statement> result = new LinkedList<>();
        for (ExecutionGroup<JDBCExecutionUnit> each : inputGroups) {
            for (JDBCExecutionUnit executionUnit : each.getInputs()) {
                result.add(executionUnit.getStorageResource());
            }
        }
        return result;
    }
    
    private SqlString createSQLString(final ShardingSphereTable table, final FilterableScanNodeExecutorContext scanContext, final SqlDialect sqlDialect) {
        return new RelToSqlConverter(sqlDialect).visitRoot(createRelNode(table, scanContext)).asStatement().toSqlString(sqlDialect);
    }
    
    private void setParameters(final Collection<ExecutionGroup<JDBCExecutionUnit>> inputGroups) {
        for (ExecutionGroup<JDBCExecutionUnit> each : inputGroups) {
            for (JDBCExecutionUnit executionUnit : each.getInputs()) {
                if (!(executionUnit.getStorageResource() instanceof PreparedStatement)) {
                    continue;
                }
                setParameters((PreparedStatement) executionUnit.getStorageResource(), executionUnit.getExecutionUnit().getSqlUnit().getParameters());
            }
        }
    }
    
    @SneakyThrows(SQLException.class)
    private void setParameters(final PreparedStatement preparedStatement, final List<Object> params) {
        for (int i = 0; i < params.size(); i++) {
            preparedStatement.setObject(i + 1, params.get(i));
        }
    }
    
    private RelNode createRelNode(final ShardingSphereTable table, final FilterableScanNodeExecutorContext scanContext) {
        String databaseName = executorContext.getDatabaseName();
        String schemaName = executorContext.getSchemaName();
        CalciteConnectionConfig connectionConfig = new CalciteConnectionConfigImpl(optimizerContext.getParserContext(databaseName).getDialectProps());
        ShardingSphereDatabase database = executorContext.getFederationContext().getMetaData().getDatabase(databaseName);
        CalciteCatalogReader catalogReader = SQLFederationPlannerUtil.createCatalogReader(schemaName,
                new FilterableSchema(schemaName, database.getSchema(schemaName), database.getProtocolType(), JAVA_TYPE_FACTORY, null), JAVA_TYPE_FACTORY, connectionConfig);
        RelOptCluster relOptCluster = RelOptCluster.create(SQLFederationPlannerUtil.createVolcanoPlanner(), new RexBuilder(JAVA_TYPE_FACTORY));
        RelBuilder builder = RelFactories.LOGICAL_BUILDER.create(relOptCluster, catalogReader).scan(table.getName()).filter(scanContext.getFilterValues());
        if (null != scanContext.getProjects()) {
            builder.project(createProjections(scanContext.getProjects(), builder, table.getColumnNames()));
        }
        return builder.build();
    }
    
    private Collection<RexNode> createProjections(final int[] projects, final RelBuilder relBuilder, final List<String> columnNames) {
        Collection<RexNode> result = new LinkedList<>();
        for (int each : projects) {
            result.add(relBuilder.field(columnNames.get(each)));
        }
        return result;
    }
    
    private AbstractEnumerable<Object[]> createEnumerable(final MergedResult mergedResult, final QueryResultMetaData metaData, final Collection<Statement> statements) throws SQLException {
        // TODO remove getRows when mergedResult support JDBC first method
        Collection<Object[]> rows = getRows(mergedResult, metaData);
        return new AbstractEnumerable<Object[]>() {
            
            @Override
            public Enumerator<Object[]> enumerator() {
                return new SQLFederationRowEnumerator<>(rows, statements);
            }
        };
    }
    
    private Collection<Object[]> getRows(final MergedResult mergedResult, final QueryResultMetaData metaData) throws SQLException {
        Collection<Object[]> result = new LinkedList<>();
        while (mergedResult.next()) {
            Object[] currentRow = new Object[metaData.getColumnCount()];
            for (int i = 0; i < metaData.getColumnCount(); i++) {
                currentRow[i] = mergedResult.getValue(i + 1, Object.class);
            }
            result.add(currentRow);
        }
        return result;
    }
    
    private QueryContext createQueryContext(final ShardingSphereMetaData metaData, final SqlString sqlString, final DatabaseType databaseType) {
        String sql = sqlString.getSql().replace("\n", " ");
        SQLStatement sqlStatement = new SQLStatementParserEngine(databaseType.getType(),
                optimizerContext.getSqlParserRule().getSqlStatementCache(), optimizerContext.getSqlParserRule().getParseTreeCache(),
                optimizerContext.getSqlParserRule().isSqlCommentParseEnabled()).parse(sql, false);
        List<Object> params = getParameters(sqlString.getDynamicParameters());
        SQLStatementContext<?> sqlStatementContext = SQLStatementContextFactory.newInstance(metaData, params, sqlStatement, executorContext.getDatabaseName());
        return new QueryContext(sqlStatementContext, sql, params);
    }
    
    private List<Object> getParameters(final List<Integer> paramIndexes) {
        if (null == paramIndexes) {
            return Collections.emptyList();
        }
        List<Object> result = new ArrayList<>();
        for (Integer each : paramIndexes) {
            result.add(executorContext.getFederationContext().getQueryContext().getParameters().get(each));
        }
        return result;
    }
    
    private AbstractEnumerable<Object[]> createEmptyEnumerable() {
        return new AbstractEnumerable<Object[]>() {
            
            @Override
            public Enumerator<Object[]> enumerator() {
                return new EmptyRowEnumerator<>();
            }
        };
    }
}
