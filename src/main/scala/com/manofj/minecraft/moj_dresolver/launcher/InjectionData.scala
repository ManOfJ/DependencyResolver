package com.manofj.minecraft.moj_dresolver.launcher

import java.io.File
import java.net.URL


object InjectionData {

  case class MinecraftDir( data: File ) extends InjectionData[ File ]

  case class LibrariesDir( data: File ) extends InjectionData[ File ]

  case class ModsDir( data: File ) extends InjectionData[ File ]

  case class DefaultLibraries( data: Seq[ String ] ) extends InjectionData[ Seq[ String ] ]

  case class ResolvedURLs( data: Seq[ URL ] ) extends InjectionData[ Seq[ URL ] ]

}

trait InjectionData[ A ] {
  def data: A
}
