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

package org.apache.shardingsphere.sharding.metadata.reviser;

import lombok.RequiredArgsConstructor;
import org.apache.shardingsphere.infra.metadata.database.schema.decorator.reviser.column.ColumnGeneratedReviser;
import org.apache.shardingsphere.infra.metadata.database.schema.loader.model.ColumnMetaData;
import org.apache.shardingsphere.sharding.rule.ShardingRule;
import org.apache.shardingsphere.sharding.rule.TableRule;

/**
 * Sharding column generated reviser.
 */
@RequiredArgsConstructor
public final class ShardingColumnGeneratedReviser implements ColumnGeneratedReviser<ShardingRule> {
    
    private final TableRule tableRule;
    
    @Override
    public boolean revise(final ColumnMetaData originalMetaData, final ShardingRule rule) {
        return originalMetaData.getName().equalsIgnoreCase(tableRule.getGenerateKeyColumn().orElse(null));
    }
}
