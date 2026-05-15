package ai.confiqure;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sun.source.util.Trees;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Names;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.util.Set;

@SupportedAnnotationTypes("ai.confiqure.Confiqure")
public class ConfiqureProcessor extends AbstractProcessor {

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    private Trees trees;
    private TreeMaker treeMaker;
    private Names names;
    private boolean initialized = false;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        try {
            ProcessingEnvironment unwrapped = unwrap(processingEnv);
            this.trees = Trees.instance(unwrapped);
            JavacProcessingEnvironment javacEnv = (JavacProcessingEnvironment) unwrapped;
            Context context = javacEnv.getContext();
            this.treeMaker = TreeMaker.instance(context);
            this.names = Names.instance(context);
            this.initialized = true;
        } catch (Exception e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,
                "[confiqure] processor could not initialize. Add the following to your maven-compiler-plugin compilerArgs:\n" +
                "  -J--add-opens=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED\n" +
                "  --add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED (and other com.sun.tools.javac.* packages)\n" +
                "Error: " + e.getMessage());
        }
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (!initialized) return false;
        for (Element element : roundEnv.getElementsAnnotatedWith(Confiqure.class)) {
            if (element.getKind() != ElementKind.CLASS) continue;
            try {
                injectConfiqureKey(element);
            } catch (Exception e) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "[confiqure] failed to inject confiqureKey into " + element.getSimpleName() + ": " + e.getMessage(),
                    element);
            }
        }
        return false;
    }

    private void injectConfiqureKey(Element element) {
        JCTree.JCClassDecl classDecl = (JCTree.JCClassDecl) trees.getTree(element);

        boolean alreadyPresent = classDecl.defs.stream()
            .anyMatch(t -> t instanceof JCTree.JCVariableDecl
                && ((JCTree.JCVariableDecl) t).name.toString().equals("confiqureKey"));
        if (alreadyPresent) return;

        treeMaker.pos = classDecl.pos;

        JCTree.JCVariableDecl field = makeField();
        JCTree.JCMethodDecl getter = makeGetter();
        classDecl.defs = classDecl.defs.prependList(List.of(field, getter));
    }

    private JCTree.JCVariableDecl makeField() {
        JCTree.JCAnnotation jsonProp = treeMaker.Annotation(
            qualifiedIdent("com.fasterxml.jackson.annotation.JsonProperty"),
            List.of(treeMaker.Assign(
                treeMaker.Ident(names.fromString("value")),
                treeMaker.Literal("confiqureKey")
            ))
        );

        JCTree.JCMethodInvocation init = treeMaker.Apply(
            List.nil(),
            treeMaker.Select(
                qualifiedIdent("ai.confiqure.ConfiqureKeys"),
                names.fromString("generate")),
            List.nil());

        return treeMaker.VarDef(
            treeMaker.Modifiers(Flags.PRIVATE, List.of(jsonProp)),
            names.fromString("confiqureKey"),
            treeMaker.Ident(names.fromString("String")),
            init);
    }

    private JCTree.JCMethodDecl makeGetter() {
        JCTree.JCBlock body = treeMaker.Block(0,
            List.of(treeMaker.Return(treeMaker.Ident(names.fromString("confiqureKey")))));

        return treeMaker.MethodDef(
            treeMaker.Modifiers(Flags.PUBLIC),
            names.fromString("getConfiqureKey"),
            treeMaker.Ident(names.fromString("String")),
            List.nil(),
            List.nil(),
            List.nil(),
            body,
            null);
    }

    private JCTree.JCExpression qualifiedIdent(String fqn) {
        String[] parts = fqn.split("\\.");
        JCTree.JCExpression expr = treeMaker.Ident(names.fromString(parts[0]));
        for (int i = 1; i < parts.length; i++) {
            expr = treeMaker.Select(expr, names.fromString(parts[i]));
        }
        return expr;
    }

    private static ProcessingEnvironment unwrap(ProcessingEnvironment env) {
        if (env instanceof JavacProcessingEnvironment) return env;
        try {
            java.lang.reflect.Field f = env.getClass().getDeclaredField("delegate");
            f.setAccessible(true);
            Object inner = f.get(env);
            if (inner instanceof ProcessingEnvironment) return unwrap((ProcessingEnvironment) inner);
        } catch (Exception ignored) {
            // fallback: scan all fields for a JavacProcessingEnvironment
            try {
                for (java.lang.reflect.Field f : env.getClass().getDeclaredFields()) {
                    f.setAccessible(true);
                    Object val = f.get(env);
                    if (val instanceof JavacProcessingEnvironment) return (JavacProcessingEnvironment) val;
                }
            } catch (Exception ignored2) {}
        }
        return env;
    }
}
