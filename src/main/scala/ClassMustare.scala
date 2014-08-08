import java.lang.reflect.{Method, Modifier}

import Implicits._
import org.kohsuke.asm5.Opcodes._
import org.kohsuke.asm5.Type._
import org.kohsuke.asm5._

import scala.collection.mutable.ArrayBuffer

case class MethodContainer(mname: String, mdesc: String, interface: String, signature: String)
class ClassMustare(visitor: ClassVisitor, byteCodeVersion: Int = V1_6) extends ClassVisitor(ASM5, visitor) {

  var methods: List[MethodContainer] = Nil
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
      } yield MethodContainer(m.getName, Type.getMethodDescriptor(m), i, signature)
      println(s"Default methods: ${defaultMethods.mkString(",")}")
      methods = defaultMethods.toList
      cname = name;
    }
    super.visit(byteCodeVersion, access, name, signature, superName, interfaces)
  }

  // gotta keep track of overriden default methods
  override def visitMethod(access: Int, name: String, desc: String, signature: String, exceptions: Array[String]): MethodVisitor = {
    implementedMethods += name -> desc
    new InterfaceToHelperRewriter(super.visitMethod(access, name, desc, signature, exceptions))
  }

  override def visitEnd() = {
    def createProxy(l: MethodContainer) = l match {
      case MethodContainer(name, desc, interface, signature) if !implementedMethods.contains(name -> desc) =>
        val tmp = super.visitMethod(ACC_PUBLIC, name, desc, signature, null)
        tmp.visitVarInsn(ALOAD, 0)
        Type.getArgumentTypes(desc).zipWithIndex.foreach{
          case (PrimitiveLoad(instruction), i) => tmp.visitVarInsn(instruction, i + 1) 
          case (_, i) => tmp.visitVarInsn(ALOAD, i + 1)
        }
        tmp.visitMethodInsn(INVOKESTATIC, interface + "helper", name, desc.addParam(interface))
        Type.getReturnType(desc) match {
          case ReturnIns(ins)  => tmp.visitInsn(ins)
          case otherwise: Type => tmp.visitInsn(ARETURN)
        }
        tmp.visitMaxs(0, 0)
        tmp.visitEnd()
      case _ => // noop
    }
    methods.foreach{ createProxy }
    super.visitEnd()
  }
}
object DefaultMethod {
  def unapply(m: Method) = !Modifier.isAbstract(m.getModifiers)
}
object InstructorExtractor {
  val (loadMap, returnMap) = {
    val types = List(INT_TYPE,  LONG_TYPE, FLOAT_TYPE, DOUBLE_TYPE)
    val loads = List(ILOAD,     LLOAD,     FLOAD,      DLOAD)
    val rets  = List(IRETURN,   LRETURN,   FRETURN,    DRETURN)
    val mkMap = types.zip(_: List[Int]).toMap
    mkMap(loads) -> mkMap(rets)
  }
}
class InstructorExtractor(m: Map[Type, Int]) {
  def unapply(f: Type) = m.get(f)
}
object PrimitiveLoad extends InstructorExtractor(InstructorExtractor.loadMap)
object ReturnIns extends InstructorExtractor(InstructorExtractor.returnMap)