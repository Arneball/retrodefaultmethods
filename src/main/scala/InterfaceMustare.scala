import java.nio.file.{Paths, Files}

import org.kohsuke.asm5._
import Opcodes._
import collection.{mutable => m}
import Implicits._

class InterfaceMustare(classWriter: ClassWriter, targetByteCode: Int = Opcodes.V1_6) extends ClassVisitor(ASM5, classWriter) with Opcodes {
  private var isInterface = false
  private lazy val helperClassVisitor = mkHelperClass
  private var cName: String = _
  override def visitMethod(access: Int, name: String, desc: String, signature: String, exceptions: Array[String]): MethodVisitor = {
    val methContcreted = (ACC_ABSTRACT & access) == 0
    (methContcreted, isInterface) match {
      case (true, true) =>
        var mv = super.visitMethod(access | ACC_ABSTRACT, name, desc, signature, exceptions)
        val tmp = helperClassVisitor.visitMethod(access | ACC_STATIC, name, desc.addParam(cName), signature, exceptions)
        new BodyStripper(tmp)
      case _ =>
        super.visitMethod(access, name, desc, signature, exceptions)
    }
  }

  override def visitEnd() = {
    val newpath = AsmTest.output.resolve(cName + "helper.class")
    println("CREATING HELPER AT " + newpath)
    helperClassVisitor.visitEnd()
    super.visitEnd()
    Files.write(newpath, helperClassVisitor.toByteArray)
  }

  private def mkHelperClass = {
    val cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);

    cw.visit(targetByteCode,
      ACC_PUBLIC + ACC_SUPER,
      cName + "helper",
      null,
      "java/lang/Object",
      null);

    cw.visitSource("Hello.java", null);

    {
      val mv = cw.visitMethod(ACC_PRIVATE, "<init>", "()V", null, null);
      mv.visitVarInsn(ALOAD, 0);
      mv.visitMethodInsn(INVOKESPECIAL,
        "java/lang/Object",
        "<init>",
        "()V");
      mv.visitInsn(RETURN);
      mv.visitMaxs(0, 0);
      mv.visitEnd();
    }
    cw
  }

  override def visit(version: Int, access: Int, name: String, signature: String, superName: String, interfaces: Array[String]) = {
    isInterface = (access & ACC_INTERFACE) != 0
    cName = name
    super.visit(targetByteCode, access, name, signature, superName, interfaces)
  }
}

// works, strips the body
class BodyStripper(newMethod: MethodVisitor) extends MethodVisitor(Opcodes.ASM5, newMethod) {
  override def visitEnd() = {
    newMethod.visitMaxs(0, 0)
    super.visitEnd()
  }
}

class InterfaceToHelperRewriter(mv: MethodVisitor) extends MethodVisitor(Opcodes.ASM5, mv) {
  override def visitMethodInsn(opcode: Int, owner: String, name: String, desc: String, itf: Boolean) = opcode match {
    case INVOKESPECIAL if owner.getInternalClass.isInterface =>
      println(s"Messing up $owner $name $desc")
      super.visitMethodInsn(INVOKESTATIC, owner + "helper", name, desc.addParam(owner), itf)
    case _ =>
      super.visitMethodInsn(opcode, owner, name, desc, itf)
  }
}
