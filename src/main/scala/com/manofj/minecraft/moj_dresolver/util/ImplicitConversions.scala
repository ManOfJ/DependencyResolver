package com.manofj.minecraft.moj_dresolver.util


object ImplicitConversions {

  implicit class ObjectExtension[ A ]( val obj: A )
    extends AnyVal
  {
    def ? : Option[ A ] = Option( obj )
  }

}
