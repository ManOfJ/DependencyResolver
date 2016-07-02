package com.manofj.minecraft.moj_dresolver.resolver

import java.net.URL

import com.manofj.minecraft.moj_dresolver.gson.DependsJson
import com.manofj.minecraft.moj_dresolver.launcher.InjectionData


abstract class DependencyResolver {

  def injectData( data: Seq[ InjectionData[ _ ] ] ): Unit

  def resolvable( json: DependsJson ): Boolean

  def resolve( json: DependsJson ): Either[ String, Seq[ URL ] ]

  def requiresContinue: Boolean

  def requiresUrlRegister: Boolean

}
