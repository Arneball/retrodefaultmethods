import org.kohsuke.asm5._
import org.kohsuke.asm5.Opcodes._
import Implicits._
import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer

case class Mustare(mname: String, mdesc: String, interface: String, signature: String)
class ClassMustare(visitor: ClassVisitor, byteCodeVersion: Int = V1_6) extends ClassVisitor(ASM5, visitor) {

  var methods: Array[Mustare] = _
  var cname: String = _
  type MethodDesc = (String, String)
  var implementedMethods = ArrayBuffer[MethodDesc]()

  override def visit(version: Int, access: Int, name: String, signature: String, superName: String, interfaces: Array[String]) = {
    val isClass = (access & ACC_INTERFACE) == 0
    if(isClass) {
      val defaultMethods = for {
        i <- interfaces
        cl = i.getInternalClass
        m @ DefaultMethod() <- cl.getMethods
      } yield Mustare(m.getName, Type.getMethodDescriptor(m), i, signature)
      println(s"Default methods: ${defaultMethods.mkString(",")}")
      methods = defaultMethods
      cname = name;
    }
    super.visit(byteCodeVersion, access, name, signature, superName, interfaces)
  }

  // gotta keep track of overriden default methods
  override def visitMethod(access: Int, name: String, desc: String, signature: String, exceptions: Array[String]): MethodVisitor = {
    implementedMethods += name -> desc
    new InterfaceToHelperRewriter(super.visitMethod(access, name, desc, signature, exceptions))
  }

  import org.kohsuke.asm5.Type._

  override def visitEnd() = {
    @tailrec def inner(l: List[Mustare]): Unit = l match {
      case Nil =>
      case Mustare(name, desc, interface, signature) :: tail if !implementedMethods.contains(name -> desc) =>
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
      case _ :: tail => inner(tail)
    }
    methods match {
      case null => super.visitEnd()
      case that => inner(that.toList)
    }
  }
}
