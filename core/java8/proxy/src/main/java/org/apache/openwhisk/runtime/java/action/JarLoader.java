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

package org.apache.openwhisk.runtime.java.action;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class JarLoader extends URLClassLoader {
    public final Class<?> mainClass;
    public final String entrypointMethodName;

    public static Path saveBase64EncodedFile(InputStream encoded) throws Exception {
        Base64.Decoder decoder = Base64.getDecoder();

        InputStream decoded = decoder.wrap(encoded);

        File destinationFile = File.createTempFile("useraction", ".jar");
        destinationFile.deleteOnExit();
        Path destinationPath = destinationFile.toPath();

        Files.copy(decoded, destinationPath, StandardCopyOption.REPLACE_EXISTING);

        return destinationPath;
    }

    public JarLoader(Path jarPath, String entrypoint)
            throws MalformedURLException, ClassNotFoundException, NoSuchMethodException, SecurityException {
        super(new URL[] { jarPath.toUri().toURL() });

        final String[] splittedEntrypoint = entrypoint.split("#");
        final String entrypointClassName = splittedEntrypoint[0];

        this.mainClass = loadClass(entrypointClassName);
        this.entrypointMethodName = splittedEntrypoint.length > 1 ? splittedEntrypoint[1] : "main";
        Method[] methods = mainClass.getDeclaredMethods();
        Boolean existMain = false;
        for(Method method: methods) {
            if (method.getName().equals(this.entrypointMethodName)) {
                existMain = true;
                break;
            }
        }
        if (!existMain) {
            throw new NoSuchMethodException(this.entrypointMethodName);
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static void augmentEnv(Map<String, String> newEnv) {
        try {
            for (Class cl : Collections.class.getDeclaredClasses()) {
                if ("java.util.Collections$UnmodifiableMap".equals(cl.getName())) {
                    Field field = cl.getDeclaredField("m");
                    field.setAccessible(true);
                    Object obj = field.get(System.getenv());
                    Map<String, String> map = (Map<String, String>) obj;
                    map.putAll(newEnv);
                }
            }
        } catch (Exception e) {}
    }
}
