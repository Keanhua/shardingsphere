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
import org.apache.shardingsphere.sharding.api.config.strategy.audit.ShardingAuditStrategyConfiguration;
import org.apache.shardingsphere.sharding.distsql.statement.DropShardingAuditorStatement;
import org.apache.shardingsphere.sharding.rule.ShardingRule;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;

/**
 * Drop sharding auditor statement executor.
 */
@DistSQLExecutorCurrentRuleRequired("Sharding auditor")
@Setter
public final class DropShardingAuditorExecutor implements DatabaseRuleDropExecutor<DropShardingAuditorStatement, ShardingRule, ShardingRuleConfiguration> {
    
    private ShardingSphereDatabase database;
    
    private ShardingRule rule;
    
    @Override
    public void checkBeforeUpdate(final DropShardingAuditorStatement sqlStatement) {
        if (!sqlStatement.isIfExists()) {
            checkExist(sqlStatement);
        }
        checkInUsed(sqlStatement);
    }
    
    private void checkExist(final DropShardingAuditorStatement sqlStatement) {
        Collection<String> notExistAuditors = sqlStatement.getNames().stream().filter(each -> !rule.getConfiguration().getAuditors().containsKey(each)).collect(Collectors.toList());
        ShardingSpherePreconditions.checkState(notExistAuditors.isEmpty(), () -> new MissingRequiredAlgorithmException("Sharding auditor", database.getName(), notExistAuditors));
    }
    
    private void checkInUsed(final DropShardingAuditorStatement sqlStatement) {
        Collection<String> usedAuditors = getUsedAuditors();
        Collection<String> inUsedNames = sqlStatement.getNames().stream().filter(usedAuditors::contains).collect(Collectors.toList());
        ShardingSpherePreconditions.checkState(inUsedNames.isEmpty(), () -> new AlgorithmInUsedException("Sharding auditor", database.getName(), inUsedNames));
    }
    
    private Collection<String> getUsedAuditors() {
        Collection<String> result = new LinkedHashSet<>();
        rule.getConfiguration().getTables().stream().filter(each -> null != each.getAuditStrategy()).forEach(each -> result.addAll(each.getAuditStrategy().getAuditorNames()));
        rule.getConfiguration().getAutoTables().stream().filter(each -> null != each.getAuditStrategy()).forEach(each -> result.addAll(each.getAuditStrategy().getAuditorNames()));
        ShardingAuditStrategyConfiguration auditStrategy = rule.getConfiguration().getDefaultAuditStrategy();
        if (null != auditStrategy && !auditStrategy.getAuditorNames().isEmpty()) {
            result.addAll(auditStrategy.getAuditorNames());
        }
        return result;
    }
    
    @Override
    public ShardingRuleConfiguration buildToBeDroppedRuleConfiguration(final DropShardingAuditorStatement sqlStatement) {
        ShardingRuleConfiguration result = new ShardingRuleConfiguration();
        for (String each : sqlStatement.getNames()) {
            result.getAuditors().put(each, rule.getConfiguration().getAuditors().get(each));
        }
        return result;
    }
    
    @Override
    public boolean updateCurrentRuleConfiguration(final DropShardingAuditorStatement sqlStatement, final ShardingRuleConfiguration currentRuleConfig) {
        currentRuleConfig.getAuditors().keySet().removeIf(sqlStatement.getNames()::contains);
        return false;
    }
    
    @Override
    public boolean hasAnyOneToBeDropped(final DropShardingAuditorStatement sqlStatement) {
        return null != rule && !getIdenticalData(rule.getConfiguration().getAuditors().keySet(), sqlStatement.getNames()).isEmpty();
    }
    
    @Override
    public Class<ShardingRule> getRuleClass() {
        return ShardingRule.class;
    }
    
    @Override
    public Class<DropShardingAuditorStatement> getType() {
        return DropShardingAuditorStatement.class;
    }
}
