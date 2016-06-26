package com.manofj.minecraft.moj_dresolver.launcher

import java.io.File
import java.net.URL
import java.util.jar.{ Attributes, JarFile }
import java.util.{ List => JavaList }

import scala.language.implicitConversions

import com.google.gson.Gson
import org.apache.commons.io.{ Charsets, IOUtils }

import net.minecraft.launchwrapper.{ ITweaker, LaunchClassLoader }

import net.minecraftforge.fml.common.FMLLog
import net.minecraftforge.fml.relauncher.FMLInjectionData

import com.manofj.minecraft.moj_dresolver.gson.DependsJson
import com.manofj.minecraft.moj_dresolver.resolver.{ DependencyResolver, MavenResolver }


class DependencyResolverTweaker
  extends ITweaker
{
  // TODO: コメントを記述する
  // TODO: ClassLoaderExclusion を追加できるようにする
  // TODO: Bintray リポジトリに対応する
  // TODO: 前提 Mod も依存関係解決できるようにする

  import com.manofj.minecraft.moj_dresolver._


  // MinecraftディレクトリをJVMオプションで指定する際の名前
  private[ this ] final val OPTION_MINECRAFT_PATH = "moj_dresolver.minecraft.path"

  // ライブラリディレクトリをJVMオプションで指定する際の名前
  private[ this ] final val OPTION_LIBRARY_PATH = "moj_dresolver.libraries.path"

  // 依存関係データJSONの属性名
  private[ this ] final val ATTRIBUTE_DEPENDS_JSON = new Attributes.Name( "DependsJson" )


  // 依存関係解決を行うリゾルバーの集合
  private[ this ] val resolvers =
  {
    val builder = Seq.newBuilder[ DependencyResolver ]
    builder += new MavenResolver
    builder.result
  }


  // インジェクションデータのうち内容が不変のもの
  private[ this ] var minecraftDir     = Option.empty[ InjectionData.MinecraftDir ]
  private[ this ] var librariesDir     = Option.empty[ InjectionData.LibrariesDir ]
  private[ this ] var modsDir          = Option.empty[ InjectionData.ModsDir ]
  private[ this ] var defaultLibraries = Option.empty[ InjectionData.DefaultLibraries ]


  // 各インジェクションデータを作成する
  override def acceptOptions( args: JavaList[ String ], gameDir: File, assetsDir: File, profile: String ): Unit = {

    minecraftDir =
      System.getProperty( OPTION_MINECRAFT_PATH ).?
        .orElse( System.getenv( "APPDATA" ).concat( "/.minecraft" ).? )
        .map( new File( _ ).getCanonicalFile )
        .withFilter( _.isDirectory )
        .map( InjectionData.MinecraftDir )

    librariesDir =
      System.getProperty( OPTION_LIBRARY_PATH ).?
        .fold { minecraftDir.map( x => new File( x.data, "libraries" ) ) }
              { path => Option( new File( path ).getCanonicalFile ) }
        .withFilter( _.isDirectory )
        .map( InjectionData.LibrariesDir )

    modsDir =
      gameDir.?
        .orElse { FMLInjectionData.data()( 6 ) match {
          case x: File => x.?
          case _       => None
        } }
        .map( new File( _, "mods" ).getCanonicalFile )
        .filter( _.isDirectory )
        .map( InjectionData.ModsDir )

    defaultLibraries =
      System.getProperty( "java.class.path" ).?
        .map( _.split( ';' ) )
        .map( _.map( new File( _ ).getName ) )
        .map( x => InjectionData.DefaultLibraries( x.toSeq ) )


    minecraftDir match {
      case Some( dir ) => FMLLog.info( s"[ DependencyResolver ] Minecraft directory is found -> ${ dir.data }" )
      case None => throw new IllegalStateException( "[ DependencyResolver ] Minecraft directory not found." )
    }

    librariesDir match {
      case Some( dir ) => FMLLog.info( s"[ DependencyResolver ] Libraries directory is found -> ${ dir.data }" )
      case None => throw new IllegalStateException( "[ DependencyResolver ] Libraries directory not found." )
    }

    modsDir match {
      case Some( dir ) => FMLLog.info( s"[ DependencyResolver ] Mods directory is found -> ${ dir.data }" )
      case None => throw new IllegalStateException( "[ DependencyResolver ] Mods directory not found." )
    }

    defaultLibraries match {
      case Some( libs ) => FMLLog.info( s"[ DependencyResolver ] Minecraft default libraries -> ${ libs.data.mkString( ";" ) }" )
      case None => throw new IllegalStateException( "[ DependencyResolver ] Minecraft default libraries undefined." )
    }

  }

  // 依存関係の解決を行う
  override def injectIntoClassLoader( classLoader: LaunchClassLoader ): Unit = {

    for {
      mc   <- minecraftDir
      libs <- librariesDir
      mods <- modsDir
      defs <- defaultLibraries
    } {
      var knownMods = Seq.empty[ String ]
      var resolvedURLs = Seq.empty[ URL ]

      var continue = false
      do {
        continue = false
        for {
          list <- mods.data.listFiles.?
          file <- list
          name <- file.getName.?

          if !knownMods.contains( name ) &&
            file.isFile &&
            name.endsWith( ".jar" )
        } {
          knownMods +:= name
          FMLLog.info( s"[ DependencyResolver ] Check manifest of $name" )

          tryToEither ( new JarFile( file ) ) match {
            case Left( error ) =>
              FMLLog.warning( s"[ DependencyResolver ] Jar file open error: ${ error.getMessage }" )

            case Right( jar ) =>
              for {
                manifest   <- jar.getManifest.?
                attributes <- manifest.getMainAttributes.?
                attribute  <- attributes.getValue( ATTRIBUTE_DEPENDS_JSON ).?
                zipEntry   <- jar.getEntry( attribute ).?

              } { tryToEither ( jar.getInputStream( zipEntry ) ) match {
                case Left( error ) =>
                  FMLLog.warning( s"[ DependencyResolver ] Jar file reading error: ${ error.getMessage }" )

                case Right( input ) =>
                  FMLLog.info( s"[ DependencyResolver ] Try resolve -> $file" )

                  val json = IOUtils.toString( input, Charsets.UTF_8 )
                  val gson = new Gson().fromJson( json, classOf[ DependsJson ] )

                  resolvers.foreach { resolver =>
                    val data = Seq( mc, libs, mods, defs, InjectionData.ResolvedURLs( resolvedURLs ) )
                    resolver.injectData( data )

                    if ( resolver.resolvable( gson ) )
                      resolver.resolve( gson ) match {
                        case Left( error ) =>
                          FMLLog.warning( s"[ DependencyResolver ] Resolve failed: $error -> $file" )

                        case Right( urls ) =>
                          if ( resolver.requiresContinue ) continue = true
                          urls.filterNot( resolvedURLs.contains )
                            .foreach { url =>
                              FMLLog.info( s"[ DependencyResolver ] Resolved URL -> $url" )
                              resolvedURLs +:= url
                              if ( resolver.requiresUrlRegister ) classLoader.addURL( url )
                            }

                  } }

                  IOUtils.closeQuietly( input )
              } }
              jar.close()
        } }
      } while ( continue )

      FMLLog.info( s"[ DependencyResolver ] Task completed." )
  } }

  override def getLaunchTarget: String = null
  override def getLaunchArguments: Array[ String ] = Array.empty

}
