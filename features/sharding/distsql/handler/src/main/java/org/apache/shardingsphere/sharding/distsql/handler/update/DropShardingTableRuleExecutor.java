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

import com.google.common.base.Splitter;
import lombok.Setter;
import org.apache.shardingsphere.distsql.handler.exception.rule.MissingRequiredRuleException;
import org.apache.shardingsphere.distsql.handler.exception.rule.RuleInUsedException;
import org.apache.shardingsphere.distsql.handler.required.DistSQLExecutorCurrentRuleRequired;
import org.apache.shardingsphere.distsql.handler.type.rdl.rule.spi.database.DatabaseRuleDropExecutor;
import org.apache.shardingsphere.infra.exception.core.ShardingSpherePreconditions;
import org.apache.shardingsphere.infra.metadata.database.ShardingSphereDatabase;
import org.apache.shardingsphere.sharding.api.config.ShardingRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.rule.ShardingAutoTableRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.rule.ShardingTableRuleConfiguration;
import org.apache.shardingsphere.sharding.distsql.statement.DropShardingTableRuleStatement;
import org.apache.shardingsphere.sharding.rule.ShardingRule;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.stream.Collectors;

/**
 * Drop sharding table rule executor.
 */
@DistSQLExecutorCurrentRuleRequired("Sharding")
@Setter
public final class DropShardingTableRuleExecutor implements DatabaseRuleDropExecutor<DropShardingTableRuleStatement, ShardingRule, ShardingRuleConfiguration> {
    
    private ShardingSphereDatabase database;
    
    private ShardingRule rule;
    
    @Override
    public void checkBeforeUpdate(final DropShardingTableRuleStatement sqlStatement) {
        if (!sqlStatement.isIfExists()) {
            checkToBeDroppedShardingTableNames(sqlStatement);
        }
        checkBindingTables(sqlStatement);
    }
    
    private void checkToBeDroppedShardingTableNames(final DropShardingTableRuleStatement sqlStatement) {
        Collection<String> currentShardingTableNames = getCurrentShardingTableNames();
        Collection<String> notExistedTableNames =
                getToBeDroppedShardingTableNames(sqlStatement).stream().filter(each -> !containsIgnoreCase(currentShardingTableNames, each)).collect(Collectors.toList());
        ShardingSpherePreconditions.checkState(notExistedTableNames.isEmpty(), () -> new MissingRequiredRuleException("sharding", database.getName(), notExistedTableNames));
    }
    
    private Collection<String> getToBeDroppedShardingTableNames(final DropShardingTableRuleStatement sqlStatement) {
        return sqlStatement.getTableNames().stream().map(each -> each.getIdentifier().getValue()).collect(Collectors.toList());
    }
    
    private boolean containsIgnoreCase(final Collection<String> collection, final String str) {
        return collection.stream().anyMatch(each -> each.equalsIgnoreCase(str));
    }
    
    private Collection<String> getCurrentShardingTableNames() {
        Collection<String> result = new LinkedList<>();
        result.addAll(rule.getConfiguration().getTables().stream().map(ShardingTableRuleConfiguration::getLogicTable).collect(Collectors.toList()));
        result.addAll(rule.getConfiguration().getAutoTables().stream().map(ShardingAutoTableRuleConfiguration::getLogicTable).collect(Collectors.toList()));
        return result;
    }
    
    private void checkBindingTables(final DropShardingTableRuleStatement sqlStatement) {
        Collection<String> bindingTables = getBindingTables();
        Collection<String> usedTableNames = getToBeDroppedShardingTableNames(sqlStatement).stream().filter(each -> containsIgnoreCase(bindingTables, each)).collect(Collectors.toList());
        if (!usedTableNames.isEmpty()) {
            throw new RuleInUsedException("Sharding", database.getName(), usedTableNames, "sharding table reference");
        }
    }
    
    private Collection<String> getBindingTables() {
        Collection<String> result = new LinkedHashSet<>();
        rule.getConfiguration().getBindingTableGroups().forEach(each -> result.addAll(Splitter.on(",").splitToList(each.getReference())));
        return result;
    }
    
    @Override
    public boolean hasAnyOneToBeDropped(final DropShardingTableRuleStatement sqlStatement) {
        if (null == rule) {
            return false;
        }
        Collection<String> currentTableNames = new LinkedList<>();
        currentTableNames.addAll(rule.getConfiguration().getTables().stream().map(ShardingTableRuleConfiguration::getLogicTable).collect(Collectors.toSet()));
        currentTableNames.addAll(rule.getConfiguration().getAutoTables().stream().map(ShardingAutoTableRuleConfiguration::getLogicTable).collect(Collectors.toSet()));
        return !getIdenticalData(currentTableNames, sqlStatement.getTableNames().stream().map(each -> each.getIdentifier().getValue()).collect(Collectors.toSet())).isEmpty();
    }
    
    @Override
    public ShardingRuleConfiguration buildToBeDroppedRuleConfiguration(final DropShardingTableRuleStatement sqlStatement) {
        ShardingRuleConfiguration result = new ShardingRuleConfiguration();
        Collection<String> toBeDroppedShardingTableNames = getToBeDroppedShardingTableNames(sqlStatement);
        for (String each : toBeDroppedShardingTableNames) {
            result.getTables().addAll(rule.getConfiguration().getTables().stream().filter(table -> each.equalsIgnoreCase(table.getLogicTable())).collect(Collectors.toList()));
            result.getAutoTables().addAll(rule.getConfiguration().getAutoTables().stream().filter(table -> each.equalsIgnoreCase(table.getLogicTable())).collect(Collectors.toList()));
            dropShardingTable(rule.getConfiguration(), each);
        }
        UnusedAlgorithmFinder.findUnusedShardingAlgorithm(
                rule.getConfiguration()).forEach(each -> result.getShardingAlgorithms().put(each, rule.getConfiguration().getShardingAlgorithms().get(each)));
        UnusedAlgorithmFinder.findUnusedKeyGenerator(rule.getConfiguration()).forEach(each -> result.getKeyGenerators().put(each, rule.getConfiguration().getKeyGenerators().get(each)));
        UnusedAlgorithmFinder.findUnusedAuditor(rule.getConfiguration()).forEach(each -> result.getAuditors().put(each, rule.getConfiguration().getAuditors().get(each)));
        return result;
    }
    
    @Override
    public boolean updateCurrentRuleConfiguration(final DropShardingTableRuleStatement sqlStatement, final ShardingRuleConfiguration currentRuleConfig) {
        Collection<String> toBeDroppedShardingTableNames = getToBeDroppedShardingTableNames(sqlStatement);
        toBeDroppedShardingTableNames.forEach(each -> dropShardingTable(currentRuleConfig, each));
        UnusedAlgorithmFinder.findUnusedShardingAlgorithm(currentRuleConfig).forEach(each -> currentRuleConfig.getShardingAlgorithms().remove(each));
        dropUnusedKeyGenerator(currentRuleConfig);
        dropUnusedAuditor(currentRuleConfig);
        return currentRuleConfig.isEmpty();
    }
    
    private void dropShardingTable(final ShardingRuleConfiguration currentRuleConfig, final String tableName) {
        currentRuleConfig.getTables().removeAll(currentRuleConfig.getTables().stream().filter(each -> tableName.equalsIgnoreCase(each.getLogicTable())).collect(Collectors.toList()));
        currentRuleConfig.getAutoTables().removeAll(currentRuleConfig.getAutoTables().stream().filter(each -> tableName.equalsIgnoreCase(each.getLogicTable())).collect(Collectors.toList()));
    }
    
    private void dropUnusedKeyGenerator(final ShardingRuleConfiguration currentRuleConfig) {
        UnusedAlgorithmFinder.findUnusedKeyGenerator(currentRuleConfig).forEach(each -> currentRuleConfig.getKeyGenerators().remove(each));
    }
    
    private void dropUnusedAuditor(final ShardingRuleConfiguration currentRuleConfig) {
        UnusedAlgorithmFinder.findUnusedAuditor(currentRuleConfig).forEach(each -> currentRuleConfig.getAuditors().remove(each));
    }
    
    @Override
    public Class<ShardingRule> getRuleClass() {
        return ShardingRule.class;
    }
    
    @Override
    public Class<DropShardingTableRuleStatement> getType() {
        return DropShardingTableRuleStatement.class;
    }
}
