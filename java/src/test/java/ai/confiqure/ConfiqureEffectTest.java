package ai.confiqure;

import org.junit.jupiter.api.Test;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.net.URI;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** 3.2: @Confiqure.Effect on a tool operation, the closed OperationEffect set, and the forms that do not compile. */
class ConfiqureEffectTest {

    private static final String TOOL = "package demo;\n"
            + "import ai.confiqure.Confiqure;\n"
            + "import static ai.confiqure.Confiqure.OperationEffect.PAGE_OPENING;\n"
            + "@Confiqure.Tool\n"
            + "public class PagesTool {\n"
            + "    public static class Ref { public String sku; }\n"
            + "    @Confiqure.Browser\n"
            + "    @Confiqure.Effect(Confiqure.OperationEffect.PAGE_OPENING)\n"
            + "    public boolean openProductPage(Ref ref) { return true; }\n"
            + "    @Confiqure.Browser\n"
            + "    @Confiqure.Effect(PAGE_OPENING)\n"
            + "    public boolean openOrdersPage(Ref ref) { return true; }\n"
            + "    @Confiqure.Effect(Confiqure.OperationEffect.NONE)\n"
            + "    public boolean refresh(Ref ref) { return true; }\n"
            + "    public boolean plain(Ref ref) { return true; }\n"
            + "}\n";

    @Test
    void theEffectCompilesOnOperationsInEveryValueForm() throws Exception {
        assertEquals(Collections.emptyList(), errors(TOOL));
    }

    @Test
    void theEffectGoesOnMethodsOnlyAndAlwaysNamesAValue() throws Exception {
        String onField = TOOL.replace("    public static class Ref { public String sku; }\n",
                "    public static class Ref { @Confiqure.Effect(Confiqure.OperationEffect.NONE) public String sku; }\n");
        assertEquals(1, errors(onField).size(), errors(onField).toString());
        String noValue = TOOL.replace("@Confiqure.Effect(Confiqure.OperationEffect.NONE)", "@Confiqure.Effect");
        assertEquals(1, errors(noValue).size(), errors(noValue).toString());
        String unknown = TOOL.replace("Confiqure.OperationEffect.NONE", "Confiqure.OperationEffect.NAVIGATION");
        assertEquals(1, errors(unknown).size(), errors(unknown).toString());
    }

    @Test
    void theEffectSetIsClosed_andTheAnnotationIsReadableAtRuntimeOnMethods() {
        assertArrayEquals(new Confiqure.OperationEffect[]{Confiqure.OperationEffect.NONE, Confiqure.OperationEffect.PAGE_OPENING},
                Confiqure.OperationEffect.values());
        assertArrayEquals(new ElementType[]{ElementType.METHOD}, Confiqure.Effect.class.getAnnotation(Target.class).value());
        assertEquals(RetentionPolicy.RUNTIME, Confiqure.Effect.class.getAnnotation(Retention.class).value());
    }

    private static List<String> errors(String code) throws Exception {
        JavaFileObject src = new SimpleJavaFileObject(URI.create("string:///demo/PagesTool.java"), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return code;
            }
        };
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager files = javac.getStandardFileManager(diagnostics, null, null)) {
            List<String> options = Arrays.asList("-classpath", System.getProperty("java.class.path"),
                    "-d", Files.createTempDirectory("confiqure-effect-test").toString(), "-proc:none");
            javac.getTask(null, files, diagnostics, options, null, Collections.singletonList(src)).call();
        }
        return diagnostics.getDiagnostics().stream().filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                .map(d -> d.getMessage(null)).collect(Collectors.toList());
    }
}
