/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps.cabal;


import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class CabalBuilder extends ModuleLevelBuilder {


    public CabalBuilder() {
        super(BuilderCategory.TRANSLATOR);
    }


    public ExitCode build(final CompileContext context,
                          final ModuleChunk chunk,
                          final DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder,
                          final OutputConsumer outputConsumer) throws ProjectBuildException {
        try {
            for (JpsModule module : chunk.getModules()) {
                File cabalFile = getCabalFile(module);
                if (cabalFile == null) {
                    //context.processMessage(new CompilerMessage("cabal", BuildMessage.Kind.ERROR,
                    //        "Can't find cabal file in " + getContentRootPath(module)));
                    continue;
                }
                CabalJspInterface cabal = new CabalJspInterface(cabalFile);

                if (runConfigure(context, module, cabal)) return ExitCode.ABORT;
                if (runBuild(context, module, cabal)) return ExitCode.ABORT;
            }
            return ExitCode.OK;
        } catch (InterruptedException e) {
            e.printStackTrace();
            context.processMessage(new CompilerMessage("cabal", BuildMessage.Kind.ERROR, e.getMessage()));
        } catch (IOException e) {
            e.printStackTrace();
            context.processMessage(new CompilerMessage("cabal", BuildMessage.Kind.ERROR, e.getMessage()));
        }
        return ExitCode.ABORT;
    }

    private boolean runBuild(CompileContext context, JpsModule module, CabalJspInterface cabal) throws IOException, InterruptedException {
        context.processMessage(new ProgressMessage("cabal build"));
        context.processMessage(new CompilerMessage("ghc", BuildMessage.Kind.INFO, "Start build"));
        Process buildProcess = cabal.build();
        processOut(context, buildProcess, module);

        if (buildProcess.waitFor() != 0) {
            context.processMessage(new CompilerMessage("cabal", BuildMessage.Kind.ERROR, "build errors."));
            return true;
        }
        return false;
    }

    private boolean runConfigure(CompileContext context, JpsModule module, CabalJspInterface cabal) throws IOException, InterruptedException {
        context.processMessage(new CompilerMessage("cabal", BuildMessage.Kind.INFO, "Start configure"));

        Process configureProcess = cabal.configure();

        processOut(context, configureProcess, module);

        if (configureProcess.waitFor() != 0) {
            context.processMessage(new CompilerMessage(
                    "cabal",
                    BuildMessage.Kind.ERROR,
                    "configure failed."));
            return true;
        }
        return false;
    }

    private void processOut(CompileContext context, Process process, JpsModule module) throws IOException {
        Iterator<String> processOut = collectOutput(process);

        while (processOut.hasNext()) {
            String line = processOut.next();
            String warningPrefix = "Warning: ";
            if (line.startsWith(warningPrefix)) {
                String text = line.substring(warningPrefix.length()) + "\n" + processOut.next();
                context.processMessage(new CompilerMessage("cabal", BuildMessage.Kind.WARNING, text));
            } else if (isError(line)) {
                Matcher matcher = Pattern.compile("(.*):(\\d+):(\\d+):").matcher(line);
                if (!matcher.find()) {
                    throw new RuntimeException("Pattern not matched");
                }
                String file = matcher.group(1);
                long lineNum = Long.parseLong(matcher.group(2));
                long colNum = Long.parseLong(matcher.group(3));
                String msg = "";
                while (processOut.hasNext()) {
                    String msgLine = processOut.next();
                    msg += msgLine + "\n";
                    if (msgLine.trim().length() == 0) {
                        break;
                    }
                }

                String sourcePath = getContentRootPath(module) + "/" + file.replace('\\', '/');
                context.processMessage(new CompilerMessage(
                        "ghc",
                        BuildMessage.Kind.ERROR,
                        msg,
                        sourcePath,
                        -1L, -1L, -1L,
                        lineNum, colNum));
            } else {
                context.processMessage(new CompilerMessage("cabal", BuildMessage.Kind.INFO, line));
            }
        }
    }

    private Iterator<String> collectOutput(Process process) throws IOException {
        final BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        return new Iterator<String>() {

            String line = null;

            @Override
            public boolean hasNext() {
                return fetch() != null;
            }

            private String fetch() {
                if (line == null) {
                    try {
                        line = reader.readLine();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                return line;
            }

            @Override
            public String next() {
                String result = fetch();
                line = null;
                return result;
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    private File getCabalFile(JpsModule module) {
        String pathname = getContentRootPath(module);
        for (File file : new File(pathname).listFiles()) {
            if (file.getName().endsWith(".cabal")) {
                return file;
            }
        }

        return null;
    }

    private String getContentRootPath(JpsModule module) {
        String url = module.getContentRootsList().getUrls().get(0);
        return url.substring("file://".length());
    }


    @Override
    public List<String> getCompilableFileExtensions() {
        return Arrays.asList("hs");
    }

    private boolean isError(String line) {
        return line.matches(".*:.*:.*:");
    }


    @Override
    public String toString() {
        return getPresentableName();
    }

    @NotNull
    public String getPresentableName() {
        return "Cabal builder";
    }

}