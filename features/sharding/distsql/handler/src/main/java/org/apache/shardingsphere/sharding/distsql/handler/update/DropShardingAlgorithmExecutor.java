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

package org.apache.shardingsphere.sharding.distsql.handler.update;

import lombok.Setter;
import org.apache.shardingsphere.distsql.handler.exception.algorithm.AlgorithmInUsedException;
import org.apache.shardingsphere.distsql.handler.exception.algorithm.MissingRequiredAlgorithmException;
import org.apache.shardingsphere.distsql.handler.required.DistSQLExecutorCurrentRuleRequired;
import org.apache.shardingsphere.distsql.handler.type.rdl.rule.spi.database.DatabaseRuleDropExecutor;
import org.apache.shardingsphere.infra.exception.core.ShardingSpherePreconditions;
import org.apache.shardingsphere.infra.metadata.database.ShardingSphereDatabase;
import org.apache.shardingsphere.sharding.api.config.ShardingRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.strategy.sharding.ShardingStrategyConfiguration;
import org.apache.shardingsphere.sharding.distsql.statement.DropShardingAlgorithmStatement;
import org.apache.shardingsphere.sharding.rule.ShardingRule;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;

/**
 * Drop sharding algorithm executor.
 */
@DistSQLExecutorCurrentRuleRequired("Sharding")
@Setter
public final class DropShardingAlgorithmExecutor implements DatabaseRuleDropExecutor<DropShardingAlgorithmStatement, ShardingRule, ShardingRuleConfiguration> {
    
    private ShardingSphereDatabase database;
    
    private ShardingRule rule;
    
    @Override
    public void checkBeforeUpdate(final DropShardingAlgorithmStatement sqlStatement) {
        if (!sqlStatement.isIfExists()) {
            checkToBeDroppedShardingAlgorithms(sqlStatement);
        }
        checkShardingAlgorithmsInUsed(sqlStatement);
    }
    
    private void checkToBeDroppedShardingAlgorithms(final DropShardingAlgorithmStatement sqlStatement) {
        Collection<String> currentShardingAlgorithms = getCurrentShardingAlgorithms();
        Collection<String> notExistedAlgorithms = sqlStatement.getNames().stream().filter(each -> !currentShardingAlgorithms.contains(each)).collect(Collectors.toList());
        if (!notExistedAlgorithms.isEmpty()) {
            throw new MissingRequiredAlgorithmException(database.getName(), notExistedAlgorithms);
        }
    }
    
    private void checkShardingAlgorithmsInUsed(final DropShardingAlgorithmStatement sqlStatement) {
        Collection<String> allInUsed = getAllOfAlgorithmsInUsed();
        Collection<String> usedAlgorithms = sqlStatement.getNames().stream().filter(allInUsed::contains).collect(Collectors.toList());
        ShardingSpherePreconditions.checkState(usedAlgorithms.isEmpty(), () -> new AlgorithmInUsedException("Sharding", database.getName(), usedAlgorithms));
    }
    
    private Collection<String> getAllOfAlgorithmsInUsed() {
        Collection<String> result = new LinkedHashSet<>();
        rule.getConfiguration().getTables().forEach(each -> {
            if (null != each.getDatabaseShardingStrategy()) {
                result.add(each.getDatabaseShardingStrategy().getShardingAlgorithmName());
            }
            if (null != each.getTableShardingStrategy()) {
                result.add(each.getTableShardingStrategy().getShardingAlgorithmName());
            }
        });
        rule.getConfiguration().getAutoTables().stream().filter(each -> null != each.getShardingStrategy()).forEach(each -> result.add(each.getShardingStrategy().getShardingAlgorithmName()));
        ShardingStrategyConfiguration tableShardingStrategy = rule.getConfiguration().getDefaultTableShardingStrategy();
        if (null != tableShardingStrategy && !tableShardingStrategy.getShardingAlgorithmName().isEmpty()) {
            result.add(tableShardingStrategy.getShardingAlgorithmName());
        }
        ShardingStrategyConfiguration databaseShardingStrategy = rule.getConfiguration().getDefaultDatabaseShardingStrategy();
        if (null != databaseShardingStrategy && !databaseShardingStrategy.getShardingAlgorithmName().isEmpty()) {
            result.add(databaseShardingStrategy.getShardingAlgorithmName());
        }
        return result;
    }
    
    private Collection<String> getCurrentShardingAlgorithms() {
        return rule.getConfiguration().getShardingAlgorithms().keySet();
    }
    
    @Override
    public ShardingRuleConfiguration buildToBeDroppedRuleConfiguration(final DropShardingAlgorithmStatement sqlStatement) {
        ShardingRuleConfiguration result = new ShardingRuleConfiguration();
        for (String each : sqlStatement.getNames()) {
            result.getShardingAlgorithms().put(each, rule.getConfiguration().getShardingAlgorithms().get(each));
        }
        return result;
    }
    
    @Override
    public boolean updateCurrentRuleConfiguration(final DropShardingAlgorithmStatement sqlStatement, final ShardingRuleConfiguration currentRuleConfig) {
        for (String each : sqlStatement.getNames()) {
            dropShardingAlgorithm(each);
        }
        return false;
    }
    
    @Override
    public boolean hasAnyOneToBeDropped(final DropShardingAlgorithmStatement sqlStatement) {
        return null != rule && !getIdenticalData(getCurrentShardingAlgorithms(), sqlStatement.getNames()).isEmpty();
    }
    
    private void dropShardingAlgorithm(final String algorithmName) {
        getCurrentShardingAlgorithms().removeIf(algorithmName::equalsIgnoreCase);
    }
    
    @Override
    public Class<ShardingRule> getRuleClass() {
        return ShardingRule.class;
    }
    
    @Override
    public Class<DropShardingAlgorithmStatement> getType() {
        return DropShardingAlgorithmStatement.class;
    }
}
