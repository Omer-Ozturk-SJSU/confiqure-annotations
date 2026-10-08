package ai.confiqure;

import org.junit.jupiter.api.Test;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 3.1: the named validators, the verifiers and Confirm compile where they belong; a bad VerifiedBy name fails the compile. */
class ConfiqureValidatorsTest {

    private static final String SETTING = "package demo;\n"
            + "import ai.confiqure.Confiqure;\n"
            + "@Confiqure.Setting(end = \"/demo-settings\")\n"
            + "public class DemoSettings {\n"
            + "    @Confiqure.Email private String contactEmail;\n"
            + "    @Confiqure.NAPhoneNumber(message = \"Give a 10-digit number.\") private String supportPhone;\n"
            + "    @Confiqure.UKPhoneNumber private String officePhone;\n"
            + "    @Confiqure.WorldPhoneNumber private String whatsappNumber;\n"
            + "    @Confiqure.USZipCode(message = \"Give a 5-digit ZIP code.\") private String warehouseZip;\n"
            + "    @Confiqure.VerifiedBy(\"DemoTool.verifyCode\") private String code;\n"
            + "    @Confiqure.Verify(Confiqure.ValidatorKind.ADDRESS) private String shipFrom;\n"
            + "}\n";

    private static final String TOOL = "package demo;\n"
            + "import ai.confiqure.Confiqure;\n"
            + "@Confiqure.Tool(name = \"DemoTool\")\n"
            + "public class DemoTool {\n"
            + "    public static class CodeRequest { @Confiqure.VerifiedBy(\"DemoTool.verifyCode\") public String code; }\n"
            + "    public boolean verifyCode(CodeRequest in) { return true; }\n"
            + "    @Confiqure.Confirm(text = \"Stop the demo schedule?\")\n"
            + "    public boolean stopSchedule(CodeRequest in) { return true; }\n"
            + "}\n";

    @Test
    void everyNewAnnotationCompilesOnASettingFieldAToolRequestFieldAndAToolOperation() throws Exception {
        List<Diagnostic<? extends JavaFileObject>> errors = compile(source("demo.DemoSettings", SETTING), source("demo.DemoTool", TOOL));
        assertEquals(Collections.emptyList(), messages(errors));
    }

    @Test
    void aVerifiedByThatIsNotToolClassDotMethodFailsTheCompileWithAClearMessage() throws Exception {
        String bad = SETTING.replace("\"DemoTool.verifyCode\"", "\"verifyCode\"");
        List<String> errors = messages(compile(source("demo.DemoSettings", bad)));
        assertEquals(1, errors.size(), errors.toString());
        assertTrue(errors.get(0).contains("@Confiqure.VerifiedBy(\"verifyCode\") on code must name a tool operation as \"ToolClass.method\""),
                errors.get(0));
    }

    @Test
    void theNamePatternTakesOnlyTwoIdentifiersJoinedByOneDot() {
        assertTrue(ConfiqureProcessor.VERIFIED_BY.matcher("DemoTool.verifyCode").matches());
        for (String no : Arrays.asList("verifyCode", "DemoTool.", ".verifyCode", "a.b.c", "Demo Tool.verify", "DemoTool#verify")) {
            assertTrue(!ConfiqureProcessor.VERIFIED_BY.matcher(no).matches(), no);
        }
    }

    // ------------------------------------------------------------------ in-process javac with the processor

    private static JavaFileObject source(String name, String code) {
        return new SimpleJavaFileObject(URI.create("string:///" + name.replace('.', '/') + ".java"), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return code;
            }
        };
    }

    private static List<Diagnostic<? extends JavaFileObject>> compile(JavaFileObject... sources) throws Exception {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        File out = Files.createTempDirectory("confiqure-processor-test").toFile();
        try (StandardJavaFileManager files = javac.getStandardFileManager(diagnostics, null, null)) {
            List<String> options = Arrays.asList("-classpath", System.getProperty("java.class.path"), "-d", out.getPath(), "-proc:only");
            JavaCompiler.CompilationTask task = javac.getTask(null, files, diagnostics, options, null, Arrays.asList(sources));
            task.setProcessors(Collections.singletonList(new ConfiqureProcessor()));
            task.call();
        }
        return diagnostics.getDiagnostics().stream().filter(d -> d.getKind() == Diagnostic.Kind.ERROR).collect(Collectors.toList());
    }

    private static List<String> messages(List<Diagnostic<? extends JavaFileObject>> errors) {
        return errors.stream().map(d -> d.getMessage(null)).collect(Collectors.toList());
    }

    /**
     * A host that compiles WITHOUT the javac exports (a separate javac, so none of this JVM's opens apply): the processor
     * warns and the compile carries on — a good class compiles, and a bad VerifiedBy name still fails with its message —
     * instead of javac aborting with an uncaught processor exception.
     */
    @Test
    void withoutTheJavacExportsTheProcessorWarnsAndTheCompileCarriesOn() throws Exception {
        java.io.File dir = Files.createTempDirectory("confiqure-no-exports").toFile();
        java.io.File pkg = new java.io.File(dir, "demo");
        assertTrue(pkg.mkdirs());
        java.io.File good = new java.io.File(pkg, "DemoSettings.java");
        Files.write(good.toPath(), SETTING.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Result ok = javac(dir, good);
        assertEquals(0, ok.exit, ok.output);
        assertTrue(ok.output.contains("[confiqure] processor could not initialize"), ok.output);
        assertTrue(!ok.output.contains("uncaught exception"), ok.output);

        Files.write(good.toPath(), SETTING.replace("\"DemoTool.verifyCode\"", "\"verifyCode\"").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Result bad = javac(dir, good);
        assertTrue(bad.exit != 0, bad.output);
        assertTrue(bad.output.contains("must name a tool operation as \"ToolClass.method\""), bad.output);
        assertTrue(!bad.output.contains("uncaught exception"), bad.output);
    }

    private static final class Result {
        final int exit;
        final String output;

        Result(int exit, String output) {
            this.exit = exit;
            this.output = output;
        }
    }

    private static Result javac(java.io.File dir, java.io.File source) throws Exception {
        String javac = System.getProperty("java.home") + java.io.File.separator + "bin" + java.io.File.separator + "javac";
        String cp = System.getProperty("java.class.path");
        ProcessBuilder pb = new ProcessBuilder(javac, "-cp", cp, "-processorpath", cp,
                "-processor", ConfiqureProcessor.class.getName(), "-d", new java.io.File(dir, "out").getPath(), source.getPath());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        return new Result(p.waitFor(), out);
    }
}
