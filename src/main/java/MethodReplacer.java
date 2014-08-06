import org.kohsuke.asm5.ClassVisitor;
import org.kohsuke.asm5.ClassWriter;
import org.kohsuke.asm5.MethodVisitor;
import org.kohsuke.asm5.Opcodes;

public class MethodReplacer extends ClassVisitor {
    private final String mname, mdesc;
    private String cname;
    public MethodReplacer(ClassWriter wr, String mname, String mdesc) {
        super(5, wr);
        this.mname = mname;
        this.mdesc = mdesc;
    }
    public void visit(int version, int access,
                      String name, String signature,
                      String superName, String[] interfaces) {
        this.cname = name;
        cv.visit(version, access, name,
                signature, superName, interfaces);
    }
    public MethodVisitor visitMethod(int access,
                                     String name, String desc,
                                     String signature, String[] exceptions) {
        String newName = name;
        if(name.equals(mname) && desc.equals(mdesc)) {
            newName = "orig$" + name;
            generateNewBody(access, desc, signature,
                    exceptions, name, newName);
        }
        return super.visitMethod(access, newName,
                desc, signature, exceptions);
    }
    private void generateNewBody(int access,
                                 String desc, String signature,
                                 String[] exceptions,
                                 String name, String newName) {
        MethodVisitor mv = cv.visitMethod(access,
                name, desc, signature, exceptions);
        // ...
        mv.visitCode();
        // call original metod
        mv.visitVarInsn(Opcodes.ALOAD, 0); // this
        mv.visitMethodInsn(access, cname, newName,
                desc);
        // ...
        mv.visitEnd();
    }
}