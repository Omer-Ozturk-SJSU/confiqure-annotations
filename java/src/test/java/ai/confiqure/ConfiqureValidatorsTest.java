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

/** 3.1: the named validators, the verifiers and Confirm compile where they belong; a ValidatedBy or VerifiedBy naming the wrong kind of class fails the compile. */
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
            + "    @Confiqure.VerifiedBy(DemoCheck.class) private String code;\n"
            + "    @Confiqure.ValidatedBy(value = DemoFormat.class, message = \"A code looks like ABC-1234.\") private String sku;\n"
            + "    @Confiqure.Verify(Confiqure.ValidatorKind.ADDRESS) private String shipFrom;\n"
            + "    @Confiqure.Verify(Confiqure.ValidatorKind.PRODUCT_CODE) private String barcode;\n"
            + "}\n";

    /** A host verifier: a tool class implementing ConfiqureVerifier, its verify mapped like any operation. */
    private static final String CHECK = "package demo;\n"
            + "import ai.confiqure.Confiqure;\n"
            + "import ai.confiqure.ConfiqureVerifier;\n"
            + "@Confiqure.Tool\n"
            + "public class DemoCheck implements ConfiqureVerifier<String> {\n"
            + "    public Confiqure.Verdict<String> verify(Confiqure.Check<String> check) {\n"
            + "        String code = check.getValue().strip();\n"
            + "        return code.equals(check.getValue()) ? Confiqure.Verdict.ok() : Confiqure.Verdict.corrected(code);\n"
            + "    }\n"
            + "}\n";

    /** A host validator: ok or not ok, never a corrected value. */
    private static final String FORMAT = "package demo;\n"
            + "import ai.confiqure.Confiqure;\n"
            + "import ai.confiqure.ConfiqureValidator;\n"
            + "@Confiqure.Tool\n"
            + "public class DemoFormat implements ConfiqureValidator<String> {\n"
            + "    public Confiqure.Verdict<Void> validate(Confiqure.Check<String> check) {\n"
            + "        return check.getValue().matches(\"[A-Z]{3}-\\\\d{4}\") ? Confiqure.Verdict.ok() : Confiqure.Verdict.notOk(\"Not a code.\");\n"
            + "    }\n"
            + "}\n";

    private static final String TOOL = "package demo;\n"
            + "import ai.confiqure.Confiqure;\n"
            + "@Confiqure.Tool(name = \"DemoTool\")\n"
            + "public class DemoTool {\n"
            + "    public static class CodeRequest { @Confiqure.VerifiedBy(DemoCheck.class) public String code; }\n"
            + "    @Confiqure.VerifiedBy(RangeCheck.class)\n"
            + "    public static class Range { public int min; public int max; }\n"
            + "    public static class RangeCheck implements ai.confiqure.ConfiqureVerifier<Range> {\n"
            + "        public Confiqure.Verdict<Range> verify(Confiqure.Check<Range> check) {\n"
            + "            return check.getValue().min <= check.getValue().max ? Confiqure.Verdict.ok() : Confiqure.Verdict.notOk(\"min above max\");\n"
            + "        }\n"
            + "    }\n"
            + "    public boolean setRange(Range in) { return true; }\n"
            + "    @Confiqure.Confirm(false)\n"
            + "    public boolean pauseDemo(CodeRequest in) { return true; }\n"
            + "    public boolean verifyCode(CodeRequest in) { return true; }\n"
            + "    @Confiqure.Confirm(text = \"Stop the demo schedule?\")\n"
            + "    public boolean stopSchedule(CodeRequest in) { return true; }\n"
            + "}\n";

    @Test
    void everyNewAnnotationCompilesOnASettingFieldAToolRequestFieldAndAToolOperation() throws Exception {
        // a full compile, so the examples' bodies are type-checked too
        List<Diagnostic<? extends JavaFileObject>> errors = compile(false, source("demo.DemoSettings", SETTING), source("demo.DemoTool", TOOL),
                source("demo.DemoCheck", CHECK), source("demo.DemoFormat", FORMAT));
        assertEquals(Collections.emptyList(), messages(errors));
    }

    /** CC-5: the retired "ToolClass.method" name check stopped a VerifiedBy that names no verifier; the type bound stops it now. */
    @Test
    void aVerifiedByNamingAValidatorOrAPlainClassFailsTheCompile() throws Exception {
        for (String wrong : Arrays.asList("DemoFormat.class", "String.class")) {
            String bad = SETTING.replace("VerifiedBy(DemoCheck.class)", "VerifiedBy(" + wrong + ")");
            List<String> errors = messages(compile(source("demo.DemoSettings", bad), source("demo.DemoCheck", CHECK),
                    source("demo.DemoFormat", FORMAT)));
            assertEquals(1, errors.size(), wrong + ": " + errors);
        }
    }

    @Test
    void aValidatedByNamingAVerifierOrAPlainClassFailsTheCompile() throws Exception {
        for (String wrong : Arrays.asList("DemoCheck.class", "String.class")) {
            String bad = SETTING.replace("ValidatedBy(value = DemoFormat.class", "ValidatedBy(value = " + wrong);
            List<String> errors = messages(compile(source("demo.DemoSettings", bad), source("demo.DemoCheck", CHECK),
                    source("demo.DemoFormat", FORMAT)));
            assertEquals(1, errors.size(), wrong + ": " + errors);
        }
    }

    @Test
    void aValidatorCannotAnswerWithACorrectedValue() throws Exception {
        String bad = FORMAT.replace("Confiqure.Verdict.ok() :", "Confiqure.Verdict.corrected(\"ABC-1234\") :");
        // a full compile: -proc:only never attributes method bodies
        List<String> errors = messages(compile(false, source("demo.DemoFormat", bad)));
        assertEquals(1, errors.size(), errors.toString());
        assertEquals(Collections.emptyList(), messages(compile(false, source("demo.DemoFormat", FORMAT))));
    }

    /**
     * The wire DispatcherHostVerifier reads: {ok, message?, value?}. An ok answer carries no "value" key at all (a null
     * one would read as a value corrected to null); not ok carries its message; corrected carries the value.
     */
    @Test
    void theVerdictWritesTheEngineWireAndTheCheckReadsIt() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper json = new com.fasterxml.jackson.databind.ObjectMapper();
        assertEquals("{\"ok\":true}", json.writeValueAsString(Confiqure.Verdict.ok()));
        assertEquals("{\"ok\":false,\"message\":\"No product has that code.\"}",
                json.writeValueAsString(Confiqure.Verdict.notOk("No product has that code.")));
        assertEquals("{\"ok\":true,\"value\":\"ABC-1234\"}", json.writeValueAsString(Confiqure.Verdict.corrected("ABC-1234")));

        Confiqure.Check<String> check = json.readValue("{\"field\":\"sku\",\"value\":\"abc-1234\",\"confiqureKey\":\"k-1\"}",
                new com.fasterxml.jackson.core.type.TypeReference<Confiqure.Check<String>>() {});
        assertEquals("sku", check.getField());
        assertEquals("abc-1234", check.getValue());
        assertEquals("k-1", check.getConfiqureKey());
        assertEquals(null, json.readValue("{\"field\":\"sku\",\"value\":\"x\"}", Confiqure.Check.class).getConfiqureKey());
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
        return compile(true, sources);
    }

    private static List<Diagnostic<? extends JavaFileObject>> compile(boolean procOnly, JavaFileObject... sources) throws Exception {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        File out = Files.createTempDirectory("confiqure-processor-test").toFile();
        try (StandardJavaFileManager files = javac.getStandardFileManager(diagnostics, null, null)) {
            List<String> options = new java.util.ArrayList<>(Arrays.asList("-classpath", System.getProperty("java.class.path"), "-d", out.getPath()));
            if (procOnly) options.add("-proc:only");
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
     * its confiqureKey); a plain class compiles, and a VerifiedBy naming no verifier fails with javac's own error only.
     */
    @Test
    void withoutTheJavacExports_anObjectClassFailsReadably_andAPlainClassCompilesWithItsOwnChecks() throws Exception {
        java.io.File dir = Files.createTempDirectory("confiqure-no-exports").toFile();
        java.io.File pkg = new java.io.File(dir, "demo");
        assertTrue(pkg.mkdirs());

        write(pkg, "DemoCheck.java", CHECK);
        write(pkg, "DemoFormat.java", FORMAT);
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
                + "    @Confiqure.VerifiedBy(DemoCheck.class) public String code;\n"
                + "}\n";
        java.io.File request = write(pkg, "DemoRequest.java", plain);
        Result good = javac(dir, request);
        assertEquals(0, good.exit, good.output);
        assertTrue(!good.output.contains("needs its confiqureKey"), good.output);

        write(pkg, "DemoRequest.java", plain.replace("DemoCheck.class", "String.class"));
        Result bad = javac(dir, request);
        assertTrue(bad.exit != 0, bad.output);
        assertTrue(bad.output.contains("incompatible types"), bad.output);
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
        write(pkg, "DemoCheck.java", CHECK);
        write(pkg, "DemoFormat.java", FORMAT);
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
        cmd.addAll(Arrays.asList("-cp", cp, "-sourcepath", dir.getPath(), "-processorpath", cp,
                "-processor", ConfiqureProcessor.class.getName(), "-d", new java.io.File(dir, "out").getPath(), source.getPath()));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        return new Result(p.waitFor(), out);
    }
}
