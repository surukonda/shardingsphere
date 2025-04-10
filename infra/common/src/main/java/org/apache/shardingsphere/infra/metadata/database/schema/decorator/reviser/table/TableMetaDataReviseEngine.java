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

package org.apache.shardingsphere.infra.metadata.database.schema.decorator.reviser.table;

import lombok.RequiredArgsConstructor;
import org.apache.shardingsphere.infra.database.type.DatabaseType;
import org.apache.shardingsphere.infra.metadata.database.schema.decorator.reviser.column.ColumnReviseEngine;
import org.apache.shardingsphere.infra.metadata.database.schema.decorator.reviser.constraint.ConstraintReviseEngine;
import org.apache.shardingsphere.infra.metadata.database.schema.decorator.reviser.index.IndexReviseEngine;
import org.apache.shardingsphere.infra.metadata.database.schema.decorator.spi.TableMetaDataReviseEntry;
import org.apache.shardingsphere.infra.metadata.database.schema.loader.model.TableMetaData;
import org.apache.shardingsphere.infra.rule.ShardingSphereRule;
import org.apache.shardingsphere.infra.util.spi.type.typed.TypedSPILoader;

import javax.sql.DataSource;
import java.util.Optional;

/**
 * Table meta data revise engine.
 *
 * @param <T> type of rule
 */
@RequiredArgsConstructor
public final class TableMetaDataReviseEngine<T extends ShardingSphereRule> {
    
    private final T rule;
    
    private final DatabaseType databaseType;
    
    private final DataSource dataSource;
    
    /**
     * Revise table meta data.
     *
     * @param originalMetaData original table meta data
     * @return revised table meta data
     */
    @SuppressWarnings("unchecked")
    public TableMetaData revise(final TableMetaData originalMetaData) {
        @SuppressWarnings("rawtypes")
        Optional<TableMetaDataReviseEntry> reviseEntry = TypedSPILoader.findService(TableMetaDataReviseEntry.class, rule.getClass().getSimpleName());
        return reviseEntry.map(optional -> revise(originalMetaData, optional)).orElse(originalMetaData);
    }
    
    private TableMetaData revise(final TableMetaData originalMetaData, final TableMetaDataReviseEntry<T> reviseEntry) {
        Optional<? extends TableNameReviser<T>> tableNameReviser = reviseEntry.getTableNameReviser();
        String revisedTableName = tableNameReviser.map(optional -> optional.revise(originalMetaData.getName(), rule)).orElse(originalMetaData.getName());
        return new TableMetaData(revisedTableName, new ColumnReviseEngine<>(rule, databaseType, dataSource, reviseEntry).revise(originalMetaData.getName(), originalMetaData.getColumns()),
                new IndexReviseEngine<>(rule, reviseEntry).revise(revisedTableName, originalMetaData.getIndexes()),
                new ConstraintReviseEngine<>(rule, reviseEntry).revise(revisedTableName, originalMetaData.getConstrains()));
    }
}
