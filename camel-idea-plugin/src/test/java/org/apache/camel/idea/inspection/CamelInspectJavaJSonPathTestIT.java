/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.idea.inspection;

import java.io.File;
import java.io.IOException;

import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.InspectionTestCase;
import com.intellij.util.ui.UIUtil;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;

public class CamelInspectJavaJSonPathTestIT extends InspectionTestCase {

    public static final String CAMEL_JSONPATH_MAVEN_ARTIFACT = "org.apache.camel:camel-jsonpath:2.20.2";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        File[] mavenArtifacts = getMavenArtifacts(CAMEL_JSONPATH_MAVEN_ARTIFACT);
        for (File file : mavenArtifacts) {
            final VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
            final LibraryTable projectLibraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(myModule.getProject());
            ApplicationManager.getApplication().runWriteAction(() -> {
                String name = file.getName();
                // special for camel JARs
                if (name.contains("camel-core")) {
                    name = "org.apache.camel:camel-core:2.20.2";
                } else if (name.contains("camel-jsonpath")) {
                    name = "org.apache.camel:camel-jsonpath:2.20.2";
                } else {
                    // need to fix the name
                    if (name.endsWith(".jar")) {
                        name = name.substring(0, name.length() - 4);
                    }
                    int lastDash = name.lastIndexOf('-');
                    name = name.substring(0, lastDash) + ":" + name.substring(lastDash + 1);
                    // add bogus groupid
                    name = "com.foo:" + name;
                }

                Library library = projectLibraryTable.createLibrary("maven: " + name);
                final Library.ModifiableModel libraryModifiableModel = library.getModifiableModel();
                libraryModifiableModel.addRoot(virtualFile, OrderRootType.CLASSES);
                libraryModifiableModel.commit();
                ModuleRootModificationUtil.addDependency(myModule, library);
            });
        }
        UIUtil.dispatchAllInvocationEvents();
    }

    private File[] getMavenArtifacts(String... mavenAritfiact) throws IOException {
        File[] libs = Maven.resolver().loadPomFromFile("pom.xml").resolve(mavenAritfiact).withTransitivity().asFile();
        return libs;
    }

    @Override
    protected String getTestDataPath() {
        return "src/test/resources/";
    }

    public void testJSonPathInspection() {
        // force Camel enabled so the inspection test can run
        CamelInspection inspection = new CamelInspection(true);

        // must be called fooroute as inspectionsimplejava fails for some odd reason
        doTest("testData/barroute/", new LocalInspectionToolWrapper(inspection), "java 1.8");
    }

    private Library addLibraryToModule(VirtualFile camelSpringVirtualFile, LibraryTable projectLibraryTable, String name) {
        return ApplicationManager.getApplication().runWriteAction((Computable<Library>)() -> {
            Library library = projectLibraryTable.createLibrary(name);
            Library.ModifiableModel libraryModifiableModel = library.getModifiableModel();
            libraryModifiableModel.addRoot(camelSpringVirtualFile, OrderRootType.CLASSES);
            libraryModifiableModel.commit();
            ModuleRootModificationUtil.addDependency(myModule, library);
            return library;
        });
    }

}
