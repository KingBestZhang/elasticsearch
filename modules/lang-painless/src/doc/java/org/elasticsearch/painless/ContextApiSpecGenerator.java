/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.painless;

import org.elasticsearch.common.SuppressForbidden;
import org.elasticsearch.common.io.PathUtils;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.core.internal.io.IOUtils;
import org.elasticsearch.painless.action.PainlessContextInfo;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

public class ContextApiSpecGenerator {
    public static void main(String[] args) throws IOException {
        List<PainlessContextInfo> contexts = ContextGeneratorCommon.getContextInfos();
        Path rootDir = resetRootDir();
        ContextGeneratorCommon.PainlessInfos infos;
        JavaClassFilesystemResolver jdksrc = getJdkSrc();
        if (jdksrc != null) {
            infos = new ContextGeneratorCommon.PainlessInfos(contexts, new JavadocExtractor(jdksrc));
        } else {
            infos = new ContextGeneratorCommon.PainlessInfos(contexts);
        }

        Path json = rootDir.resolve("painless-common.json");
        try (PrintStream jsonStream = new PrintStream(
             Files.newOutputStream(json, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
             false, StandardCharsets.UTF_8.name())) {

            XContentBuilder builder = XContentFactory.jsonBuilder(jsonStream);
            builder.startObject();
            builder.field(PainlessContextInfo.CLASSES.getPreferredName(), infos.common);
            builder.endObject();
            builder.flush();
        }

        for (PainlessInfoJson.Context context : infos.contexts) {
            json = rootDir.resolve("painless-" + context.getName() + ".json");
            try (PrintStream jsonStream = new PrintStream(
                Files.newOutputStream(json, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
                false, StandardCharsets.UTF_8.name())) {

                XContentBuilder builder = XContentFactory.jsonBuilder(jsonStream);
                context.toXContent(builder, null);
                builder.flush();
            }
        }
    }

    @SuppressForbidden(reason = "resolve context api directory with environment")
    private static Path resetRootDir() throws IOException {
        Path rootDir = PathUtils.get("./src/main/generated/whitelist-json");
        IOUtils.rm(rootDir);
        Files.createDirectories(rootDir);

        return rootDir;
    }

    @SuppressForbidden(reason = "resolve jdk src directory with environment")
    private static JavaClassFilesystemResolver getJdkSrc() {
        String jdksrc = System.getProperty("jdksrc");
        if (jdksrc == null || "".equals(jdksrc)) {
            return null;
        }
        return new JavaClassFilesystemResolver(PathUtils.get(jdksrc));
    }

    public static class JavaClassFilesystemResolver implements JavaClassResolver {
        private final Path root;

        public JavaClassFilesystemResolver(Path root) {
            this.root = root;
        }

        @SuppressForbidden(reason = "resolve class file from java src directory with environment")
        public InputStream openClassFile(String className) throws IOException {
            // TODO(stu): handle primitives & not stdlib
            if (className.contains(".") && className.startsWith("java")) {
                int dollarPosition = className.indexOf("$");
                if (dollarPosition >= 0) {
                    className = className.substring(0, dollarPosition);
                }
                String[] packages = className.split("\\.");
                String path = String.join("/", packages);
                Path classPath = root.resolve(path + ".java");
                return new FileInputStream(classPath.toFile());
            }
            return new InputStream() {
                @Override
                public int read() throws IOException {
                    return -1;
                }
            };
        }
    }
}
