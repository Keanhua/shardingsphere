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
import org.apache.shardingsphere.distsql.handler.exception.rule.DuplicateRuleException;
import org.apache.shardingsphere.distsql.handler.exception.rule.InvalidRuleConfigurationException;
import org.apache.shardingsphere.distsql.handler.exception.rule.MissingRequiredRuleException;
import org.apache.shardingsphere.distsql.handler.required.DistSQLExecutorCurrentRuleRequired;
import org.apache.shardingsphere.distsql.handler.type.rdl.rule.spi.database.DatabaseRuleCreateExecutor;
import org.apache.shardingsphere.infra.exception.core.ShardingSpherePreconditions;
import org.apache.shardingsphere.infra.metadata.database.ShardingSphereDatabase;
import org.apache.shardingsphere.sharding.api.config.ShardingRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.rule.ShardingAutoTableRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.rule.ShardingTableReferenceRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.rule.ShardingTableRuleConfiguration;
import org.apache.shardingsphere.sharding.distsql.handler.checker.ShardingTableRuleStatementChecker;
import org.apache.shardingsphere.sharding.distsql.segment.table.TableReferenceRuleSegment;
import org.apache.shardingsphere.sharding.distsql.statement.CreateShardingTableReferenceRuleStatement;
import org.apache.shardingsphere.sharding.rule.ShardingRule;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.stream.Collectors;

/**
 * Create sharding table reference rule executor.
 */
@DistSQLExecutorCurrentRuleRequired("Sharding")
@Setter
public final class CreateShardingTableReferenceRuleExecutor implements DatabaseRuleCreateExecutor<CreateShardingTableReferenceRuleStatement, ShardingRule, ShardingRuleConfiguration> {
    
    private ShardingSphereDatabase database;
    
    private ShardingRule rule;
    
    @Override
    public void checkBeforeUpdate(final CreateShardingTableReferenceRuleStatement sqlStatement) {
        if (!sqlStatement.isIfNotExists()) {
            checkDuplicatedRuleNames(sqlStatement);
        }
        checkDuplicatedTablesInShardingTableReferenceRules(sqlStatement);
        checkToBeReferencedShardingTablesExisted(sqlStatement);
        checkShardingTableReferenceRulesValid(sqlStatement);
    }
    
    private void checkDuplicatedRuleNames(final CreateShardingTableReferenceRuleStatement sqlStatement) {
        Collection<String> duplicatedRuleNames = getDuplicatedRuleNames(sqlStatement);
        ShardingSpherePreconditions.checkState(duplicatedRuleNames.isEmpty(), () -> new DuplicateRuleException("sharding table reference", database.getName(), duplicatedRuleNames));
    }
    
    private void checkDuplicatedTablesInShardingTableReferenceRules(final CreateShardingTableReferenceRuleStatement sqlStatement) {
        Collection<String> currentReferencedTableNames = getReferencedTableNames();
        Collection<String> duplicatedTableNames = sqlStatement.getTableNames().stream().filter(currentReferencedTableNames::contains).collect(Collectors.toSet());
        ShardingSpherePreconditions.checkState(duplicatedTableNames.isEmpty(), () -> new DuplicateRuleException("sharding table reference", database.getName(), duplicatedTableNames));
    }
    
    private void checkToBeReferencedShardingTablesExisted(final CreateShardingTableReferenceRuleStatement sqlStatement) {
        Collection<String> existedShardingTables = getCurrentLogicTables();
        Collection<String> notExistedShardingTables = sqlStatement.getTableNames().stream().filter(each -> !containsIgnoreCase(existedShardingTables, each)).collect(Collectors.toSet());
        ShardingSpherePreconditions.checkState(notExistedShardingTables.isEmpty(), () -> new MissingRequiredRuleException("Sharding", database.getName(), notExistedShardingTables));
    }
    
    private Collection<String> getCurrentLogicTables() {
        Collection<String> result = new HashSet<>();
        result.addAll(rule.getConfiguration().getTables().stream().map(ShardingTableRuleConfiguration::getLogicTable).collect(Collectors.toSet()));
        result.addAll(rule.getConfiguration().getAutoTables().stream().map(ShardingAutoTableRuleConfiguration::getLogicTable).collect(Collectors.toSet()));
        return result;
    }
    
    private void checkShardingTableReferenceRulesValid(final CreateShardingTableReferenceRuleStatement sqlStatement) {
        Collection<ShardingTableReferenceRuleConfiguration> bindingTableGroups = buildToBeCreatedRuleConfiguration(sqlStatement).getBindingTableGroups();
        Collection<String> names = bindingTableGroups.stream().map(ShardingTableReferenceRuleConfiguration::getName).collect(Collectors.toList());
        ShardingSpherePreconditions.checkState(ShardingTableRuleStatementChecker.isValidBindingTableGroups(bindingTableGroups, rule.getConfiguration()),
                () -> new InvalidRuleConfigurationException("sharding table", names, Collections.singleton("invalid sharding table reference.")));
    }
    
    private Collection<String> getReferencedTableNames() {
        return rule.getConfiguration().getBindingTableGroups().stream().flatMap(each -> Arrays.stream(each.getReference().split(","))).map(String::trim).collect(Collectors.toList());
    }
    
    private boolean containsIgnoreCase(final Collection<String> currentRules, final String ruleName) {
        return currentRules.stream().anyMatch(each -> each.equalsIgnoreCase(ruleName));
    }
    
    @Override
    public ShardingRuleConfiguration buildToBeCreatedRuleConfiguration(final CreateShardingTableReferenceRuleStatement sqlStatement) {
        Collection<TableReferenceRuleSegment> segments = sqlStatement.getRules();
        if (sqlStatement.isIfNotExists()) {
            Collection<String> duplicatedRuleNames = getDuplicatedRuleNames(sqlStatement);
            segments.removeIf(each -> duplicatedRuleNames.contains(each.getName()));
        }
        ShardingRuleConfiguration result = new ShardingRuleConfiguration();
        segments.forEach(each -> result.getBindingTableGroups().add(new ShardingTableReferenceRuleConfiguration(each.getName(), each.getReference())));
        return result;
    }
    
    private Collection<String> getDuplicatedRuleNames(final CreateShardingTableReferenceRuleStatement sqlStatement) {
        Collection<String> currentRuleNames = rule.getConfiguration().getBindingTableGroups().stream().map(ShardingTableReferenceRuleConfiguration::getName).collect(Collectors.toSet());
        return sqlStatement.getRules().stream().map(TableReferenceRuleSegment::getName).filter(currentRuleNames::contains).collect(Collectors.toSet());
    }
    
    @Override
    public void updateCurrentRuleConfiguration(final ShardingRuleConfiguration currentRuleConfig, final ShardingRuleConfiguration toBeCreatedRuleConfig) {
        currentRuleConfig.getBindingTableGroups().addAll(toBeCreatedRuleConfig.getBindingTableGroups());
    }
    
    @Override
    public Class<ShardingRule> getRuleClass() {
        return ShardingRule.class;
    }
    
    @Override
    public Class<CreateShardingTableReferenceRuleStatement> getType() {
        return CreateShardingTableReferenceRuleStatement.class;
    }
}
