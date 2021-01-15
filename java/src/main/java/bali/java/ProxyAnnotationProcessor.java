/*
 * Copyright © 2021 Schlichtherle IT Services
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package bali.java;

import lombok.val;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

@SupportedAnnotationTypes("bali.*")
@SupportedOptions({"bali.ea", "bali.enableassertions"})
public final class ProxyAnnotationProcessor extends AbstractProcessor {

    private AnnotationProcessor proc;

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @Override
    public synchronized void init(final ProcessingEnvironment processingEnv) {
        val options = processingEnv.getOptions();
        Optional.ofNullable(getClass().getClassLoader())
                .orElseGet(ClassLoader::getSystemClassLoader)
                .setClassAssertionStatus(AnnotationProcessor.class.getName(),
                        Stream.of("ea", "enableassertions").anyMatch(name ->
                                Boolean.parseBoolean(options.getOrDefault("bali." + name, "false"))));
        proc = new AnnotationProcessor();
        proc.init(processingEnv);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        return proc.process(annotations, roundEnv);
    }
}
