/**
 * Created by arneball on 2014-07-29.
 */

import java.io.IOException
import java.lang.reflect.{Modifier, Method}
import java.net.{URL, URLClassLoader}
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file._

import org.kohsuke.asm5._

import scala.annotation.tailrec

object AsmTest extends App{
  val ucl = new URLClassLoader(Array(new URL("file:///tmp/cp/")))
  Files.walkFileTree(Paths.get("/tmp/cp"), new FileVisitor[Path] {
    override def visitFileFailed(file: Path, exc: IOException): FileVisitResult = ???

    override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = file.toString.toLowerCase.endsWith("class") match {
      case true =>
        println("Found class file " + file)
        val bytecode = Files.readAllBytes(file)
        val wr = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES)
        val stage1 = new InterfaceMustare(wr)
        val stage2 = new ClassMustare(stage1)
        val reader = new ClassReader(bytecode).accept(stage2, 0);
        Files.write(file, wr.toByteArray)
        FileVisitResult.CONTINUE
      case that =>
        FileVisitResult.CONTINUE
    }

    override def preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult = {
      FileVisitResult.CONTINUE
    }

    override def postVisitDirectory(dir: Path, exc: IOException): FileVisitResult = {
      FileVisitResult.CONTINUE
    }
  })
}
object Implicits {
  val Sig = raw"\((.*)\)(.*)".r
  implicit class StrWr(val str: String) extends AnyVal {
    def addParam(cl: String) = str match {
      case Sig(content, returntype) =>
        val tmp = s"(L${cl};$content)$returntype"
        println(s"Before $str, now $tmp")
        tmp
    }
  }
}
import Opcodes._
import Implicits._
class InterfaceMustare(classWriter: ClassWriter) extends ClassVisitor(Opcodes.ASM5, classWriter) with Opcodes {
  private var isInterface = false
  private lazy val helperClassVisitor = mkHelperClass
  private var cName: String = _
  override def visitMethod(access: Int, name: String, desc: String, signature: String, exceptions: Array[String]): MethodVisitor = {
    val methContcreted = (ACC_ABSTRACT & access) == 0
    (methContcreted, isInterface) match {
      case (true, true) =>
        var mv = super.visitMethod(access | ACC_ABSTRACT, name, desc, signature, exceptions)
        val tmp = helperClassVisitor.visitMethod(access | ACC_STATIC, name, desc.addParam(cName), signature, exceptions)
        tmp.visitMaxs(0, 0)
        new BodyStripper(cName, tmp)
      case _ =>
        super.visitMethod(access, name, desc, signature, exceptions)
    }
  }

  override def visitEnd = {
    Files.write(Paths.get(s"/tmp/cp/${cName}helper.class"), helperClassVisitor.toByteArray)
    helperClassVisitor.visitEnd()
    super.visitEnd
  }

  private def mkHelperClass = {
    val cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);

    cw.visit(49,
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
    super.visit(version, access, name, signature, superName, interfaces)
  }
}
// works, strips the body
class BodyStripper(cname: String, newMethod: MethodVisitor) extends MethodVisitor(Opcodes.ASM5, newMethod) {
  override def visitEnd = {
    visitMaxs(0, 0)

    super.visitEnd
  }
}

class ClassMustare(visitor: ClassVisitor) extends ClassVisitor(Opcodes.ASM5, visitor) {
  case class Mustare(mname: String, mdesc: String, interface: String, signature: String)

  var methods: Array[Mustare] = _
  var cname: String = _
  override def visit(version: Int, access: Int, name: String, signature: String, superName: String, interfaces: Array[String]) = {
    val isClass = (access & ACC_INTERFACE) == 0
    if(isClass) {
      val defaultMethods = for {
        i <- interfaces
        cl = AsmTest.ucl.loadClass(i)
        m @ DefaultMethod() <- cl.getMethods
      } yield Mustare(m.getName, Type.getMethodDescriptor(m), i, signature)
      println(s"Default methods: ${defaultMethods.mkString(",")}")
      methods = defaultMethods
      cname = name;
    }
    super.visit(version, access, name, signature, superName, interfaces)
  }

  import Type._
  override def visitMethod(access: Int, name: String, desc: String, signature: String, exceptions: Array[String]): MethodVisitor = {
    super.visitMethod(access, name, desc, signature, exceptions)
  }

  override def visitEnd = {
    @tailrec def inner(l: List[Mustare]): Unit = l match {
      case Nil =>
      case Mustare(name, desc, interface, signature) :: tail =>
        val tmp = super.visitMethod(ACC_PUBLIC, name, desc, signature, null)
        tmp.visitVarInsn(ALOAD, 0)
        Type.getArgumentTypes(desc).zipWithIndex.foreach{
          case (INT_TYPE, i) => tmp.visitVarInsn(ILOAD, i + 1)
          case (typ, i) => tmp.visitVarInsn(ALOAD, i + 1)
        }
        tmp.visitMethodInsn(INVOKESTATIC, interface + "helper", name, desc.addParam(interface))
        Type.getReturnType(desc) match {
          case VOID_TYPE => tmp.visitInsn(RETURN)
          case INT_TYPE => tmp.visitInsn(IRETURN)
            // TODO add others
          case otherwise: Type => tmp.visitInsn(ARETURN)
        }
        tmp.visitMaxs(0, 0)
        tmp.visitEnd()
        inner(tail)
    }
    methods match {
      case null => super.visitEnd
      case that => inner(that.toList)
    }
  }
}

class ConcreteMaker(cv: MethodVisitor) extends MethodVisitor(Opcodes.ASM5, cv) {

}

object DefaultMethod {
  def unapply(m: Method) = !Modifier.isAbstract(m.getModifiers)
}

case class Extractor(t: Type) {
  def unapply(i: Type) = t eq i
}
object ILoad extends Extractor(Type.INT_TYPE)
object DLoad extends Extractor(Type.DOUBLE_TYPE)