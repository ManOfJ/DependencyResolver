package com.manofj.minecraft.moj_dresolver.util

import java.io.File

import scala.util.control.NonFatal


object Functions {

  def tryQuietly( function: => Unit ): Unit =
    try function catch { case NonFatal( _ ) => }

  def tryToEither[ A ]( function: => A ): Either[ Throwable, A ] =
    try Right( function ) catch { case NonFatal( e ) => Left( e ) }

  def tryToOption[ A ]( function: => A ): Option[ A ] =
    try Option( function ) catch { case NonFatal( _ ) => None }


  def toCanonical( file: File ): Option[ File ] =
    tryToOption( file.getCanonicalFile )

  def newCanonicalFile( parent: File, name: String ): Option[ File ] =
    ( for { f <- Option( parent ); n <- Option( name ) } yield new File( f, n ) ).flatMap( toCanonical )

  def newCanonicalFile( pathname: String ): Option[ File ] =
    Option( pathname ).flatMap( x => toCanonical( new File( x ) ) )

}
