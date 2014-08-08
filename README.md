retrodefaultmethods
===================

This project rewrites java 8 classes with default methods so that they are compatible with JRE7 and JRE6

What it does is that it rips the method bodies out of the interface to a helper class.

Classes implementing the interface and not overriding the default method will then override the default method and call the helper class's method.

The remaining interfaces are purely abstract and the 

Before
======
```java
interface I {
  default void kalle() {
  }
}

class C implements I {
  public static void main(String [] args) {
    new C().kalle();
  }
}

Compiled from "I.java"
interface I {
  public void kalle();
    Code:
       0: return        
}
Compiled from "C.java"
class C implements I {
  C();
    Code:
       0: aload_0       
       1: invokespecial #1                  // Method java/lang/Object."<init>":()V
       4: return        

  public static void main(java.lang.String[]);
    Code:
       0: new           #2                  // class C
       3: dup           
       4: invokespecial #3                  // Method "<init>":()V
       7: invokevirtual #4                  // Method kalle:()V
      10: return        
}
```

After
=====
```java
interface I {
  public abstract void kalle();
}
Compiled from "C.java"
class C implements I {
  C();
    Code:
       0: aload_0       
       1: invokespecial #11                 // Method java/lang/Object."<init>":()V
       4: return        

  public static void main(java.lang.String[]);
    Code:
       0: new           #2                  // class C
       3: dup           
       4: invokespecial #14                 // Method "<init>":()V
       7: invokevirtual #17                 // Method kalle:()V
      10: return        

  public void kalle();
    Code:
       0: aload_0       
       1: invokestatic  #22                 // Method Ihelper.kalle:(LI;)V
       4: return       
}
Compiled from "Hello.java"
public class Ihelper {
  private Ihelper();
    Code:
       0: aload_0       
       1: invokespecial #9                  // Method java/lang/Object."<init>":()V
       4: return        

  public static void kalle(I);
    Code:
       0: return        
}
```
