package com.manofj.minecraft.moj_dresolver.launcher

import java.io.File

import net.minecraft.launchwrapper.{LaunchClassLoader => LaunchClazzLoader}


object InjectionData {

  case class MinecraftDirectory( data: File ) extends InjectionData[ File ]

  case class LibrariesDirectory( data: File ) extends InjectionData[ File ]

  case class ModsDirectory( data: File ) extends InjectionData[ File ]

  case class LaunchClassLoader( data: LaunchClazzLoader ) extends InjectionData[ LaunchClazzLoader ]

  case class DefaultLibraries( data: Seq[ String ] ) extends InjectionData[ Seq[ String ] ]

}

sealed trait InjectionData[ A ] { def data: A }
