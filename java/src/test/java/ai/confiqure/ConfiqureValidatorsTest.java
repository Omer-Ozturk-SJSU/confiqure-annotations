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
     * A host that compiles WITHOUT the javac exports (a separate javac, so none of this JVM's opens apply): never javac's
     * uncaught-exception abort. An object class fails with a readable error naming it and the flags (it would ship without
     * its confiqureKey); a plain class compiles, and a bad VerifiedBy on it fails with its own message only.
     */
    @Test
    void withoutTheJavacExports_anObjectClassFailsReadably_andAPlainClassCompilesWithItsOwnChecks() throws Exception {
        java.io.File dir = Files.createTempDirectory("confiqure-no-exports").toFile();
        java.io.File pkg = new java.io.File(dir, "demo");
        assertTrue(pkg.mkdirs());

        java.io.File setting = write(pkg, "DemoSettings.java", SETTING);
        Result object = javac(dir, setting);
        assertTrue(object.exit != 0, object.output);
        assertTrue(object.output.contains("[confiqure] DemoSettings needs its confiqureKey, and the processor could not initialize"),
                object.output);
        assertTrue(object.output.contains("--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED"), object.output);
        assertTrue(!object.output.contains("uncaught exception"), object.output);

        String plain = "package demo;\n"
                + "import ai.confiqure.Confiqure;\n"
                + "public class DemoRequest {\n"
                + "    @Confiqure.VerifiedBy(\"DemoTool.verifyCode\") public String code;\n"
                + "}\n";
        java.io.File request = write(pkg, "DemoRequest.java", plain);
        Result good = javac(dir, request);
        assertEquals(0, good.exit, good.output);
        assertTrue(!good.output.contains("needs its confiqureKey"), good.output);

        write(pkg, "DemoRequest.java", plain.replace("\"DemoTool.verifyCode\"", "\"verifyCode\""));
        Result bad = javac(dir, request);
        assertTrue(bad.exit != 0, bad.output);
        assertTrue(bad.output.contains("must name a tool operation as \"ToolClass.method\""), bad.output);
        assertTrue(!bad.output.contains("needs its confiqureKey"), bad.output);
        assertTrue(!bad.output.contains("uncaught exception"), bad.output);
    }

    /**
     * Review 10978: the flags the message tells the host to add are the ones that work. javac run with exactly the -J options
     * taken from the message's text compiles the Setting class, and the class carries its confiqureKey.
     */
    @Test
    void theFlagsTheMessageNamesAreTheOnesThatMakeTheBuildWork() throws Exception {
        List<String> flags = new java.util.ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("-J--add-\\S+").matcher(ConfiqureProcessor.FLAGS);
        while (m.find()) flags.add(m.group());
        assertEquals(6, flags.size(), ConfiqureProcessor.FLAGS);
        assertTrue(ConfiqureProcessor.FLAGS.contains("<fork>true</fork>"), ConfiqureProcessor.FLAGS);

        java.io.File dir = Files.createTempDirectory("confiqure-with-flags").toFile();
        java.io.File pkg = new java.io.File(dir, "demo");
        assertTrue(pkg.mkdirs());
        java.io.File setting = write(pkg, "DemoSettings.java", SETTING);
        Result r = javac(dir, setting, flags);
        assertEquals(0, r.exit, r.output);
        assertTrue(!r.output.contains("could not initialize"), r.output);
        try (java.net.URLClassLoader loader = new java.net.URLClassLoader(
                new java.net.URL[]{new java.io.File(dir, "out").toURI().toURL()}, getClass().getClassLoader())) {
            Class<?> c = loader.loadClass("demo.DemoSettings");
            assertEquals(String.class, c.getMethod("getConfiqureKey").getReturnType());
            assertEquals(String.class, c.getDeclaredField("confiqureKey").getType());
        }
    }

    private static java.io.File write(java.io.File pkg, String name, String code) throws Exception {
        java.io.File f = new java.io.File(pkg, name);
        Files.write(f.toPath(), code.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return f;
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
        return javac(dir, source, Collections.emptyList());
    }

    private static Result javac(java.io.File dir, java.io.File source, List<String> jvmFlags) throws Exception {
        String javac = System.getProperty("java.home") + java.io.File.separator + "bin" + java.io.File.separator + "javac";
        String cp = System.getProperty("java.class.path");
        List<String> cmd = new java.util.ArrayList<>();
        cmd.add(javac);
        cmd.addAll(jvmFlags);
        cmd.addAll(Arrays.asList("-cp", cp, "-processorpath", cp,
                "-processor", ConfiqureProcessor.class.getName(), "-d", new java.io.File(dir, "out").getPath(), source.getPath()));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        return new Result(p.waitFor(), out);
    }
}
