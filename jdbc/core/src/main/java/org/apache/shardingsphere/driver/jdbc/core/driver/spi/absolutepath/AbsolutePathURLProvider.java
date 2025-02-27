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

package org.apache.shardingsphere.driver.jdbc.core.driver.spi.absolutepath;

import com.google.common.base.Strings;
import lombok.SneakyThrows;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Absolute path URL provider.
 */
public final class AbsolutePathURLProvider extends AbstractAbsolutePathURLProvider {
    
    private static final String PATH_TYPE = "absolutepath:";
    
    @Override
    public boolean accept(final String url) {
        return !Strings.isNullOrEmpty(url) && url.contains(PATH_TYPE);
    }
    
    @Override
    @SneakyThrows(IOException.class)
    public byte[] getContent(final String url, final String urlPrefix) {
        String file = getConfigurationFile(url, urlPrefix, PATH_TYPE);
        try (
                InputStream stream = Files.newInputStream(new File(file).toPath());
                BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while (null != (line = reader.readLine())) {
                if (!line.startsWith("#")) {
                    builder.append(line).append('\n');
                }
            }
            return builder.toString().getBytes(StandardCharsets.UTF_8);
        }
    }
}
