package com.manofj.minecraft.moj_dresolver.resolver

import com.manofj.minecraft.moj_dresolver.gson.DependsJson
import com.manofj.minecraft.moj_dresolver.launcher.InjectionData


trait Resolver {

  def inject( data: Seq[ InjectionData[ _ ] ] ): Unit

  def resolvable( dependsJson: DependsJson ): Boolean

  def process( dependsJson: DependsJson ): Either[ Throwable, String ]

  def finish(): Unit

}
