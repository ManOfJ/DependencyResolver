package com.manofj.minecraft

import scala.util.control.NonFatal


package object moj_dresolver {

  private[ moj_dresolver ] implicit class ObjectExtension[ A ]( val obj: A )
    extends AnyVal
  {
    def ? : Option[ A ] = Option( obj )
  }

  private[ moj_dresolver ] def tryToEither[ A ]( function: => A ): Either[ Throwable, A ] =
    try Right( function ) catch { case NonFatal( e ) => Left( e ) }

  private[ moj_dresolver ] def tryToOption[ A ]( function: => A ): Option[ A ] =
    try Option( function ) catch { case NonFatal( _ ) => None }

}
